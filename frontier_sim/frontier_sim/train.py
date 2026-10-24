"""Pretraining loop: AdamW, linear warmup + cosine decay, gradient clipping.

A run writes to its output directory:
  config.json    the fully resolved config (reproduce with `run config.json`)
  metrics.jsonl  one JSON object per log/eval step
  summary.json   final metrics, parameter counts, compute used
  model.pt       final weights (if train.save_checkpoint)
"""

from __future__ import annotations

import json
import math
import random
import time
from pathlib import Path

import torch

from .config import ExperimentConfig, TrainConfig
from .model import IGNORE_INDEX, Transformer
from .tasks import Task, build_task, make_batch

EVAL_SEED = 1234  # fixed so every run is scored on identical eval batches


def pick_device(name: str) -> torch.device:
    if name != "auto":
        return torch.device(name)
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def lr_at(step: int, tc: TrainConfig) -> float:
    if step < tc.warmup_steps:
        return tc.lr * (step + 1) / tc.warmup_steps
    progress = (step - tc.warmup_steps) / max(1, tc.steps - tc.warmup_steps)
    cosine = 0.5 * (1 + math.cos(math.pi * min(1.0, progress)))
    return tc.lr * (tc.min_lr_ratio + (1 - tc.min_lr_ratio) * cosine)


def build_optimizer(model: torch.nn.Module, tc: TrainConfig) -> torch.optim.Optimizer:
    # Decay matrices only; norms/gains are exempt.
    decay = [p for p in model.parameters() if p.requires_grad and p.dim() >= 2]
    no_decay = [p for p in model.parameters() if p.requires_grad and p.dim() < 2]
    groups = [{"params": decay, "weight_decay": tc.weight_decay},
              {"params": no_decay, "weight_decay": 0.0}]
    return torch.optim.AdamW(groups, lr=tc.lr, betas=(tc.beta1, tc.beta2))


@torch.no_grad()
def evaluate(model: Transformer, task: Task, batch_size: int, n_batches: int,
             device: torch.device) -> dict[str, float]:
    """Masked loss and answer-token accuracy for each task component and split."""
    model.eval()
    results: dict[str, float] = {}
    for name, comp in task.components().items():
        for split in ("train", "eval"):
            rng = random.Random(f"{EVAL_SEED}-{name}-{split}")
            loss_sum, correct, count = 0.0, 0, 0
            for _ in range(n_batches):
                x, y = make_batch(comp, batch_size, model.cfg.seq_len, rng, split)
                x, y = x.to(device), y.to(device)
                logits = model(x)["logits"].float()
                m = y != IGNORE_INDEX
                loss_sum += torch.nn.functional.cross_entropy(logits[m], y[m], reduction="sum").item()
                correct += (logits[m].argmax(-1) == y[m]).sum().item()
                count += m.sum().item()
            results[f"{split}/{name}/loss"] = loss_sum / max(1, count)
            results[f"{split}/{name}/acc"] = correct / max(1, count)
    model.train()
    return results


def train(cfg: ExperimentConfig, out_dir: str | Path, verbose: bool = True) -> dict:
    tc = cfg.train
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / "config.json").write_text(json.dumps(cfg.to_dict(), indent=2))

    torch.manual_seed(tc.seed)
    device = pick_device(tc.device)
    task = build_task(cfg.task)
    if task.tokens_needed > cfg.model.vocab_size:
        raise ValueError(f"task needs vocab_size >= {task.tokens_needed}, model has {cfg.model.vocab_size}")

    model = Transformer(cfg.model).to(device)
    step_fn = torch.compile(model) if tc.compile else model
    opt = build_optimizer(model, tc)
    counts = model.param_counts()
    flops_per_token = model.flops_per_token()
    tokens_per_step = tc.batch_size * cfg.model.seq_len
    use_bf16 = device.type == "cuda" and torch.cuda.is_bf16_supported()
    if verbose:
        print(f"[{cfg.name}] device={device} params={counts} "
              f"flops/token={flops_per_token:.3g} tokens/step={tokens_per_step}")

    rng = random.Random(tc.seed)
    log = open(out / "metrics.jsonl", "w")
    t0 = time.time()
    last_eval: dict[str, float] = {}
    for step in range(tc.steps):
        lr = lr_at(step, tc)
        for g in opt.param_groups:
            g["lr"] = lr
        x, y = make_batch(task, tc.batch_size, cfg.model.seq_len, rng, "train")
        x, y = x.to(device), y.to(device)
        with torch.autocast(device.type, dtype=torch.bfloat16, enabled=use_bf16):
            res = step_fn(x, y)
        opt.zero_grad(set_to_none=True)
        res["loss"].backward()
        grad_norm = torch.nn.utils.clip_grad_norm_(model.parameters(), tc.grad_clip).item()
        opt.step()

        done = step + 1
        if done % tc.log_every == 0 or done == tc.steps:
            rec = {"step": done, "loss": res["ce"].item(), "lr": lr, "grad_norm": grad_norm,
                   "tokens": done * tokens_per_step, "elapsed_s": round(time.time() - t0, 2)}
            if "moe_aux" in res:
                rec["moe_aux"] = res["moe_aux"].item()
                rec["moe_max_load"] = max(m.load.max().item() for m in model.moe_layers())
            if not math.isfinite(rec["loss"]):
                rec["diverged"] = True
                log.write(json.dumps(rec) + "\n")
                print(f"[{cfg.name}] loss diverged at step {done}; stopping")
                break
            log.write(json.dumps(rec) + "\n")
            log.flush()
            if verbose:
                print(f"step {done:6d} loss {rec['loss']:.4f} lr {lr:.2e} gnorm {grad_norm:.2f}")
        if done % tc.eval_every == 0 or done == tc.steps:
            last_eval = evaluate(model, task, tc.batch_size, tc.eval_batches, device)
            log.write(json.dumps({"step": done, "eval": last_eval}) + "\n")
            log.flush()
            if verbose:
                accs = {k.split("/")[1]: round(v, 3) for k, v in last_eval.items()
                        if k.startswith("eval/") and k.endswith("/acc")}
                print(f"  eval acc {accs}")
    log.close()

    summary = {
        "name": cfg.name,
        "steps": done,
        "params": counts,
        "flops_per_token": flops_per_token,
        "train_flops": flops_per_token * done * tokens_per_step,
        "tokens": done * tokens_per_step,
        "wall_time_s": round(time.time() - t0, 2),
        "final": last_eval,
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=2))
    if tc.save_checkpoint:
        torch.save({"model": model.state_dict(), "config": cfg.to_dict()}, out / "model.pt")
    return summary


def load_model(run_dir: str | Path, device: str = "cpu") -> tuple[Transformer, ExperimentConfig]:
    """Reload a trained run for analysis (probing, patching, generation)."""
    from .config import from_dict

    ckpt = torch.load(Path(run_dir) / "model.pt", map_location=device)
    cfg = from_dict(ckpt["config"])
    model = Transformer(cfg.model).to(device)
    model.load_state_dict(ckpt["model"])
    return model.eval(), cfg

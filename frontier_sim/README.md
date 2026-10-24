# frontier_sim

A small, hackable **model organism of a frontier language model**. It uses the same architecture and
training recipe as current frontier LLMs, scaled down so an experiment runs in minutes on a CPU. Every
component is a config switch, so you can change one thing and measure the effect.

The research plan (goals, protocol, roadmap, experiment queue) is in **[PLAN.md](PLAN.md)**.

## Setup

```bash
cd frontier_sim
pip install -r requirements.txt     # CPU-only torch: add --index-url https://download.pytorch.org/whl/cpu
python -m pytest -q tests           # 29 tests, about 1 minute on CPU
```

## Use

```bash
# Inspect a config: resolved values, parameter counts, FLOPs per token
python -m frontier_sim show experiments/baseline.json

# Train one model (≈5 min on 4 CPU cores); results land in runs/baseline/
python -m frontier_sim run experiments/baseline.json

# Test an idea by overriding any field
python -m frontier_sim run experiments/baseline.json --set model.n_experts=8 --set model.expert_d_ff=88 --out runs/moe8

# Compare variants × seeds and write runs/ablations/report.md (mean ± std)
python -m frontier_sim sweep experiments/ablations.json

# Scaling ladder with a power-law fit
python -m frontier_sim sweep experiments/scaling.json
```

Run one training process at a time on a small machine. Several PyTorch processes on the same few cores
slow each other down a lot.

## What you can change

| Area | Switches |
|---|---|
| Attention | `n_heads`, `n_kv_heads` (GQA/MQA), `qk_norm`, `sliding_window`, `pos` (`rope`/`learned`/`none`), `rope_base` |
| Blocks | `mlp` (`swiglu`/`gelu`), `norm` (`rms`/`layer`), `d_model`, `n_layers`, `d_ff`, `logit_softcap`, `tie_embeddings` |
| Mixture of experts | `n_experts`, `moe_top_k`, `expert_d_ff`, `moe_every`, `moe_aux_loss_coef` |
| Training | `lr`, `warmup_steps`, `min_lr_ratio`, `weight_decay`, `beta1/2`, `grad_clip`, `batch_size`, `steps`, `seed` |
| Data | `task`: `assoc_recall`, `multihop`, `modadd`, `copy`, `text` (your own file), or a weighted `mixture` |

To add a new idea that config can't express, add a switch to `ModelConfig`/`TrainConfig` whose default
keeps the current behavior, branch on it in `model.py`/`train.py`, add a test, and add a sweep variant.

## Layout

```
frontier_sim/
  config.py   dataclass configs, JSON loading, dotted overrides
  model.py    transformer: RMSNorm, RoPE, GQA, QK-norm, SwiGLU, MoE, sliding window, soft-cap
  tasks.py    synthetic capability probes and data mixtures
  train.py    AdamW + warmup/cosine training loop, per-task evals, checkpoints
  sweep.py    variants × seeds runner, report.md, power-law fit
experiments/  baseline.json, ablations.json, scaling.json, smoke.json
tests/        invariants: causality, masking, accounting, config validation, learning
```

For analysis, `train.load_model(run_dir)` reloads a checkpoint, and
`model(x, return_hidden=True)["hidden"]` returns the residual stream at every layer for probing or
patching.

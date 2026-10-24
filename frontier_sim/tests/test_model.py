import random

import pytest
import torch

from frontier_sim import config as config_mod
from frontier_sim.model import IGNORE_INDEX, Transformer
from frontier_sim.tasks import AssocRecall, Copy, ModAdd, MultiHop, build_task, make_batch
from frontier_sim.train import train

SMALL = dict(vocab_size=256, seq_len=32, d_model=32, n_layers=2, n_heads=4)

VARIANTS = [
    {},
    {"n_kv_heads": 1},
    {"n_kv_heads": 2, "qk_norm": False},
    {"mlp": "gelu", "norm": "layer", "pos": "learned", "tie_embeddings": False},
    {"pos": "none"},
    {"sliding_window": 8},
    {"logit_softcap": 15.0},
    {"n_experts": 4, "moe_top_k": 2},
    {"n_experts": 4, "moe_top_k": 1, "moe_every": 2, "expert_d_ff": 16},
]


def make(**kw):
    return Transformer(config_mod.ModelConfig(**{**SMALL, **kw}))


@pytest.mark.parametrize("variant", VARIANTS, ids=lambda v: ",".join(f"{k}={x}" for k, x in v.items()) or "default")
def test_forward_backward(variant):
    torch.manual_seed(0)
    model = make(**variant)
    x = torch.randint(0, 256, (2, 32))
    out = model(x, x)
    assert out["logits"].shape == (2, 32, 256)
    assert torch.isfinite(out["loss"])
    out["loss"].backward()


@pytest.mark.parametrize("variant", VARIANTS, ids=lambda v: ",".join(f"{k}={x}" for k, x in v.items()) or "default")
def test_causal(variant):
    """Changing a future token must not change earlier logits."""
    torch.manual_seed(0)
    model = make(**variant).eval()
    x = torch.randint(0, 256, (1, 32))
    x2 = x.clone()
    x2[0, 20] = (x2[0, 20] + 1) % 256
    a, b = model(x)["logits"], model(x2)["logits"]
    assert torch.allclose(a[:, :20], b[:, :20], atol=1e-5)
    assert not torch.allclose(a[:, 20:], b[:, 20:])


def test_sliding_window_limits_context():
    torch.manual_seed(0)
    model = make(sliding_window=4, n_layers=1).eval()
    x = torch.randint(0, 256, (1, 32))
    x2 = x.clone()
    x2[0, 0] += 1
    a, b = model(x)["logits"], model(x2)["logits"]
    # one layer, window 4: position 10 cannot see position 0
    assert torch.allclose(a[:, 10:], b[:, 10:], atol=1e-5)


def test_moe_param_accounting():
    dense = make()
    moe = make(n_experts=4, moe_top_k=1)
    c = moe.param_counts()
    assert c["active_non_embedding"] < c["non_embedding"]
    # top-1 MoE with full-size experts has the same active params as dense, plus the router
    router = 2 * 32 * 4
    assert c["active_non_embedding"] == dense.param_counts()["non_embedding"] + router


def test_config_overrides_and_unknown_keys():
    data = {"model": dict(SMALL), "task": {"name": "copy"}}
    cfg = config_mod.from_dict(config_mod.apply_overrides(data, ["model.mlp=gelu", "train.steps=5"]))
    assert cfg.model.mlp == "gelu" and cfg.train.steps == 5
    with pytest.raises(ValueError):
        config_mod.from_dict(config_mod.apply_overrides(data, ["model.d_modle=64"]))


@pytest.mark.parametrize("task", [AssocRecall(), MultiHop(), ModAdd(), Copy()], ids=lambda t: t.name)
def test_tasks_score_only_answers(task):
    x, y = make_batch(task, 4, 64, random.Random(0))
    assert x.shape == y.shape == (4, 64)
    scored = y != IGNORE_INDEX
    assert scored.any()
    assert (y[scored] >= 16).all() and (y[scored] < task.tokens_needed).all()
    # targets are next tokens wherever scored
    assert torch.equal(y[:, :-1][scored[:, :-1]], x[:, 1:][scored[:, :-1]])


def test_assoc_recall_answers_are_consistent():
    task = AssocRecall(n_pairs=4)
    toks, scored = task.sample_row(64, random.Random(0), "train")
    table = {toks[i]: toks[i + 1] for i in range(0, 8, 2)}
    for i, s in enumerate(scored):
        if s:
            assert toks[i - 2] == 2 and table[toks[i - 1]] == toks[i]


def test_modadd_splits_disjoint():
    t = ModAdd(p=11)
    assert not set(t.pairs["train"]) & set(t.pairs["eval"])
    assert len(t.pairs["train"]) + len(t.pairs["eval"]) == 121
    # mirrored pairs share a split, so commutativity can't leak eval answers
    train = set(t.pairs["train"])
    assert all((b, a) not in train for a, b in t.pairs["eval"])


def test_training_reduces_loss(tmp_path):
    cfg = config_mod.from_dict({
        "model": {**SMALL, "seq_len": 64},
        "train": {"steps": 60, "batch_size": 16, "lr": 3e-3, "warmup_steps": 5, "eval_every": 60,
                  "eval_batches": 2, "log_every": 10, "save_checkpoint": False},
        "task": {"name": "copy", "length": 8, "n_symbols": 8},
    })
    s = train(cfg, tmp_path, verbose=False)
    assert s["final"]["eval/copy/loss"] < 3.0  # uniform over the 256-token vocab is ~5.5
    assert (tmp_path / "metrics.jsonl").exists()


def test_mixture_components():
    t = build_task({"name": "mixture", "components": {
        "a": {"task": "multihop", "hops": 1}, "b": {"task": "multihop", "hops": 3}, "c": {"task": "copy", "weight": 0}}})
    assert set(t.components()) == {"a", "b"}


def test_power_law_fit_needs_a_size_range():
    from frontier_sim.sweep import fit_power_law

    assert fit_power_law([(700_000, 2.0), (705_000, 1.9), (740_000, 1.8)]) is None
    a, b, r2 = fit_power_law([(1e5, 3.0 * 1e5 ** -0.1), (1e6, 3.0 * 1e6 ** -0.1), (1e7, 3.0 * 1e7 ** -0.1)])
    assert abs(b - 0.1) < 1e-6 and r2 > 0.999


def test_stop_when_and_solved_step(tmp_path):
    cfg = config_mod.from_dict({
        "model": {**SMALL, "seq_len": 64},
        "train": {"steps": 400, "batch_size": 16, "lr": 3e-3, "warmup_steps": 5, "eval_every": 20,
                  "eval_batches": 2, "log_every": 20, "save_checkpoint": False,
                  "stop_when": {"eval/copy/acc": 0.9}},
        "task": {"name": "copy", "length": 4, "n_symbols": 4},
    })
    s = train(cfg, tmp_path, verbose=False)
    assert s["stopped_early"] and s["steps"] < 400
    assert s["solved_step"]["eval/copy/acc"] == s["steps"]


def test_stop_when_rejects_unknown_metric(tmp_path):
    cfg = config_mod.from_dict({
        "model": SMALL,
        "train": {"steps": 2, "batch_size": 2, "eval_every": 1, "eval_batches": 1,
                  "save_checkpoint": False, "stop_when": {"eval/cpy/acc": 0.9}},
        "task": {"name": "copy", "length": 4},
    })
    with pytest.raises(ValueError, match="unknown metrics"):
        train(cfg, tmp_path, verbose=False)


def test_parallel_sweep(tmp_path):
    import json

    from frontier_sim.sweep import run_sweep

    spec = {"name": "t", "seeds": [0, 1],
            "base": {"model": SMALL, "task": {"name": "copy", "length": 4},
                     "train": {"steps": 4, "batch_size": 2, "eval_every": 4, "eval_batches": 1,
                               "log_every": 4, "save_checkpoint": False}},
            "variants": {"a": {}, "b": {"model.mlp": "gelu"}}}
    path = tmp_path / "spec.json"
    path.write_text(json.dumps(spec))
    out = run_sweep(path, tmp_path / "runs", workers=2)
    assert len(list(out.glob("*/seed*/summary.json"))) == 4
    assert "solved" in (out / "report.md").read_text()

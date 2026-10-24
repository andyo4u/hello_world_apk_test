"""Experiment configuration.

Everything an experiment can change lives in one of three places:
  model  -> ModelConfig  (architecture)
  train  -> TrainConfig  (optimizer, schedule, budget)
  task   -> plain dict   (data / capability probes, see tasks.py)

Configs are JSON files. Any field can be overridden from the command line with a
dotted path, e.g. `--set model.n_experts=8 --set task.components.copy.weight=0`.
Unknown keys are an error so a typo can't silently run the baseline.
"""

from __future__ import annotations

import copy
import dataclasses
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class ModelConfig:
    vocab_size: int = 256
    seq_len: int = 128
    d_model: int = 128
    n_layers: int = 4
    n_heads: int = 4
    n_kv_heads: int | None = None  # < n_heads -> grouped-query attention; 1 -> multi-query
    d_ff: int | None = None  # None -> 8/3*d (swiglu) or 4*d (gelu), rounded to 32
    mlp: str = "swiglu"  # "swiglu" | "gelu"
    norm: str = "rms"  # "rms" | "layer"
    pos: str = "rope"  # "rope" | "learned" | "none"
    rope_base: float = 10000.0
    qk_norm: bool = True
    sliding_window: int | None = None  # None -> full causal attention
    logit_softcap: float | None = None
    tie_embeddings: bool = True
    # Mixture of experts. 0 -> dense MLP in every block.
    n_experts: int = 0
    moe_top_k: int = 2
    expert_d_ff: int | None = None  # None -> d_ff (set d_ff/top_k to compute-match dense)
    moe_aux_loss_coef: float = 0.01
    moe_every: int = 1  # MoE in every k-th block, dense elsewhere

    @property
    def kv_heads(self) -> int:
        return self.n_kv_heads or self.n_heads

    @property
    def ff_dim(self) -> int:
        if self.d_ff is not None:
            return self.d_ff
        raw = 8 * self.d_model / 3 if self.mlp == "swiglu" else 4 * self.d_model
        return int(32 * round(raw / 32))

    def validate(self) -> None:
        assert self.d_model % self.n_heads == 0, "d_model must divide by n_heads"
        assert self.n_heads % self.kv_heads == 0, "n_heads must divide by n_kv_heads"
        assert self.mlp in ("swiglu", "gelu"), self.mlp
        assert self.norm in ("rms", "layer"), self.norm
        assert self.pos in ("rope", "learned", "none"), self.pos
        if self.pos == "rope":
            assert (self.d_model // self.n_heads) % 2 == 0, "rope needs an even head dim"
        if self.n_experts:
            assert 1 <= self.moe_top_k <= self.n_experts


@dataclass
class TrainConfig:
    steps: int = 2000
    batch_size: int = 32
    lr: float = 3e-3
    min_lr_ratio: float = 0.1
    warmup_steps: int = 100
    weight_decay: float = 0.1
    beta1: float = 0.9
    beta2: float = 0.95
    grad_clip: float = 1.0
    eval_every: int = 250
    eval_batches: int = 8
    log_every: int = 50
    seed: int = 0
    device: str = "auto"  # "auto" | "cpu" | "cuda" | "mps"
    compile: bool = False
    save_checkpoint: bool = True
    # Stop early once every listed eval metric reaches its threshold, e.g.
    # {"eval/assoc_recall/acc": 0.9}. Useful when measuring time-to-capability.
    stop_when: dict[str, float] | None = None


@dataclass
class ExperimentConfig:
    name: str = "experiment"
    model: ModelConfig = field(default_factory=ModelConfig)
    train: TrainConfig = field(default_factory=TrainConfig)
    task: dict[str, Any] = field(default_factory=lambda: {"name": "assoc_recall"})

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def _parse_value(raw: str) -> Any:
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw  # bare strings: --set model.mlp=gelu


def apply_overrides(cfg: dict[str, Any], overrides: dict[str, Any] | list[str]) -> dict[str, Any]:
    """Return a copy of a nested dict with dotted-path overrides applied.

    `overrides` is either {"model.d_model": 256} or ["model.d_model=256"].
    """
    cfg = copy.deepcopy(cfg)
    if isinstance(overrides, list):
        pairs = {}
        for item in overrides:
            key, sep, raw = item.partition("=")
            if not sep:
                raise ValueError(f"override must look like key=value, got {item!r}")
            pairs[key.strip()] = _parse_value(raw.strip())
        overrides = pairs
    for dotted, value in overrides.items():
        node = cfg
        *parents, leaf = dotted.split(".")
        for p in parents:
            node = node.setdefault(p, {})
            if not isinstance(node, dict):
                raise ValueError(f"cannot override {dotted!r}: {p!r} is not a section")
        node[leaf] = value
    return cfg


def _build(cls, data: dict[str, Any], where: str):
    names = {f.name for f in dataclasses.fields(cls)}
    unknown = set(data) - names
    if unknown:
        raise ValueError(f"unknown {where} keys: {sorted(unknown)}")
    return cls(**data)


def from_dict(data: dict[str, Any]) -> ExperimentConfig:
    data = dict(data)
    unknown = set(data) - {"name", "model", "train", "task"}
    if unknown:
        raise ValueError(f"unknown top-level keys: {sorted(unknown)}")
    cfg = ExperimentConfig(
        name=data.get("name", "experiment"),
        model=_build(ModelConfig, data.get("model", {}), "model"),
        train=_build(TrainConfig, data.get("train", {}), "train"),
        task=data.get("task", {"name": "assoc_recall"}),
    )
    cfg.model.validate()
    return cfg


def load_dict(path: str | Path) -> dict[str, Any]:
    with open(path) as f:
        return json.load(f)


def load(path: str | Path, overrides: dict[str, Any] | list[str] | None = None) -> ExperimentConfig:
    data = load_dict(path)
    if overrides:
        data = apply_overrides(data, overrides)
    return from_dict(data)

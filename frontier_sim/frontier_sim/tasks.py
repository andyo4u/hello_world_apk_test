"""Synthetic "worlds" that stand in for pretraining data.

Each task isolates a capability frontier models are measured on, and has an exact
right answer, so small models give clean, low-noise signals:

  assoc_recall  in-context retrieval (key -> value lookup; the induction-head task)
  multihop      compositional reasoning (follow k facts a->b->c in context)
  modadd        generalization vs memorization (held-out (a, b) pairs)
  copy          long-range exact copying (context-length stress)
  text          ordinary byte-level language modeling on any file you supply
  mixture       weighted blend of the above, like a pretraining data mix

Only answer tokens are scored (others get IGNORE_INDEX), so loss and accuracy
measure the capability, not how well the model predicts random keys.
Token layout: 0..15 are reserved control tokens, content tokens start at 16.
"""

from __future__ import annotations

import random
from pathlib import Path
from typing import Any

import torch

from .model import IGNORE_INDEX

PAD, SEP, QUERY, EQ = 0, 1, 2, 3
FIRST = 16

Row = tuple[list[int], list[bool]]


class Task:
    name = "task"
    tokens_needed = FIRST  # highest token id used + 1

    def sample_row(self, n: int, rng: random.Random, split: str) -> Row:
        """Return n tokens and an is-scored flag per token."""
        raise NotImplementedError

    def components(self) -> dict[str, "Task"]:
        return {self.name: self}


def _fill(n: int, prefix: Row, unit) -> Row:
    """Append units from unit() after prefix until the next one won't fit, then pad."""
    toks, scored = list(prefix[0]), list(prefix[1])
    while True:
        t, s = unit()
        if len(toks) + len(t) > n:
            break
        toks += t
        scored += s
    pad = n - len(toks)
    return toks + [PAD] * pad, scored + [False] * pad


class AssocRecall(Task):
    name = "assoc_recall"

    def __init__(self, n_pairs: int = 8, n_keys: int = 64, n_values: int = 64):
        assert n_pairs <= n_keys
        self.n_pairs, self.n_keys, self.n_values = n_pairs, n_keys, n_values
        self.tokens_needed = FIRST + n_keys + n_values

    def sample_row(self, n, rng, split):
        keys = rng.sample(range(FIRST, FIRST + self.n_keys), self.n_pairs)
        vals = [rng.randrange(FIRST + self.n_keys, self.tokens_needed) for _ in keys]
        toks = [t for kv in zip(keys, vals) for t in kv] + [SEP]
        assert len(toks) + 3 <= n, "seq_len too short for n_pairs"

        def query():
            i = rng.randrange(self.n_pairs)
            return [QUERY, keys[i], vals[i]], [False, False, True]

        return _fill(n, (toks, [False] * len(toks)), query)


class MultiHop(Task):
    name = "multihop"

    def __init__(self, n_chains: int = 4, hops: int = 2, n_entities: int = 128):
        assert n_chains * (hops + 1) <= n_entities
        self.n_chains, self.hops, self.n_entities = n_chains, hops, n_entities
        self.tokens_needed = FIRST + n_entities

    def sample_row(self, n, rng, split):
        ents = rng.sample(range(FIRST, self.tokens_needed), self.n_chains * (self.hops + 1))
        chains = [ents[i * (self.hops + 1):(i + 1) * (self.hops + 1)] for i in range(self.n_chains)]
        facts = [(c[j], c[j + 1]) for c in chains for j in range(self.hops)]
        rng.shuffle(facts)
        toks = [t for a, b in facts for t in (a, b, SEP)]
        assert len(toks) + 4 <= n, "seq_len too short for this many facts"

        def query():
            c = chains[rng.randrange(self.n_chains)]
            return [QUERY, c[0], EQ, c[-1]], [False, False, False, True]

        return _fill(n, (toks, [False] * len(toks)), query)


class ModAdd(Task):
    name = "modadd"

    def __init__(self, p: int = 53, train_frac: float = 0.5, split_seed: int = 0):
        self.p = p
        self.tokens_needed = FIRST + p
        pairs = [(a, b) for a in range(p) for b in range(p)]
        random.Random(split_seed).shuffle(pairs)
        cut = int(train_frac * len(pairs))
        self.pairs = {"train": pairs[:cut], "eval": pairs[cut:]}

    def sample_row(self, n, rng, split):
        pairs = self.pairs[split]

        def unit():
            a, b = pairs[rng.randrange(len(pairs))]
            return [FIRST + a, FIRST + b, EQ, FIRST + (a + b) % self.p, SEP], [False, False, False, True, False]

        return _fill(n, ([], []), unit)


class Copy(Task):
    name = "copy"

    def __init__(self, length: int = 16, n_symbols: int = 64):
        self.length, self.n_symbols = length, n_symbols
        self.tokens_needed = FIRST + n_symbols

    def sample_row(self, n, rng, split):
        def unit():
            s = [rng.randrange(FIRST, self.tokens_needed) for _ in range(self.length)]
            return s + [SEP] + s + [SEP], [False] * (self.length + 1) + [True] * self.length + [False]

        assert 2 * self.length + 2 <= n, "seq_len too short for copy length"
        return _fill(n, ([], []), unit)


class Text(Task):
    """Byte-level LM on a local file: first 90% train, last 10% eval."""

    name = "text"
    tokens_needed = 256

    def __init__(self, path: str):
        data = Path(path).read_bytes()
        assert len(data) > 10_000, "text file is too small to be useful"
        cut = int(0.9 * len(data))
        self.data = {"train": data[:cut], "eval": data[cut:]}

    def sample_row(self, n, rng, split):
        d = self.data[split]
        i = rng.randrange(len(d) - n)
        return list(d[i:i + n]), [True] * n


class Mixture(Task):
    name = "mixture"

    def __init__(self, components: dict[str, dict[str, Any]]):
        self.tasks: dict[str, Task] = {}
        self.weights: list[float] = []
        for name, spec in components.items():
            spec = dict(spec)
            weight = spec.pop("weight", 1.0)
            if weight <= 0:
                continue
            self.tasks[name] = build_task({"name": spec.pop("task", name), **spec})
            self.weights.append(weight)
        assert self.tasks, "mixture has no components with positive weight"
        self.tokens_needed = max(t.tokens_needed for t in self.tasks.values())
        self._order = list(self.tasks.values())

    def sample_row(self, n, rng, split):
        return rng.choices(self._order, weights=self.weights)[0].sample_row(n, rng, split)

    def components(self):
        return dict(self.tasks)


REGISTRY = {cls.name: cls for cls in (AssocRecall, MultiHop, ModAdd, Copy, Text, Mixture)}


def build_task(spec: dict[str, Any]) -> Task:
    spec = dict(spec)
    name = spec.pop("name")
    if name not in REGISTRY:
        raise ValueError(f"unknown task {name!r}; choose from {sorted(REGISTRY)}")
    return REGISTRY[name](**spec)


def make_batch(task: Task, batch_size: int, seq_len: int, rng: random.Random,
               split: str = "train") -> tuple[torch.Tensor, torch.Tensor]:
    """Inputs x and next-token targets y (unscored targets set to IGNORE_INDEX)."""
    rows = [task.sample_row(seq_len + 1, rng, split) for _ in range(batch_size)]
    toks = torch.tensor([r[0] for r in rows], dtype=torch.long)
    scored = torch.tensor([r[1] for r in rows], dtype=torch.bool)
    y = toks[:, 1:].clone()
    y[~scored[:, 1:]] = IGNORE_INDEX
    return toks[:, :-1], y

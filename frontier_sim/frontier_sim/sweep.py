"""Run a set of config variants over several seeds and compare them.

A sweep spec is JSON:
  {
    "name": "ablations",
    "base": "baseline.json",              # path relative to the spec, or an inline config
    "seeds": [0, 1, 2],
    "common": {"train.steps": 1500},       # optional overrides applied to every variant
    "variants": {
      "baseline": {},
      "moe": {"model.n_experts": 4, "model.expert_d_ff": 176}
    }
  }
Each run goes to <out>/<variant>/seed<k>/ and a report.md comparing variants
(mean ± std across seeds) is written to <out>/.
"""

from __future__ import annotations

import json
import math
import statistics
from pathlib import Path

from . import config as config_mod
from .train import SOLVED_ACC, train

MIN_SIZE_RATIO = 2.0  # fit a scaling law only when variant sizes span at least this ratio


def _run_one(data: dict, run_dir: str, verbose: bool, threads: int | None) -> str:
    if threads:
        import torch

        torch.set_num_threads(threads)
    train(config_mod.from_dict(data), run_dir, verbose=verbose)
    return data["name"]


def run_sweep(spec_path: str | Path, out_root: str | Path, only: list[str] | None = None,
              verbose: bool = False, workers: int = 1) -> Path:
    """Train every variant x seed not already finished, then write report.md.

    workers > 1 runs that many single-threaded trainings in parallel; for small models
    this gets more done per second than one multi-threaded run.
    """
    spec_path = Path(spec_path)
    spec = config_mod.load_dict(spec_path)
    base = spec["base"]
    base = config_mod.load_dict(spec_path.parent / base) if isinstance(base, str) else base
    base = config_mod.apply_overrides(base, spec.get("common", {}))
    out = Path(out_root) / spec.get("name", spec_path.stem)
    jobs = []
    for variant, overrides in spec["variants"].items():
        if only and variant not in only:
            continue
        for seed in spec.get("seeds", [0]):
            run_dir = out / variant / f"seed{seed}"
            if (run_dir / "summary.json").exists():
                print(f"skip {variant}/seed{seed} (done)")
                continue
            data = config_mod.apply_overrides(base, {**overrides, "train.seed": seed})
            data["name"] = f"{variant}/seed{seed}"
            config_mod.from_dict(data)  # fail on a bad variant before any training starts
            jobs.append((data, str(run_dir)))

    if workers <= 1:
        for data, run_dir in jobs:
            print(f"=== {data['name']}")
            _run_one(data, run_dir, verbose, None)
    else:
        import multiprocessing as mp
        from concurrent.futures import ProcessPoolExecutor, as_completed

        with ProcessPoolExecutor(workers, mp_context=mp.get_context("spawn")) as pool:
            futures = [pool.submit(_run_one, data, run_dir, False, 1) for data, run_dir in jobs]
            for i, fut in enumerate(as_completed(futures), 1):
                print(f"=== done {fut.result()} ({i}/{len(jobs)})", flush=True)
    report = compare(out)
    print(report)
    return out


def _mean_std(xs: list[float]) -> str:
    if len(xs) == 1:
        return f"{xs[0]:.3f}"
    return f"{statistics.mean(xs):.3f} ± {statistics.stdev(xs):.3f}"


def compare(sweep_dir: str | Path, metric_filter: str = "eval/") -> str:
    """Write and return a markdown table comparing variants in a sweep directory."""
    sweep_dir = Path(sweep_dir)
    rows: dict[str, list[dict]] = {}
    for summary in sorted(sweep_dir.glob("*/seed*/summary.json")):
        rows.setdefault(summary.parent.parent.name, []).append(json.loads(summary.read_text()))
    if not rows:
        return "no finished runs found"
    metrics = sorted({k for runs in rows.values() for r in runs for k in r["final"]
                      if k.startswith(metric_filter)})
    header = ["variant", "seeds", "active params", "train PFLOPs"] + metrics
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    scaling = []
    for variant, runs in rows.items():
        active = runs[0]["params"]["active_non_embedding"]
        pflops = runs[0]["train_flops"] / 1e15
        cells = [variant, str(len(runs)), f"{active:,}", f"{pflops:.3f}"]
        for m in metrics:
            vals = [r["final"][m] for r in runs if m in r["final"]]
            cell = _mean_std(vals) if vals else "-"
            if vals and m.endswith("/acc") and len(vals) > 1:
                # Capability onsets are often bimodal across seeds; show how many seeds got there.
                cell += f" ({sum(v >= SOLVED_ACC for v in vals)}/{len(vals)} solved"
                steps = [r["solved_step"][m] for r in runs if m in r.get("solved_step", {})]
                cell += f", median step {statistics.median(steps):g})" if steps else ")"
            cells.append(cell)
        lines.append("| " + " | ".join(cells) + " |")
        losses = [r["final"][k] for r in runs for k in r["final"] if k.startswith("eval/") and k.endswith("/loss")]
        if losses:
            scaling.append((active, statistics.mean(losses)))

    text = "\n".join(lines)
    fit = fit_power_law(scaling)
    if fit:
        text += (f"\n\nPower-law fit over variants (mean eval loss vs active params): "
                 f"loss ≈ {fit[0]:.3g} · N^({-fit[1]:.3f})  (R² = {fit[2]:.3f})")
    (sweep_dir / "report.md").write_text(text + "\n")
    return text


def fit_power_law(points: list[tuple[float, float]]) -> tuple[float, float, float] | None:
    """Least squares in log-log space; None unless the variants span a real range of sizes."""
    pts = [(math.log(n), math.log(l)) for n, l in points if n > 0 and l > 0]
    xs = [x for x, _ in pts]
    if len(set(xs)) < 3 or max(xs) - min(xs) < math.log(MIN_SIZE_RATIO):
        return None
    mx = statistics.mean(x for x, _ in pts)
    my = statistics.mean(y for _, y in pts)
    sxx = sum((x - mx) ** 2 for x, _ in pts)
    sxy = sum((x - mx) * (y - my) for x, y in pts)
    slope = sxy / sxx
    intercept = my - slope * mx
    ss_res = sum((y - (intercept + slope * x)) ** 2 for x, y in pts)
    ss_tot = sum((y - my) ** 2 for _, y in pts) or 1e-12
    return math.exp(intercept), -slope, 1 - ss_res / ss_tot

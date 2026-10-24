"""Command line entry point.

  python -m frontier_sim run experiments/baseline.json [--set key=value ...] [--out DIR]
  python -m frontier_sim sweep experiments/ablations.json [--only v1 v2] [--out DIR] [-j 4]
  python -m frontier_sim compare runs/ablations
  python -m frontier_sim show experiments/baseline.json [--set ...]   # params/FLOPs, no training
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from . import config as config_mod


def main() -> None:
    p = argparse.ArgumentParser(prog="frontier_sim")
    sub = p.add_subparsers(dest="cmd", required=True)

    r = sub.add_parser("run", help="train one config")
    r.add_argument("config")
    r.add_argument("--set", action="append", default=[], metavar="KEY=VALUE")
    r.add_argument("--out", default=None, help="default: runs/<config name>")

    s = sub.add_parser("sweep", help="train variants x seeds and compare")
    s.add_argument("spec")
    s.add_argument("--only", nargs="*", default=None)
    s.add_argument("--out", default="runs")
    s.add_argument("-v", "--verbose", action="store_true")
    s.add_argument("-j", "--workers", type=int, default=1, help="parallel single-threaded runs")

    c = sub.add_parser("compare", help="(re)build report.md for a sweep directory")
    c.add_argument("sweep_dir")

    sh = sub.add_parser("show", help="print resolved config, params and FLOPs")
    sh.add_argument("config")
    sh.add_argument("--set", action="append", default=[], metavar="KEY=VALUE")

    args = p.parse_args()
    if args.cmd == "run":
        from .train import train

        cfg = config_mod.load(args.config, args.set)
        train(cfg, args.out or Path("runs") / cfg.name)
    elif args.cmd == "sweep":
        from .sweep import run_sweep

        run_sweep(args.spec, args.out, args.only, args.verbose, args.workers)
    elif args.cmd == "compare":
        from .sweep import compare

        print(compare(args.sweep_dir))
    elif args.cmd == "show":
        from .model import Transformer

        cfg = config_mod.load(args.config, args.set)
        model = Transformer(cfg.model)
        print(json.dumps(cfg.to_dict(), indent=2))
        print("params:", model.param_counts())
        print(f"flops/token: {model.flops_per_token():.3g}")


if __name__ == "__main__":
    main()

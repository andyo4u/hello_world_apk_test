# Frontier-model simulator: research plan

## 1. Goal

Build a **model organism** of a frontier language model. It should be small enough that one experiment
runs in minutes on a laptop CPU, yet faithful enough to the real recipe that ideas tested on it are
worth trying at scale. Concretely, we want to:

1. **Simulate**: train a model whose architecture, optimizer, data pipeline and evals mirror those of
   current frontier LLMs, scaled down about 10⁵–10⁶×.
2. **Change**: swap any one component (an architecture block, a training trick, a data mix, a
   post-training method) through config, without editing the core code.
3. **Measure**: get low-noise, compute-matched, multi-seed comparisons, and use a scaling ladder to tell
   ideas that keep helping as models grow from ideas that only help tiny models.

The core assumption is that *many* properties of frontier models already appear in small models
trained on the right data: induction heads, in-context learning, grokking, scaling-law slopes,
MoE routing dynamics, and training instabilities such as attention-logit growth. Small models are
the cheap place to falsify ideas. Big models are the expensive place to confirm the survivors.

## 2. What we copy from frontier models, and what we drop

| Layer | Frontier practice | In the simulator | Status |
|---|---|---|---|
| Architecture | Decoder-only, pre-norm, RMSNorm, RoPE, SwiGLU, GQA, QK-norm, top-k MoE, sliding-window layers, logit soft-cap | All present, each a config switch (`model.py`) | ✅ v0 |
| Optimizer | AdamW (β₂=0.95), warmup + cosine/WSD, grad clip, weight decay on matrices only, bf16 | Same; bf16 autocast on GPU | ✅ v0 (WSD, Muon: phase 2) |
| Data | Trillions of tokens of web, code and math, with tuned mixtures | Synthetic capability tasks with exact answers, plus optional byte-level text | ✅ v0 |
| Scale | 10¹¹–10¹² params, 10²⁵+ FLOPs | 10⁵–10⁷ params, 10¹²–10¹⁵ FLOPs | by design |
| Evals | Benchmark suites (MMLU, GSM8K, long-context, ...) | Per-capability loss and accuracy on held-out data, fixed eval seed | ✅ v0 |
| Post-training | SFT → preference optimization / RL with verifiable rewards | Planned (phase 3) | ⏳ |
| Inference | KV cache, speculative decoding, sampling | Simple sampling (`generate`) | ⏳ |
| Interpretability / safety | Probes, SAEs, activation patching, model-organism studies | Residual stream exposed (`return_hidden`), checkpoints reloadable | ⏳ phase 4 |

**What we deliberately drop:** distributed training, tokenizer design, and real-world knowledge. The
model cannot "know" facts. This simulates *mechanisms* and *training dynamics*, not world knowledge.

## 3. System design

```
experiments/*.json ──► config.py ──► ExperimentConfig {model, train, task}
                                             │
              tasks.py ◄─────────────────────┤  build_task(task) → Task (or Mixture)
              model.py ◄─────────────────────┤  Transformer(model)
                                             ▼
                                         train.py  (AdamW, schedule, eval loop)
                                             │
                     runs/<name>/ config.json · metrics.jsonl · summary.json · model.pt
                                             │
                     sweep.py  variants × seeds → report.md (mean ± std, power-law fit)
```

Design rules that keep experiments trustworthy:

- **One source of truth per run.** The fully resolved config is saved next to its results, and
  `python -m frontier_sim run runs/x/config.json` reproduces it.
- **Unknown config keys are errors.** A typo like `model.n_expert` fails instead of quietly running the
  baseline.
- **Fixed eval batches.** Every run is scored on identical data (`EVAL_SEED`), so differences come from
  the model, not the sample.
- **Only answers are scored.** Tasks mask everything except answer tokens, so the metric is the
  capability itself.
- **Compute is accounted.** Every summary records active non-embedding params, FLOPs/token and total
  training FLOPs. Comparisons are made at equal compute.

## 4. The synthetic world (capability probes)

| Task | Capability it stands in for | Knobs that set difficulty |
|---|---|---|
| `assoc_recall` | In-context retrieval / learning (induction heads) | `n_pairs`, vocabulary size |
| `multihop` | Compositional, multi-step reasoning | `hops`, `n_chains` |
| `modadd` | Generalization vs memorization (grokking); eval uses *held-out* (a, b) pairs | `p`, `train_frac` |
| `copy` | Long-range exact copying, context use | `length` |
| `text` | Ordinary language modeling on any file (e.g. TinyStories) | file |
| `mixture` | Pretraining data mix; weights are an experimental variable | per-component `weight` |

Planned additions, in priority order: (1) **needle-in-haystack** for long context; (2) **in-context
linear regression** for ICL as algorithm learning; (3) **arithmetic with chain-of-thought tokens**,
the setting for testing reasoning traces and RL; (4) a **toy "world" with facts plus a
knowledge-vs-retrieval split**, to study hallucination; (5) **code-like bracket/stack languages**.

## 5. Measurement protocol

Every idea goes through the same steps:

1. **State the hypothesis and the predicted metric movement** before running anything.
2. **Express it as a config diff** (add a switch to the code if needed; the default keeps old behavior).
3. **Compute-match.** Equal training FLOPs (or report the FLOP ratio). For MoE, compare at equal
   *active* params (e.g. `expert_d_ff = d_ff / top_k`).
4. **Three or more seeds.** Report mean ± std. A difference smaller than about 2σ is "no effect".
5. **Tune the learning rate for both arms.** Many "wins" are just a better effective LR. Do a quick
   3-point LR sweep (×0.5, ×1, ×2) for baseline and variant before claiming anything.
6. **Climb the scaling ladder** (`experiments/scaling.json`, 5 sizes, about 30× in params). An idea
   counts as promising only if its gain holds or grows across the ladder, i.e. it improves the scaling
   exponent or offset rather than only the smallest model.
7. **Write it up.** `report.md` from the sweep, plus a short note: hypothesis, result, verdict, next
   step.

Metrics tracked by default: training CE, per-task eval loss/accuracy (train split and held-out
split), gradient norm, learning rate, tokens, wall time; for MoE, the aux loss and max expert load.
Divergence (non-finite loss) stops the run and is recorded.

## 6. Roadmap

**Phase 0: scaffold (done in this commit)**
- Configurable frontier-style transformer, synthetic task suite, training loop, sweep runner,
  report generator, 30 unit tests (shapes, causality for every variant, sliding-window reach, MoE
  param accounting, task masking, config validation, loss decreases).

**Phase 1: calibrate the baseline (≈1 day of CPU or 1 GPU-hour)**
- Run `ablations.json` and `scaling.json`. Confirm that known results reproduce: modern recipe ≥
  GPT-2 recipe, MoE beats dense at equal active compute, loss follows a power law in N, and
  assoc_recall shows a sharp induction-head phase change.
- If these don't reproduce, the simulator isn't faithful yet. Fix that before testing new ideas.
- Find the compute-optimal tokens-per-parameter ratio for this data (our "Chinchilla point").

**Phase 2: training-dynamics and architecture testbed**
- Add: WSD schedule, Muon optimizer, µP parameterization (so tuned LRs transfer across the ladder),
  z-loss, router z-loss, KV cache, attention-logit and activation-norm monitoring.
- Instability lab: deliberately raise the LR or remove QK-norm and study loss spikes, which are cheap to
  reproduce here.

**Phase 3: post-training**
- SFT on formatted Q→A data; DPO-style preference optimization; **RL with verifiable rewards**
  (GRPO/PPO) on `modadd`, `multihop` and chain-of-thought arithmetic, where rewards are exact.
- Questions: does RL teach new skills or only surface existing ones? How do RL gains scale with
  pretraining compute? When does reward hacking appear if the verifier has a loophole?

**Phase 4: interpretability and safety model organisms**
- Linear probes and activation patching on the residual stream (`return_hidden=True`): find the
  induction circuit and the multi-hop composition circuit.
- Sparse autoencoders on small checkpoints.
- Model organisms: a backdoored model (a trigger token flips behavior) to test detection and whether
  post-training removes it; a model with a specification-gaming opportunity during RL; steering
  vectors.

**Phase 5: scale up**
- Real text (TinyStories → FineWeb-Edu sample) with a BPE tokenizer; one GPU, 10⁷–10⁸ params.
- Re-test every phase 1–4 conclusion that matters at this scale.

## 7. First experiment queue

| # | Idea | Hypothesis | Config / work |
|---|---|---|---|
| 1 | Modern vs GPT-2 recipe | Modern recipe reaches lower loss and stays stable at a higher LR | `ablations.json` (ready) |
| 2 | QK-norm and LR stability | Without QK-norm, the stable max LR drops | `--set model.qk_norm=false`, LR sweep |
| 3 | GQA/MQA | Small quality cost at much smaller KV size | `ablations.json` (ready) |
| 4 | MoE at matched compute | MoE wins, and the gap grows with experts | `ablations.json` + `n_experts` sweep |
| 5 | Sliding-window attention | Hurts `copy`/`assoc_recall` once spans exceed the window; mixed local/global layers recover it | `sliding_window`, then per-layer pattern (code change) |
| 6 | Data mix | Upweighting `multihop` speeds up reasoning without hurting retrieval | edit mixture weights |
| 7 | Grokking | Weight decay sets when held-out `modadd` accuracy jumps | `task.components.modadd.train_frac`, `train.weight_decay` |
| 8 | Depth vs width at fixed params | Depth matters more for `multihop` (one hop per layer pair) | `n_layers`/`d_model` sweep |

## 8. Compute budget (measured on a 4-core CPU)

| Run | Params (non-emb.) | Time |
|---|---|---|
| Baseline, 2,000 steps × 4,096 tokens | 0.74 M | ≈5 min |
| `ablations.json` (6 variants × 3 seeds) | ≈0.7 M each | ≈1.5 h |
| `scaling.json` (5 sizes, 1 seed) | 0.1 M – 6 M | ≈1–2 h |

On one GPU, each of these drops to minutes; set `train.device` to `cuda` (bf16 is automatic).

### First baseline result (seed 0, 2,000 steps, 8.2M tokens)

| Task | Train-split acc | Held-out acc | Reading |
|---|---|---|---|
| `copy` | 1.00 | 1.00 | Solved by step 250 |
| `assoc_recall` | 1.00 | 1.00 | Sharp jump between steps 250 and 500 (induction-head phase change) |
| `multihop` | 0.24 | 0.23 | Stuck at the shortcut level: picking one of the 4 chain endpoints at random gives 0.25. The model has learned *where* answers come from but not how to compose two hops. |
| `modadd` | 0.78 | 0.16 (rising) | Memorizing the training pairs; held-out accuracy starting to climb late (possible onset of grokking) |

The tasks span solved, phase-change, stuck-on-a-shortcut and memorize-then-generalize behavior. That
spread is what an idea testbed needs. `multihop` is the most informative open target. Ideas that help
composition (depth, data mix, chain-of-thought tokens) should move it off 0.25.

### First ablation sweep (`ablations.json`, 3 seeds × 6 variants, 2,000 steps)

Held-out accuracy per seed (seed 0 / 1 / 2). "Jump" is the first eval step with `assoc_recall` ≥ 0.9.

| Variant | `assoc_recall` | `multihop` | Jump |
|---|---|---|---|
| baseline | 1.00 / 0.16 / 0.16 | 0.23 / 0.24 / 0.23 | 500 / – / – |
| gpt2_style | 0.14 / 0.15 / 0.14 | 0.23 / 0.23 / 0.23 | – / – / – |
| no_qk_norm | 0.14 / 0.14 / 0.14 | 0.22 / 0.23 / 0.23 | – / – / – |
| mqa | 1.00 / 0.16 / 0.16 | 0.50 / 0.23 / 0.24 | 1250 / – / – |
| sliding_window_32 | 0.95 / 0.15 / 0.15 | 0.80 / 0.23 / 0.23 | 500 / – / – |
| moe4_top2_matched | 1.00 / 1.00 / 0.16 | 0.51 / 0.77 / 0.24 | 250 / 250 / – |

`copy` is 1.00 everywhere. `modadd` stays at chance (≈0.02, which is 1/53) everywhere except
baseline seed 0, so 2,000 steps is too short for it.

What this tells us:

1. **Outcomes are bimodal.** Each run either goes through the induction-head phase change
   (`assoc_recall` → 1.0) or stays on a plateau (≈0.15). Mean ± std hides this, so `report.md` now
   also shows "k/n seeds solved". The seed-0 baseline in the previous section was a lucky seed: the
   baseline solves the task in 1 of 3 seeds.
2. **Multi-hop builds on retrieval.** `multihop` rises above the 0.25 shortcut only in runs that
   already solved `assoc_recall`. Composition seems to need the retrieval circuit first, which fits
   the curriculum view of capability onset.
3. **Suggestive, not significant:** MoE at matched compute solved 2/3 and jumped earliest (step
   250). GPT-2 recipe and no-QK-norm solved 0/3. With n = 3 and a baseline at 1/3, none of these
   differences pass the 2σ rule.
4. **The main problem is the protocol.** Comparing variants near a coin-flip phase transition needs
   either many seeds (≥ 10 per arm, with "fraction solved" and "step of jump" as the metrics) or a
   baseline that reliably crosses the transition (more steps or a tuned LR), so that the
   comparison is about *how fast* and *how well*.

Next steps, in order: (a) make the baseline reliable: steps × 2, then a 3-point LR sweep, choosing
the cheapest setting that solves `assoc_recall` in ≥ 9/10 seeds; (b) rerun the ablations with 10 seeds
and "time to solve" as the primary metric; (c) give `modadd` its own longer run for grokking.

## 9. Risks and limitations

- **Extrapolation risk.** A trick that helps a 1M-param model can hurt at 1B. Mitigation: scaling
  ladder, µP, and treating small-scale results as hypotheses rather than conclusions.
- **Synthetic data is not language.** Some effects (knowledge storage, emergent abilities that depend on
  data diversity) won't show up. Mitigation: `text` task and phase 5.
- **Benchmark overfitting.** Tuning against the same eval tasks repeatedly. Mitigation: keep one held-out
  task family per phase that is not looked at until the write-up.
- **Small-model noise.** Mitigation: seeds, fixed eval batches, a 2σ rule.

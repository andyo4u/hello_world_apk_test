"""A scaled-down frontier-style decoder-only transformer.

Defaults follow the recipe shared by current open frontier models (Llama 3,
Qwen, DeepSeek, Gemma, OLMo): pre-norm residual blocks, RMSNorm, rotary position
embeddings, SwiGLU MLPs, grouped-query attention, QK-norm, optional top-k
mixture-of-experts, optional sliding-window attention and logit soft-capping.
Every one of these is a switch in ModelConfig so it can be ablated.
"""

from __future__ import annotations

import math

import torch
import torch.nn as nn
import torch.nn.functional as F

from .config import ModelConfig

IGNORE_INDEX = -100


class RMSNorm(nn.Module):
    def __init__(self, dim: int, eps: float = 1e-6):
        super().__init__()
        self.eps = eps
        self.weight = nn.Parameter(torch.ones(dim))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x32 = x.float()
        normed = x32 * torch.rsqrt(x32.pow(2).mean(-1, keepdim=True) + self.eps)
        return normed.type_as(x) * self.weight


def make_norm(cfg: ModelConfig, dim: int) -> nn.Module:
    return RMSNorm(dim) if cfg.norm == "rms" else nn.LayerNorm(dim)


def rope_tables(seq_len: int, head_dim: int, base: float) -> tuple[torch.Tensor, torch.Tensor]:
    inv_freq = 1.0 / base ** (torch.arange(0, head_dim, 2).float() / head_dim)
    freqs = torch.outer(torch.arange(seq_len).float(), inv_freq)  # (T, head_dim/2)
    return freqs.cos(), freqs.sin()


def apply_rope(x: torch.Tensor, cos: torch.Tensor, sin: torch.Tensor) -> torch.Tensor:
    """Rotate interleaved channel pairs of x (B, H, T, hd) by position."""
    T = x.shape[-2]
    cos, sin = cos[:T], sin[:T]
    x1, x2 = x[..., 0::2].float(), x[..., 1::2].float()
    out = torch.stack((x1 * cos - x2 * sin, x1 * sin + x2 * cos), dim=-1).flatten(-2)
    return out.type_as(x)


class Attention(nn.Module):
    def __init__(self, cfg: ModelConfig):
        super().__init__()
        self.n_heads, self.n_kv = cfg.n_heads, cfg.kv_heads
        self.head_dim = cfg.d_model // cfg.n_heads
        self.q_proj = nn.Linear(cfg.d_model, self.n_heads * self.head_dim, bias=False)
        self.kv_proj = nn.Linear(cfg.d_model, 2 * self.n_kv * self.head_dim, bias=False)
        self.o_proj = nn.Linear(self.n_heads * self.head_dim, cfg.d_model, bias=False)
        self.o_proj.is_residual_out = True
        self.qk_norm = cfg.qk_norm
        if cfg.qk_norm:
            self.q_norm, self.k_norm = RMSNorm(self.head_dim), RMSNorm(self.head_dim)

    def forward(self, x, rope, mask):
        B, T, _ = x.shape
        q = self.q_proj(x).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)
        k, v = self.kv_proj(x).view(B, T, 2, self.n_kv, self.head_dim).unbind(2)
        k, v = k.transpose(1, 2), v.transpose(1, 2)
        if self.qk_norm:
            q, k = self.q_norm(q), self.k_norm(k)
        if rope is not None:
            q, k = apply_rope(q, *rope), apply_rope(k, *rope)
        if self.n_kv != self.n_heads:
            rep = self.n_heads // self.n_kv
            k, v = k.repeat_interleave(rep, dim=1), v.repeat_interleave(rep, dim=1)
        y = F.scaled_dot_product_attention(q, k, v, attn_mask=mask, is_causal=mask is None)
        return self.o_proj(y.transpose(1, 2).reshape(B, T, -1))


class MLP(nn.Module):
    def __init__(self, cfg: ModelConfig, d_ff: int):
        super().__init__()
        self.swiglu = cfg.mlp == "swiglu"
        self.up = nn.Linear(cfg.d_model, d_ff, bias=False)
        if self.swiglu:
            self.gate = nn.Linear(cfg.d_model, d_ff, bias=False)
        self.down = nn.Linear(d_ff, cfg.d_model, bias=False)
        self.down.is_residual_out = True

    def forward(self, x):
        h = F.silu(self.gate(x)) * self.up(x) if self.swiglu else F.gelu(self.up(x))
        return self.down(h)


class MoE(nn.Module):
    """Token-choice top-k routing with a Switch-style load-balancing loss."""

    def __init__(self, cfg: ModelConfig):
        super().__init__()
        self.n_experts, self.top_k = cfg.n_experts, cfg.moe_top_k
        self.router = nn.Linear(cfg.d_model, cfg.n_experts, bias=False)
        d_ff = cfg.expert_d_ff or cfg.ff_dim
        self.experts = nn.ModuleList(MLP(cfg, d_ff) for _ in range(cfg.n_experts))
        self.aux_loss = torch.zeros(())
        self.load = torch.zeros(cfg.n_experts)

    def forward(self, x):
        B, T, D = x.shape
        flat = x.reshape(-1, D)
        probs = self.router(flat).float().softmax(-1)
        weights, chosen = probs.topk(self.top_k, dim=-1)
        weights = (weights / weights.sum(-1, keepdim=True)).to(flat.dtype)
        out = torch.zeros_like(flat)
        for e, expert in enumerate(self.experts):
            tok, slot = (chosen == e).nonzero(as_tuple=True)
            if tok.numel():
                out.index_add_(0, tok, expert(flat[tok]) * weights[tok, slot, None])
        # f_e: share of routed slots sent to e; p_e: mean router prob. Balanced -> 1.0.
        f = F.one_hot(chosen, self.n_experts).float().sum((0, 1)) / chosen.numel()
        self.aux_loss = self.n_experts * (f * probs.mean(0)).sum()
        self.load = f.detach()
        return out.view(B, T, D)


class Block(nn.Module):
    def __init__(self, cfg: ModelConfig, use_moe: bool):
        super().__init__()
        self.attn_norm = make_norm(cfg, cfg.d_model)
        self.attn = Attention(cfg)
        self.mlp_norm = make_norm(cfg, cfg.d_model)
        self.mlp = MoE(cfg) if use_moe else MLP(cfg, cfg.ff_dim)

    def forward(self, x, rope, mask):
        x = x + self.attn(self.attn_norm(x), rope, mask)
        return x + self.mlp(self.mlp_norm(x))


class Transformer(nn.Module):
    def __init__(self, cfg: ModelConfig):
        super().__init__()
        cfg.validate()
        self.cfg = cfg
        self.tok_emb = nn.Embedding(cfg.vocab_size, cfg.d_model)
        self.pos_emb = nn.Embedding(cfg.seq_len, cfg.d_model) if cfg.pos == "learned" else None
        self.blocks = nn.ModuleList(
            Block(cfg, use_moe=cfg.n_experts > 0 and (i + 1) % cfg.moe_every == 0)
            for i in range(cfg.n_layers)
        )
        self.final_norm = make_norm(cfg, cfg.d_model)
        self.lm_head = nn.Linear(cfg.d_model, cfg.vocab_size, bias=False)
        if cfg.tie_embeddings:
            self.lm_head.weight = self.tok_emb.weight
        if cfg.pos == "rope":
            cos, sin = rope_tables(cfg.seq_len, cfg.d_model // cfg.n_heads, cfg.rope_base)
            self.register_buffer("rope_cos", cos, persistent=False)
            self.register_buffer("rope_sin", sin, persistent=False)
        self._init_weights()

    def _init_weights(self):
        # GPT-2 style: N(0, 0.02), residual output projections scaled by 1/sqrt(2L).
        resid_std = 0.02 / math.sqrt(2 * self.cfg.n_layers)
        for m in self.modules():
            if isinstance(m, nn.Linear):
                std = resid_std if getattr(m, "is_residual_out", False) else 0.02
                nn.init.normal_(m.weight, 0.0, std)
            elif isinstance(m, nn.Embedding):
                nn.init.normal_(m.weight, 0.0, 0.02)

    def _mask(self, T: int, device) -> torch.Tensor | None:
        w = self.cfg.sliding_window
        if w is None or w >= T:
            return None
        i = torch.arange(T, device=device)
        dist = i[:, None] - i[None, :]
        return (dist >= 0) & (dist < w)

    def moe_layers(self) -> list[MoE]:
        return [b.mlp for b in self.blocks if isinstance(b.mlp, MoE)]

    def forward(self, idx: torch.Tensor, targets: torch.Tensor | None = None,
                return_hidden: bool = False) -> dict[str, torch.Tensor]:
        B, T = idx.shape
        assert T <= self.cfg.seq_len, f"sequence length {T} > seq_len {self.cfg.seq_len}"
        x = self.tok_emb(idx)
        if self.pos_emb is not None:
            x = x + self.pos_emb(torch.arange(T, device=idx.device))
        rope = (self.rope_cos, self.rope_sin) if self.cfg.pos == "rope" else None
        mask = self._mask(T, idx.device)
        hidden = [x] if return_hidden else None
        for block in self.blocks:
            x = block(x, rope, mask)
            if return_hidden:
                hidden.append(x)
        logits = self.lm_head(self.final_norm(x))
        if self.cfg.logit_softcap:
            c = self.cfg.logit_softcap
            logits = c * torch.tanh(logits / c)

        out: dict[str, torch.Tensor] = {"logits": logits}
        if return_hidden:
            out["hidden"] = torch.stack(hidden)  # (L+1, B, T, D) residual stream
        moe = self.moe_layers()
        if moe:
            out["moe_aux"] = torch.stack([m.aux_loss for m in moe]).mean()
        if targets is not None:
            ce = F.cross_entropy(logits.float().reshape(-1, logits.size(-1)),
                                 targets.reshape(-1), ignore_index=IGNORE_INDEX)
            out["ce"] = ce
            out["loss"] = ce + self.cfg.moe_aux_loss_coef * out["moe_aux"] if moe else ce
        return out

    # ---- accounting -------------------------------------------------------

    def param_counts(self) -> dict[str, int]:
        total = sum(p.numel() for p in self.parameters())
        emb = self.tok_emb.weight.numel() + (self.pos_emb.weight.numel() if self.pos_emb else 0)
        if not self.cfg.tie_embeddings:
            emb += self.lm_head.weight.numel()
        inactive = 0
        for m in self.moe_layers():
            per_expert = sum(p.numel() for p in m.experts[0].parameters())
            inactive += per_expert * (m.n_experts - m.top_k)
        non_emb = total - emb
        return {"total": total, "non_embedding": non_emb, "active_non_embedding": non_emb - inactive}

    def flops_per_token(self) -> float:
        """Training FLOPs/token, Kaplan et al. style: 6N + attention term."""
        c = self.cfg
        attn_ctx = min(c.seq_len, c.sliding_window or c.seq_len)
        return 6 * self.param_counts()["active_non_embedding"] + 6 * c.n_layers * c.d_model * attn_ctx

    # ---- inference --------------------------------------------------------

    @torch.no_grad()
    def generate(self, idx: torch.Tensor, max_new_tokens: int, temperature: float = 1.0,
                 top_k: int | None = None) -> torch.Tensor:
        for _ in range(max_new_tokens):
            logits = self(idx[:, -self.cfg.seq_len:])["logits"][:, -1]
            if temperature == 0:
                nxt = logits.argmax(-1, keepdim=True)
            else:
                logits = logits / temperature
                if top_k:
                    kth = logits.topk(top_k, dim=-1).values[:, -1:]
                    logits = logits.masked_fill(logits < kth, float("-inf"))
                nxt = torch.multinomial(logits.softmax(-1), 1)
            idx = torch.cat([idx, nxt], dim=1)
        return idx

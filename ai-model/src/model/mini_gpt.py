"""
Mini-GPT: a tiny character-level Transformer language model, from scratch in pure NumPy.

This is intentionally dependency-free (NumPy only) so the project trains and exports a model
on any developer laptop without GPU / PyTorch.  The architecture mirrors a decoder-only
Transformer (multi-head self-attention + MLP) but at a small scale (~1M parameters).

The output is:
  * a NumPy checkpoint (mini_gpt.npz) - portable
  * an ONNX-style JSON export (mini_gpt.onnx.json) - the inference layer can load this
    even when the Java side does not have the official onnxruntime Python package.
"""
from __future__ import annotations

import math
import os
import time
from dataclasses import dataclass, asdict
from typing import List, Tuple

import numpy as np


@dataclass
class GPTConfig:
    vocab_size: int = 256        # byte-level vocab (one char = 1 byte)
    block_size: int = 64         # context window
    n_layer: int = 4
    n_head: int = 4
    n_embd: int = 128
    dropout: float = 0.0
    learning_rate: float = 3e-3
    batch_size: int = 16
    max_iters: int = 1500
    eval_interval: int = 200
    seed: int = 1337


# ---------------------------------------------------------------------------
# Layers
# ---------------------------------------------------------------------------

def softmax(x: np.ndarray, axis: int = -1) -> np.ndarray:
    x = x - x.max(axis=axis, keepdims=True)
    e = np.exp(x)
    return e / e.sum(axis=axis, keepdims=True)


def gelu(x: np.ndarray) -> np.ndarray:
    return 0.5 * x * (1.0 + np.tanh(math.sqrt(2.0 / math.pi) * (x + 0.044715 * x ** 3)))


def layer_norm(x: np.ndarray, gamma: np.ndarray, beta: np.ndarray, eps: float = 1e-5) -> np.ndarray:
    mean = x.mean(axis=-1, keepdims=True)
    var = x.var(axis=-1, keepdims=True)
    return gamma * (x - mean) / np.sqrt(var + eps) + beta


def linear(x: np.ndarray, w: np.ndarray, b: np.ndarray) -> np.ndarray:
    return x @ w + b


def attention(q: np.ndarray, k: np.ndarray, v: np.ndarray, mask: np.ndarray) -> np.ndarray:
    # q,k,v: (B, n_head, T, head_dim)
    head_dim = q.shape[-1]
    # k.transpose(0,1,3,2): (B, n_head, head_dim, T) so q @ k_T -> (B, n_head, T, T)
    scores = q @ k.transpose(0, 1, 3, 2) / math.sqrt(head_dim)
    scores = scores + mask  # mask is additive causal, shape (1, T, T) broadcasts to (B, n_head, T, T)
    weights = softmax(scores, axis=-1)
    return weights @ v  # (B, n_head, T, head_dim)


class MiniGPT:
    """Decoder-only Transformer."""

    def __init__(self, cfg: GPTConfig):
        self.cfg = cfg
        rng = np.random.default_rng(cfg.seed)
        # Token + positional embeddings
        self.wte = rng.normal(0, 0.02, (cfg.vocab_size, cfg.n_embd))
        self.wpe = rng.normal(0, 0.02, (cfg.block_size, cfg.n_embd))
        # Transformer blocks
        self.blocks = []
        for _ in range(cfg.n_layer):
            head_dim = cfg.n_embd // cfg.n_head
            block = {
                "ln1_g": np.ones(cfg.n_embd), "ln1_b": np.zeros(cfg.n_embd),
                "ln2_g": np.ones(cfg.n_embd), "ln2_b": np.zeros(cfg.n_embd),
                "wq": rng.normal(0, 0.02, (cfg.n_embd, cfg.n_embd)),
                "wk": rng.normal(0, 0.02, (cfg.n_embd, cfg.n_embd)),
                "wv": rng.normal(0, 0.02, (cfg.n_embd, cfg.n_embd)),
                "wo": rng.normal(0, 0.02, (cfg.n_embd, cfg.n_embd)),
                "fc1_w": rng.normal(0, 0.02, (cfg.n_embd, 4 * cfg.n_embd)),
                "fc1_b": np.zeros(4 * cfg.n_embd),
                "fc2_w": rng.normal(0, 0.02, (4 * cfg.n_embd, cfg.n_embd)),
                "fc2_b": np.zeros(cfg.n_embd),
            }
            self.blocks.append(block)
        # Final layer norm + output projection (tied to wte)
        self.ln_f_g = np.ones(cfg.n_embd)
        self.ln_f_b = np.zeros(cfg.n_embd)
        self.masks = self._causal_mask(cfg.block_size)  # (T, T)

    # ------------------------------------------------------------------
    # Forward
    # ------------------------------------------------------------------
    def forward(self, idx: np.ndarray, targets: np.ndarray = None):
        """Forward pass returning (logits, loss). idx: (B, T) byte ids."""
        B, T = idx.shape
        assert T <= self.cfg.block_size
        tok = self.wte[idx]                              # (B, T, C)
        pos = self.wpe[:T][None, :, :]                   # (1, T, C)
        x = tok + pos

        for blk in self.blocks:
            # ---- Self-attention ----
            xn = layer_norm(x, blk["ln1_g"], blk["ln1_b"])
            q = (xn @ blk["wq"]).reshape(B, T, self.cfg.n_head, -1).transpose(0, 2, 1, 3)
            k = (xn @ blk["wk"]).reshape(B, T, self.cfg.n_head, -1).transpose(0, 2, 1, 3)
            v = (xn @ blk["wv"]).reshape(B, T, self.cfg.n_head, -1).transpose(0, 2, 1, 3)
            attn = attention(q, k, v, self.masks[:T, :T])
            attn = attn.transpose(0, 2, 1, 3).reshape(B, T, self.cfg.n_embd)
            x = x + attn @ blk["wo"]
            # ---- MLP ----
            xn = layer_norm(x, blk["ln2_g"], blk["ln2_b"])
            h = gelu(xn @ blk["fc1_w"] + blk["fc1_b"])
            x = x + h @ blk["fc2_w"] + blk["fc2_b"]

        x = layer_norm(x, self.ln_f_g, self.ln_f_b)
        logits = x @ self.wte.T  # tied weights
        loss = None
        if targets is not None:
            flat_logits = logits.reshape(-1, logits.shape[-1])
            flat_targets = targets.reshape(-1)
            # cross entropy
            shifted = flat_logits - flat_logits.max(axis=-1, keepdims=True)
            log_probs = shifted - np.log(np.exp(shifted).sum(axis=-1, keepdims=True))
            loss = -log_probs[np.arange(flat_targets.size), flat_targets].mean()
        return logits, loss

    # ------------------------------------------------------------------
    # Backward (manual, for the training script)
    # ------------------------------------------------------------------
    # We keep it simple: use the analytical gradient of cross-entropy + softmax
    # applied to the final logits, then back-prop through the network numerically.
    # For a project demo this is fine; production training uses PyTorch's autograd.

    def parameters(self):
        params = [self.wte, self.wpe, self.ln_f_g, self.ln_f_b]
        for b in self.blocks:
            params.extend([b["ln1_g"], b["ln1_b"], b["ln2_g"], b["ln2_b"],
                            b["wq"], b["wk"], b["wv"], b["wo"],
                            b["fc1_w"], b["fc1_b"], b["fc2_w"], b["fc2_b"]])
        return params

    @staticmethod
    def _causal_mask(T: int) -> np.ndarray:
        m = np.triu(np.ones((T, T)) * -1e10, k=1)
        return m.astype(np.float32)

    # ------------------------------------------------------------------
    # Save / Load
    # ------------------------------------------------------------------
    def save(self, path: str) -> None:
        flat = {}
        flat["wte"] = self.wte
        flat["wpe"] = self.wpe
        flat["ln_f_g"] = self.ln_f_g
        flat["ln_f_b"] = self.ln_f_b
        for i, b in enumerate(self.blocks):
            for k, v in b.items():
                flat[f"blk{i}_{k}"] = v
        np.savez(path, **flat)

    def load(self, path: str) -> None:
        data = np.load(path)
        self.wte = data["wte"]
        self.wpe = data["wpe"]
        self.ln_f_g = data["ln_f_g"]
        self.ln_f_b = data["ln_f_b"]
        for i, b in enumerate(self.blocks):
            for k in b.keys():
                b[k] = data[f"blk{i}_{k}"]


# ---------------------------------------------------------------------------
# Data utils
# ---------------------------------------------------------------------------

class ByteTokenizer:
    """Byte-level tokenizer: one UTF-8 byte per id, no preprocessing required."""

    @property
    def vocab_size(self) -> int:
        return 256

    def encode(self, text: str) -> List[int]:
        return list(text.encode("utf-8"))

    def decode(self, ids: List[int]) -> str:
        return bytes([b & 0xff for b in ids]).decode("utf-8", errors="replace")


def get_batch(data: np.ndarray, block_size: int, batch_size: int) -> Tuple[np.ndarray, np.ndarray]:
    ix = np.random.randint(0, len(data) - block_size - 1, size=batch_size)
    x = np.stack([data[i:i + block_size] for i in ix]).astype(np.int64)
    y = np.stack([data[i + 1:i + 1 + block_size] for i in ix]).astype(np.int64)
    return x, y

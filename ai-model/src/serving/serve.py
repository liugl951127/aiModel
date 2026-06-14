"""
Local inference server for an exported Mini-GPT bundle.

Run:
    python -m src.serving.serve --bundle exports/mini_gpt.bundle --port 8000

Then POST to /generate:
    {"prompt": "你好", "max_tokens": 50, "temperature": 0.8}

This server is used to test the export end-to-end before the Java side
takes over with the same bundle.
"""
from __future__ import annotations

import argparse
import json
import math
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import List

import numpy as np


def load_bundle(bundle_dir: str):
    with open(os.path.join(bundle_dir, "config.json"), "r", encoding="utf-8") as f:
        cfg = json.load(f)
    with open(os.path.join(bundle_dir, "weights.json"), "r", encoding="utf-8") as f:
        weights = json.load(f)
    import base64
    tensors = {}
    for k, b64 in weights.items():
        tensors[k] = np.frombuffer(base64.b64decode(b64), dtype=np.float32).copy()
    return cfg, tensors


def layer_norm(x, g, b, eps=1e-5):
    mean = x.mean(axis=-1, keepdims=True)
    var = x.var(axis=-1, keepdims=True)
    return g * (x - mean) / np.sqrt(var + eps) + b


def gelu(x):
    return 0.5 * x * (1.0 + np.tanh(math.sqrt(2.0 / math.pi) * (x + 0.044715 * x ** 3)))


def softmax(x, axis=-1):
    x = x - x.max(axis=axis, keepdims=True)
    e = np.exp(x)
    return e / e.sum(axis=axis, keepdims=True)


def forward(idx: np.ndarray, cfg: dict, w: dict) -> np.ndarray:
    B, T = idx.shape
    assert T <= cfg["block_size"]
    wte = w["wte"].reshape(cfg["vocab_size"], cfg["n_embd"])
    wpe = w["wpe"].reshape(cfg["block_size"], cfg["n_embd"])
    x = wte[idx] + wpe[:T][None, :, :]
    for i in range(cfg["n_layer"]):
        xn = layer_norm(x, w[f"blk{i}_ln1_g"], w[f"blk{i}_ln1_b"])
        n_embd, n_head, head_dim = cfg["n_embd"], cfg["n_head"], cfg["head_dim"]
        q = (xn @ w[f"blk{i}_wq"].reshape(n_embd, n_embd)).reshape(B, T, n_head, head_dim).transpose(0, 2, 1, 3)
        k = (xn @ w[f"blk{i}_wk"].reshape(n_embd, n_embd)).reshape(B, T, n_head, head_dim).transpose(0, 2, 1, 3)
        v = (xn @ w[f"blk{i}_wv"].reshape(n_embd, n_embd)).reshape(B, T, n_head, head_dim).transpose(0, 2, 1, 3)
        scores = q @ k.transpose(0, 1, 3, 2) / math.sqrt(head_dim)
        mask = np.triu(np.ones((T, T)) * -1e10, k=1)
        scores = scores + mask
        attn = softmax(scores, axis=-1) @ v
        attn = attn.transpose(0, 2, 1, 3).reshape(B, T, n_embd)
        x = x + attn @ w[f"blk{i}_wo"].reshape(n_embd, n_embd)
        xn = layer_norm(x, w[f"blk{i}_ln2_g"], w[f"blk{i}_ln2_b"])
        h = gelu(xn @ w[f"blk{i}_fc1_w"].reshape(n_embd, 4 * n_embd) + w[f"blk{i}_fc1_b"].reshape(4 * n_embd))
        x = x + h @ w[f"blk{i}_fc2_w"].reshape(4 * n_embd, n_embd) + w[f"blk{i}_fc2_b"].reshape(n_embd)
    x = layer_norm(x, w["ln_f_g"], w["ln_f_b"])
    return x @ wte.T


def sample(logits, temperature: float = 1.0, top_k: int = 0) -> int:
    logits = logits / max(1e-5, temperature)
    if top_k > 0:
        idx = np.argpartition(-logits, top_k - 1)[:top_k]
        mask = np.full_like(logits, -1e10)
        mask[idx] = logits[idx]
        logits = mask
    p = softmax(logits)
    return int(np.random.choice(len(p), p=p))


def generate(model, prompt: str, max_tokens: int, temperature: float, top_k: int = 0) -> str:
    cfg, w = model
    ids = list(prompt.encode("utf-8"))
    for _ in range(max_tokens):
        ctx = np.array([ids[-cfg["block_size"]:]], dtype=np.int64)
        logits = forward(ctx, cfg, w)[0, -1]
        nxt = sample(logits, temperature, top_k)
        ids.append(nxt)
    return bytes(ids).decode("utf-8", errors="replace")


class Handler(BaseHTTPRequestHandler):
    model = None

    def do_POST(self):
        if self.path != "/generate":
            self.send_error(404)
            return
        ln = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(ln).decode("utf-8")
        req = json.loads(body)
        text = generate(Handler.model, req.get("prompt", ""),
                        int(req.get("max_tokens", 50)),
                        float(req.get("temperature", 0.8)),
                        int(req.get("top_k", 0)))
        out = json.dumps({"text": text}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        # silence access log
        pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle", required=True)
    ap.add_argument("--port", type=int, default=8000)
    args = ap.parse_args()
    Handler.model = load_bundle(args.bundle)
    print(f"Loaded bundle from {args.bundle}, listening on :{args.port}")
    HTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()

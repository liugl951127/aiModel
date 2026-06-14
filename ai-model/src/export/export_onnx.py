"""
Export a trained Mini-GPT checkpoint into a self-contained JSON bundle.

Why JSON? The Java inference layer can load this with no third-party libraries
(gson / fastjson are already on the classpath).  The bundle contains:

  * config.json   - model hyperparameters
  * weights.json  - flat tensor dictionary (base64-encoded float32)
  * tokenizer.json- tokenizer description
  * manifest.json - the file list, ready to be zipped by the Java side

Usage:
    python -m src.export.export_onnx --in checkpoints/mini_gpt.npz --out exports/mini_gpt.bundle
"""
from __future__ import annotations

import argparse
import base64
import json
import os
from typing import Dict

import numpy as np


def to_b64(arr: np.ndarray) -> str:
    return base64.b64encode(arr.astype(np.float32).tobytes()).decode("ascii")


def export(npz_path: str, out_dir: str) -> None:
    data: Dict[str, np.ndarray] = dict(np.load(npz_path))
    os.makedirs(out_dir, exist_ok=True)

    # ------------------------------------------------------------------
    # Detect block size and head count from tensor shapes
    # ------------------------------------------------------------------
    wpe = data["wpe"]
    block_size, n_embd = wpe.shape
    wte = data["wte"]
    vocab_size = wte.shape[0]
    # Count blocks
    blk_keys = sorted(k for k in data.keys() if k.startswith("blk"))
    n_layer = len({k.split("_")[0] for k in blk_keys})
    # head_dim from wo shape
    wo = next(v for k, v in data.items() if k.endswith("_wo"))
    head_dim = wo.shape[1] // n_embd
    # Detect n_head by checking the attn block wq shape: total = n_embd * n_embd
    n_head = n_embd // head_dim

    cfg = {
        "model_type": "mini-gpt",
        "framework": "numpy-mini-gpt",
        "export_format": "onnx-bundle",
        "runtime": "onnxruntime-1.17+ / custom-numpy",
        "vocab_size": int(vocab_size),
        "block_size": int(block_size),
        "n_embd": int(n_embd),
        "n_layer": int(n_layer),
        "n_head": int(n_head),
        "head_dim": int(head_dim),
        "tokenizer": "byte-level",
    }

    weights = {k: to_b64(v) for k, v in data.items()}

    with open(os.path.join(out_dir, "config.json"), "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)
    with open(os.path.join(out_dir, "weights.json"), "w", encoding="utf-8") as f:
        json.dump(weights, f)
    with open(os.path.join(out_dir, "tokenizer.json"), "w", encoding="utf-8") as f:
        json.dump({
            "type": "byte-level",
            "vocab_size": 256,
            "pre_tokenizer": "utf-8-bytes",
            "decoder": "utf-8-bytes",
        }, f, indent=2)
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump({
            "files": ["config.json", "weights.json", "tokenizer.json", "README.md"],
        }, f, indent=2)
    with open(os.path.join(out_dir, "README.md"), "w", encoding="utf-8") as f:
        f.write(
            "# Mini-GPT Export Bundle\n\n"
            "## Files\n"
            "- config.json: model hyperparameters\n"
            "- weights.json: tensor dictionary (base64 float32)\n"
            "- tokenizer.json: byte-level tokenizer\n\n"
            "## Quick start (Java)\n"
            "```java\n"
            "MiniGptModel m = MiniGptModel.loadFromDirectory(Paths.get(\"mini_gpt.bundle\"));\n"
            "String text = m.generate(\"你好\", 100, 0.8f);\n"
            "```\n"
        )
    print(f"Exported bundle to {out_dir}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="in_path", required=True)
    ap.add_argument("--out", dest="out_path", required=True)
    args = ap.parse_args()
    export(args.in_path, args.out_path)


if __name__ == "__main__":
    main()

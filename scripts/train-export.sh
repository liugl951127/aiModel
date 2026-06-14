#!/usr/bin/env bash
# Train a Mini-GPT model and export it to an ONNX-style bundle.
set -e

cd "$(dirname "$0")/../ai-model"

DATA=${1:-data/sample_corpus.txt}
OUT=${2:-checkpoints/mini_gpt.npz}
BUNDLE=${3:-exports/mini_gpt.bundle}

echo "==> 1) Training on $DATA -> $OUT"
python3 -m src.training.train --data "$DATA" --out "$OUT" --max-iters 1500

echo "==> 2) Exporting $OUT -> $BUNDLE"
python3 -m src.export.export_onnx --in "$OUT" --out "$BUNDLE"

echo "==> 3) Bundle contents:"
ls -la "$BUNDLE"
echo "Done. Drop $BUNDLE into /opt/ai-platform/inference-bundles/<name> to use from Java side."

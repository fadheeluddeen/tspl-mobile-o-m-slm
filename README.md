# Vibration Meter Reading POC — Qwen2.5-VL-3B-Instruct

Evaluates whether a 3B-parameter vision-language model can accurately OCR
numeric readings from vibration meter display photos, targeting an offline
Android field-engineer app.

## Project Layout

```
├── test_images/          ← Drop your meter photos here (.jpg/.png/…)
├── ground_truth.csv      ← filename,reading  (you fill this in)
├── scripts/
│   ├── run_eval.py       ← Main evaluation pipeline
│   ├── check_env.py      ← Prints GPU/library/device-plan diagnostics
│   ├── test_parsing.py   ← Unit tests for the numeric parser
│   └── meter_eval/       ← Shared modules (device planning, parsing)
├── results/
│   └── eval_results.csv  ← Written by run_eval.py after each run
├── synthetic_images/     ← (optional) synthetic test images for dry runs
└── .venv/                ← Python 3.14 virtual environment
```

## Quick Start

```bash
# 1. Activate the venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux

# 2. Verify the environment
python scripts/check_env.py

# 3. Put images in test_images/ and fill in ground_truth.csv

# 4. Run the evaluation
python scripts/run_eval.py

# With a tolerance of ±0.1 for the match:
python scripts/run_eval.py --tolerance 0.1

# Use the plain-number prompt instead of JSON:
python scripts/run_eval.py --prompt-style 1
```

## Notes

- **No NVIDIA GPU?** The pipeline falls back to CPU with bf16 weights
  (~7.5 GiB RAM). Expect ~30–120 s per image on a laptop i5.
- **CUDA GPU present?** The script auto-detects it and uses 4-bit NF4
  quantisation (via bitsandbytes) when VRAM ≤ 10 GiB.
- The model is public on Hugging Face — no login or token required.
- First run downloads ~6 GiB of model weights (cached for future runs).

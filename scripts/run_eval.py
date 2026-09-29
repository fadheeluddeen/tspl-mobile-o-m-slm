#!/usr/bin/env python
"""
run_eval.py  -  Qwen2.5-VL-3B-Instruct  meter-reading evaluation
================================================================
Loads every image in test_images/, asks the VLM to read the meter,
then scores against ground_truth.csv.

Usage:  python scripts/run_eval.py [--tolerance 0.1] [--prompt-style 0]
"""
from __future__ import annotations

import argparse
import csv
import gc
import os
import sys
import time
from pathlib import Path

# Make sure scripts/ is on sys.path so local imports work
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import torch
from PIL import Image
from transformers import Qwen2_5_VLForConditionalGeneration, AutoProcessor
from qwen_vl_utils import process_vision_info

from meter_eval.device import plan_device
from meter_eval.parsing import extract_number, extract_all_readings, values_match

# ------------------------------------------------------------------
# Prompt bank - we want the model to return ONLY a number/JSON.
# style 0 is the strictest; style 1 is a fallback if 0 confuses the model.
# ------------------------------------------------------------------
PROMPTS = [
    # 0  strict JSON – all readings
    (
        "You are reading a digital meter display. "
        "Extract ALL numeric readings visible on the screen. "
        'Return ONLY a JSON object: {"readings": [{"value": <number>, "unit": "<unit>"}]}. '
        "List each distinct reading as a separate entry. "
        'If the display is unreadable, return {"readings": []}.'
    ),
    # 1  strict JSON – single primary reading (legacy vibration-meter prompt)
    (
        "You are reading a vibration meter display. "
        "Look at the main numeric value shown on the screen. "
        'Return ONLY a JSON object: {"reading": <number>}. '
        'If the display is unreadable, return {"reading": null}.'
    ),
    # 2  plain-number
    (
        "What are the numeric values currently displayed on this meter? "
        "Reply with ONLY the numbers and their units, one per line. "
        "If you cannot read it, reply N/A."
    ),
]

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp"}
MODEL_ID = "Qwen/Qwen2.5-VL-3B-Instruct"

ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = ROOT / "results"

# ------------------------------------------------------------------
def load_ground_truth(path: Path) -> dict[str, list[float]]:
    """filename -> list of expected numeric readings.

    CSV format:  filename,reading[,reading2,reading3,...]
    Multiple readings per image go in extra columns OR as multiple rows
    with the same filename.
    """
    gt: dict[str, list[float]] = {}
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if not header:
            return gt
        for row in reader:
            if not row or not row[0].strip() or row[0].strip().startswith("#"):
                continue
            fname = row[0].strip()
            values = []
            for cell in row[1:]:
                cell = cell.strip()
                if not cell or cell.lower() in ("", "null", "none", "n/a", "na"):
                    continue
                try:
                    values.append(float(cell))
                except ValueError:
                    print(f"  [WARN] ground_truth.csv: cannot parse '{cell}' "
                          f"for {fname} - skipping this cell")
            if fname in gt:
                gt[fname].extend(values)
            else:
                gt[fname] = values
    return gt


def discover_images(folder: Path) -> list[Path]:
    """Return sorted list of images in folder."""
    return sorted(
        p for p in folder.iterdir()
        if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS
    )


def load_model(plan: dict, model_id: str):
    """Load processor and model according to the device plan."""
    import torch as _torch

    quant_config = None
    if "4-bit" in plan.get("quantization", ""):
        from transformers import BitsAndBytesConfig
        quant_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=_torch.bfloat16,
        )

    dtype = getattr(_torch, plan["dtype"])
    kwargs: dict = {"torch_dtype": dtype}

    if quant_config:
        kwargs["quantization_config"] = quant_config

    if plan.get("device_map"):
        kwargs["device_map"] = plan["device_map"]

    print(f"  loading model  {model_id}")
    print(f"  kwargs = {kwargs}")
    model = Qwen2_5_VLForConditionalGeneration.from_pretrained(model_id, **kwargs)
    if not plan.get("device_map"):
        model = model.to(plan["device"])
    model.eval()

    min_px = 28 * 28
    max_px = plan.get("max_pixels", 401_408)
    processor = AutoProcessor.from_pretrained(
        model_id, min_pixels=min_px, max_pixels=max_px,
    )
    return model, processor


def run_inference(
    model, processor, image_path: Path, prompt_text: str, device: str
) -> str:
    """Run VLM inference on a single image. Returns raw text."""
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "image", "image": str(image_path)},
                {"type": "text", "text": prompt_text},
            ],
        }
    ]

    text_prompt = processor.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True,
    )
    image_inputs, video_inputs = process_vision_info(messages)

    inputs = processor(
        text=[text_prompt],
        images=image_inputs,
        videos=video_inputs,
        padding=True,
        return_tensors="pt",
    ).to(model.device)

    with torch.no_grad():
        ids = model.generate(**inputs, max_new_tokens=128, do_sample=False)

    trimmed = ids[:, inputs.input_ids.shape[1]:]
    return processor.batch_decode(trimmed, skip_special_tokens=True)[0]


# ------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tolerance", type=float, default=0.0,
                    help="Absolute tolerance when comparing readings (0 = exact)")
    ap.add_argument("--prompt-style", type=int, default=0, choices=range(len(PROMPTS)))
    ap.add_argument("--images", type=str, default=None,
                    help="Image folder (default: test_images/)")
    ap.add_argument("--gt", type=str, default=None,
                    help="Ground-truth CSV (default: ground_truth.csv)")
    args = ap.parse_args()

    print("=" * 62)
    print("  Qwen2.5-VL  Meter-Reading  Evaluation")
    print("=" * 62)

    # ------ discover images ------
    img_dir = Path(args.images) if args.images else ROOT / "test_images"
    if not img_dir.is_absolute():
        img_dir = ROOT / img_dir
    images = discover_images(img_dir)
    if not images:
        print(f"\nNo images found in {img_dir}/")
        print("   Drop your meter photos there and re-run.")
        return 1
    print(f"\nfound {len(images)} image(s) in {img_dir}")

    # ------ load ground truth ------
    gt_path = Path(args.gt) if args.gt else ROOT / "ground_truth.csv"
    if not gt_path.is_absolute():
        gt_path = ROOT / gt_path
    if gt_path.is_file():
        ground_truth = load_ground_truth(gt_path)
        print(f"loaded {len(ground_truth)} ground-truth row(s) from {gt_path.name}")
    else:
        ground_truth = {}
        print(f"no {gt_path.name} found - will run inference without scoring")

    # ------ device plan ------
    plan = plan_device()
    print(f"\ndevice plan:")
    for k, v in plan.items():
        print(f"  {k:<14} {v}")

    # ------ load model ------
    print()
    t0 = time.time()
    model, processor = load_model(plan, MODEL_ID)
    print(f"  model loaded in {time.time() - t0:.1f}s\n")

    prompt_text = PROMPTS[args.prompt_style]
    print(f"prompt style {args.prompt_style}:")
    print(f"  \"{prompt_text[:90]}...\"\n")

    # ------ inference loop ------
    results: list[dict] = []
    for i, img_path in enumerate(images, 1):
        fname = img_path.name
        print(f"[{i}/{len(images)}] {fname} ... ", end="", flush=True)
        t1 = time.time()

        try:
            raw = run_inference(model, processor, img_path, prompt_text, plan["device"])
        except Exception as exc:
            raw = f"<ERROR: {type(exc).__name__}: {exc}>"
            print(f"ERROR ({type(exc).__name__})")
        else:
            elapsed = time.time() - t1
            print(f"done ({elapsed:.1f}s)")

        # Extract all readings from model output
        all_readings = extract_all_readings(raw)
        pred_values = [r["value"] for r in all_readings]
        strategies = [r["strategy"] for r in all_readings]
        pred_units = [r.get("unit") for r in all_readings]

        # Also keep legacy single-value extraction for backward compat
        pred_value, parse_strategy = extract_number(raw)

        truth_list = ground_truth.get(fname, [])

        # Score: each ground-truth value must be matched by a prediction
        if truth_list:
            matched_truths = 0
            matched_preds = set()
            for tv in truth_list:
                for pi, pv in enumerate(pred_values):
                    if pi not in matched_preds and values_match(pv, tv, abs_tol=args.tolerance):
                        matched_truths += 1
                        matched_preds.add(pi)
                        break
            match = matched_truths == len(truth_list)
            match_detail = f"{matched_truths}/{len(truth_list)}"
        else:
            match = None
            match_detail = None

        results.append({
            "filename": fname,
            "raw_output": raw,
            "parsed_all": pred_values,
            "parsed_units": pred_units,
            "parsed_primary": pred_value,
            "strategies": strategies,
            "ground_truth": truth_list,
            "match": match,
            "match_detail": match_detail,
        })

        # free image tensor memory eagerly
        gc.collect()

    # ------ report ------
    print()
    print("=" * 72)
    print("  RESULTS")
    print("=" * 72)

    scored = [r for r in results if r["match"] is not None]
    unscored = [r for r in results if r["match"] is None]
    correct = sum(1 for r in scored if r["match"])

    for r in results:
        status = (
            "PASS" if r["match"] is True
            else "FAIL" if r["match"] is False
            else "????"
        )
        gt_str = str(r["ground_truth"]) if r["ground_truth"] else "-"
        pred_str = str(r["parsed_all"]) if r["parsed_all"] else "-"
        detail = f" [{r['match_detail']}]" if r["match_detail"] else ""
        print(f"  {status} {r['filename']:<28} truth={gt_str:<18} pred={pred_str:<18}{detail}")
        if r["match"] is False:
            # show raw output so user can debug prompt issues
            raw_trunc = r["raw_output"][:300]
            print(f"      raw: {raw_trunc}")

    print()
    if scored:
        pct = 100 * correct / len(scored)
        total_readings = sum(len(r["ground_truth"]) for r in scored)
        matched_readings = sum(
            int(r["match_detail"].split("/")[0]) for r in scored if r["match_detail"]
        )
        print(f"  images scored   : {len(scored)}")
        print(f"  images all-correct : {correct}  ({pct:.1f}%)")
        print(f"  readings matched: {matched_readings}/{total_readings}")
    if unscored:
        print(f"  unscored        : {len(unscored)}  (no ground-truth row)")
    if args.tolerance > 0:
        print(f"  tolerance       : +/-{args.tolerance}")
    print()

    # ------ CSV dump ------
    RESULTS_DIR.mkdir(exist_ok=True)
    csv_path = RESULTS_DIR / "eval_results.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=[
            "filename", "ground_truth", "parsed_all", "parsed_units",
            "parsed_primary", "match", "match_detail", "strategies", "raw_output",
        ])
        w.writeheader()
        w.writerows(results)
    print(f"  full results saved to {csv_path}")

    return 0 if (scored and correct == len(scored)) else 1


if __name__ == "__main__":
    sys.exit(main())

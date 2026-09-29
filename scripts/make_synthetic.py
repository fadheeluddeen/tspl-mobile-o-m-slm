#!/usr/bin/env python
"""
Generate synthetic meter display images for dry-run testing.

Creates simple images with a black background and large white digits
simulating a basic LCD/LED display. These are NOT realistic enough to
benchmark accuracy, but they verify the pipeline runs end-to-end.

Usage:  python scripts/make_synthetic.py
"""
from __future__ import annotations

import csv
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "synthetic_images"
GT_PATH = ROOT / "synthetic_ground_truth.csv"

READINGS = [
    "12.45",
    "0.71",
    "3.08",
    "27.6",
    "0.00",
    "105.3",
    "4.72",
    "0.5",
]


def draw_meter(reading: str, width: int = 480, height: int = 320) -> Image.Image:
    """Render a fake LCD-style meter display."""
    img = Image.new("RGB", (width, height), color=(10, 10, 30))
    draw = ImageDraw.Draw(img)

    # Try to use a monospace font; fall back to default
    font_size = 72
    try:
        font = ImageFont.truetype("consola.ttf", font_size)
    except (IOError, OSError):
        try:
            font = ImageFont.truetype("cour.ttf", font_size)
        except (IOError, OSError):
            font = ImageFont.load_default()

    # Outer bezel
    draw.rectangle([10, 10, width - 10, height - 10], outline=(80, 80, 80), width=3)

    # Display area background (dark green LCD look)
    lcd_rect = [30, 40, width - 30, height - 80]
    draw.rectangle(lcd_rect, fill=(0, 30, 0))

    # The reading
    text = f" {reading} mm/s"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (width - tw) // 2
    y = (height - 80 - th) // 2 + 20
    draw.text((x, y), text, fill=(0, 255, 70), font=font)

    # Label
    label_font_size = 18
    try:
        label_font = ImageFont.truetype("arial.ttf", label_font_size)
    except (IOError, OSError):
        label_font = ImageFont.load_default()
    draw.text((30, height - 65), "VIB VELOCITY  RMS", fill=(180, 180, 180), font=label_font)

    return img


def main():
    OUT_DIR.mkdir(exist_ok=True)
    rows = []

    for i, reading in enumerate(READINGS):
        fname = f"synth_{i:02d}.png"
        img = draw_meter(reading)
        img.save(OUT_DIR / fname)
        rows.append({"filename": fname, "reading": reading})
        print(f"  wrote {fname}  (reading={reading})")

    with open(GT_PATH, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["filename", "reading"])
        w.writeheader()
        w.writerows(rows)
    print(f"\nground truth -> {GT_PATH}")
    print(f"images       -> {OUT_DIR}/")
    print(f"\nTo run eval on synthetic images:")
    print(f"  python scripts/run_eval.py --images synthetic_images --gt synthetic_ground_truth.csv")


if __name__ == "__main__":
    main()

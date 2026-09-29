"""Device / precision planning.

Single source of truth for *how* the model gets loaded, so the same pipeline is
correct on a CUDA workstation and on a CPU-only laptop.
"""
from __future__ import annotations

# Qwen2.5-VL-3B is ~3.75e9 params. Weight footprint by precision:
#   fp32 ~15.0 GiB | bf16/fp16 ~7.5 GiB | 4-bit NF4 ~2.4 GiB
_VRAM_4BIT_CEILING_GIB = 10.0  # at/below this, prefer 4-bit to leave room for activations


def _cpu_plan(available_gib: float | None) -> dict:
    # fp32 needs ~15 GiB of weights alone, which no 16 GiB laptop can hold
    # alongside an OS. bf16 halves that and is the only realistic CPU option.
    plan = {
        "device": "cpu",
        "device_map": None,
        "dtype": "bfloat16",
        "quantization": "none (bitsandbytes requires CUDA)",
        "max_pixels": 401_408,  # 512 * 28 * 28 - caps vision tokens; big CPU speed lever
        "reason": "no CUDA device visible",
    }
    if available_gib is not None:
        plan["ram_available_gib"] = round(available_gib, 1)
        if available_gib < 9.0:
            plan["warning"] = (
                f"only {available_gib:.1f} GiB RAM available but bf16 weights need "
                "~7.5 GiB plus activations - close other applications before running"
            )
    return plan


def plan_device() -> dict:
    """Decide device, dtype and quantization from what is actually present."""
    try:
        import torch
    except ImportError:
        return {"device": "unknown", "reason": "torch not installed"}

    available_gib = None
    try:
        import psutil
        available_gib = psutil.virtual_memory().available / 1024**3
    except ImportError:
        pass

    if not torch.cuda.is_available():
        return _cpu_plan(available_gib)

    props = torch.cuda.get_device_properties(0)
    vram = props.total_memory / 1024**3

    has_bnb = False
    try:
        import bitsandbytes  # noqa: F401
        has_bnb = True
    except Exception:  # noqa: BLE001
        pass

    if vram <= _VRAM_4BIT_CEILING_GIB and has_bnb:
        return {
            "device": "cuda",
            "device_map": "auto",
            "dtype": "bfloat16",
            "quantization": "4-bit NF4 (bitsandbytes, double-quant)",
            "max_pixels": 1_003_520,
            "gpu": props.name,
            "vram_gib": round(vram, 1),
            "reason": f"VRAM {vram:.1f} GiB <= {_VRAM_4BIT_CEILING_GIB} GiB -> quantize",
        }

    if vram <= _VRAM_4BIT_CEILING_GIB and not has_bnb:
        return {
            "device": "cuda",
            "device_map": "auto",
            "dtype": "bfloat16",
            "quantization": "none (bitsandbytes unavailable - may OOM)",
            "max_pixels": 602_112,
            "gpu": props.name,
            "vram_gib": round(vram, 1),
            "reason": "low VRAM but bitsandbytes not importable",
        }

    return {
        "device": "cuda",
        "device_map": "auto",
        "dtype": "bfloat16",
        "quantization": "none (ample VRAM)",
        "max_pixels": 1_003_520,
        "gpu": props.name,
        "vram_gib": round(vram, 1),
        "reason": f"VRAM {vram:.1f} GiB > {_VRAM_4BIT_CEILING_GIB} GiB -> full precision",
    }

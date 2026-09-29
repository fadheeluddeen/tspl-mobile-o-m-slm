"""Environment + device capability report for the Qwen2.5-VL meter-reading POC."""
import platform, shutil, subprocess, sys


def _sh(cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=30).stdout.strip()
    except Exception as exc:  # noqa: BLE001
        return f"<failed: {exc}>"


def main():
    print("=" * 62)
    print("HOST")
    print("=" * 62)
    print(f"platform : {platform.platform()}")
    print(f"processor: {platform.processor()}")
    print(f"python   : {sys.version.split()[0]} ({sys.executable})")

    smi = shutil.which("nvidia-smi")
    print(f"nvidia-smi: {smi or '<not found - no NVIDIA driver installed>'}")
    if smi:
        print(_sh([smi, "--query-gpu=name,driver_version,memory.total",
                   "--format=csv,noheader"]))

    print()
    print("=" * 62)
    print("TORCH / DEVICE")
    print("=" * 62)
    try:
        import torch
    except ImportError:
        print("torch not installed")
        return 1

    print(f"torch          : {torch.__version__}")
    print(f"built with CUDA: {torch.version.cuda or '<CPU-only build>'}")
    print(f"cuda available : {torch.cuda.is_available()}")
    if torch.cuda.is_available():
        for i in range(torch.cuda.device_count()):
            p = torch.cuda.get_device_properties(i)
            print(f"  [{i}] {p.name}  VRAM={p.total_memory / 1024**3:.1f} GiB  cc={p.major}.{p.minor}")
    else:
        print("  -> inference will run on CPU")

    try:
        import psutil
        vm = psutil.virtual_memory()
        print(f"system RAM     : {vm.total / 1024**3:.1f} GiB total, "
              f"{vm.available / 1024**3:.1f} GiB available")
    except ImportError:
        pass

    print()
    print("=" * 62)
    print("LIBRARIES")
    print("=" * 62)
    for mod in ("transformers", "accelerate", "qwen_vl_utils", "PIL", "cv2", "pandas", "bitsandbytes"):
        try:
            m = __import__(mod)
            print(f"  {mod:<16} {getattr(m, '__version__', '<no __version__>')}")
        except Exception as exc:  # noqa: BLE001
            print(f"  {mod:<16} MISSING ({type(exc).__name__})")

    print()
    from meter_eval.device import plan_device  # noqa: PLC0415
    plan = plan_device()
    print("=" * 62)
    print("CHOSEN LOAD PLAN")
    print("=" * 62)
    for k, v in plan.items():
        print(f"  {k:<14} {v}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Unit tests for the numeric extractor. Run: python scripts/test_parsing.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from meter_eval.parsing import extract_number, values_match, normalize_number

CASES = [
    # (raw model output, expected value)
    ('12.45',                                   12.45),
    ('  4.7  ',                                 4.7),
    ('{"reading": 12.45}',                      12.45),
    ('{"reading": "12.45"}',                    12.45),
    ('```json\n{"reading": 3.08}\n```',         3.08),
    ('{"reading": "12.45 mm/s"}',               12.45),
    ('The reading is 12.45 mm/s',               12.45),
    ('The display shows 4.72 mm/s RMS',         4.72),
    ('0.71 mm/s at 1500 RPM',                   0.71),
    ('reading of 2.3 mm/s while running 1780 rpm', 2.3),
    ('-0.08',                                   -0.08),
    ('+1.5 in/s',                               1.5),
    ('1,234.5 mm/s',                            1234.5),
    ('12,45 mm/s',                              12.45),
    ('.5',                                      0.5),
    ('42',                                      42.0),
    ('The meter reads 0.0 mm/s',                0.0),
    ('105.6 \u00b5m',                           105.6),
    ('{"reading": null}',                       None),
    ('unreadable',                              None),
    ('N/A',                                     None),
    ('no display visible in this photo',        None),
]

def main():
    failed = []
    for raw, want in CASES:
        got, strat = extract_number(raw)
        ok = (got is None and want is None) or (
            got is not None and want is not None and abs(got - want) < 1e-9)
        flag = "PASS" if ok else "FAIL"
        if not ok:
            failed.append((raw, want, got))
        print(f"  [{flag}] {raw!r:<48} -> {got!r:<10} ({strat})")

    # tolerance behaviour
    assert values_match(12.40, 12.4) is True, "trailing-zero equality"
    assert values_match(12.5, 12.4) is False, "distinct values must not match"
    assert values_match(12.45, 12.4, abs_tol=0.1) is True, "abs tolerance"
    assert values_match(None, 12.4) is False, "None never matches"
    assert normalize_number("1,000") == 1000.0, "thousands separator"

    print()
    if failed:
        print(f"{len(failed)}/{len(CASES)} parser cases FAILED")
        for raw, want, got in failed:
            print(f"   {raw!r}: wanted {want!r}, got {got!r}")
        return 1
    print(f"all {len(CASES)} parser cases passed; tolerance assertions passed")
    return 0

if __name__ == "__main__":
    sys.exit(main())

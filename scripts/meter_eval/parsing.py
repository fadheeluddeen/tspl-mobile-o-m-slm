"""Turn free-form VLM text into a single numeric meter reading.

The model is prompted for strict JSON, but VLMs drift, so this falls back
through progressively looser strategies and always records which one fired.
"""
from __future__ import annotations

import json
import math
import re

# Amplitude units first: if several numbers appear, the one carrying a
# vibration unit is far more likely to be the reading than an RPM or a date.
_AMPLITUDE_UNITS = r"mm\s*/\s*s(?:ec)?|in\s*/\s*s(?:ec)?|ips|m\s*/\s*s(?:\^?2|\u00b2)|\u00b5m|um|mils?|g\b"
_ANY_UNIT = _AMPLITUDE_UNITS + r"|hz|cpm|rpm|\u00b0c|\u00b0f"

# 1,234.56 | 12.45 | 12,45 | .5 | -0.08 | 42
_NUM = r"[-+]?(?:\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d*[.,]\d+|\d+)"

_NUM_RE = re.compile(_NUM)
_NUM_THEN_AMP_RE = re.compile(rf"({_NUM})\s*(?:{_AMPLITUDE_UNITS})", re.IGNORECASE)
_NUM_THEN_ANY_RE = re.compile(rf"({_NUM})\s*(?:{_ANY_UNIT})", re.IGNORECASE)

_NULLISH = {"", "null", "none", "n/a", "na", "nan", "unreadable", "unknown", "-", "--"}


def normalize_number(token: str) -> float | None:
    """Parse one numeric token, handling both , thousands and , decimal styles."""
    t = token.strip().replace(" ", "")
    if not t:
        return None
    if "," in t and "." in t:
        # Both present: comma must be the thousands separator (1,234.56).
        t = t.replace(",", "")
    elif "," in t:
        head, _, tail = t.rpartition(",")
        digits = tail
        # 1,234 / 12,345,678 -> thousands.  12,45 -> European decimal.
        if len(digits) == 3 and head.lstrip("+-").replace(",", "").isdigit():
            t = t.replace(",", "")
        else:
            t = t.replace(",", ".")
    try:
        val = float(t)
    except ValueError:
        return None
    return val if math.isfinite(val) else None


def extract_number(raw: str) -> tuple[float | None, str]:
    """Return (value, strategy). value is None when nothing numeric was found."""
    if raw is None:
        return None, "empty"
    text = raw.strip()
    if not text:
        return None, "empty"

    # 1) strict JSON, incl. a ```json fenced block
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    blob = fenced.group(1) if fenced else None
    if blob is None:
        brace = re.search(r"\{.*\}", text, re.DOTALL)
        blob = brace.group(0) if brace else None
    if blob:
        try:
            obj = json.loads(blob)
            if isinstance(obj, dict):
                for key in ("reading", "value", "number", "display", "result"):
                    if key in obj:
                        v = obj[key]
                        if isinstance(v, (int, float)) and math.isfinite(float(v)):
                            return float(v), "json"
                        if isinstance(v, str):
                            if v.strip().lower() in _NULLISH:
                                return None, "json:null"
                            got = normalize_number(v)
                            if got is not None:
                                return got, "json"
                            m = _NUM_RE.search(v)
                            if m:
                                got = normalize_number(m.group(0))
                                if got is not None:
                                    return got, "json:embedded"
        except json.JSONDecodeError:
            pass

    # 2) the whole reply is just a number
    whole = normalize_number(text)
    if whole is not None:
        return whole, "bare"

    if text.lower() in _NULLISH:
        return None, "explicit-null"

    # 3) number carrying an amplitude unit, then any unit
    for rx, name in ((_NUM_THEN_AMP_RE, "unit:amplitude"), (_NUM_THEN_ANY_RE, "unit:any")):
        m = rx.search(text)
        if m:
            got = normalize_number(m.group(1))
            if got is not None:
                return got, name

    # 4) first number anywhere
    m = _NUM_RE.search(text)
    if m:
        got = normalize_number(m.group(0))
        if got is not None:
            return got, "first-number"

    return None, "no-number"


def extract_all_readings(raw: str) -> list[dict]:
    """Extract all numeric readings from VLM output.

    Returns a list of {"value": float, "unit": str|None, "strategy": str}.
    Handles the multi-reading JSON format: {"readings": [{"value": N, "unit": "..."}]}
    as well as free-form text with multiple numbers.
    """
    if raw is None:
        return []
    text = raw.strip()
    if not text:
        return []

    results = []

    # 1) Try multi-reading JSON format
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    blob = fenced.group(1) if fenced else None
    if blob is None:
        brace = re.search(r"\{.*\}", text, re.DOTALL)
        blob = brace.group(0) if brace else None
    if blob:
        try:
            obj = json.loads(blob)
            if isinstance(obj, dict) and "readings" in obj:
                for entry in obj["readings"]:
                    if isinstance(entry, dict):
                        v = entry.get("value")
                        u = entry.get("unit")
                        if isinstance(v, (int, float)) and math.isfinite(float(v)):
                            results.append({"value": float(v), "unit": u, "strategy": "json:multi"})
                        elif isinstance(v, str):
                            got = normalize_number(v)
                            if got is not None:
                                results.append({"value": got, "unit": u, "strategy": "json:multi"})
                if results:
                    return results
            # Fall back to single-reading JSON
            if isinstance(obj, dict):
                for key in ("reading", "value", "number", "display", "result"):
                    if key in obj:
                        v = obj[key]
                        if isinstance(v, (int, float)) and math.isfinite(float(v)):
                            results.append({"value": float(v), "unit": None, "strategy": "json:single"})
                            return results
                        if isinstance(v, str):
                            got = normalize_number(v)
                            if got is not None:
                                results.append({"value": got, "unit": None, "strategy": "json:single"})
                                return results
        except json.JSONDecodeError:
            pass

    # 2) Find all number+unit pairs in free text
    for m in _NUM_THEN_ANY_RE.finditer(text):
        got = normalize_number(m.group(1))
        if got is not None:
            # grab unit text after the number
            unit_match = re.match(rf"{re.escape(m.group(1))}\s*({_ANY_UNIT})", m.group(0), re.IGNORECASE)
            unit = unit_match.group(1) if unit_match else None
            results.append({"value": got, "unit": unit, "strategy": "unit:text"})
    if results:
        return results

    # 3) All numbers in text
    for m in _NUM_RE.finditer(text):
        got = normalize_number(m.group(0))
        if got is not None:
            results.append({"value": got, "unit": None, "strategy": "first-number"})
    return results


def values_match(pred: float | None, truth: float | None,
                 abs_tol: float = 0.0, rel_tol: float = 0.0) -> bool:
    """Numeric comparison, so 12.40 and 12.4 agree. Zero tolerance = exact value."""
    if pred is None or truth is None:
        return False
    if abs_tol <= 0 and rel_tol <= 0:
        # Round-trip through repr to absorb float noise like 0.1+0.2.
        return math.isclose(pred, truth, rel_tol=1e-9, abs_tol=1e-9)
    return math.isclose(pred, truth, rel_tol=rel_tol, abs_tol=abs_tol)

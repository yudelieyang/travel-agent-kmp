"""Generate Unicode tables using the pinned Python environment."""
import sys
import ctypes
import unicodedata
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'core/src/main/resources/unicode'
root.mkdir(parents=True, exist_ok=True)
for name in ('lower', 'casefold'):
    lines = [f'# Python {sys.version.split()[0]}; Unicode {unicodedata.unidata_version}\n']
    for cp in range(0x110000):
        char = chr(cp)
        result = getattr(char, name)()
        if result != char:
            lines.append(f'{cp:X}\t' + ' '.join(f'{ord(c):X}' for c in result) + '\n')
    (root / f'{name}.tsv').write_text(''.join(lines), encoding='utf8', newline='\n')
    print(name, len(lines) - 1)

# CPython exports its Unicode 14 case properties; use the same tables as str.lower.
for symbol, filename in (("_PyUnicode_IsCased", "cased"), ("_PyUnicode_IsCaseIgnorable", "case_ignorable")):
    predicate = getattr(ctypes.pythonapi, symbol)
    predicate.argtypes = [ctypes.c_uint32]
    predicate.restype = ctypes.c_int
    points = [cp for cp in range(0x110000) if predicate(cp)]
    text = f"# Python {sys.version.split()[0]}; Unicode {unicodedata.unidata_version}\n" + "".join(f"{cp:X}\n" for cp in points)
    (root / f"{filename}.tsv").write_text(text, encoding="utf8", newline="\n")
    print(filename, len(points))

# Human-readable expectations are produced by the same interpreter as the maps.
import json
cases = [{"input": s, "lower": s.lower(), "casefold": s.casefold()} for s in ("Boſton", "İstanbul", "STRASSE", "straße", "ΟΣ", "ΟΣΑ", "Σ", "AΣ\u0301", "AΣ\u0301B", "A\u0301Σ", "A.Σ", "ΣΣ", "𐐀Σ", "AΣ𐐀")]
out = Path(__file__).resolve().parents[1] / "core/src/test/resources/unicode-cases.json"
out.write_text(json.dumps(cases, ensure_ascii=True, indent=2) + "\n", encoding="utf8", newline="\n")

"""Generate Unicode tables using the pinned Python environment."""
import sys
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

# Human-readable expectations are produced by the same interpreter as the maps.
import json
cases = [{"input": s, "lower": s.lower(), "casefold": s.casefold()} for s in ("Boſton", "İstanbul", "STRASSE", "straße")]
out = Path(__file__).resolve().parents[1] / "core/src/test/resources/unicode-cases.json"
out.write_text(json.dumps(cases, ensure_ascii=True, indent=2) + "\n", encoding="utf8", newline="\n")

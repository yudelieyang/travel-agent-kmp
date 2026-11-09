"""Copy byte-identical, tracked data from the Python golden-v1 commit."""
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
COMMIT = '0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd'

def git(*args):
    return subprocess.check_output(['git', '-C', str(SOURCE), *args])

def main():
    resolved = git('rev-parse', 'golden-v1^{commit}').decode().strip()
    if resolved != COMMIT:
        raise RuntimeError(f'golden-v1 resolved to unexpected commit: {resolved}')
    prefixes = ['golden/', 'data/travel/snapshots/openstreetmap/', 'data/travel/us/cities.json', 'frontend/src/presets.ts']
    paths = git('ls-tree', '-r', '--name-only', '-z', COMMIT, '--', *prefixes).decode().split('\0')
    paths = [path for path in paths if path]
    counts = {'golden': 0, 'snapshot': 0, 'cities': 0, 'presets': 0}
    for path in paths:
        content = git('show', f'{COMMIT}:{path}')
        if path.startswith('golden/'):
            dest = ROOT / 'core/src/test/resources' / path
            counts['golden'] += 1
        elif path.startswith('data/travel/snapshots/openstreetmap/'):
            dest = ROOT / 'core/src/main/resources' / path
            counts['snapshot'] += 1
        elif path == 'data/travel/us/cities.json':
            dest = ROOT / 'core/src/main/resources' / path
            counts['cities'] += 1
        else:
            dest = ROOT / 'core/src/main/resources/data/presets.ts'
            counts['presets'] += 1
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(content)
        if hashlib.sha256(content).digest() != hashlib.sha256(dest.read_bytes()).digest():
            raise RuntimeError(f'SHA-256 mismatch: {path}')
    if counts != {'golden': 230, 'snapshot': 41, 'cities': 1, 'presets': 1}:
        raise RuntimeError(f'Unexpected file count: {counts}')
    (ROOT / 'core/src/test/resources/golden/SOURCE.txt').write_text(COMMIT + '\n', encoding='utf8', newline='\n')
    (ROOT / 'NOTICE.md').write_bytes((ROOT / 'core/src/main/resources/data/travel/snapshots/openstreetmap/NOTICE.md').read_bytes())
    print('SHA-256 verified for each copied file:', counts)

if __name__ == '__main__':
    main()

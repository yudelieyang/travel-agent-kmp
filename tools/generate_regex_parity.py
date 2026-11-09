"""Capture CPython regex behavior from the golden-v1 source; never edit the source repository."""
import ast
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PYTHON = ROOT.parent / 'travel-planning-agent'
COMMIT = '0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd'
if subprocess.check_output(['git', '-C', str(PYTHON), 'rev-parse', 'golden-v1^{commit}']).decode().strip() != COMMIT:
    raise RuntimeError('Unexpected golden-v1 commit')
if subprocess.check_output(['git', '-C', str(PYTHON), 'diff', '--name-only', COMMIT, '--', 'backend/app', 'data/travel']):
    raise RuntimeError('Python runtime code or data differs from golden-v1')
sys.path.insert(0, str(PYTHON / 'backend'))
from app.agent import requirements, semantic_coverage
from app.tools import candidate_data

modules = (requirements, semantic_coverage, candidate_data)
queries = [x['input'] for x in json.loads((PYTHON / 'evals/datasets/hybrid_semantics_200_v1.json').read_text(encoding='utf8'))['cases']]
queries += [json.loads(p.read_text(encoding='utf8'))['query'] for p in sorted((PYTHON / 'golden/e2e').glob('*.json'))]
# Handwritten cases exercise punctuation, boundaries, currencies, corrections, negation and Unicode.
manual = ['whole-trip', 'entire-trip', 'more then', 'U.S.', 'Boston, MA', 'Boston or Miami', '2-day Boston trip',
          'to Boston for 2 travelers', 'from Austin to Boston', 'three-day', 'family of four', 'just me',
          'traveling alone', 'me and my partner', '2026-01-01', 'per person', 'total', 'whole trip',
          'spend as much of the budget as reasonably possible', 'but', 'budget hotel', 'city walking',
          'no walking', 'avoid nightlife', 'without meat', 'never rent a car', 'no expensive restaurant',
          'wheelchair', 'must visit parks', 'cheap', 'affordable', 'under $500', 'I also like fried chicken',
          'I like zoos and parks', 'however', 'hotel around $500 per night', 'not a strict cap',
          'actually make the hotel $400', 'food $80', 'transport $100', 'museum tickets $50',
          'total trip cannot exceed $500', 'my hotel budget', 'per night', 'around', 'hotel',
          'public transit is preferred', 'quiet hotel', 'USD 500', 'USD', 'find me museums', 'Café', 'São Paulo',
          'Boston，Miami！', 'ſ', 'İ', 'Boston', '', 'no match here', '⛄',
          'for two', 'have', ' and actually make that to ', "can't go above", 'history', 'art', 'central',
          'luxury', 'vegetarian', 'vegan', 'seafood', 'taxi', 'if necessary', 'not',
          'austin', 'austin texas', 'austin tx', 'boston ma', 'boston massachusetts', 'chicago', 'chicago il',
          'chicago illinois', 'columbus', 'columbus oh', 'columbus ohio', 'dc', 'denver', 'denver co',
          'denver colorado', 'district of columbia', 'l a', 'la', 'las vegas', 'las vegas nevada',
          'las vegas nv', 'los angeles', 'los angeles ca', 'los angeles california', 'miami', 'miami fl',
          'miami florida', 'new york', 'new york city', 'new york city ny', 'new york ny', 'nyc',
          'san francisco', 'san francisco ca', 'san francisco california', 'seattle', 'seattle wa',
          'seattle washington', 'sf', 'vegas', 'washington d c', 'washington dc']
patterns = []
seen = set()
def add(pattern, flags, origin):
    compiled = re.compile(pattern, flags)
    key = (compiled.pattern, compiled.flags)
    if key not in seen:
        seen.add(key); patterns.append({'pattern': compiled.pattern, 'flags': compiled.flags, 'origins': [origin], 'replacements': []})
    else:
        next(x for x in patterns if (x['pattern'], x['flags']) == key)['origins'].append(origin)

for module in modules:
    source = Path(module.__file__).read_text(encoding='utf8')
    tree = ast.parse(source)
    vocabulary = ['museums','food','parks','history','art','quiet','central','budget','luxury','vegetarian','vegan','seafood','walking','public transit','taxi']
    aliases = [candidate_data.location_key(alias) for city in candidate_data.load_candidate_dataset().cities for alias in [city.city, *city.aliases]]
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == 'Field':
            for keyword in node.keywords:
                if keyword.arg == 'pattern' and isinstance(keyword.value, ast.Constant):
                    add(keyword.value.value, 0, f'{module.__name__}:{node.lineno}:Field')
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute) or not isinstance(node.func.value, ast.Name) or node.func.value.id != 're' or node.func.attr not in ('compile','search','match','fullmatch','finditer','findall','split','sub') or not node.args:
            continue
        expression = node.args[0]
        # city_at_start uses a dynamic pattern derived from every registered alias.
        if isinstance(expression, ast.Name) and expression.id == 'pattern':
            for alias in aliases:
                add(r'^' + r'[\s,.]+'.join(map(re.escape, alias.split())) + r'\b', re.I, f'{module.__name__}:{node.lineno}:alias:{alias}')
            continue
        terms = vocabulary if 'term' in {n.id for n in ast.walk(expression) if isinstance(n, ast.Name)} and not isinstance(expression, ast.Name) else list(requirements.NEGATIVE_TERMS) if isinstance(expression, ast.Name) and expression.id == 'term' else [None]
        for term in terms:
            scope = dict(vars(module)); scope.update(term=term)
            try:
                pattern = eval(compile(ast.Expression(expression), module.__file__, 'eval'), scope)
                if not isinstance(pattern, str): continue
                flag_expr = next((k.value for k in node.keywords if k.arg == 'flags'), None)
                index = 2 if node.func.attr in ('search','match','fullmatch','finditer','findall','split') else 1 if node.func.attr == 'compile' else 4
                if flag_expr is None and len(node.args) > index: flag_expr = node.args[index]
                flags = eval(compile(ast.Expression(flag_expr), module.__file__, 'eval'), scope) if flag_expr else 0
                add(pattern, flags, f'{module.__name__}:{node.lineno}:{term}' if term else f'{module.__name__}:{node.lineno}')
                if node.func.attr == 'sub':
                    repl = eval(compile(ast.Expression(node.args[1]), module.__file__, 'eval'), scope)
                    compiled = re.compile(pattern, flags)
                    item = next(x for x in patterns if x['pattern'] == compiled.pattern and x['flags'] == compiled.flags)
                    if repl not in item['replacements']: item['replacements'].append(repl)
            except (NameError, TypeError) as exc:
                raise RuntimeError(f'Cannot resolve {module.__name__}:{node.lineno}: {ast.unparse(expression)}') from exc

def match_data(match):
    if match is None: return None
    groups = [{'value': match.group(i), 'span': list(match.span(i))} for i in range(match.re.groups + 1)]
    named = {name: groups[index] for name, index in match.re.groupindex.items()}
    return {'groups': groups, 'named': named}

out = ROOT / 'core/src/test/resources/parity/regex'
out.mkdir(parents=True, exist_ok=True)
corpus = queries + manual
manifest = {'python_version': sys.version.split()[0], 'source_commit': COMMIT, 'corpus': corpus, 'patterns': patterns, 'query_count': len(queries), 'manual_count': len(manual)}
(out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')
# Compact JSONL allows streaming instead of loading the entire corpus at once.
with (out / 'results.jsonl').open('w', encoding='utf8', newline='\n') as target:
    for i, item in enumerate(patterns):
        regex = re.compile(item['pattern'], item['flags'])
        for j, text in enumerate(corpus):
            result = {'p': i, 'c': j, 'search': match_data(regex.search(text)), 'match': match_data(regex.match(text)),
                      'fullmatch': match_data(regex.fullmatch(text)), 'finditer': [match_data(m) for m in regex.finditer(text)],
                      'split': regex.split(text), 'sub': regex.sub('<X>', text),
                      'sub_templates': {replacement: regex.sub(replacement, text) for replacement in item['replacements']}}
            target.write(json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n')
print('patterns', len(patterns), 'queries', len(queries), 'handwritten corpus', len(manual), 'records', len(patterns) * len(corpus))
missing = [i for i, p in enumerate(patterns) if not any(re.search(p['pattern'], t, p['flags']) for t in manual)]
print('patterns lacking a handwritten positive:', missing)

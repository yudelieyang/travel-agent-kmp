"""Record Python golden-v1 parser helper outputs without modifying the Python repository."""
import json
import subprocess
import sys
from pathlib import Path
sys.dont_write_bytecode = True
from pydantic import BaseModel
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
COMMIT = '0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd'
if subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'golden-v1^{commit}']).decode().strip() != COMMIT:
    raise RuntimeError('Unexpected golden-v1 commit')
if subprocess.check_output(['git', '-C', str(SOURCE), 'diff', '--name-only', COMMIT, '--', 'backend/app', 'data/travel', 'evals/datasets/hybrid_semantics_200_v1.json']):
    raise RuntimeError('Python runtime code or data differs from golden-v1')
sys.path.insert(0, str(SOURCE / 'backend'))
from app.agent import requirements as r, semantic_coverage as c
from app.tools import candidate_data as cities

def data(value):
    if isinstance(value, BaseModel): return value.model_dump(mode='json')
    if isinstance(value, (tuple, list)): return [data(v) for v in value]
    if isinstance(value, dict): return {str(k): data(v) for k, v in value.items()}
    return value

def attempt(f):
    try: return {'value': data(f()), 'error': None}
    except Exception as exc: return {'value': None, 'error': type(exc).__name__}

def match_data(match):
    return {'text': match.group(), 'span': list(match.span()), 'groups': match.groupdict(),
            'group_spans': {name: list(match.span(name)) for name in match.re.groupindex}}

cases = json.loads((SOURCE / 'evals/datasets/hybrid_semantics_200_v1.json').read_text(encoding='utf8'))['cases']
records=[]
for case in cases:
    query=case['input']
    amounts=r.recognized_money_expressions(query)
    parsed=attempt(lambda:r.parse_requirements(query))
    req = r.TravelRequirements.model_validate(parsed['value']) if parsed['error'] is None else None
    context=[]
    for match in amounts:
        prefix,suffix=r.budget_context(query,match)
        intro=match['intro'] or ''
        context.append({'match':match_data(match), 'prefix':prefix, 'suffix':suffix, 'intro':intro,
                        'monetary_scope':data(r.monetary_scope(prefix,suffix,intro)),
                        'budget_strength':data(r.budget_strength(f'{prefix} {intro} {suffix}')),
                        'budget_currency':r.budget_currency(match)})
    extraction=attempt(lambda:r.extract_budget_constraints(query,amounts))
    resolved=cities.resolve_city(query)
    start=cities.city_at_start(query)
    out={'id':case['id'],'query':query,
         'recognized_money_expressions':[match_data(m) for m in amounts],
         'extract_budget_constraints':extraction,
         'amount_context':context,
         'budget_strength':data(r.budget_strength(query)),
         'merge_budget_constraints': attempt(lambda:r.merge_budget_constraints([r.BudgetConstraint.model_validate(x) for x in extraction['value'][0]])) if extraction['error'] is None else None,
         'extract_specific_preferences':attempt(lambda:r.extract_specific_preferences(query)),
         'extract_budget_preferences':attempt(lambda:r.extract_budget_preferences(query,amounts)),
         'city_at_start': data(start), 'resolve_city': data(resolved),
         'parsed':parsed,
         'budget_like_amounts':attempt(lambda:c.budget_like_amounts(query)),
         'unrepresented_hard_money':attempt(lambda:c.unrepresented_hard_money(query,req)) if req else None,
         'unresolved_correction':attempt(lambda:c.unresolved_correction(query,req)) if req else None,
         'unrepresented_exclusion':attempt(lambda:c.unrepresented_exclusion(query))}
    records.append(data(out))
out=ROOT/'core/src/test/resources/parity/functions/python.json'
out.write_text(json.dumps({'source_commit':COMMIT,'python_version':sys.version.split()[0], 'cases':records}, ensure_ascii=False, indent=2)+'\n',encoding='utf8',newline='\n')
print('function cases:',len(records))

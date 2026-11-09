"""Compare Python and Kotlin against the same semantic evaluator and gold annotations."""
import json
import subprocess
import sys
from pathlib import Path
sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
COMMIT = '0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd'
if subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'golden-v1^{commit}']).decode().strip() != COMMIT:
    raise RuntimeError('golden-v1 changed')
if subprocess.check_output(['git', '-C', str(SOURCE), 'diff', '--name-only', COMMIT, '--', 'backend/app', 'data/travel', 'evals/datasets/hybrid_semantics_200_v1.json']):
    raise RuntimeError('Python runtime differs from golden-v1')
sys.path.insert(0, str(SOURCE / 'backend'))
from app.agent.hybrid_evaluation import load_dataset, _evaluate_case, _failures
from app.agent.requirements import TravelRequirements

dataset = load_dataset(SOURCE / 'evals/datasets/hybrid_semantics_200_v1.json')
rows = json.loads((ROOT / 'core/build/p2-kotlin-results.json').read_text(encoding='utf8'))
by_id = {row['id']: row for row in rows}
if len(by_id) != 200: raise ValueError('Expected exactly 200 Kotlin cases')
python_pass = kotlin_pass = 0
mismatches=[]
for case in dataset.cases:
    if case.mock_proposal is not None: raise ValueError('Unexpected mock proposal in dataset')
    py = _evaluate_case(case, mode='offline', extractor=None)
    row = by_id[case.id]
    parsed = TravelRequirements.model_validate(row['requirements'])
    failures = _failures(case, parsed, row['coverage']['needs_llm'], None, parsed)
    python_pass += py['passed']
    kotlin_pass += not failures
    if py['failure_categories'] != failures:
        mismatches.append({'id':case.id, 'python':py['failure_categories'], 'kotlin':failures})
result={'dataset':dataset.version,'source_commit':COMMIT,'cases':len(dataset.cases), 'python_pass':python_pass,
        'kotlin_pass':kotlin_pass, 'python_rate':python_pass/len(dataset.cases),'kotlin_rate':kotlin_pass/len(dataset.cases),'mismatches':mismatches}
out = ROOT / 'docs/P2_GOLD_COMPARISON.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
print(json.dumps(result,ensure_ascii=False))
if mismatches: raise SystemExit(1)

"""Export Python PlanResponse errors for parity; the Python checkout is read-only."""
import json, sys, subprocess
from functools import partial
from pathlib import Path
sys.dont_write_bytecode = True
root = Path(__file__).resolve().parents[1]
source = root.parent / 'travel-planning-agent'
commit = '0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd'
if subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'golden-v1^{commit}']).decode().strip() != commit:
    raise RuntimeError('Unexpected golden-v1 commit')
if subprocess.check_output(['git', '-C', str(source), 'diff', '--name-only', commit, '--', 'backend/app', 'data/travel']):
    raise RuntimeError('Python runtime code or data differs from golden-v1')
sys.path.insert(0, str(source / 'backend'))
from app.agent.planner import DeterministicTestPlanner
from app.agent.service import TravelService
from app.tools.candidate_providers import create_candidate_provider
from app.tools.mock import run_tool
provider = create_candidate_provider('snapshot', source / 'data/travel/snapshots/openstreetmap/migration_manifest.json')
service = TravelService(planner=DeterministicTestPlanner(), tool_runner=partial(run_tool, provider=provider), semantic_augmentation_enabled=False)
queries = [(p.stem, json.loads(p.read_text(encoding='utf8'))['query']) for p in sorted((root / 'core/src/test/resources/golden/e2e').glob('*.json'))]
queries += [('unknown-city', 'Plan a 2-day trip to wonderland for 1 traveler under $500 total.'), ('no-hotels', 'Plan a 3-day trip to Boston for 1 traveler. Hotel spending must stay under $1 total.')]
queries += [
 ('missing-destination', 'Plan a 3-day trip for 1 traveler.'),
 ('missing-both', 'Plan a trip.'),
 ('coverage-unresolved', 'Find me zoos in Columbus.'),
 ('coverage-ambiguous', 'Do not let Seattle hotel spending exceed $635 total.'),
 ('coverage-money', 'Keep the entire Seattle trip within $1,700, and keep accommodation within $760 total.'),
 ('coverage-correction', 'Hotel spending cannot exceed $580 total. Actually, $530.'),
 ('coverage-negation', 'Boston is fine, but do not include zoos.'),
 ('coverage-tradeoff', 'Spend more on the Boston hotel if it is downtown, but keep the whole trip below $1,300.'),
]
assert len(queries) == 38
cases = []
for name, query in queries:
    state, trace = service.run(query)
    assert not trace.llm_invoked
    cases.append({'id': name, 'query': query, 'errors': state['errors']})
out = root / 'core/src/test/resources/parity/errors.json'
out.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + '\n', encoding='utf8')

"""Generate supplemental deterministic Python cases without modifying the source repository."""
import importlib.util
import json
import sys
from functools import partial
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
sys.path.insert(0, str(SOURCE / 'backend'))
spec = importlib.util.spec_from_file_location('golden_export', SOURCE / 'scripts/export_golden_fixtures.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
from app.agent.planner import DeterministicTestPlanner
from app.agent.service import TravelService
from app.tools.candidate_providers import create_candidate_provider
from app.tools.mock import run_tool

provider = create_candidate_provider('snapshot', SOURCE / 'data/travel/snapshots/openstreetmap/migration_manifest.json')
service = TravelService(planner=DeterministicTestPlanner(), tool_runner=partial(run_tool, provider=provider), semantic_augmentation_enabled=False)
queries = [
    'Plan a 2-day trip to Boston for 2 travelers. Hotel spending must not exceed $1 total.',
    'Plan a 1-day trip to Boston for 2 travelers. Hotel spending must not exceed $0 total.',
    'Plan a 2-day trip to Atlantis for 1 traveler.',
    'Plan a 4-day trip to Boston for 2 travelers. Hotel spending must not exceed $400 total. The total budget is $1200.',
    'Plan a 3-day trip to Miami for 3 travelers. The total budget is $250.',
    'Plan a 3-day trip to Seattle from 2026-12-01 to 2026-12-03 for 1 traveler.',
    'Plan a 10-day trip to Chicago for 2 travelers. I like walking and seafood.',
    'Plan a 2-day trip to Austin with a budget of EUR 500 total.',
    'Plan a 2-day trip to Boston. The total budget is $500.',
    'Plan a 2-day trip to Boston for 2 travelers. I like museums and vegetarian food.',
]
records = []
for index, query in enumerate(queries, 1):
    try:
        result = module.fixture(service, query)
        records.append({'id': f'extra-{index:02}', 'fixture': module.encode(result)})
    except ValueError as exc:
        raise RuntimeError(f'Invalid supplemental query {index}: {query}') from exc
out = ROOT / 'core/src/test/resources/p1b-supplemental.json'
out.write_text(json.dumps(records, ensure_ascii=False, sort_keys=True, indent=2) + '\n', encoding='utf8', newline='\n')
print('Generated', len(records), 'supplemental cases')

"""Capture Python golden-v1 candidate-provider outputs, including mock routes."""
import json, sys
from functools import partial
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
sys.path.insert(0, str(SOURCE / 'backend'))
from app.tools.candidate_data import load_candidate_dataset
from app.tools.candidate_providers import create_candidate_provider
provider = create_candidate_provider('snapshot', SOURCE / 'data/travel/snapshots/openstreetmap/migration_manifest.json')
cases = []
for city in load_candidate_dataset().cities:
    for category in ('attractions', 'hotel', 'food', 'transport'):
        options = provider.candidates(city.city, category)
        cases.append({'city': city.city, 'category': category, 'options': [option.model_dump(mode='json') for option in options]})
out = ROOT / 'core/src/test/resources/candidate-projection-cases.json'
out.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')
print('Python candidate projection cases:', len(cases))

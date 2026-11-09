"""Generate validation parity cases using golden-v1 Python source without modifying it."""
import copy, json, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'travel-planning-agent'
sys.path.insert(0, str(SOURCE / 'backend'))
from app.tools.candidate_providers import CandidateSnapshot
path = 'data/travel/snapshots/openstreetmap/osm_boston_hotels_2026-09-16_v1/boston_hotels.json'
raw = subprocess.check_output(['git', '-C', str(SOURCE), 'show', 'golden-v1:' + path])
base = json.loads(raw)
def mutations(d):
    row = d['cities'][0]['candidates']['hotel'][0]
    return {
        'valid': lambda: None,
        'bool_price': lambda: row.update(price=True),
        'epoch_timestamp': lambda: d.update(snapshot_fetched_at=0),
        'compact_timezone': lambda: d.update(snapshot_fetched_at='2026-09-16T12:00:00+0000'),
        'negative_price': lambda: row.update(price=-1),
        'bad_latitude': lambda: row.update(latitude=91),
        'missing_longitude': lambda: row.pop('longitude'),
        'missing_provider': lambda: d.pop('provider'),
        'naive_timestamp': lambda: d.update(snapshot_fetched_at='2026-09-16T12:00:00'),
        'bad_version': lambda: d.update(snapshot_version='INVALID VERSION'),
        'bad_origin': lambda: row.update(cost_origin='provider_observed'),
        'bad_unit': lambda: row.update(unit='per_person_meal'),
        'empty_name': lambda: row.update(name=''),
        'invalid_osm_id': lambda: row.update(provider_place_id='way/not-a-number'),
        'wrong_count': lambda: d.update(record_count=999),
        'duplicate_id': lambda: d['cities'][0]['candidates']['hotel'].append({**copy.deepcopy(row), 'provider_place_id': 'node/99999999999'}),
        'duplicate_provider_id': lambda: d['cities'][0]['candidates']['hotel'].append({**copy.deepcopy(row), 'id': 'new-id'}),
        'empty_candidates': lambda: d['cities'][0]['candidates'].update(hotel=[]),
        'unknown_field': lambda: row.update(extra_field=1),
        'null_currency': lambda: row.update(currency=None),
    }
result=[]
for name in mutations(copy.deepcopy(base)):
    d=copy.deepcopy(base); mutations(d)[name]()
    try: CandidateSnapshot.model_validate(d); accepted=True
    except Exception: accepted=False
    result.append({'id':name, 'accepted':accepted, 'snapshot':d})
out=ROOT / 'core/src/test/resources/validation-cases.json'
out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
print('Generated Python oracle cases:',len(result))

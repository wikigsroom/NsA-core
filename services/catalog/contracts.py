from __future__ import annotations
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

CONTRACTS = Path(__file__).resolve().parents[2] / 'docs' / 'engineering' / 'contracts'
DOCUMENTS = {p.name: json.loads(p.read_text(encoding='utf-8')) for p in CONTRACTS.glob('*.schema.json')}
REGISTRY = Registry().with_resources((doc['$id'], Resource.from_contents(doc)) for doc in DOCUMENTS.values())
OPENAPI = json.loads((CONTRACTS / 'openapi.json').read_text(encoding='utf-8'))
OPENAPI['paths'].update(json.loads((CONTRACTS / 'extensions-v1.1.json').read_text(encoding='utf-8'))['paths'])

def validate(value, reference: str):
    file, _, fragment = reference.partition('#')
    root = DOCUMENTS[file]
    schema = {'$ref': root['$id'] + ('#' + fragment if fragment else '')}
    Draft202012Validator(schema, registry=REGISTRY, format_checker=FormatChecker()).validate(value)
    return value

def strict_json(raw: bytes, limit=2 * 1024 * 1024):
    if len(raw) > limit:
        raise ValueError('JSON exceeds size limit')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw.decode('utf-8', errors='strict'), object_pairs_hook=pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))

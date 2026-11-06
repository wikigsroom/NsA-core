from __future__ import annotations

import base64
from concurrent.futures import ThreadPoolExecutor
import json

from catalog.crypto import canonical
from catalog.db import Record, change, create
from catalog.policy import initialize_policy_sequence, policy_snapshot
from test_distribution import bootstrap


def payload(envelope):
    return json.loads(base64.b64decode(envelope['payload_base64']))


def snapshot(platform, internal=True):
    with platform.sessions.begin() as db:
        return policy_snapshot(db, platform.signer, internal)


def test_repeat_fetch_keeps_exact_signed_payload_and_scope_visibility(system):
    platform, client, _ = system
    with platform.sessions.begin() as db:
        create(db, 'release', 'public-release', {'channel': 'official'}, state='published')
        create(db, 'release', 'private-release', {'channel': 'internal'}, state='blocked')
    official = snapshot(platform, False)
    internal = snapshot(platform)
    assert snapshot(platform, False) == official
    assert snapshot(platform) == internal
    assert [r['release_id'] for r in payload(official)['releases']] == ['public-release']
    assert [r['release_id'] for r in payload(internal)['releases']] == ['private-release', 'public-release']
    credentials = bootstrap(platform, client, False)
    response = client.get('/v1/policies', headers={'Authorization': 'Bearer ' + credentials})
    assert response.status_code == 200
    assert response.json() == official


def test_frozen_or_reversed_wall_clock_never_reuses_a_sequence(system, monkeypatch):
    platform, _, _ = system
    monkeypatch.setattr('catalog.policy.time.time_ns', lambda: 1)
    with platform.sessions.begin() as db:
        create(db, 'release', 'release-1', {'channel': 'official'}, state='published')
    first = snapshot(platform)
    with platform.sessions.begin() as db:
        row = db.get(Record, ('release', 'release-1'))
        change(db, row, row.revision, row.data(), state='blocked')
    second = snapshot(platform)
    assert payload(second)['epoch'] > payload(first)['epoch']
    assert payload(second)['releases'][0]['state'] == 'blocked'
    initialize_policy_sequence(platform.sessions)
    assert snapshot(platform) == second


def test_concurrent_scoped_readers_share_one_durable_snapshot_per_change(system):
    platform, _, _ = system
    with platform.sessions.begin() as db:
        create(db, 'release', 'release-1', {'channel': 'official'}, state='published')
    with ThreadPoolExecutor(max_workers=8) as pool:
        envelopes = list(pool.map(lambda _: snapshot(platform), range(24)))
    assert len({canonical(envelope) for envelope in envelopes}) == 1
    with platform.sessions.begin() as db:
        row = db.get(Record, ('release', 'release-1'))
        change(db, row, row.revision, row.data(), state='blocked')
    with ThreadPoolExecutor(max_workers=8) as pool:
        newer = list(pool.map(lambda _: snapshot(platform), range(24)))
    assert len({canonical(envelope) for envelope in newer}) == 1
    assert payload(newer[0])['epoch'] > payload(envelopes[0])['epoch']

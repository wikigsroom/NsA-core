"""A lost renewal response must be recoverable without reusing an invitation."""
import secrets
import time
import uuid

from catalog.crypto import canonical, digest, token_hash
from catalog.db import Record, change, create


def enroll(platform, client):
    invitation = secrets.token_urlsafe(40)
    with platform.sessions() as db:
        create(db, 'access_grant', token_hash(invitation), {'expires': int(time.time()) + 60}, state='active')
        db.commit()
    body = {'app_version_code': 1, 'installation_nonce': uuid.uuid4().hex,
            'locale': 'en-US', 'channel': 'internal', 'access_grant': invitation}
    key = uuid.uuid4().hex
    response = client.post('/v1/bootstrap', json=body, headers={'Idempotency-Key': key})
    assert response.status_code == 201
    return response.json(), body, key


def test_bootstrap_receipt_recovers_one_use_invitation(system):
    platform, client, _ = system
    original, body, key = enroll(platform, client)
    replay = client.post('/v1/bootstrap', json=body, headers={'Idempotency-Key': key})
    assert replay.status_code == 201
    assert digest(canonical(replay.json())) == digest(canonical(original))
    reused = client.post('/v1/bootstrap', json=body, headers={'Idempotency-Key': uuid.uuid4().hex})
    assert reused.status_code == 403 and reused.json()['code'] == 'CAT_SCOPE_DENIED'


def test_lost_refresh_response_replays_without_losing_internal_scope(system):
    platform, client, _ = system
    original, _, _ = enroll(platform, client)
    with platform.sessions() as db:
        row = db.get(Record, ('credential', token_hash(original['access_token'])))
        change(db, row, row.revision, {**row.data(), 'expires': int(time.time()) - 5})
        db.commit()
    expired = client.get('/v1/catalogue', headers={'Authorization': 'Bearer ' + original['access_token']})
    assert expired.status_code == 401 and expired.json()['code'] == 'AUTH_EXPIRED'
    key = uuid.uuid4().hex
    body = {'refresh_token': original['refresh_token']}
    # The server committed the first rotation; pretend its response was lost.
    first = client.post('/v1/credentials/refresh', json=body, headers={'Idempotency-Key': key})
    assert first.status_code == 200
    replay = client.post('/v1/credentials/refresh', json=body, headers={'Idempotency-Key': key})
    assert replay.status_code == 200
    assert digest(canonical(replay.json())) == digest(canonical(first.json()))
    recovered = replay.json()
    with platform.sessions() as db:
        row = db.get(Record, ('credential', token_hash(recovered['access_token'])))
        assert row.owner == original['installation_id']
        assert row.data()['scopes'] == ['internal_content']
    assert client.get('/v1/catalogue', headers={'Authorization': 'Bearer ' + recovered['access_token']}).status_code == 200
    rejected = client.post('/v1/credentials/refresh', json=body, headers={'Idempotency-Key': uuid.uuid4().hex})
    assert rejected.status_code == 401 and rejected.json()['code'] == 'AUTH_EXPIRED'
    denied = client.get('/admin/v1/games', headers={'Authorization': 'Bearer ' + recovered['access_token']})
    assert denied.status_code == 403 and denied.json()['code'] == 'PERMISSION_DENIED'

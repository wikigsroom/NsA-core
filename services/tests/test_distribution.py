from __future__ import annotations
import base64
from dataclasses import replace
import io
import json
from pathlib import Path
import secrets
import time
import uuid
import zipfile
import pytest
from cryptography.exceptions import InvalidSignature
from catalog.contracts import validate
from catalog.crypto import canonical, digest, token_hash
from catalog.db import Record, create
from catalog.packages import safe_path, verify_archive
from content_tools.package import package
from worker.main import run_once

def call(client, token, path, body=None, method=None, key=None):
    return client.request(method or ('POST' if body is not None else 'GET'), path, json=body,
                          headers={'Authorization': 'Bearer ' + token, 'Idempotency-Key': key or str(uuid.uuid4())})

def upload(client, token, data, kind):
    response = call(client, token, '/admin/v1/artifact-uploads', {'bytes': str(len(data)), 'sha256': digest(data), 'kind': kind, 'content_type': 'application/octet-stream'})
    assert response.status_code == 201, response.text
    grant = response.json(); validate(grant, 'domain.schema.json#/$defs/ArtifactUploadGrant')
    assert client.put(grant['upload_url'], content=data).status_code == 200
    verified = call(client, token, '/admin/v1/artifact-uploads/' + grant['object_id'] + '/complete', {'expected_revision': 1, 'reason': 'Full object read-back verification for an integration test.'})
    assert verified.is_success, verified.text
    return {'object_id': grant['object_id'], 'bytes': str(len(data)), 'sha256': digest(data)}

def release(system, tmp_path):
    platform, client, tokens = system; editor, reviewer = tokens['editor'], tokens['reviewer']
    source = tmp_path / 'source'; (source / 'RomFS').mkdir(parents=True); (source / 'RomFS/data.txt').write_bytes(b'actual resource\n' * 1000)
    spec = {'game_id': 'test-game', 'release_id': 'test-release', 'game_version': '1.0', 'channel': 'internal', 'required_app_version_code': 1,
            'runtime': {'runtime_api_version': 1, 'runtime_slot_id': 'test-slot', 'profile_digest': 'a' * 64, 'module_build_ids': ['b' * 64], 'shader_chain_id': 'test-chain', 'shader_bundle_version': 1, 'approved_driver_ids': ['system']},
            'save': {'compatibility_group': 'save-1', 'compatible_previous_releases': [], 'backup_before_upgrade': True}, 'offline_permitted': True, 'minimum_capability': {'api_level': 31, 'abi': 'arm64-v8a'}}
    folder = tmp_path / 'package'; manifest = package(source, folder, spec)
    assert call(client, editor, '/admin/v1/games', {'game_id': 'test-game', 'name': 'Integration game', 'genre': 'puzzle', 'description': 'Real streamed package for service integration checks.', 'languages': ['en-US'], 'input_modes': ['touch']}).is_success
    document = upload(client, editor, b'Internal testing material, no public distribution grant.', 'rights_document')
    assert call(client, editor, '/admin/v1/rights-records', {'rights_record_id': 'rights-1', 'game_id': 'test-game', 'kind': 'internal_test', 'regions': [], 'valid_from': '2026-01-01T00:00:00Z', 'material_object_ids': [document['object_id']]}).is_success
    review = {'expected_revision': 1, 'reason': 'Independent review of internal test rights material.', 'verdict': 'approve', 'evidence_ids': []}
    assert call(client, editor, '/admin/v1/rights-records/rights-1/reviews', review).status_code == 403
    assert call(client, reviewer, '/admin/v1/rights-records/rights-1/reviews', review).is_success
    archive_data = (folder / 'test-release-archive.zip').read_bytes()
    manifest['archive'] = upload(client, editor, archive_data, 'content_archive')
    manifest['file_index'] = upload(client, editor, (folder / 'test-release-files.json').read_bytes(), 'file_index')
    chunks = json.loads((folder / 'test-release-chunks.json').read_text()); chunks['object_id'] = manifest['archive']['object_id']
    manifest['chunk_index'] = upload(client, editor, canonical(chunks), 'chunk_index')
    sbom = upload(client, editor, canonical({'properties': [{'name': 'nsa:release-manifest', 'value': canonical(manifest).decode()}]}), 'sbom')
    assert call(client, editor, '/admin/v1/builds', {'build_id': 'build-1', 'source_revision': 'c' * 40, 'core_revision': 'c' * 40, 'profile_digest': 'a' * 64, 'input_inventory_digest': sbom['sha256'], 'runtime_slot_id': 'test-slot', 'artifact_ids': [manifest[k]['object_id'] for k in ('archive', 'file_index', 'chunk_index')] + [sbom['object_id']]}).is_success
    assert call(client, editor, '/admin/v1/releases', {'release_id': 'test-release', 'game_id': 'test-game', 'game_version': '1.0', 'build_id': 'build-1', 'rights_record_id': 'rights-1', 'channel': 'internal'}).is_success
    requested = call(client, editor, '/admin/v1/releases/test-release/verify', {'expected_revision': 1, 'reason': 'Verify complete archive, indexes and binding identities.'})
    assert requested.is_success, requested.text
    assert run_once(platform)
    operation = call(client, editor, '/admin/v1/operations/' + requested.json()['operation_id']).json()
    assert operation['state'] == 'succeeded', operation
    row = call(client, editor, '/admin/v1/releases/test-release').json()
    reviewed = call(client, reviewer, '/admin/v1/releases/test-release/reviews', {**review, 'expected_revision': row['revision']})
    assert reviewed.is_success, reviewed.text
    published = call(client, editor, '/admin/v1/releases/test-release/publish', {'expected_revision': reviewed.json()['revision'], 'reason': 'Publish only to the internal integration-test channel.'})
    assert published.is_success, published.text
    return manifest, archive_data

def bootstrap(platform, client, internal):
    data = {'app_version_code': 1, 'installation_nonce': uuid.uuid4().hex, 'locale': 'en-US', 'channel': 'internal' if internal else 'official'}
    if internal:
        invitation = secrets.token_urlsafe(40)
        with platform.sessions() as db: create(db, 'access_grant', token_hash(invitation), {'expires': int(time.time()) + 60}, state='active'); db.commit()
        data['access_grant'] = invitation
    response = call(client, '', '/v1/bootstrap', data)
    assert response.status_code == 201, response.text
    validate(response.json(), 'domain.schema.json#/$defs/Credentials')
    return response.json()['access_token']

def test_complete_publication_download_revocation(system, tmp_path):
    platform, client, tokens = system
    manifest, original = release(system, tmp_path)
    public = bootstrap(platform, client, False)
    assert call(client, public, '/v1/catalogue').json()['items'] == []
    internal = bootstrap(platform, client, True)
    page = call(client, internal, '/v1/catalogue').json(); validate(page, 'domain.schema.json#/$defs/CataloguePage')
    assert len(page['items']) == 1
    detail = call(client, internal, '/v1/games/test-game').json(); validate(detail, 'domain.schema.json#/$defs/ReleaseDetail')
    envelope = call(client, internal, '/v1/releases/test-release/manifest').json()
    raw = base64.b64decode(envelope['payload_base64'])
    platform.signer.key.public_key().verify(base64.b64decode(envelope['signature_base64']), b'NSA/release/v1\0' + raw)
    assert digest(raw) == detail['manifest_sha256']
    capability = call(client, internal, '/v1/compatibility/evaluate', {'app_version_code': 1, 'runtime_registry_sha256': 'd' * 64, 'capability': {'api_level': 31, 'abi': 'arm64-v8a'}, 'release_ids': ['test-release']}).json()
    validate(capability, 'domain.schema.json#/$defs/CompatibilityResponse')
    request = {'release_id': 'test-release', 'manifest_sha256': digest(raw), 'object_id': manifest['archive']['object_id'], 'app_version_code': 1, 'runtime_registry_sha256': 'd' * 64, 'capability_context_id': capability['capability_context_id'], 'experimental_acknowledged': True}
    denied = call(client, internal, '/v1/download-sessions', {**request, 'experimental_acknowledged': False}); assert not denied.is_success
    download = call(client, internal, '/v1/download-sessions', request).json(); validate(download, 'domain.schema.json#/$defs/DownloadGrant')
    response = client.get(download['url'], headers={'Range': 'bytes=17-499', 'If-Range': download['etag']})
    assert response.status_code == 206 and response.content == original[17:500]
    assert response.headers['content-range'] == f'bytes 17-499/{len(original)}'
    other = bootstrap(platform, client, True)
    assert call(client, other, '/v1/download-sessions/' + download['download_session_id'] + '/renew', {'object_id': request['object_id'], 'manifest_sha256': request['manifest_sha256']}).status_code == 403
    current = call(client, tokens['editor'], '/admin/v1/releases/test-release').json()
    assert call(client, tokens['editor'], '/admin/v1/releases/test-release/block', {'expected_revision': current['revision'], 'reason': 'Revoke this experimental release during integration verification.'}).is_success
    assert client.get(download['url']).status_code == 403
    assert call(client, internal, '/v1/download-sessions', request).status_code == 403

def test_idempotency_and_rbac(system):
    _, client, tokens = system
    body = {'game_id': 'same-game', 'name': 'A title', 'genre': 'puzzle', 'description': 'An idempotent game registration.', 'languages': ['en-US'], 'input_modes': ['touch']}
    key = uuid.uuid4().hex
    first = call(client, tokens['editor'], '/admin/v1/games', body, key=key)
    replay = call(client, tokens['editor'], '/admin/v1/games', body, key=key)
    assert first.status_code == replay.status_code == 201 and first.json() == replay.json()
    assert call(client, tokens['editor'], '/admin/v1/games', {**body, 'name': 'Changed'}, key=key).status_code == 409
    assert call(client, tokens['viewer'], '/admin/v1/games', {**body, 'game_id': 'forbidden'}).status_code == 403
    assert call(client, tokens['editor'], '/admin/v1/games/same-game', {'expected_revision': 99, 'reason': 'Attempt to update a record at a stale revision.', 'changes': {'name': 'Changed'}}, method='PATCH').status_code == 409

def test_duplicate_fields_and_size_limit(system):
    _, client, tokens = system
    response = client.post('/admin/v1/games', content=b'{"game_id":"one","game_id":"two"}', headers={'Authorization': 'Bearer ' + tokens['editor'], 'Idempotency-Key': uuid.uuid4().hex})
    assert response.status_code == 422 and response.json()["code"] == "REQUEST_INVALID"
    response = client.post('/v1/bootstrap', content=b' ' * (256 * 1024 + 1), headers={'Idempotency-Key': uuid.uuid4().hex})
    assert response.status_code == 422 and response.json()["code"] == "REQUEST_INVALID"

@pytest.mark.parametrize('path', ['../save', '/absolute', 'a/../../b', 'a\\b', 'a//b', 'a/./b', 'x:y', 'a/' + 'x' * 1025, 'trailing.'])
def test_unsafe_paths(path):
    with pytest.raises(ValueError): safe_path(path)

def test_signature_domain_separation(system):
    platform, _, _ = system
    policy = {'policy_schema': 1, 'epoch': 1, 'issued_at': '2026-11-06T00:00:00Z', 'releases': [], 'revoked_key_ids': []}
    envelope = platform.signer.envelope('policy', policy)
    raw, signature = base64.b64decode(envelope['payload_base64']), base64.b64decode(envelope['signature_base64'])
    with pytest.raises(InvalidSignature): platform.signer.key.public_key().verify(signature, b'NSA/release/v1\0' + raw)
    with pytest.raises(InvalidSignature): platform.signer.key.public_key().verify(signature, b'NSA/policy/v1\0' + raw + b' ')


def test_catalogue_snapshot_binding_and_stability(system, tmp_path):
    platform, client, _ = system
    release(system, tmp_path)
    token = bootstrap(platform, client, True)
    first = call(client, token, '/v1/catalogue?limit=1').json()
    assert first['items'][0]['game_id'] == 'test-game'
    from catalog.crypto import seal
    binding = digest(canonical({'owner': None, 'q': '', 'genre': ''}))
    with platform.sessions() as db:
        record = db.get(Record, ('catalogue_snapshot', first['snapshot_id']))
        data = record.data(); binding = data['binding']
    cursor = seal(platform.settings.secret, {'snapshot': first['snapshot_id'], 'binding': binding, 'offset': 0, 'exp': int(time.time()) + 900})
    same = call(client, token, '/v1/catalogue', method='GET')
    replay = client.get('/v1/catalogue', params={'cursor': cursor, 'limit': 1}, headers={'Authorization': 'Bearer ' + token})
    assert replay.status_code == 200 and replay.json()['snapshot_id'] == first['snapshot_id']
    assert replay.json()['items'] == first['items']
    changed = client.get('/v1/catalogue', params={'cursor': cursor, 'q': 'different'}, headers={'Authorization': 'Bearer ' + token})
    assert changed.status_code == 409 and changed.json()['code'] == 'CURSOR_EXPIRED'
    other = bootstrap(platform, client, True)
    stolen = client.get('/v1/catalogue', params={'cursor': cursor}, headers={'Authorization': 'Bearer ' + other})
    assert stolen.status_code == 409

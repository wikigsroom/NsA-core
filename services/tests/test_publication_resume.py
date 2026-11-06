from concurrent.futures import ThreadPoolExecutor
import json
import threading
import pytest
from catalog.db import rows
from content_tools.package import package
from content_tools.publish import Publisher
from worker.main import run_once


def test_publication_resumes_uploaded_objects_and_review(system, tmp_path, monkeypatch):
    platform, client, tokens = system
    source = tmp_path / 'resources'
    (source / 'RomFS').mkdir(parents=True)
    (source / 'RomFS/rules.json').write_text('{"board":4}', encoding='utf-8')
    spec = {'game_id': 'resume-game', 'release_id': 'resume-v1', 'game_version': '1.0', 'channel': 'internal', 'required_app_version_code': 1,
            'runtime': {'runtime_api_version': 1, 'runtime_slot_id': 'resume-slot', 'profile_digest': 'a' * 64, 'module_build_ids': ['b' * 64], 'shader_chain_id': 'resume-chain', 'shader_bundle_version': 1, 'approved_driver_ids': ['system']},
            'save': {'compatibility_group': 'resume-save', 'compatible_previous_releases': [], 'backup_before_upgrade': True}, 'offline_permitted': True, 'minimum_capability': {'api_level': 31, 'abi': 'arm64-v8a'}}
    folder = tmp_path / 'package'; package(source, folder, spec)
    material = folder / 'rights.txt'; material.write_text('Original MIT licensed demonstration material.', encoding='utf-8')
    metadata = {'game_id': 'resume-game', 'release_id': 'resume-v1', 'name': 'Resume integration game', 'genre': 'puzzle', 'description': 'Real publication after interruption.', 'languages': ['en-US'], 'input_modes': ['touch'], 'rights_kind': 'open_license'}
    profile = {'core_revision': 'c' * 40, 'profile_digest': 'a' * 64, 'runtime_slot_id': 'resume-slot'}
    publisher = Publisher('https://testserver', None, tokens['editor'], tokens['reviewer'])
    publisher.client.close(); publisher.client = client
    original = publisher.upload
    def interrupted(path, kind, expected_sha=None):
        if kind == 'file_index': raise RuntimeError('Simulated interruption after the archive upload')
        return original(path, kind, expected_sha)
    monkeypatch.setattr(publisher, 'upload', interrupted)
    with pytest.raises(RuntimeError, match='Simulated interruption'):
        publisher.publish(folder, metadata, profile, material)
    assert (folder / 'resume-v1-publish-journal.json').is_file()
    monkeypatch.setattr(publisher, 'upload', original)
    stopped = threading.Event()
    def work():
        while not stopped.is_set():
            if not run_once(platform): stopped.wait(.05)
    with ThreadPoolExecutor(max_workers=1) as pool:
        task = pool.submit(work)
        try:
            assert publisher.publish(folder, metadata, profile, material)['state'] == 'published'
            assert publisher.publish(folder, metadata, profile, material)['state'] == 'published'
        finally:
            stopped.set()
        task.result(timeout=10)
    with platform.sessions() as db:
        archives = [r for r in rows(db, 'artifact') if r.data()['kind'] == 'content_archive']
        assert len(archives) == 1 and archives[0].state == 'verified'
        assert len(rows(db, 'release')) == 1 and len(rows(db, 'rights')) == 1
    changed = json.loads((folder / 'resume-v1-manifest.json').read_text())
    changed['game_version'] = '2.0'
    (folder / 'resume-v1-manifest.json').write_text(json.dumps(changed), encoding='utf-8')
    with pytest.raises(ValueError, match='new immutable release ID'):
        publisher.publish(folder, metadata, profile, material)

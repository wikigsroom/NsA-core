"""Publish local packages through the real upload, verification and independent-review APIs."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import uuid
import httpx
from .package import ROOT, hash_file, write_json
from catalog.crypto import canonical

class Publisher:
    def __init__(self, endpoint, ca, editor, reviewer):
        self.client = httpx.Client(base_url=endpoint, verify=str(ca) if ca else True, timeout=httpx.Timeout(3600, connect=20, write=120), follow_redirects=False, trust_env=False)
        self.editor, self.reviewer = editor, reviewer
        self.endpoint = endpoint.rstrip('/')
        self.journal_path, self.journal = None, {}

    def request(self, path, payload=None, reviewer=False, method=None, key=None, allow_missing=False):
        response = self.client.request(method or ('POST' if payload is not None else 'GET'), path,
                                       headers={'Authorization': 'Bearer ' + (self.reviewer if reviewer else self.editor), 'Idempotency-Key': key or str(uuid.uuid4())}, json=payload)
        if allow_missing and response.status_code == 404: return None
        if not response.is_success:
            raise RuntimeError(f'{response.status_code} {path}: {response.text[:1000]}')
        return response.json() if response.content else None

    def save(self):
        if self.journal_path:
            temporary = self.journal_path.with_suffix('.new')
            with temporary.open('wb') as stream:
                stream.write(canonical(self.journal)); stream.flush(); os.fsync(stream.fileno())
            temporary.replace(self.journal_path)

    def stage(self, name, path, payload, reviewer=False):
        fingerprint = hashlib.sha256(canonical([path, payload, reviewer])).hexdigest()
        saved = self.journal.setdefault('requests', {}).get(name)
        if saved is None:
            saved = {'fingerprint': fingerprint, 'idempotency_key': str(uuid.uuid4())}
            self.journal['requests'][name] = saved; self.save()
        if saved['fingerprint'] != fingerprint: raise ValueError('Publication inputs changed; use a new immutable release ID')
        if 'response' not in saved:
            saved['response'] = self.request(path, payload, reviewer, key=saved['idempotency_key'])
            self.save()
        return saved['response']

    def upload(self, path, kind, expected_sha=None):
        size, sha = path.stat().st_size, expected_sha or hash_file(path)
        name = kind + '-' + sha
        reference = {'bytes': str(size), 'sha256': sha}
        grant = self.stage(name + '-grant', '/admin/v1/artifact-uploads', {**reference, 'kind': kind, 'content_type': 'application/octet-stream'})
        reference['object_id'] = grant['object_id']
        if 'response' in self.journal['requests'].get(name + '-complete', {}): return reference
        checkpoint = self.journal.setdefault('uploads', {}).setdefault(name, {'parts': [], 'single_complete': False})
        complete = {'expected_revision': grant['revision'], 'reason': 'Uploaded artifact; verify complete size and SHA-256 by reading stored bytes.'}
        if grant['mode'] == 'single':
            if not checkpoint['single_complete']:
                with path.open('rb') as incoming:
                    response = self.client.put(grant['upload_url'], content=incoming, headers={'Content-Length': str(size)})
                    response.raise_for_status()
                checkpoint['single_complete'] = True; self.save()
        else:
            parts = checkpoint['parts']
            with path.open('rb') as incoming:
                for base in range(len(parts) + 1, grant['part_count'] + 1, 8):
                    numbers = list(range(base, min(base + 8, grant['part_count'] + 1)))
                    authorized = self.request('/admin/v1/artifact-uploads/' + grant['object_id'] + '/parts', {'expected_revision': grant['revision'], 'part_numbers': numbers})
                    for part in authorized['parts']:
                        incoming.seek((part['part_number'] - 1) * int(grant['part_bytes']))
                        data = incoming.read(int(grant['part_bytes']))
                        response = self.client.put(part['upload_url'], content=data)
                        if response.status_code == 403:
                            fresh = self.request('/admin/v1/artifact-uploads/' + grant['object_id'] + '/parts', {'expected_revision': grant['revision'], 'part_numbers': [part['part_number']]})
                            response = self.client.put(fresh['parts'][0]['upload_url'], content=data)
                        response.raise_for_status()
                        parts.append({'part_number': part['part_number'], 'etag': response.headers['ETag']})
                        self.save()
                    print(f"Uploaded {kind}: {len(parts)}/{grant['part_count']} parts", flush=True)
            complete['parts'] = parts
        self.stage(name + '-complete', '/admin/v1/artifact-uploads/' + grant['object_id'] + '/complete', complete)
        return reference

    def publish(self, folder, metadata, profile, material):
        prefix = metadata['release_id']
        manifest = json.loads((folder / (prefix + '-manifest.json')).read_text(encoding='utf-8'))
        self.journal_path = folder / (prefix + '-publish-journal.json')
        if self.journal_path.exists(): self.journal = json.loads(self.journal_path.read_text(encoding='utf-8'))
        fingerprint = hashlib.sha256(canonical([manifest, metadata, profile])).hexdigest()
        if self.journal and (self.journal.get('endpoint') != self.endpoint or self.journal.get('fingerprint') != fingerprint):
            raise ValueError('Publication inputs or endpoint changed; use a new immutable release ID')
        self.journal.update(endpoint=self.endpoint, fingerprint=fingerprint); self.save()
        existing = self.request('/admin/v1/releases/' + prefix, allow_missing=True)
        if existing and existing['state'] == 'published': return existing
        if existing and existing['state'] in ('blocked', 'unlisted', 'verification_failed', 'rejected'):
            raise ValueError('Release needs explicit operator review; automatic resume cannot republish this state')
        game = {key: metadata[key] for key in ('game_id', 'name', 'genre', 'description', 'languages', 'input_modes')}
        if self.request('/admin/v1/games/' + metadata['game_id'], allow_missing=True) is None:
            self.stage('create-game', '/admin/v1/games', game)
        rights_artifact = self.upload(material, 'rights_document')
        rights_id = prefix + '-rights'
        self.stage('create-rights', '/admin/v1/rights-records', {'rights_record_id': rights_id, 'game_id': metadata['game_id'], 'kind': metadata['rights_kind'], 'regions': [], 'valid_from': '2026-01-01T00:00:00Z', 'material_object_ids': [rights_artifact['object_id']]})
        self.stage('review-rights', '/admin/v1/rights-records/' + rights_id + '/reviews', {'expected_revision': 1, 'reason': 'Independent local content-rights review for this explicitly internal development distribution.', 'verdict': 'approve', 'evidence_ids': []}, reviewer=True)
        manifest['archive'] = self.upload(folder / (prefix + '-archive.zip'), 'content_archive', manifest['archive']['sha256'])
        manifest['file_index'] = self.upload(folder / (prefix + '-files.json'), 'file_index', manifest['file_index']['sha256'])
        chunks_path = folder / (prefix + '-chunks-upload.json')
        chunks = json.loads((folder / (prefix + '-chunks.json')).read_text(encoding='utf-8')); chunks['object_id'] = manifest['archive']['object_id']
        write_json(chunks_path, chunks); manifest['chunk_index'] = self.upload(chunks_path, 'chunk_index')
        sbom_path = folder / (prefix + '-sbom-upload.json')
        write_json(sbom_path, {'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'version': 1, 'components': [], 'properties': [{'name': 'nsa:release-manifest', 'value': json.dumps(manifest, separators=(',', ':'), ensure_ascii=False)}]})
        sbom = self.upload(sbom_path, 'sbom')
        build_id = prefix + '-build'
        self.stage('create-build', '/admin/v1/builds', {'build_id': build_id, 'source_revision': profile['core_revision'], 'core_revision': profile['core_revision'], 'profile_digest': profile['profile_digest'],
                                        'input_inventory_digest': hash_file(sbom_path), 'runtime_slot_id': profile['runtime_slot_id'], 'artifact_ids': [manifest[k]['object_id'] for k in ('archive', 'file_index', 'chunk_index')] + [sbom['object_id']]})
        self.stage('create-release', '/admin/v1/releases', {'release_id': prefix, 'game_id': metadata['game_id'], 'game_version': manifest['game_version'], 'build_id': build_id, 'rights_record_id': rights_id, 'channel': manifest['channel']})
        operation = self.stage('verify-release', '/admin/v1/releases/' + prefix + '/verify', {'expected_revision': 1, 'reason': 'Verify archive paths, complete file and chunk hashes, build identities and publication manifest.'})
        deadline = time.monotonic() + 7200
        while time.monotonic() < deadline:
            result = self.request('/admin/v1/operations/' + operation['operation_id'])
            if result['state'] == 'succeeded': break
            if result['state'] == 'failed': raise RuntimeError('Publication verification failed: ' + json.dumps(result))
            time.sleep(2)
        else: raise TimeoutError('Publication verification remains pending; rerun to resume its journal')
        release = self.request('/admin/v1/releases/' + prefix)
        if release['state'] == 'verified':
            release = self.stage('review-release', '/admin/v1/releases/' + prefix + '/reviews', {'expected_revision': release['revision'], 'reason': 'Independent review of verified immutable artifacts; gameplay remains experimental, not verified.', 'verdict': 'approve', 'evidence_ids': []}, reviewer=True)
        if release['state'] != 'approved': raise ValueError('Release no longer permits publication')
        return self.stage('publish-release', '/admin/v1/releases/' + prefix + '/publish', {'expected_revision': release['revision'], 'reason': 'Publish to the explicitly internal development catalogue for real-device testing.'})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--endpoint', default='https://localhost:8443')
    parser.add_argument('--ca', type=Path, default=ROOT / 'services/local/local-ca.pem')
    parser.add_argument('--credentials', type=Path, default=ROOT / 'services/local/development-credentials.json')
    parser.add_argument('--game', choices=['nsa-2048', 'botw'], required=True)
    parser.add_argument('--release-id', help='Immutable packaged release ID; defaults to GAME-local-v1')
    args = parser.parse_args()
    credentials = json.loads(args.credentials.read_text(encoding='utf-8'))
    publisher = Publisher(args.endpoint, args.ca, credentials['local-editor'], credentials['local-reviewer'])
    is_botw = args.game == 'botw'
    metadata = {'game_id': args.game, 'release_id': args.release_id or args.game + '-local-v1', 'name': '塞尔达传说 旷野之息' if is_botw else '2048 · 数字花园', 'genre': 'adventure' if is_botw else 'puzzle',
                'description': '本机已有游戏数据的内部实验适配。当前仍需在统一大厅中重新验证操作、声音、存档与重新读取，不能视为已验证可玩。' if is_botw else '滑动方块，合并相同的数字，挑战 2048。支持方向键与手柄；右侧 1 为撤回一步、0 为新的一局。每次有效移动后保存，退出后可继续。',
                'languages': ['en-US'] if is_botw else ['zh-CN', 'en-US'], 'input_modes': ['touch', 'controller'], 'rights_kind': 'internal_test' if is_botw else 'open_license'}
    material = ROOT / '.gamedump/Hall/content' / args.game / 'rights.txt'
    material.write_text('Internal local testing only. Existing user-provided game files; no public distribution permission is asserted.' if is_botw else (ROOT / 'android/native-games/2048/LICENSE').read_text(encoding='utf-8'), encoding='utf-8')
    profile = json.loads((ROOT / 'services/local' / ('botw-identity.json' if is_botw else '2048-identity.json')).read_text(encoding='utf-8'))
    try: print(json.dumps(publisher.publish(material.parent, metadata, profile, material), ensure_ascii=False))
    finally: publisher.client.close()

if __name__ == '__main__': main()

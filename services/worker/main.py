from __future__ import annotations
import argparse
import json
import os
import time
import threading
import uuid
from sqlalchemy import select
from catalog.app import Platform, needed, utc
from catalog.config import Settings
from catalog.contracts import strict_json, validate
from catalog.crypto import canonical, digest
from catalog.db import Audit, Conflict, Record, change, create, rows
from catalog.packages import verify_archive

LEASE_SECONDS = 600
RENEW_SECONDS = 30


class Lease:
    def __init__(self, platform, operation_id, owner):
        self.platform, self.operation_id, self.owner = platform, operation_id, owner
        self.stopped, self.lost = threading.Event(), threading.Event()
        self.thread = threading.Thread(target=self.keep_alive, name='nsa-verification-lease', daemon=True)

    def renew(self):
        with self.platform.sessions() as db:
            row = needed(db, 'operation', self.operation_id)
            if row.state != 'running' or row.data().get('lease_owner') != self.owner:
                self.lost.set(); return False
            change(db, row, row.revision, {**row.data(), 'lease_until': time.time() + LEASE_SECONDS})
            db.commit()
        return True

    def keep_alive(self):
        while not self.stopped.wait(RENEW_SECONDS):
            try:
                if not self.renew(): return
            except Exception:
                # Losing ownership or storage connectivity must prevent publication.
                self.lost.set(); return

    def stop(self):
        self.stopped.set()
        self.thread.join(timeout=35)
        if self.thread.is_alive(): self.lost.set()


def verify(platform, db, release):
    data = release.data()
    build = needed(db, 'build', data['build_id']).data()
    artifacts = [needed(db, 'artifact', key) for key in build['artifact_ids']]
    if any(row.state != 'verified' for row in artifacts):
        raise ValueError('Build contains an unverified artifact')
    for row in artifacts:
        # The complete archive digest is checked alongside its chunks below.
        if row.data()['kind'] == 'content_archive': continue
        size, sha = platform.objects.size_hash(row.identity)
        if size != int(row.data()['bytes']) or sha != row.data()['sha256']:
            raise ValueError('Artifact changed after upload verification')
    sboms = [r for r in artifacts if r.data()['kind'] == 'sbom']
    if len(sboms) != 1:
        raise ValueError('Exactly one build SBOM must contain the proposed release manifest')
    sbom = strict_json(platform.objects.read(sboms[0].identity, 2 * 1024 * 1024))
    proposed = [p['value'] for p in sbom.get('properties', []) if p['name'] == 'nsa:release-manifest']
    if len(proposed) != 1:
        raise ValueError('SBOM requires one nsa:release-manifest property')
    manifest = strict_json(proposed[0].encode())
    validate(manifest, 'release-manifest.schema.json')
    for row in artifacts:
        if row.data()['kind'] == 'content_archive' and row.identity != manifest['archive']['object_id']:
            size, sha = platform.objects.size_hash(row.identity)
            if size != int(row.data()['bytes']) or sha != row.data()['sha256']:
                raise ValueError('Additional build artifact changed after upload verification')
    if any(manifest[key] != data[key] for key in ('game_id', 'release_id', 'game_version', 'channel')):
        raise ValueError('Proposed manifest belongs to another release')
    if manifest['runtime']['profile_digest'] != build['profile_digest'] or manifest['runtime']['runtime_slot_id'] != build['runtime_slot_id']:
        raise ValueError('Build profile/slot differs from manifest')
    by_id = {row.identity: row for row in artifacts}
    for key in ('archive', 'file_index', 'chunk_index'):
        reference = manifest[key]
        row = by_id.get(reference['object_id'])
        if not row or any(row.data()[field] != reference[field] for field in ('bytes', 'sha256')):
            raise ValueError('Manifest object differs from registered artifacts')
    file_index = strict_json(platform.objects.read(manifest['file_index']['object_id'], 64 * 1024 * 1024), 64 * 1024 * 1024)
    chunk_index = strict_json(platform.objects.read(manifest['chunk_index']['object_id'], 16 * 1024 * 1024), 16 * 1024 * 1024)
    if file_index['release_id'] != release.identity or chunk_index['object_id'] != manifest['archive']['object_id']:
        raise ValueError('Index identity differs')
    path, temporary = platform.objects.download_path(manifest['archive']['object_id'])
    try:
        result = verify_archive(path, manifest, file_index, chunk_index)
    finally:
        if temporary: path.unlink(missing_ok=True)
    # Signing validates the complete schema and is attempted before committing verified state.
    platform.signer.envelope('release', manifest)
    return result, manifest

def run_once(platform):
    operation_id = None
    owner = uuid.uuid4().hex
    with platform.sessions() as db:
        now = time.time()
        candidates = [r for r in rows(db, 'operation') if r.state == 'pending' or (r.state == 'running' and r.data().get('lease_until', 0) < now)]
        for row in candidates:
            try:
                data = row.data()
                change(db, row, row.revision, {**data, 'lease_owner': owner, 'lease_until': now + LEASE_SECONDS, 'attempts': data.get('attempts', 0) + 1}, 'running')
                operation_id = row.identity
                db.commit()
                break
            except Conflict:
                db.rollback()
    if not operation_id: return False
    lease = Lease(platform, operation_id, owner)
    lease.thread.start()
    failure, result, manifest, release_revision = '', None, None, None
    try:
        # No writes while reading large objects: renewals use a separate connection.
        with platform.sessions() as db:
            operation = needed(db, 'operation', operation_id)
            release = needed(db, 'release', operation.data()['target_id'])
            release_revision = release.revision
            if release.state != 'verifying': raise ValueError('Release no longer awaits verification')
            result, manifest = verify(platform, db, release)
    except Exception as error:
        failure = type(error).__name__
    finally:
        lease.stop()
    if lease.lost.is_set(): return True
    try:
        with platform.sessions() as db:
            operation = needed(db, 'operation', operation_id)
            if operation.state != 'running' or operation.data().get('lease_owner') != owner: return True
            release = needed(db, 'release', operation.data()['target_id'])
            unchanged = release.revision == release_revision and release.state == 'verifying'
            if not unchanged: failure = 'ReleaseChanged'
            if failure:
                if unchanged: change(db, release, release.revision, release.data(), 'verification_failed')
                state, extra = 'failed', {'error': failure}
            else:
                old = db.get(Record, ('manifest', release.identity))
                if old: change(db, old, old.revision, manifest, 'verified')
                else: create(db, 'manifest', release.identity, manifest, state='verified')
                change(db, release, release.revision, release.data(), 'verified')
                state, extra = 'succeeded', {'result': result}
            change(db, operation, operation.revision, {**operation.data(), **extra, 'state': state, 'completed_at': utc()}, state)
            db.commit()
    except Conflict:
        # A newer owner or administrator won the CAS. Never overwrite their result.
        return True
    return True

def maintenance(platform):
    """Delete expired private uploads; preserve immutable release artifacts and audit."""
    now = int(time.time())
    with platform.sessions() as db:
        for row in rows(db, 'diagnostic'):
            expires = row.data().get('retention_until', row.data().get('expires', now))
            if expires < now and row.state != 'expired':
                if platform.objects.s3: platform.objects.s3.delete_object(Bucket=platform.settings.s3_bucket, Key=row.identity)
                else: platform.objects.path(row.identity).unlink(missing_ok=True)
                change(db, row, row.revision, row.data(), 'expired')
        for kind in ('idempotency', 'capability', 'credential', 'refresh', 'catalogue_snapshot'):
            for row in rows(db, kind):
                if row.data().get('expires', now) < now - 86400:
                    db.delete(row)
        db.commit()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--once', action='store_true')
    args = parser.parse_args()
    platform = Platform(Settings.load())
    while True:
        worked = run_once(platform)
        if args.once: break
        if not worked:
            maintenance(platform)
            time.sleep(2)

if __name__ == '__main__': main()

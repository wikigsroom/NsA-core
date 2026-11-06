"""Contract-driven HTTP service. Every normative operation is dispatched explicitly."""
from __future__ import annotations
import asyncio
from collections import defaultdict, deque
from datetime import datetime, timezone
import hashlib
import hmac
import json
import math
import logging
import os
from pathlib import Path
import re
import secrets
import time
from threading import Lock
import uuid
from fastapi import FastAPI, Request
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from jsonschema import ValidationError
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from starlette.concurrency import run_in_threadpool
from .auth import Actor, Auth, CredentialExpired, require
from .capability import meets_minimum
from .config import Settings
from .contracts import OPENAPI, strict_json, validate
from .crypto import Signer, canonical, digest, seal, token_hash, unseal
from .db import Audit, Base, Conflict, Record, change, connect, create, rows
from .storage import Objects
from .policy import initialize_policy_sequence, policy_snapshot

logger = logging.getLogger('nsa.contracts')

def identity(prefix=''):
    return prefix + uuid.uuid4().hex

def utc(timestamp=None):
    return datetime.fromtimestamp(timestamp or time.time(), timezone.utc).isoformat().replace('+00:00', 'Z')

class ApiError(Exception):
    def __init__(self, code, message, status=400):
        self.code, self.message, self.status = code, message, status

def needed(db, kind, key):
    result = db.get(Record, (kind, key))
    if not result:
        raise ApiError('NOT_FOUND', 'The requested record was not found', 404)
    return result

def view(row):
    return {**row.data(), 'revision': row.revision, 'state': row.state}

def artifact(row):
    return {key: row.data()[key] for key in ('object_id', 'bytes', 'sha256')}

class Platform:
    def __init__(self, settings):
        self.settings = settings
        self.engine, self.sessions = connect(settings.database_url)
        if settings.mode != 'production':
            Base.metadata.create_all(self.engine)
            initialize_policy_sequence(self.sessions)
        self.signer, self.objects, self.auth = Signer(settings), Objects(settings), Auth(settings)
        self.limits = defaultdict(deque)
        self.rate_lock = Lock()
        self.catalogue_observations = deque()

    def credentials(self, db, installation_id, scopes):
        access, refresh = secrets.token_urlsafe(48), secrets.token_urlsafe(64)
        expires = int(time.time()) + 3600
        data = {'installation_id': installation_id, 'expires': expires, 'scopes': scopes}
        create(db, 'credential', token_hash(access), data, installation_id, 'active')
        create(db, 'refresh', token_hash(refresh), {**data, 'expires': int(time.time()) + 30 * 86400}, installation_id, 'active')
        return {'installation_id': installation_id, 'access_token': access, 'expires_at': utc(expires), 'refresh_token': refresh}

    def signed_url(self, object_id, owner, method='GET', seconds=900, part=None):
        if self.objects.s3:
            if part:
                return self.objects.presign('upload_part', object_id, UploadId=part['upload_id'], PartNumber=part['number'])
            return self.objects.presign('get_object' if method == 'GET' else 'put_object', object_id)
        grant = seal(self.settings.secret, {'object': object_id, 'owner': owner, 'method': method,
                                          'exp': int(time.time()) + seconds, 'part': part['number'] if part else None})
        return f'{self.settings.public_url}/objects/{object_id}?grant={grant}'

    def audit(self, db, actor, action, target, request_id, data):
        # Store references and reasons, never bearer tokens or private material contents.
        db.add(Audit(identity=identity(), actor=actor.identity, action=action, target=target, request_id=request_id,
                     occurred=int(time.time()), payload=json.dumps({'reason': data.get('reason', ''), 'expected_revision': data.get('expected_revision')})))

    def admin_result(self, db, row, request_id, operation=None):
        operation_id = operation or identity('op-')
        if not operation:
            create(db, 'operation', operation_id, {'target_id': row.identity, 'state': 'succeeded'}, state='succeeded')
        return {'operation_id': operation_id, 'target_id': row.identity, 'revision': row.revision, 'state': row.state, 'request_id': request_id}

    def manifest(self, db, release):
        return needed(db, 'manifest', release.identity).data()

    def visible(self, db, release, actor):
        if release.data()['channel'] == 'internal' and 'internal_content' not in actor.scopes:
            return False
        if release.state != 'published':
            return False
        rights = needed(db, 'rights', release.data()['rights_record_id'])
        data = rights.data()
        now = utc()
        if rights.state != 'approved' or data['valid_from'] > now or data.get('valid_until', '9999') < now:
            return False
        return True

    def compatibility(self, db, release, capability=None, app_version=1):
        manifest = self.manifest(db, release)
        if app_version < manifest['required_app_version_code']:
            status, reason = 'app_update_required', 'app_version'
        elif capability and not meets_minimum(capability, manifest['minimum_capability']):
            status, reason = 'unsupported', 'minimum_capability'
        elif release.state == 'blocked':
            status, reason = 'unsupported', 'release_blocked'
        else:
            evidence = [r for r in rows(db, 'evidence') if r.data()['release_id'] == release.identity]
            matched = [r for r in evidence if not capability or all(r.data()['capability'].get(k) == capability.get(k) for k in ('api_level', 'abi', 'gpu_family', 'gpu_driver_digest', 'page_size_bytes'))]
            verified = next((r for r in matched if r.data()['result'] == 'verified'), None)
            status, reason = ('verified', 'device_evidence') if verified else ('experimental', 'device_not_verified')
            result = {'status': status, 'reason_code': reason, 'evaluated_at': utc(), 'known_limitations': [] if verified else ['Actual gameplay and save/reload are not verified for this device.']}
            if verified:
                result['evidence_id'] = verified.identity
            return result
        return {'status': status, 'reason_code': reason, 'evaluated_at': utc(), 'required_app_version_code': manifest['required_app_version_code'], 'known_limitations': []}

    def summary(self, db, release):
        game = needed(db, 'game', release.data()['game_id']).data()
        manifest = self.manifest(db, release)
        return {'game_id': game['game_id'], 'name': game['name'], 'aliases': game.get('aliases', []), 'genre': game['genre'],
                'release_id': release.identity, 'published_at': release.data().get('published_at', utc(release.updated)),
                'compatibility': self.compatibility(db, release), 'download_bytes': manifest['archive']['bytes']}

    def page(self, db, kind, query, transform=view, predicate=lambda row: True):
        limit = min(max(int(query.get('limit', 24)), 1), 50)
        offset = 0
        if query.get('cursor'):
            cursor = unseal(self.settings.secret, query['cursor'])
            if cursor.get('kind') != kind:
                raise ValueError('Cursor belongs to another collection')
            offset = cursor['offset']
        items = [r for r in rows(db, kind) if predicate(r)]
        next_cursor = seal(self.settings.secret, {'kind': kind, 'offset': offset + limit, 'exp': int(time.time()) + 900}) if len(items) > offset + limit else None
        return {'items': [transform(r) for r in items[offset:offset + limit]], 'next_cursor': next_cursor}

    def dispatch(self, db, op, body, path, query, actor, request_id):
        if op == 'getAppUpdateArtifact':
            updates = [r for r in rows(db, 'app_update') if r.state == 'published']
            if not updates: return None
            info = updates[0].data()['apk']
            row = needed(db, 'artifact', info['object_id'])
            if row.state != 'verified': raise Conflict('Update artifact is not verified')
            return {**info, 'download_url': self.signed_url(row.identity, 'update-' + actor.identity), 'expires_at': utc(time.time() + 900)}
        if op == 'bootstrapInstallation':
            if body['channel'] == 'internal':
                grant = body.get('access_grant', '')
                record = db.get(Record, ('access_grant', token_hash(grant)))
                if not record or record.state != 'active' or record.data()['expires'] < time.time():
                    raise ApiError('CAT_SCOPE_DENIED', 'A valid internal testing invitation is required', 403)
                scopes = ['internal_content']
                change(db, record, record.revision, record.data(), 'consumed')
            else:
                scopes = []
            installation = identity('installation-')
            create(db, 'installation', installation, {'nonce': digest(body['installation_nonce'].encode()), 'channel': body['channel']}, state='active')
            return self.credentials(db, installation, scopes)
        if op == 'refreshInstallationCredential':
            row = db.get(Record, ('refresh', token_hash(body['refresh_token'])))
            if not row or row.state != 'active' or row.data()['expires'] < time.time():
                raise ApiError('AUTH_EXPIRED', 'Refresh credential expired or was rotated', 401)
            data = row.data()
            change(db, row, row.revision, data, 'rotated')
            return self.credentials(db, row.owner, data['scopes'])
        if op == 'listCatalogue':
            q = query.get('q', '').strip().casefold()
            if len(q) > 64:
                raise ValueError('Search is limited to 64 characters')
            def include(r):
                if not self.visible(db, r, actor): return False
                game = needed(db, 'game', r.data()['game_id']).data()
                return (not q or q in ' '.join([game['name'], *game.get('aliases', [])]).casefold()) and (not query.get('genre') or game['genre'] == query['genre'])
            binding = digest(canonical({'owner': actor.identity, 'q': q, 'genre': query.get('genre', '')}))
            offset = 0
            if query.get('cursor'):
                try:
                    cursor = unseal(self.settings.secret, query['cursor'])
                    snapshot = needed(db, 'catalogue_snapshot', cursor['snapshot']).data()
                    if cursor['binding'] != binding or snapshot['binding'] != binding or snapshot['expires'] < time.time():
                        raise ValueError('Expired or mismatched snapshot')
                    offset = cursor['offset']
                except (ValueError, KeyError):
                    raise ApiError('CURSOR_EXPIRED', 'Start a new catalogue search', 409)
            else:
                selected, seen = [], set()
                for release in rows(db, 'release'):
                    if not include(release): continue
                    game_id = release.data()['game_id']
                    if game_id not in seen:
                        selected.append(self.summary(db, release)); seen.add(game_id)
                snapshot = {'snapshot_id': identity('snapshot-'), 'binding': binding, 'expires': int(time.time()) + 900,
                            'updated_at': utc(), 'items': selected}
                create(db, 'catalogue_snapshot', snapshot['snapshot_id'], snapshot, actor.identity, 'active')
            limit = min(max(int(query.get('limit', 24)), 1), 50)
            page = snapshot['items'][offset:offset + limit]
            # A cached snapshot must never bypass a later block or withdrawal.
            page = [item for item in page if self.visible(db, needed(db, 'release', item['release_id']), actor)]
            next_cursor = seal(self.settings.secret, {'snapshot': snapshot['snapshot_id'], 'binding': binding, 'offset': offset + limit,
                                                     'exp': snapshot['expires']}) if len(snapshot['items']) > offset + limit else None
            return {'items': page, 'next_cursor': next_cursor, 'snapshot_id': snapshot['snapshot_id'], 'updated_at': snapshot['updated_at']}
        if op == 'getGameDetail':
            releases = [r for r in rows(db, 'release') if r.data()['game_id'] == path['game_id'] and self.visible(db, r, actor)]
            if not releases: raise ApiError('NOT_FOUND', 'Game is not available in this catalogue', 404)
            release, game = releases[0], needed(db, 'game', path['game_id']).data()
            manifest = self.manifest(db, release)
            return {'game': self.summary(db, release), 'game_version': release.data()['game_version'], 'manifest_sha256': digest(canonical(manifest)),
                    'languages': game['languages'], 'input_modes': game['input_modes'], 'description': game['description'],
                    'download_bytes': manifest['archive']['bytes'], 'unpacked_bytes': manifest['unpacked_bytes'], 'working_bytes': manifest['working_bytes'],
                    'runtime_slot_id': manifest['runtime']['runtime_slot_id'], 'offline_permitted': manifest['offline_permitted'], 'release_state': release.state}
        if op == 'evaluateCompatibility':
            context = identity('cap-')
            data = {**body, 'expires': int(time.time()) + 3600}
            create(db, 'capability', context, data, actor.identity, 'active')
            evaluations = []
            for key in body['release_ids']:
                release = needed(db, 'release', key)
                if not self.visible(db, release, actor): continue
                evaluations.append({'release_id': key, 'compatibility': self.compatibility(db, release, body['capability'], body['app_version_code'])})
            return {'evaluations': evaluations, 'capability_context_id': context, 'expires_at': utc(data['expires'])}
        if op == 'getReleaseManifest':
            release = needed(db, 'release', path['release_id'])
            if release.data()['channel'] == 'internal' and 'internal_content' not in actor.scopes: raise PermissionError('Internal release')
            if release.state == 'blocked': raise ApiError('RELEASE_BLOCKED', 'Release is blocked', 403)
            return self.signer.envelope('release', self.manifest(db, release))
        if op == 'createDownloadSession':
            release = needed(db, 'release', body['release_id'])
            if not self.visible(db, release, actor): raise ApiError('RELEASE_BLOCKED', 'Release cannot be downloaded', 403)
            manifest = self.manifest(db, release)
            if body['manifest_sha256'] != digest(canonical(manifest)): raise Conflict('Manifest identity differs')
            context = needed(db, 'capability', body['capability_context_id'])
            capability = context.data()
            if context.owner != actor.identity or capability['expires'] < time.time() or body['runtime_registry_sha256'] != capability['runtime_registry_sha256'] or body['app_version_code'] != capability['app_version_code'] or release.identity not in capability['release_ids']:
                raise PermissionError('Capability context is expired or belongs to another request')
            evaluation = self.compatibility(db, release, capability['capability'], body['app_version_code'])
            if evaluation['status'] != 'verified' and not (evaluation['status'] == 'experimental' and 'internal_content' in actor.scopes and body.get('experimental_acknowledged')):
                raise ApiError('DEVICE_UNVERIFIED', 'Compatibility requires verified evidence or explicit internal experimental consent', 422)
            obj = next((manifest[k] for k in ('archive', 'file_index', 'chunk_index') if manifest[k]['object_id'] == body['object_id']), None)
            if not obj: raise PermissionError('Object does not belong to this manifest')
            session_id = identity('download-')
            data = {'release_id': release.identity, 'manifest_sha256': body['manifest_sha256'], **obj, 'expires': int(time.time()) + 7 * 86400}
            data['etag'] = needed(db, 'artifact', obj['object_id']).data().get('etag', '"' + obj['sha256'] + '"')
            create(db, 'download', session_id, data, actor.identity, 'active')
            return self.download_grant(session_id, data, actor.identity)
        if op == 'renewDownloadSession':
            row = needed(db, 'download', path['download_session_id'])
            data = row.data()
            release = needed(db, 'release', data['release_id'])
            if row.owner != actor.identity or data['expires'] < time.time() or any(body[k] != data[k] for k in ('object_id', 'manifest_sha256')): raise PermissionError('Download session identity differs or expired')
            if not self.visible(db, release, actor): raise ApiError('RELEASE_BLOCKED', 'Release was unlisted or blocked', 403)
            return self.download_grant(row.identity, data, actor.identity)
        if op == 'getReleasePolicies':
            return policy_snapshot(db, self.signer, 'internal_content' in actor.scopes)
        if op == 'getAppUpdate':
            candidates = [r for r in rows(db, 'app_update') if r.state == 'published']
            if not candidates: return None
            return self.signer.envelope('app_update', candidates[0].data())
        if op == 'createDiagnosticUpload':
            if int(body['bytes']) > 10 * 1024 * 1024: raise ValueError('Diagnostic limit is 10 MiB')
            upload_id = identity('diagnostic-')
            create(db, 'diagnostic', upload_id, {**body, 'object_id': upload_id, 'expires': int(time.time()) + 900}, actor.identity, 'pending')
            return {'upload_id': upload_id, 'upload_url': self.signed_url(upload_id, actor.identity, 'PUT'), 'expires_at': utc(time.time() + 900), 'maximum_bytes': '10485760'}
        if op == 'completeDiagnosticUpload':
            row = needed(db, 'diagnostic', path['upload_id'])
            if row.owner != actor.identity: raise PermissionError('Diagnostic owner differs')
            size, sha = self.objects.size_hash(row.identity)
            if sha != body['sha256'] or sha != row.data()['sha256'] or size != int(row.data()['bytes']): raise ValueError('Diagnostic read-back verification failed')
            change(db, row, row.revision, {**row.data(), 'retention_until': int(time.time()) + 30 * 86400}, 'verified')
            return {'upload_id': row.identity, 'verified': True, 'retention_until': utc(row.data()['retention_until'])}
        if op == 'submitFeedback':
            if body.get('diagnostic_upload_id'):
                diagnostic = needed(db, 'diagnostic', body['diagnostic_upload_id'])
                if diagnostic.owner != actor.identity or diagnostic.state != 'verified': raise PermissionError('Diagnostic is not a verified upload owned by this installation')
            ticket = identity('feedback-')
            received = utc()
            create(db, 'feedback', ticket, {**body, 'ticket_id': ticket, 'received_at': received, 'status': 'open'}, actor.identity, 'open')
            return {'ticket_id': ticket, 'received_at': received, 'diagnostic_attached': bool(body.get('diagnostic_upload_id'))}
        return self.admin(db, op, body, path, query, actor, request_id)

    def download_grant(self, key, data, owner):
        return {'download_session_id': key, **{k: data[k] for k in ('release_id', 'manifest_sha256', 'object_id', 'bytes', 'sha256')},
                'etag': data.get('etag', '"' + data['sha256'] + '"'), 'url': self.signed_url(data['object_id'], owner), 'expires_at': utc(time.time() + 900)}

    def admin(self, db, op, body, path, query, actor, request_id):
        if op in ('getSubjectRoles', 'changeSubjectRoles'):
            require(actor, 'roles')
            subject = path['subject_id']
            row = db.get(Record, ('roles', subject))
            if op == 'getSubjectRoles': return {'subject_id': subject, 'revision': row.revision if row else 0, 'roles': row.data()['roles'] if row else ['viewer']}
            if subject == actor.identity: raise PermissionError('An administrator cannot change their own roles')
            from .auth import ROLE_ACTIONS
            if any(role not in ROLE_ACTIONS for role in body['roles']): raise ValueError('Unknown role')
            if row: row = change(db, row, body['expected_revision'], {'roles': body['roles']}, 'active')
            else:
                if body['expected_revision'] != 0: raise Conflict('Role record has not been created')
                row = create(db, 'roles', subject, {'roles': body['roles']}, actor.identity, 'active')
            self.audit(db, actor, op, subject, request_id, body)
            return self.admin_result(db, row, request_id)
        if op == 'publishAppUpdate':
            require(actor, 'publish')
            metadata = body['metadata']; validate(metadata, 'app-update.schema.json')
            apk = needed(db, 'artifact', metadata['apk']['object_id'])
            if apk.state != 'verified' or apk.data()['kind'] != 'app_apk' or any(apk.data()[key] != metadata['apk'][key] for key in ('bytes', 'sha256')): raise Conflict('App update APK is not verified')
            self.signer.envelope('app_update', metadata)
            row = create(db, 'app_update', metadata['application_id'] + '-' + str(metadata['version_code']), metadata, actor.identity, 'published')
            self.audit(db, actor, op, row.identity, request_id, body)
            return self.admin_result(db, row, request_id)
        reads = {'listAdminGames': 'game', 'listAdminReleases': 'release', 'listFeedbackTickets': 'feedback'}
        singles = {'getAdminGame': ('game', 'game_id'), 'getAdminRelease': ('release', 'release_id'), 'getRightsRecord': ('rights', 'rights_record_id'), 'getBuildRecord': ('build', 'build_id'), 'getAdminOperation': ('operation', 'operation_id')}
        if op in reads:
            require(actor, 'read')
            return self.page(db, reads[op], query)
        if op in singles:
            require(actor, 'read')
            kind, key = singles[op]
            return view(needed(db, kind, path[key]))
        if op == 'readHealthSummary':
            require(actor, 'read')
            while self.catalogue_observations and time.monotonic() - self.catalogue_observations[0][0] > 300:
                self.catalogue_observations.popleft()
            failures = sum(status >= 500 for _, status in self.catalogue_observations)
            rate = failures / len(self.catalogue_observations) if self.catalogue_observations else 0.0
            return {'observed_at': utc(), 'window_seconds': 300, 'catalogue_error_rate': rate, 'failed_jobs': len([r for r in rows(db, 'operation') if r.state == 'failed']),
                    'signer_available': self.settings.signing_key.is_file(), 'object_store_available': self.objects.available()}
        if op == 'listAuditLog':
            require(actor, 'read')
            limit = min(int(query.get('limit', 24)), 50)
            offset = int(unseal(self.settings.secret, query['cursor'])['offset']) if query.get('cursor') else 0
            entries = list(db.scalars(select(Audit).order_by(Audit.occurred.desc(), Audit.identity).offset(offset).limit(limit + 1)))
            return {'items': [{'audit_id': r.identity, 'actor_id': r.actor, 'action': r.action, 'target_id': r.target, 'request_id': r.request_id, 'occurred_at': utc(r.occurred), **json.loads(r.payload)} for r in entries[:limit]],
                    'next_cursor': seal(self.settings.secret, {'offset': offset + limit, 'exp': int(time.time()) + 900}) if len(entries) > limit else None}
        if op == 'getArtifactAccess':
            require(actor, 'read')
            row = needed(db, 'artifact', path['object_id'])
            if row.state != 'verified': raise Conflict('Artifact is not verified')
            return {**artifact(row), 'download_url': self.signed_url(row.identity, actor.identity), 'expires_at': utc(time.time() + 900)}
        creates = {'createGame': ('game', 'game_id', 'game'), 'createRightsRecord': ('rights', 'rights_record_id', 'rights'),
                   'registerBuild': ('build', 'build_id', 'build'), 'createRelease': ('release', 'release_id', 'release'),
                   'createCompatibilityEvidence': ('evidence', 'evidence_id', 'evidence')}
        if op in creates:
            kind, key, permission = creates[op]
            require(actor, permission)
            if kind in ('rights', 'release'): needed(db, 'game', body['game_id'])
            if kind == 'build':
                for obj in body['artifact_ids']:
                    if needed(db, 'artifact', obj).state != 'verified': raise Conflict('Build references an unverified artifact')
            if kind == 'release':
                rights, build = needed(db, 'rights', body['rights_record_id']), needed(db, 'build', body['build_id'])
                if rights.data()['game_id'] != body['game_id']: raise ValueError('Rights belong to another game')
                if rights.data()['kind'] == 'internal_test' and body['channel'] != 'internal': raise PermissionError('Internal rights cannot publish to the official channel')
                body = {**body, 'evidence_ids': []}
            if kind == 'evidence':
                release = needed(db, 'release', body['release_id'])
                for obj in body['evidence_object_ids']:
                    if needed(db, 'artifact', obj).state != 'verified': raise Conflict('Evidence artifact is not verified')
                if body['result'] == 'verified':
                    threshold = (27, 50, 20) if body['target_fps'] == 30 else (54, 25, 40)
                    if body['average_fps'] < threshold[0] or body['frame_p95_ms'] > threshold[1] or body['low1_fps'] < threshold[2] or not .95 <= body['game_speed_ratio'] <= 1.05:
                        raise ValueError('Verified evidence does not meet the performance gate')
                change(db, release, release.revision, {**release.data(), 'evidence_ids': [*release.data().get('evidence_ids', []), body[key]]})
            row = create(db, kind, body[key], body, actor.identity, 'recorded' if kind in ('evidence', 'build') else 'draft')
        elif op == 'updateGame':
            require(actor, 'game')
            row = needed(db, 'game', path['game_id'])
            row = change(db, row, body['expected_revision'], {**row.data(), **body['changes']})
        elif op == 'triageFeedbackTicket':
            require(actor, 'feedback')
            row = needed(db, 'feedback', path['ticket_id'])
            row = change(db, row, body['expected_revision'], {**row.data(), 'status': body['status']}, body['status'])
        elif op == 'createArtifactUpload':
            require(actor, 'upload')
            key = identity('object-')
            mode = 'multipart' if int(body['bytes']) >= 64 * 1024 * 1024 else 'single'
            data = {**body, 'object_id': key, 'mode': mode, 'expires': int(time.time()) + 86400}
            if mode == 'multipart':
                data.update(part_bytes=64 * 1024 * 1024, part_count=math.ceil(int(body['bytes']) / (64 * 1024 * 1024)))
                if data['part_count'] > 10000: raise ValueError('Multipart upload exceeds 10000 parts')
                if self.objects.s3: data['upload_id'] = self.objects.s3.create_multipart_upload(Bucket=self.settings.s3_bucket, Key=key)['UploadId']
            row = create(db, 'artifact', key, data, actor.identity, 'uploading')
            self.audit(db, actor, op, key, request_id, body)
            grant = {'object_id': key, 'revision': 1, 'mode': mode, 'upload_session_expires_at': utc(data['expires'])}
            return {**grant, **({'part_bytes': str(data['part_bytes']), 'part_count': data['part_count']} if mode == 'multipart' else {'upload_url': self.signed_url(key, actor.identity, 'PUT'), 'expires_at': utc(time.time() + 900)})}
        elif op == 'authorizeUploadParts':
            require(actor, 'upload')
            row = needed(db, 'artifact', path['object_id']); data = row.data()
            if row.owner != actor.identity or row.state != 'uploading' or row.revision != body['expected_revision'] or data['expires'] < time.time(): raise Conflict('Upload is not active at the expected revision')
            if data['mode'] != 'multipart' or len(set(body['part_numbers'])) != len(body['part_numbers']) or any(n < 1 or n > data['part_count'] for n in body['part_numbers']): raise ValueError('Invalid part numbers')
            return {'object_id': row.identity, 'parts': [{'part_number': n, 'upload_url': self.signed_url(row.identity, actor.identity, 'PUT', part={'number': n, 'upload_id': data.get('upload_id')}), 'expires_at': utc(time.time() + 900)} for n in body['part_numbers']]}
        elif op in ('completeArtifactUpload', 'abortArtifactUpload'):
            require(actor, 'upload')
            row = needed(db, 'artifact', path['object_id']); data = row.data()
            if row.owner != actor.identity or row.state != 'uploading' or data['expires'] < time.time(): raise Conflict('Upload is not active')
            if row.revision != body['expected_revision']: raise Conflict('Upload revision differs')
            if op == 'abortArtifactUpload':
                if self.objects.s3 and data.get('upload_id'): self.objects.s3.abort_multipart_upload(Bucket=self.settings.s3_bucket, Key=row.identity, UploadId=data['upload_id'])
                for part in self.objects.root.glob(row.identity + '.part-*'): part.unlink(missing_ok=True)
                self.objects.temporary(row.identity).unlink(missing_ok=True)
                row = change(db, row, row.revision, data, 'aborted')
            else:
                if data['mode'] == 'multipart':
                    parts = body.get('parts', [])
                    if [p['part_number'] for p in parts] != list(range(1, data['part_count'] + 1)): raise ValueError('Completion must contain every part in order')
                    if self.objects.s3:
                        self.objects.s3.complete_multipart_upload(Bucket=self.settings.s3_bucket, Key=row.identity, UploadId=data['upload_id'], MultipartUpload={'Parts': [{'PartNumber': p['part_number'], 'ETag': p['etag']} for p in parts]})
                    else:
                        with self.objects.temporary(row.identity).open('wb') as out:
                            for part in parts:
                                source = self.objects.path(row.identity + '.part-' + str(part['part_number']))
                                with source.open('rb') as incoming:
                                    part_sha = hashlib.sha256()
                                    while chunk := incoming.read(1024 * 1024):
                                        part_sha.update(chunk); out.write(chunk)
                                    if part['etag'].strip('"') != part_sha.hexdigest(): raise ValueError('Part ETag differs')
                            out.flush(); os.fsync(out.fileno())
                        self.objects.temporary(row.identity).replace(self.objects.path(row.identity))
                size, sha = self.objects.size_hash(row.identity)
                if size != int(data['bytes']) or sha != data['sha256']: raise ValueError('Object read-back hash or size differs')
                data['etag'] = self.objects.s3.head_object(Bucket=self.settings.s3_bucket, Key=row.identity)['ETag'] if self.objects.s3 else '"' + sha + '"'
                row = change(db, row, row.revision, data, 'verified')
                for part in self.objects.root.glob(row.identity + '.part-*'): part.unlink(missing_ok=True)
        elif op in ('reviewRightsRecord', 'reviewRelease'):
            kind, key = ('rights', 'rights_record_id') if op == 'reviewRightsRecord' else ('release', 'release_id')
            require(actor, 'rights_review' if kind == 'rights' else 'review')
            row = needed(db, kind, path[key])
            if row.owner == actor.identity: raise PermissionError('A different reviewer must approve this record')
            if kind == 'release' and row.state not in ('verified', 'rejected'): raise Conflict('Release must pass artifact verification before review')
            if kind == 'rights':
                for obj in row.data()['material_object_ids']:
                    if needed(db, 'artifact', obj).state != 'verified': raise Conflict('Rights document is not verified')
            row = change(db, row, body['expected_revision'], {**row.data(), 'reviewer_id': actor.identity, 'review_reason': body['reason']}, 'approved' if body['verdict'] == 'approve' else 'rejected')
        elif op == 'verifyReleaseArtifacts':
            require(actor, 'release')
            row = needed(db, 'release', path['release_id'])
            if row.state not in ('draft', 'verification_failed', 'verified', 'rejected'): raise Conflict('Release cannot enter verification from this state')
            operation = identity('op-')
            row = change(db, row, body['expected_revision'], {**row.data(), 'verification_operation_id': operation}, 'verifying')
            create(db, 'operation', operation, {'target_id': row.identity, 'state': 'pending', 'lease_until': 0, 'attempts': 0}, actor.identity, 'pending')
            self.audit(db, actor, op, row.identity, request_id, body)
            return self.admin_result(db, row, request_id, operation)
        elif op in ('publishRelease', 'unlistRelease', 'blockRelease', 'unblockRelease'):
            action = {'publishRelease': 'publish', 'unlistRelease': 'unlist', 'blockRelease': 'block', 'unblockRelease': 'unblock'}[op]
            require(actor, action)
            row = needed(db, 'release', path['release_id']); data = row.data()
            rights = needed(db, 'rights', data['rights_record_id'])
            state = {'publish': 'published', 'unlist': 'unlisted', 'block': 'blocked', 'unblock': 'published'}[action]
            if action == 'publish' and row.state != 'approved': raise Conflict('Release needs independent approval before publication')
            if action == 'unblock' and row.state != 'blocked': raise Conflict('Release is not blocked')
            if action in ('publish', 'unblock'):
                if rights.state != 'approved' or rights.data().get('valid_until', '9999') < utc() or rights.data()['valid_from'] > utc(): raise Conflict('Rights are not valid')
                if not self.manifest(db, row): raise Conflict('Verified manifest is missing')
                evidence = [needed(db, 'evidence', e).data() for e in data.get('evidence_ids', [])]
                if data['channel'] == 'official' and not any(e['result'] == 'verified' for e in evidence): raise Conflict('Official publication requires verified device evidence')
            row = change(db, row, body['expected_revision'], {**data, 'published_at': data.get('published_at', utc()), 'policy_reason': body['reason']}, state)
        else:
            raise ApiError('NOT_FOUND', 'Unknown operation', 404)
        self.audit(db, actor, op, row.identity, request_id, body)
        return self.admin_result(db, row, request_id)

def create_app(settings=None):
    platform = Platform(settings or Settings.load())
    app = FastAPI(title='NsA catalogue', docs_url=None, redoc_url=None, openapi_url=None)
    app.state.platform = platform

    @app.middleware('http')
    async def boundary(request, call_next):
        request.state.request_id = identity('request-')
        try:
            response = await call_next(request)
        except Exception:
            # Exception details can contain storage paths or credentials; do not return them.
            response = JSONResponse({'request_id': request.state.request_id, 'code': 'INTERNAL_ERROR', 'message': 'The operation failed. Retry using the request ID.'}, status_code=500)
        response.headers.update({'X-Request-ID': request.state.request_id, 'X-Content-Type-Options': 'nosniff',
                                 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY'})
        if request.url.path == '/v1/catalogue':
            observed = time.monotonic()
            while platform.catalogue_observations and observed - platform.catalogue_observations[0][0] > 300:
                platform.catalogue_observations.popleft()
            platform.catalogue_observations.append((observed, response.status_code))
        return response

    def endpoint(operation, path):
        async def handle(request: Request):
            request_id = request.state.request_id
            try:
                maximum = 4 * 1024 * 1024 if operation['operationId'] == 'completeArtifactUpload' else 256 * 1024
                raw = bytearray()
                async for part in request.stream():
                    raw.extend(part)
                    if len(raw) > maximum: raise ApiError('REQUEST_INVALID', 'Request body exceeds its limit', 422)
                body = strict_json(bytes(raw), maximum) if raw else {}
                reference = operation.get('requestBody', {}).get('content', {}).get('application/json', {}).get('schema', {}).get('$ref')
                if reference: validate(body, reference)
                admin = path.startswith('/admin/')
                public = operation.get('security') == []
                def execute():
                    # Object read-back, OIDC and database work must not block the ASGI loop.
                    with platform.sessions() as db:
                        actor = Actor('bootstrap', set(), set(), True) if public else platform.auth.actor(db, request.headers.get('Authorization', ''), admin)
                        now = time.monotonic()
                        rate_key = (actor.identity if not public else (request.client.host if request.client else 'local'), operation['operationId'])
                        with platform.rate_lock:
                            window = platform.limits[rate_key]
                            while window and now - window[0] > 60: window.popleft()
                            if len(window) >= (20 if public else 120): raise ApiError('RATE_LIMITED', 'Too many requests', 429)
                            window.append(now)
                        mutation = request.method not in ('GET', 'HEAD')
                        idempotency = request.headers.get('Idempotency-Key', '')
                        key = digest(canonical([actor.identity, request.url.path, idempotency]))
                        request_digest = digest(bytes(raw))
                        if mutation:
                            if not re.fullmatch(r'[A-Za-z0-9._-]{8,128}', idempotency): raise ValueError('A valid Idempotency-Key is required')
                            previous = db.get(Record, ('idempotency', key))
                            if previous and previous.data()['expires'] >= time.time():
                                saved = previous.data()
                                if saved['digest'] != request_digest: raise Conflict('Idempotency key was reused for different input')
                                return JSONResponse(saved['response'], status_code=saved['status'])
                        result = platform.dispatch(db, operation['operationId'], body, request.path_params, dict(request.query_params), actor, request_id)
                        statuses = [int(code) for code in operation['responses'] if code.isdigit() and int(code) < 300]
                        status = min(statuses or [200])
                        if result is None: status = 204
                        receipt_seconds = 86400 if not public or operation['operationId'] == 'refreshInstallationCredential' else 600
                        if mutation:
                            if previous:
                                change(db, previous, previous.revision, {'digest': request_digest, 'response': result, 'status': status, 'expires': int(time.time()) + receipt_seconds})
                            else:
                                create(db, 'idempotency', key, {'digest': request_digest, 'response': result, 'status': status, 'expires': int(time.time()) + receipt_seconds})
                        db.commit()
                        return Response(status_code=204) if status == 204 else JSONResponse(result, status_code=status)
                return await run_in_threadpool(execute)
            except CredentialExpired:
                return JSONResponse({'request_id': request_id, 'code': 'AUTH_EXPIRED', 'message': 'Installation credential expired'}, status_code=401)
            except (PermissionError, jwt_error_types()) as error:
                return JSONResponse({'request_id': request_id, 'code': 'PERMISSION_DENIED', 'message': str(error) if isinstance(error, PermissionError) else 'Invalid identity credential'}, status_code=403)
            except (Conflict, IntegrityError) as error:
                return JSONResponse({'request_id': request_id, 'code': 'REVISION_CONFLICT', 'message': str(error) if isinstance(error, Conflict) else 'Concurrent request conflict'}, status_code=409)
            except ValidationError as error:
                # Only contract locations are logged, never request values or credentials.
                logger.warning('Contract rejected request=%s route=%s validator=%s received_type=%s path=%s schema=%s', request_id, path,
                               error.validator, type(error.instance).__name__, json.dumps(list(error.absolute_path)), json.dumps(list(error.absolute_schema_path)))
                return JSONResponse({'request_id': request_id, 'code': 'REQUEST_INVALID', 'message': 'Request validation failed'}, status_code=422)
            except (ValueError, KeyError) as error:
                return JSONResponse({'request_id': request_id, 'code': 'REQUEST_INVALID', 'message': 'Request validation failed'}, status_code=422)
            except ApiError as error:
                return JSONResponse({'request_id': request_id, 'code': error.code, 'message': error.message}, status_code=error.status)
        return handle

    for path, methods in OPENAPI['paths'].items():
        for method, operation in methods.items():
            if method.lower() in ('get', 'post', 'patch', 'delete', 'put'):
                app.add_api_route(path, endpoint(operation, path), methods=[method.upper()], name=operation['operationId'])

    @app.api_route('/objects/{object_id}', methods=['GET', 'PUT'])
    async def local_object(object_id: str, request: Request):
        if platform.settings.mode == 'production': return Response(status_code=404)
        try:
            grant = unseal(platform.settings.secret, request.query_params.get('grant', ''))
            if grant['object'] != object_id or grant['method'] != request.method: raise ValueError('Grant identity differs')
            with platform.sessions() as db:
                row = db.get(Record, ('artifact', object_id)) or db.get(Record, ('diagnostic', object_id))
                if not row: raise ValueError('Unknown object')
                data = row.data()
                part_number = grant.get('part')
                if request.method == 'PUT':
                    if row.owner != grant['owner'] or row.state not in ('uploading', 'pending') or data['expires'] < time.time(): raise PermissionError('Upload expired')
                    maximum = int(data['bytes'])
                    if part_number:
                        if data.get('mode') != 'multipart' or not 1 <= part_number <= data['part_count']: raise ValueError('Invalid upload part')
                        maximum = min(data['part_bytes'], maximum - (part_number - 1) * data['part_bytes'])
                    target = platform.objects.path(object_id + ('.part-' + str(part_number) if part_number else ''))
                    temporary = platform.objects.path(identity('incoming-'))
                    count, sha = 0, hashlib.sha256()
                    try:
                        with temporary.open('xb') as stream:
                            async for chunk in request.stream():
                                count += len(chunk)
                                if count > maximum: raise ValueError('Upload exceeds declared size')
                                sha.update(chunk)
                                await run_in_threadpool(stream.write, chunk)
                            await run_in_threadpool(stream.flush)
                            await run_in_threadpool(os.fsync, stream.fileno())
                        if count != maximum: raise ValueError('Upload is incomplete')
                        temporary.replace(target)
                    finally: temporary.unlink(missing_ok=True)
                    return Response(status_code=200, headers={'ETag': '"' + sha.hexdigest() + '"'})
                if row.state != 'verified': raise PermissionError('Object has not passed verification')
                if row.kind == 'artifact' and grant['owner'].startswith('installation-'):
                    # Re-check active publication to revoke local signed URLs immediately.
                    references = [r for r in rows(db, 'download') if r.owner == grant['owner'] and r.data()['object_id'] == object_id and r.data()['expires'] > time.time()]
                    if not any(needed(db, 'release', r.data()['release_id']).state == 'published' for r in references): raise PermissionError('Download authorization was revoked')
                path = platform.objects.path(object_id); size = path.stat().st_size
                start, end, status = 0, size - 1, 200
                etag = '"' + data['sha256'] + '"'
                if request.headers.get('range'):
                    match = re.fullmatch(r'bytes=(\d+)-(\d*)', request.headers['range'])
                    if not match or request.headers.get('if-range', etag) != etag: return Response(status_code=412)
                    start, end = int(match[1]), min(int(match[2]) if match[2] else end, end)
                    if start > end or start >= size: return Response(status_code=416, headers={'Content-Range': f'bytes */{size}'})
                    status = 206
                def stream_file():
                    with path.open('rb') as source:
                        source.seek(start); remaining = end - start + 1
                        while remaining:
                            chunk = source.read(min(1024 * 1024, remaining))
                            if not chunk: break
                            remaining -= len(chunk); yield chunk
                headers = {'ETag': etag, 'Accept-Ranges': 'bytes', 'Content-Length': str(end - start + 1)}
                if status == 206: headers['Content-Range'] = f'bytes {start}-{end}/{size}'
                return StreamingResponse(stream_file(), status_code=status, media_type='application/octet-stream', headers=headers)
        except (ValueError, PermissionError, KeyError): return Response(status_code=403)

    @app.get('/healthz')
    def health():
        with platform.engine.connect() as connection:
            from sqlalchemy import text
            connection.execute(text('SELECT 1'))
        return {'status': 'ok', 'mode': platform.settings.mode}

    @app.get('/operations-config')
    def operations_config():
        # Public OIDC client metadata only. No service credentials are sent to the browser.
        return {'client_id': os.environ.get('NSA_OIDC_CLIENT_ID', ''),
                'authorization_endpoint': os.environ.get('NSA_OIDC_AUTHORIZATION_ENDPOINT', ''),
                'token_endpoint': os.environ.get('NSA_OIDC_TOKEN_ENDPOINT', '')}

    @app.options('/objects/{object_id}')
    def object_options(object_id: str):
        return Response(headers={'Access-Control-Allow-Origin': platform.settings.public_url, 'Access-Control-Allow-Methods': 'PUT,GET', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Max-Age': '600'})

    admin_assets = Path(__file__).resolve().parents[1] / 'admin'
    app.mount('/operations', StaticFiles(directory=admin_assets, html=True), name='operations')
    return app

def jwt_error_types():
    import jwt
    return jwt.PyJWTError

"""Stable scoped policy snapshots, serialized by a durable global sequence row."""
from __future__ import annotations

from datetime import datetime, timezone
import time
from sqlalchemy import update
from sqlalchemy.exc import IntegrityError
from .crypto import canonical, digest
from .db import Record, change, create, rows


def initialize_policy_sequence(sessions):
    """Explicit data bootstrap; no game, credential or existing policy is replaced."""
    try:
        with sessions.begin() as db:
            if db.get(Record, ('policy_sequence', 'global')) is None:
                create(db, 'policy_sequence', 'global', {'epoch': time.time_ns() // 1_000_000})
    except IntegrityError:
        # Two development API processes may bootstrap an empty database together.
        with sessions() as db:
            if db.get(Record, ('policy_sequence', 'global')) is None:
                raise


def policy_snapshot(db, signer, internal: bool):
    # A write fence works for SQLite development and PostgreSQL, including two
    # API processes. A Python mutex would not serialize independent workers.
    fenced = db.execute(update(Record).where(Record.kind == 'policy_sequence', Record.identity == 'global')
                        .values(revision=Record.revision), execution_options={'synchronize_session': False})
    if fenced.rowcount != 1:
        raise RuntimeError('Policy sequence is missing; run services/migrate.py before starting the API')
    db.expire_all()
    sequence = db.get(Record, ('policy_sequence', 'global'))
    scope = 'internal' if internal else 'official'
    releases = sorted(({'release_id': r.identity, 'state': r.state,
                        'reason_code': r.data().get('policy_reason', 'publication_state')}
                       for r in rows(db, 'release') if r.state in ('published', 'blocked', 'unlisted')
                       and (r.data()['channel'] != 'internal' or internal)), key=lambda item: item['release_id'])
    semantic = {'visibility_scope': scope, 'releases': releases, 'revoked_key_ids': []}
    fingerprint = digest(canonical(semantic))
    previous = db.get(Record, ('policy_snapshot', scope))
    if previous is not None and previous.data()['semantic_sha256'] == fingerprint:
        return previous.data()['envelope']
    epoch = max(sequence.data()['epoch'] + 1, time.time_ns() // 1_000_000)
    change(db, sequence, sequence.revision, {'epoch': epoch})
    payload = {'policy_schema': 1, 'epoch': epoch,
               'issued_at': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'), **semantic}
    saved = {'semantic_sha256': fingerprint, 'envelope': signer.envelope('policy', payload)}
    if previous is None:
        create(db, 'policy_snapshot', scope, saved)
    else:
        change(db, previous, previous.revision, saved)
    return saved['envelope']

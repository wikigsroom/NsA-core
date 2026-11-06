from __future__ import annotations
from dataclasses import dataclass
import secrets
import time
import jwt
from .crypto import token_hash
from .db import Record, rows

class CredentialExpired(PermissionError):
    """An expired installation identity, distinct from a missing content scope."""

@dataclass
class Actor:
    identity: str
    roles: set[str]
    scopes: set[str]
    installation: bool = False

ROLE_ACTIONS = {
    'viewer': {'read'}, 'editor': {'read', 'game', 'build', 'upload', 'release', 'evidence'},
    'rights_reviewer': {'read', 'rights', 'rights_review'},
    'reviewer': {'read', 'review'}, 'publisher': {'read', 'publish', 'unlist', 'block', 'unblock'},
    'support': {'read', 'feedback'}, 'administrator': {'read', 'roles'},
}

class Auth:
    def __init__(self, settings):
        self.settings = settings
        self.jwks = jwt.PyJWKClient(settings.oidc_jwks_url, cache_keys=True, lifespan=300) if settings.oidc_jwks_url else None

    def actor(self, db, authorization: str, admin: bool):
        if not authorization.startswith('Bearer '):
            raise PermissionError('A bearer credential is required')
        token = authorization[7:]
        if not admin:
            record = db.get(Record, ('credential', token_hash(token)))
            if not record or record.state != 'active' or record.data()['expires'] <= time.time():
                raise CredentialExpired('Installation credential expired')
            data = record.data()
            return Actor(data['installation_id'], set(), set(data.get('scopes', [])), True)
        if self.settings.mode != 'production':
            # These tokens are generated locally and never supplied in frontend assets.
            record = db.get(Record, ('dev_admin', token_hash(token)))
            if record and record.state == 'active':
                data = record.data()
                assigned = db.get(Record, ('roles', data['subject']))
                return Actor(data['subject'], set(assigned.data()['roles'] if assigned else data['roles']), {'internal_content'})
        if not self.jwks:
            raise PermissionError('OIDC is required for operations access')
        key = self.jwks.get_signing_key_from_jwt(token).key
        claims = jwt.decode(token, key, algorithms=['RS256', 'ES256'], audience=self.settings.oidc_audience,
                            issuer=self.settings.oidc_issuer, options={'require': ['exp', 'iat', 'sub', 'iss', 'aud']})
        roles = db.get(Record, ('roles', claims['sub']))
        return Actor(claims['sub'], set(roles.data()['roles']) if roles else {'viewer'}, set(claims.get('scope', '').split()))

def require(actor: Actor, action: str):
    if actor.installation or not any(action in ROLE_ACTIONS.get(role, set()) for role in actor.roles):
        raise PermissionError('This role cannot perform this action')

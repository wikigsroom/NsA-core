from __future__ import annotations
import base64
import hashlib
import hmac
import json
import time
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from .contracts import strict_json, validate

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), sort_keys=True, allow_nan=False).encode('utf-8')

def token_hash(token: str) -> str:
    return digest(token.encode())

class Signer:
    def __init__(self, settings):
        if settings.mode != 'production' and not settings.signing_key.exists():
            settings.signing_key.parent.mkdir(parents=True, exist_ok=True)
            key = Ed25519PrivateKey.generate()
            settings.signing_key.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        self.key = serialization.load_pem_private_key(settings.signing_key.read_bytes(), None)
        if not isinstance(self.key, Ed25519PrivateKey):
            raise ValueError('An Ed25519 signing key is required')
        self.key_id = settings.key_id

    def envelope(self, purpose, payload):
        schema = {'release': 'release-manifest', 'policy': 'policy', 'app_update': 'app-update'}[purpose]
        validate(payload, schema + '.schema.json')
        raw = canonical(payload)
        if len(raw) > 2 * 1024 * 1024:
            raise ValueError('Signed payload exceeds 2 MiB')
        signature = self.key.sign(b'NSA/' + purpose.encode() + b'/v1\0' + raw)
        return {'envelope_schema': 1, 'purpose': purpose, 'key_id': self.key_id, 'algorithm': 'Ed25519',
                'payload_sha256': digest(raw), 'payload_base64': base64.b64encode(raw).decode(), 'signature_base64': base64.b64encode(signature).decode()}

    def public_document(self):
        public = self.key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        return {'keys': [{'key_id': self.key_id, 'public_key_base64': base64.b64encode(public).decode(), 'purposes': ['release', 'policy', 'app_update']}]}

def seal(secret: bytes, payload: dict) -> str:
    encoded = base64.urlsafe_b64encode(canonical(payload)).decode().rstrip('=')
    return encoded + '.' + hmac.new(secret, encoded.encode(), hashlib.sha256).hexdigest()

def unseal(secret: bytes, value: str) -> dict:
    encoded, signature = value.rsplit('.', 1)
    if not hmac.compare_digest(signature, hmac.new(secret, encoded.encode(), hashlib.sha256).hexdigest()):
        raise ValueError('Invalid signature')
    result = strict_json(base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)))
    if result.get('exp', 0) < time.time():
        raise ValueError('Expired token')
    return result

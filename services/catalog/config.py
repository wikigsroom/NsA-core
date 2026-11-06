from __future__ import annotations
from dataclasses import dataclass
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

@dataclass(frozen=True)
class Settings:
    mode: str
    database_url: str
    local_root: Path
    public_url: str
    signing_key: Path
    key_id: str
    oidc_issuer: str
    oidc_audience: str
    oidc_jwks_url: str
    s3_bucket: str
    s3_endpoint: str | None
    secret: bytes

    @classmethod
    def load(cls) -> 'Settings':
        mode = os.environ.get('NSA_MODE', 'production')
        if mode not in ('production', 'development', 'test'):
            raise RuntimeError('Invalid NSA_MODE')
        local = Path(os.environ.get('NSA_LOCAL_ROOT', str(ROOT / 'local'))).resolve()
        if mode != 'production':
            local.mkdir(parents=True, exist_ok=True)
        secret_path = local / 'service-secret.bin'
        if mode != 'production' and not secret_path.exists():
            secret_path.write_bytes(os.urandom(64))
        secret = os.environ.get('NSA_SERVICE_SECRET', '').encode() or (secret_path.read_bytes() if secret_path.exists() else b'')
        if len(secret) < 32:
            raise RuntimeError('Configure NSA_SERVICE_SECRET (at least 32 random bytes)')
        database = os.environ.get('NSA_DATABASE_URL', '' if mode == 'production' else 'sqlite:///' + (local / 'catalogue.db').as_posix())
        public_url = os.environ.get('NSA_PUBLIC_URL', 'https://localhost:8443').rstrip('/')
        issuer = os.environ.get('NSA_OIDC_ISSUER', '')
        audience = os.environ.get('NSA_OIDC_AUDIENCE', '')
        jwks = os.environ.get('NSA_OIDC_JWKS_URL', '')
        bucket = os.environ.get('NSA_S3_BUCKET', '')
        if mode == 'production':
            if not database.startswith('postgresql') or not all((issuer, audience, jwks, bucket)):
                raise RuntimeError('Production requires PostgreSQL, OIDC issuer/audience/JWKS and S3 bucket')
            if not all(url.startswith('https://') for url in (public_url, issuer, jwks)):
                raise RuntimeError('Production endpoints must use HTTPS')
        return cls(mode, database, local, public_url,
                   Path(os.environ.get('NSA_SIGNING_KEY', str(local / 'signing-key.pem'))),
                   os.environ.get('NSA_KEY_ID', 'local-release-v1'), issuer, audience, jwks, bucket,
                   os.environ.get('NSA_S3_ENDPOINT'), secret)

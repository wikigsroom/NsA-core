"""Private object provider; local development has the same explicit read-back semantics."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import shutil
import tempfile
import boto3

ID = re.compile(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,159}$')

class Objects:
    def __init__(self, settings):
        self.settings = settings
        self.root = settings.local_root / 'objects'
        self.root.mkdir(parents=True, exist_ok=True)
        self.s3 = boto3.client('s3', endpoint_url=settings.s3_endpoint) if settings.mode == 'production' else None

    def path(self, object_id):
        if not ID.fullmatch(object_id):
            raise ValueError('Invalid object identity')
        return self.root / object_id

    def available(self):
        try:
            if self.s3:
                self.s3.head_bucket(Bucket=self.settings.s3_bucket)
                return True
            return self.root.is_dir()
        except Exception:
            return False

    def temporary(self, object_id):
        return self.path(object_id + '.partial')

    def size_hash(self, object_id):
        if self.s3:
            response = self.s3.get_object(Bucket=self.settings.s3_bucket, Key=object_id)
            stream, size = response['Body'], response['ContentLength']
        else:
            path = self.path(object_id)
            stream, size = path.open('rb'), path.stat().st_size
        with stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        return size, digest

    def read(self, object_id, maximum):
        if self.s3:
            response = self.s3.get_object(Bucket=self.settings.s3_bucket, Key=object_id)
            with response['Body'] as stream:
                data = stream.read(maximum + 1)
        else:
            with self.path(object_id).open('rb') as stream:
                data = stream.read(maximum + 1)
        if len(data) > maximum:
            raise ValueError('Object exceeds parsing limit')
        return data

    def download_path(self, object_id):
        if not self.s3:
            return self.path(object_id), False
        handle, name = tempfile.mkstemp(dir=self.settings.local_root, suffix='.verification')
        import os
        os.close(handle)
        self.s3.download_file(self.settings.s3_bucket, object_id, name)
        return Path(name), True

    def put(self, object_id, source: Path):
        if self.s3:
            self.s3.upload_file(str(source), self.settings.s3_bucket, object_id)
        else:
            target = self.temporary(object_id)
            with source.open('rb') as incoming, target.open('wb') as outgoing:
                shutil.copyfileobj(incoming, outgoing, 1024 * 1024)
                outgoing.flush()
                import os
                os.fsync(outgoing.fileno())
            target.replace(self.path(object_id))

    def presign(self, operation, object_id, **extra):
        return self.s3.generate_presigned_url(operation, Params={'Bucket': self.settings.s3_bucket, 'Key': object_id, **extra}, ExpiresIn=900)

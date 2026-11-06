"""Streaming, bounded package validation used by publication workers and local tools."""
from __future__ import annotations
import hashlib
import stat
import unicodedata
import zipfile
from pathlib import PurePosixPath
from .contracts import strict_json, validate

def safe_path(name: str) -> str:
    if not name or len(name.encode('utf-8')) > 1024 or '\\' in name or '\x00' in name or ':' in name or name.startswith('/'):
        raise ValueError('Unsafe archive path')
    parts = name.split('/')
    if len(parts) > 32 or any(part in ('', '.', '..') or part.endswith(('.', ' ')) for part in parts):
        raise ValueError('Unsafe archive path components')
    if unicodedata.normalize('NFC', name) != name:
        raise ValueError('Archive paths must use NFC')
    return name

def verify_archive(path, manifest, index, chunk_index):
    validate(manifest, 'release-manifest.schema.json')
    validate(index, 'file-index.schema.json')
    validate(chunk_index, 'chunk-index.schema.json')
    files = index['files']
    expected = {safe_path(item['path']): item for item in files}
    if len(expected) != len(files) or len(expected) != manifest['file_count']:
        raise ValueError('File index is not unique or count differs')
    folded = [name.casefold() for name in expected]
    if len(set(folded)) != len(folded):
        raise ValueError('Case-colliding paths')
    total = 0
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if len(entries) != len(expected):
            raise ValueError('Archive has missing or unindexed files')
        seen = set()
        for item in entries:
            name = safe_path(item.filename)
            mode = item.external_attr >> 16
            if name in seen or item.flag_bits & 1 or stat.S_ISLNK(mode) or (stat.S_IFMT(mode) and not stat.S_ISREG(mode)):
                raise ValueError('Duplicate, encrypted, linked or special archive entry')
            if item.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                raise ValueError('Unsupported ZIP compression')
            seen.add(name)
            target = expected.get(name)
            if not target or item.file_size != int(target['bytes']):
                raise ValueError('File size differs from signed index')
            h, size = hashlib.sha256(), 0
            with archive.open(item) as stream:
                while data := stream.read(1024 * 1024):
                    size += len(data)
                    if size > int(target['bytes']):
                        raise ValueError('Expanded file exceeds signed size')
                    h.update(data)
            if h.hexdigest() != target['sha256'] or size != int(target['bytes']):
                raise ValueError('File hash differs')
            total += size
    if total != int(manifest['unpacked_bytes']):
        raise ValueError('Total expanded bytes differ')
    archive_sha = hashlib.sha256()
    with open(path, 'rb') as stream:
        for number, chunk in enumerate(chunk_index['chunks']):
            if chunk['index'] != number: raise ValueError('Chunk sequence differs')
            if int(chunk['offset_bytes']) != stream.tell():
                raise ValueError('Chunk offsets must be contiguous')
            data = stream.read(int(chunk['length_bytes']))
            if len(data) != int(chunk['length_bytes']) or hashlib.sha256(data).hexdigest() != chunk['sha256']:
                raise ValueError('Archive chunk differs')
            archive_sha.update(data)
        if stream.read(1):
            raise ValueError('Unindexed trailing archive bytes')
        if stream.tell() != int(manifest['archive']['bytes']) or chunk_index['bytes'] != manifest['archive']['bytes']:
            raise ValueError('Archive total size differs')
    if archive_sha.hexdigest() != manifest['archive']['sha256']:
        raise ValueError('Complete archive hash differs')
    return {'files': len(expected), 'bytes': str(total), 'archive_verified': True}

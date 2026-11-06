"""Create ZIP64, signed-size file/chunk indexes and the proposed manifest from local data."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'services'))
from catalog.contracts import validate
from catalog.crypto import canonical
from catalog.packages import safe_path

CHUNK = 8 * 1024 * 1024

def source_files(root: Path):
    """Keep traversal memory bounded and reject Windows reparse links before descent."""
    for directory, folders, names in os.walk(root, followlinks=False):
        folders.sort()
        for name in folders:
            child = Path(directory) / name
            if child.is_symlink() or child.is_junction():
                raise ValueError('Directory links cannot be packaged')
        for name in sorted(names):
            yield Path(directory) / name

def hash_file(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()

def write_json(path, value):
    path.write_bytes(canonical(value))
    return {'object_id': path.stem, 'bytes': str(path.stat().st_size), 'sha256': hash_file(path)}

def package(source: Path, destination: Path, spec: dict):
    source, destination = source.resolve(), destination.resolve()
    if source == destination or source in destination.parents:
        raise ValueError('Output must be outside the input tree')
    destination.mkdir(parents=True, exist_ok=True)
    release = spec['release_id']
    archive_path = destination / (release + '-archive.zip')
    if archive_path.exists(): raise FileExistsError('Immutable package output already exists')
    files, seen, total = [], set(), 0
    with zipfile.ZipFile(archive_path, 'x', compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        for path in source_files(source):
            if path.is_symlink(): raise ValueError('Symbolic links cannot be packaged')
            if not path.is_file(): continue
            name = safe_path(path.relative_to(source).as_posix())
            if name.casefold() in seen: raise ValueError('Case-colliding path')
            seen.add(name.casefold())
            if len(seen) > 250000: raise ValueError('File count limit exceeded')
            size = path.stat().st_size
            role = 'program' if path.suffix in ('.nso', '.mod', '.elf') or name.startswith('modules/') else 'shader' if path.suffix in ('.dmsv', '.spv') else 'configuration' if path.suffix in ('.json', '.txt', '.tsv') and not name.startswith('RomFS/') else 'resource'
            sha = hashlib.sha256()
            info = zipfile.ZipInfo.from_file(path, name)
            count = 0
            with path.open('rb') as incoming, archive.open(info, 'w', force_zip64=True) as outgoing:
                while data := incoming.read(1024 * 1024):
                    count += len(data)
                    if count > size: raise ValueError('Source changed while packaging')
                    sha.update(data); outgoing.write(data)
            if count != size: raise ValueError('Source changed while packaging')
            files.append({'path': name, 'bytes': str(size), 'sha256': sha.hexdigest(), 'role': role})
            total += size
            if len(files) == 1 or len(files) % 1000 == 0:
                print(f"Packaged {len(files)} files, {total} expanded bytes", flush=True)
    if not files: raise ValueError('Cannot package an empty tree')
    archive = {'object_id': release + '-archive', 'bytes': str(archive_path.stat().st_size)}
    file_index = {'index_schema': 1, 'release_id': release, 'file_count': len(files), 'unpacked_bytes': str(total), 'files': files}
    chunk_items = []
    archive_sha = hashlib.sha256()
    with archive_path.open('rb') as stream:
        while data := stream.read(CHUNK):
            archive_sha.update(data)
            chunk_items.append({'index': len(chunk_items), 'offset_bytes': str(stream.tell() - len(data)), 'length_bytes': str(len(data)), 'sha256': hashlib.sha256(data).hexdigest()})
    archive['sha256'] = archive_sha.hexdigest()
    chunks = {'index_schema': 1, 'object_id': archive['object_id'], 'bytes': archive['bytes'], 'chunk_size_bytes': str(CHUNK), 'chunks': chunk_items}
    validate(file_index, 'file-index.schema.json'); validate(chunks, 'chunk-index.schema.json')
    file_artifact = write_json(destination / (release + '-files.json'), file_index)
    chunk_artifact = write_json(destination / (release + '-chunks.json'), chunks)
    manifest = {**spec, 'manifest_schema': 1, 'created_at': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'), 'archive': archive,
                'file_index': file_artifact, 'chunk_index': chunk_artifact, 'unpacked_bytes': str(total), 'working_bytes': spec.get('working_bytes', '0'),
                'chunk_size_bytes': str(CHUNK), 'file_count': len(files)}
    validate(manifest, 'release-manifest.schema.json')
    write_json(destination / (release + '-manifest.json'), manifest)
    sbom = {'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'version': 1, 'components': [],
            'properties': [{'name': 'nsa:release-manifest', 'value': canonical(manifest).decode()}]}
    write_json(destination / (release + '-sbom.json'), sbom)
    return manifest

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--spec', type=Path, required=True)
    args = parser.parse_args()
    result = package(args.source, args.out, json.loads(args.spec.read_text(encoding='utf-8')))
    print(json.dumps({'release_id': result['release_id'], 'files': result['file_count'], 'archive_bytes': result['archive']['bytes']}))

if __name__ == '__main__': main()

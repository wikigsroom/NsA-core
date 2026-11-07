"""Prepare isolated native Windows development credentials, content and compiled-slot identities."""
from __future__ import annotations
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'services'))
sys.path.insert(0, str(ROOT))
os.environ.setdefault('NSA_MODE', 'development')
os.environ.setdefault('NSA_LOCAL_ROOT', str(ROOT / 'services/local'))
from catalog.app import Platform
from catalog.config import Settings
from catalog.crypto import canonical, digest, token_hash
from catalog.db import Record, create
from content_tools.package import package, hash_file, write_json
from nxnative.dm.android.slot_identity import write_identity_header

def initialize():
    platform = Platform(Settings.load())
    local = platform.settings.local_root
    from cryptography import x509
    from cryptography.x509.oid import NameOID
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    certificate, key_path = local / 'local-ca.pem', local / 'tls-key.pem'
    if not certificate.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'NsA local development')])
        cert = x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(datetime.now(timezone.utc) - timedelta(days=1)).not_valid_after(datetime.now(timezone.utc) + timedelta(days=90)).add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True).add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'), x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False).sign(key, hashes.SHA256())
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        certificate.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    android = ROOT / 'android/app/src'
    (android / 'debug/assets').mkdir(parents=True, exist_ok=True)
    (android / 'main/assets').mkdir(parents=True, exist_ok=True)
    shutil.copy2(certificate, android / 'debug/assets/local-ca.pem')
    (android / 'main/assets/trusted-keys.json').write_bytes(canonical(platform.signer.public_document()))
    credentials_path = local / 'development-credentials.json'
    if not credentials_path.exists():
        credentials = {}
        with platform.sessions() as db:
            for subject, roles in [('local-editor', ['editor', 'publisher', 'support', 'administrator', 'rights_reviewer']), ('local-reviewer', ['reviewer', 'rights_reviewer', 'viewer'])]:
                token = secrets.token_urlsafe(48)
                create(db, 'dev_admin', token_hash(token), {'subject': subject, 'roles': roles}, state='active')
                credentials[subject] = token
            db.commit()
        credentials_path.write_bytes(canonical(credentials))
    # A single-use invitation is a development provisioning aid, never a release asset.
    invitation = secrets.token_urlsafe(48)
    with platform.sessions() as db:
        import time
        create(db, 'access_grant', token_hash(invitation), {'expires': int(time.time()) + 7 * 86400}, state='active')
        db.commit()
    (android / 'debug/assets/internal-grant.txt').write_text(invitation, encoding='utf-8')
    print('Development TLS, signing trust and separate editor/reviewer credentials prepared. Credential values were not printed.')
    return platform

def profile_digest(directory):
    h = hashlib.sha256()
    for path in sorted(directory.rglob('*')):
        if path.is_file() and '__pycache__' not in path.parts:
            h.update(path.relative_to(directory).as_posix().encode()); h.update(bytes.fromhex(hash_file(path)))
    return h.hexdigest()

def prepare_slots(with_botw=False):
    local = ROOT / 'services/local'; local.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    source = ROOT / 'android/native-games/2048/game.c'
    mini = {'runtime_slot_id': 'nsa-2048-v1', 'library_name': 'libnsa_2048.so', 'runtime_api_version': 1, 'core_revision': revision,
            'profile_digest': hash_file(source), 'supported_module_build_ids': [hash_file(source)], 'shader_chain_id': 'software-rgba-v1',
            'shader_bundle_versions': [1], 'approved_driver_ids': ['system'], 'settings_schema_version': 1}
    write_json(local / '2048-identity.json', mini)
    generated = ROOT / '.gamedump/Hall/build/2048'; generated.mkdir(parents=True, exist_ok=True)
    write_identity_header(generated / 'hall_identity.h', mini)
    if not with_botw:
        write_json(local / 'slots.json', {'slots': [{'library_path': str(generated / 'libnsa_2048.so'), 'registry': mini}]})
        print('Open-source 2048 demo slot prepared; no private game inputs were read.')
        return
    profile = ROOT / 'nxnative/dm/games/01007EF00011E000'
    exefs = ROOT / '.gamedump/BOTW/input/01007EF00011E000_build/extract/v1114112/ExeFS'
    module_ids = []
    for name in ('main', 'subsdk0', 'sdk'):
        with (exefs / name).open('rb') as file:
            require_magic = file.read(4)
            if require_magic != b'NSO0': raise ValueError('Expected original NSO module')
            file.seek(0x40); module_ids.append(file.read(32).hex())
    botw = {'runtime_slot_id': 'botw-190-arm64-v1', 'library_name': 'libnsa_botw.so', 'runtime_api_version': 1, 'core_revision': revision,
            'profile_digest': profile_digest(profile), 'supported_module_build_ids': module_ids, 'shader_chain_id': 'nx2a-dmsv-v1',
            'shader_bundle_versions': [1], 'approved_driver_ids': ['turnip26-sysmem-adreno650'], 'settings_schema_version': 1,
            'runtime_env': {'DM_LANG': 'en-US', 'DM_FPS_TARGET': '30', 'DM_SHARED_FONT_EXT': '${PACKAGE_ROOT}/nintendo_ext.ttf', 'DM_VK_DRIVER': 'libnsa_driver_ad07xx.so', 'DM_VK_NOPREWARM': '1', 'DM_VK_UBWC': '3', 'DM_VK_VALIDATE': '0', 'DM_VK_RP': '0', 'DM_RENDER_SCALE': '0.6'}}
    write_json(local / 'botw-identity.json', botw)
    dependencies = [ROOT / '.gamedump/BOTW/host' / name for name in ('libadrenotools.so', 'libhook_impl.so', 'libmain_hook.so', 'libfile_redirect_hook.so', 'libgsl_alloc_hook.so')]
    driver = ROOT / '.gamedump/BOTW/graphics-debug/turnip26-sysmem/vulkan.ad07xx.so'
    bundled_driver = ROOT / '.gamedump/Hall/build/libnsa_driver_ad07xx.so'; bundled_driver.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(driver, bundled_driver)
    dependencies.append(bundled_driver)
    slot_file = {'slots': [{'library_path': str(generated / 'libnsa_2048.so'), 'registry': mini}, {'library_path': str(ROOT / '.gamedump/Hall/build/botw/libnsa_botw.so'), 'registry': {k: v for k, v in botw.items() if k != 'runtime_env'}, 'dependencies': [str(p) for p in dependencies]}]}
    write_json(local / 'slots.json', slot_file)
    print('Two explicit compiled-slot identities prepared; BotW remains experimental.')

def native_build():
    local = ROOT / 'services/local'
    ndk = ROOT / 'tools/player/toolchains/android-ndk-r27c'
    clang = ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
    mini = ROOT / '.gamedump/Hall/build/2048'
    subprocess.run([str(clang), '--target=aarch64-linux-android31', '-O2', '-shared', '-fPIC', '-I' + str(mini), str(ROOT / 'android/native-games/2048/game.c'), '-o', str(mini / 'libnsa_2048.so'), '-landroid', '-laaudio', '-lm', '-llog', '-Wl,-soname,libnsa_2048.so', '-Wl,-z,max-page-size=16384'], check=True)
    slots = json.loads((local / 'slots.json').read_text(encoding='utf-8'))['slots']
    if any(item['registry']['library_name'] == 'libnsa_botw.so' for item in slots):
        subprocess.run([sys.executable, str(ROOT / 'nxnative/dm/android/build.py'), '--game', '01007EF00011E000', '--ndk', str(ndk), '--out', str(ROOT / '.gamedump/Hall/build/botw'), '--lib', '--hall-identity', str(local / 'botw-identity.json'), '--jobs', '4'], check=True)
    subprocess.run([sys.executable, str(ROOT / 'devtools/hall_native.py'), '--slots', str(local / 'slots.json'), '--ndk', str(ndk)], check=True)

def content_prepare():
    local = ROOT / 'services/local'
    materials = ROOT / '.gamedump/Hall/content'
    mini_source = materials / '2048-input'; (mini_source / 'RomFS').mkdir(parents=True, exist_ok=True)
    (mini_source / 'RomFS/rules.json').write_text('{"board":4,"target":2048,"save_schema":1}', encoding='utf-8')
    selected = json.loads((local / 'slots.json').read_text(encoding='utf-8'))['slots']
    inputs = [('nsa-2048', mini_source, '2048-identity.json')]
    if any(item['registry']['library_name'] == 'libnsa_botw.so' for item in selected):
        inputs.append(('botw', ROOT / '.gamedump/BOTW/output/ZeldaBreathOfTheWild-data', 'botw-identity.json'))
    for game, source, identity_file in inputs:
        profile = json.loads((local / identity_file).read_text(encoding='utf-8'))
        spec = {'game_id': game, 'release_id': game + '-local-v1', 'game_version': '1.0' if game == 'nsa-2048' else '1.9.0', 'channel': 'internal', 'required_app_version_code': 1,
                'runtime': {'runtime_api_version': 1, 'runtime_slot_id': profile['runtime_slot_id'], 'profile_digest': profile['profile_digest'], 'module_build_ids': profile['supported_module_build_ids'],
                            'shader_chain_id': profile['shader_chain_id'], 'shader_bundle_version': 1, 'approved_driver_ids': profile['approved_driver_ids']},
                'save': {'compatibility_group': game + '-save-v1', 'compatible_previous_releases': [], 'backup_before_upgrade': True}, 'offline_permitted': True, 'minimum_capability': {'api_level': 31, 'abi': 'arm64-v8a'}}
        destination = materials / game
        if not (destination / (spec['release_id'] + '-manifest.json')).exists(): package(source, destination, spec)
    print('Local content packages prepared without modifying original game data.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['init', 'identities', 'native-build', 'content'])
    parser.add_argument('--with-botw', action='store_true', help='Explicitly include the existing local private BotW profile')
    args = parser.parse_args()
    if args.with_botw and args.command != 'identities': parser.error('--with-botw only applies to identities')
    if args.command == 'identities': prepare_slots(args.with_botw)
    else: {'init': initialize, 'native-build': native_build, 'content': content_prepare}[args.command]()

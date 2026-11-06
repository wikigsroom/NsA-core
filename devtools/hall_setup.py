"""Native Windows prerequisites. Downloads and caches stay in the task directory."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools' / 'hall'

def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        return
    temporary = destination.with_suffix(destination.suffix + '.part')
    if os.name == 'nt':
        subprocess.run(['curl.exe', '--fail', '--location', '--retry', '3', '--connect-timeout', '30', '--max-time', '1800', '--silent', '--show-error', '--output', str(temporary), url], check=True)
    else:
        with urllib.request.urlopen(url, timeout=120) as response, temporary.open('wb') as stream:
            shutil.copyfileobj(response, stream, 1024 * 1024)
    temporary.replace(destination)

def setup() -> None:
    TOOLS.mkdir(parents=True, exist_ok=True)
    archive = TOOLS / 'downloads' / 'gradle-8.13-bin.zip'
    download('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip', archive)
    checksum = archive.with_suffix('.sha256')
    download('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256', checksum)
    expected = checksum.read_text().strip()
    with archive.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != expected:
        raise RuntimeError('Gradle distribution checksum mismatch')
    if not (TOOLS / 'gradle-8.13').exists():
        with zipfile.ZipFile(archive) as source:
            source.extractall(TOOLS)
    sdk = TOOLS / 'android-sdk'
    existing = Path(os.environ.get('NSA_SDK_SOURCE') or os.environ.get('ANDROID_SDK_ROOT') or str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android' / 'Sdk'))
    for relative in ('platforms/android-36', 'build-tools/36.0.0', 'platform-tools', 'licenses'):
        source, target = existing / relative, sdk / relative
        if not source.exists():
            raise FileNotFoundError(f'Install Android SDK component first: {relative}')
        if not target.exists():
            shutil.copytree(source, target)
    (ROOT / 'android' / 'local.properties').parent.mkdir(parents=True, exist_ok=True)
    (ROOT / 'android' / 'local.properties').write_text('sdk.dir=' + sdk.as_posix().replace(':', '\\:') + '\n', encoding='utf-8', newline='\n')
    ndk = ROOT / 'tools/player/toolchains/android-ndk-r27c'
    if not ndk.exists():
        candidates = [os.environ.get('NSA_NDK_SOURCE'), os.environ.get('ANDROID_NDK_HOME'), os.environ.get('ANDROID_NDK_ROOT'), str(existing / 'ndk/27.2.12479018')]
        source = next((Path(p) for p in candidates if p and (Path(p) / 'source.properties').is_file() and '27.2.12479018' in (Path(p) / 'source.properties').read_text()), None)
        if not source: raise FileNotFoundError('Install NDK r27c first and set NSA_NDK_SOURCE to its directory')
        ndk.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, ndk)
    print('Native Windows Android toolchain is ready in tools/hall.')

if __name__ == '__main__':
    argparse.ArgumentParser(description=__doc__).parse_args()
    setup()

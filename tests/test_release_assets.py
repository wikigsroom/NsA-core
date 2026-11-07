"""Public bundle exclusions and byte-integrity checks, using disposable fixtures."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location('nsa_release_assets', Path(__file__).resolve().parents[1] / 'packaging/release_assets.py')
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseAssetsTest(unittest.TestCase):
    def bundle(self, directory, platform='windows-x64', extra=None, missing=None, version='v0.4.0'):
        files = {'README.md': 'English', 'README.zh-CN.md': '中文', 'docs/README.md': 'index',
                 'docs/README.zh-CN.md': '索引', 'docs/media/README.md': 'captures',
                 'docs/media/botw-gameplay.png': 'fixture', 'docs/media/botw-input.gif': 'fixture',
                 'docs/releases/v0.4.0.md': 'notes', 'LICENSE': 'license', 'VERSION': version,
                 'INSTALL.md': 'install', 'INSTALL.zh-CN.md': '安装',
                 'nxnative/dm/tools/nso.py': 'tracked source', 'nxnative/LICENSE': 'engine license'}
        files['nx2android.exe' if platform.startswith('windows') else 'nx2android'] = 'executable fixture'
        if missing: files.pop(missing)
        files.update(extra or {})
        path = directory / f'nx2android-v0.4.0-{platform}.zip'
        with zipfile.ZipFile(path, 'w') as target:
            for name, text in files.items(): target.writestr('nx2android/' + name, text)
        return path

    def test_private_game_credentials_and_unsafe_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in ['.gamedump/game/data', 'services/local/key', 'prod.keys', 'internal-grant.txt',
                         'tools/player/cache', 'game.nsp', 'game.apk', 'libnsa_botw.so', '../outside', '/outside']:
                with self.subTest(name=name):
                    with self.assertRaises(ValueError): release.inspect_bundle(self.bundle(root, extra={name: 'private'}), 'v0.4.0')

    def test_both_languages_and_gameplay_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            for name in ['README.zh-CN.md', 'docs/README.md', 'docs/media/botw-input.gif']:
                with self.subTest(name=name):
                    with self.assertRaises(ValueError): release.inspect_bundle(self.bundle(Path(temporary), missing=name), 'v0.4.0')

    def test_cached_or_dirty_bundle_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            for version in ['v0.3.0', 'v0.4.0-dirty', 'v0.4.0-1-g1234567']:
                with self.subTest(version=version):
                    with self.assertRaises(ValueError): release.inspect_bundle(self.bundle(Path(temporary), version=version), 'v0.4.0')

    def test_uploaded_readback_detects_tampering_and_extra_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            windows = self.bundle(root)
            self.bundle(root, 'macos-arm64')
            with patch.object(release.subprocess, 'check_output', return_value='a' * 40):
                release.prepare(root, 'v0.4.0')
            release.verify_download(root)
            extra = root / 'unlisted.txt'; extra.write_text('extra')
            with self.assertRaises(ValueError): release.verify_download(root)
            extra.unlink()
            windows.write_bytes(windows.read_bytes() + b'changed')
            with self.assertRaises(ValueError): release.verify_download(root)


if __name__ == '__main__': unittest.main()

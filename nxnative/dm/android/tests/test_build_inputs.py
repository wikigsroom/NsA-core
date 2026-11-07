"""Actual Clang/ELF regressions for stale Unicode build inputs and slot identity."""
from pathlib import Path
import json
import os
import struct
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'nxnative/dm/android'))
from build_deps import dependency_paths, dep_stale
from slot_identity import identity_record, verify_compiled_identity, write_identity_header

ARTIFACTS = ROOT / 'output/hall/native-input-regression'
HOST = 'windows-x86_64' if os.name == 'nt' else 'darwin-x86_64' if sys.platform == 'darwin' else 'linux-x86_64'
NDK = Path(os.environ.get('NSA_TEST_NDK', str(ROOT / 'tools/player/toolchains/android-ndk-r27c')))
CLANG = NDK / 'toolchains/llvm/prebuilt' / HOST / 'bin' / ('clang.exe' if os.name == 'nt' else 'clang')
IDENTITY = {'runtime_slot_id': 'fixture-v1', 'library_name': 'libnsa_fixture.so',
            'runtime_api_version': 1, 'core_revision': '1' * 40, 'profile_digest': '2' * 64,
            'supported_module_build_ids': ['3' * 64], 'shader_chain_id': 'fixture-v1',
            'shader_bundle_versions': [1], 'approved_driver_ids': ['system'], 'settings_schema_version': 1}


def workspace():
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    folder = tempfile.TemporaryDirectory(prefix='\u4f9d\u8d56 \u7a7a\u683c-', dir=ARTIFACTS)
    assert Path(folder.name).resolve().is_relative_to(ARTIFACTS.resolve())
    return folder


def escaped(path):
    return path.as_posix().replace('$', '$$').replace('#', r'\#').replace(' ', r'\ ')


class DependencyTest(unittest.TestCase):
    def setUp(self):
        self.folder = workspace(); self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.source = self.root / 'source.c'; self.header = self.root / '\u8eab\u4efd #$.h'
        self.obj = self.root / 'source.o'; self.dep = self.root / 'source.d'
        for path in (self.source, self.header, self.obj): path.write_text('fixture', encoding='utf-8')
        self.dep.write_text(escaped(self.obj) + ': \\\n  ' + escaped(self.source) + ' \\\n  ' + escaped(self.header) + '\n', encoding='utf-8')
        stamp = time.time_ns()
        os.utime(self.source, ns=(stamp - 3_000_000, stamp - 3_000_000))
        os.utime(self.header, ns=(stamp - 2_000_000, stamp - 2_000_000))
        os.utime(self.obj, ns=(stamp, stamp))

    def test_unchanged_unicode_inputs_reuse_object(self):
        self.assertFalse(dep_stale(self.obj, self.dep, self.source))

    def test_changed_unicode_header_invalidates_object(self):
        stamp = self.obj.stat().st_mtime_ns + 1_000_000
        os.utime(self.header, ns=(stamp, stamp))
        self.assertTrue(dep_stale(self.obj, self.dep, self.source))

    def test_missing_header_invalidates_object(self):
        self.header.unlink()
        self.assertTrue(dep_stale(self.obj, self.dep, self.source))

    def test_missing_depfile_invalidates_object(self):
        self.dep.unlink()
        self.assertTrue(dep_stale(self.obj, self.dep, self.source))

    def test_invalid_encoding_invalidates_object(self):
        self.dep.write_bytes(b'object.o: \xff\xfe.h')
        self.assertTrue(dep_stale(self.obj, self.dep, self.source))

    def test_malformed_depfile_invalidates_object(self):
        for text in ('missing separator', 'object.o: \n'):
            self.dep.write_text(text, encoding='utf-8')
            self.assertTrue(dep_stale(self.obj, self.dep, self.source))

    def test_drive_colons_and_escaped_paths(self):
        text = 'E:\\work\\object.o: E:/\u6e38\u620f\u79fb\u690d/source.c \\\r\n E:/\u76ee\u5f55\\ \u7a7a\u683c/a\\#b$$c.h C:\\kit\\api.h\r\n'
        self.assertEqual(dependency_paths(text), ['E:/\u6e38\u620f\u79fb\u690d/source.c', 'E:/\u76ee\u5f55 \u7a7a\u683c/a#b$c.h', r'C:\kit\api.h'])

    def test_comments_and_escaped_colon(self):
        self.assertEqual(dependency_paths('object.o: file\\:name.h # comment\n second.h\n'), ['file:name.h', 'second.h'])


@unittest.skipUnless(CLANG.is_file(), 'Native Android NDK is required for actual ELF cases')
class CompiledIdentityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = workspace(); cls.addClassCleanup(cls.folder.cleanup)
        cls.root = Path(cls.folder.name)
        cls.source = cls.root / 'slot.c'; cls.header = cls.root / 'hall_identity.h'
        cls.library = cls.root / 'libnsa_fixture.so'; cls.dep = cls.root / 'slot.d'
        cls.source.write_text('#include "hall_identity.h"\n__attribute__((visibility("default"), section(".nsa_identity"))) const char nsa_runtime_identity_json_v1[] = NSA_RUNTIME_IDENTITY_JSON;\nconst char *fixture_core(void) { return NSA_CORE_REVISION; }\n', encoding='utf-8')
        cls.compile(IDENTITY)
        cls.image = cls.library.read_bytes()

    @classmethod
    def compile(cls, identity):
        write_identity_header(cls.header, identity)
        subprocess.run([str(CLANG), '--target=aarch64-linux-android31', '-shared', '-nostdlib', '-fPIC',
                        '-MMD', '-MF', str(cls.dep), str(cls.source), '-o', str(cls.library),
                        '-Wl,-soname,libnsa_fixture.so'], capture_output=True, check=True)

    def section_location(self, image):
        header = struct.unpack_from('<16sHHIQQQIHHHHHH', image)
        offset, count, strings_index = header[6], header[12], header[13]
        sections = [struct.unpack_from('<IIQQQQIIQQ', image, offset + index * 64) for index in range(count)]
        names_section = sections[strings_index]; names = image[names_section[4]:names_section[4] + names_section[5]]
        for index, section in enumerate(sections):
            if names[section[0]:].split(b'\0', 1)[0] == b'.nsa_identity':
                return offset + index * 64, section
        raise AssertionError('Compiled fixture has no identity section')

    def with_payload(self, payload):
        image = bytearray(self.image); location, section = self.section_location(image)
        struct.pack_into('<QQ', image, location + 24, len(image), len(payload))
        image.extend(payload)
        return image

    def test_real_compiled_elf_matches_selected_registry(self):
        self.assertEqual(verify_compiled_identity(self.image, IDENTITY), identity_record(IDENTITY))

    def test_actual_rebuild_after_header_change(self):
        changed = {**IDENTITY, 'core_revision': '4' * 40}
        write_identity_header(self.header, changed)
        stamp = self.library.stat().st_mtime_ns + 1_000_000
        os.utime(self.header, ns=(stamp, stamp))
        self.assertTrue(dep_stale(self.library, self.dep, self.source))
        self.compile(changed)
        self.assertEqual(verify_compiled_identity(self.library.read_bytes(), changed)['core_revision'], '4' * 40)
        with self.assertRaisesRegex(ValueError, 'core_revision'):
            verify_compiled_identity(self.image, changed)

    def test_old_core_rejected_even_with_new_library_hash(self):
        changed = {**IDENTITY, 'core_revision': '5' * 40, 'library_sha256': '6' * 64}
        with self.assertRaisesRegex(ValueError, 'core_revision'):
            verify_compiled_identity(self.image, changed)

    def test_missing_identity_rejected(self):
        image = self.image.replace(b'.nsa_identity\0', b'.bad_identity\0')
        with self.assertRaisesRegex(ValueError, 'Missing'):
            verify_compiled_identity(image, IDENTITY)

    def test_duplicate_identity_section_rejected(self):
        image = bytearray(self.image); location, _ = self.section_location(image)
        table = struct.unpack_from('<Q', image, 40)[0]
        image[table + 64:table + 128] = image[location:location + 64]
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            verify_compiled_identity(image, IDENTITY)

    def test_duplicate_json_key_rejected(self):
        raw = json.dumps(identity_record(IDENTITY)).encode()
        payload = b'{"identity_schema":1,' + raw[1:] + b'\0'
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            verify_compiled_identity(self.with_payload(payload), IDENTITY)

    def test_boolean_version_rejected(self):
        record = identity_record(IDENTITY); record['runtime_api_version'] = True
        with self.assertRaises(ValueError):
            verify_compiled_identity(self.with_payload(json.dumps(record).encode() + b'\0'), IDENTITY)

    def test_nonfinite_json_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Non-finite'):
            verify_compiled_identity(self.with_payload(b'{"identity_schema":NaN}\0'), IDENTITY)

    def test_unterminated_metadata_rejected(self):
        with self.assertRaisesRegex(ValueError, 'terminator'):
            verify_compiled_identity(self.with_payload(b'{}'), IDENTITY)

    def test_actual_soname_mismatch_rejected(self):
        image = self.image.replace(b'libnsa_fixture.so\0', b'libnsa_wrongxx.so\0')
        with self.assertRaisesRegex(ValueError, 'SONAME'):
            verify_compiled_identity(image, IDENTITY)

    def test_wrong_architecture_rejected(self):
        image = bytearray(self.image); struct.pack_into('<H', image, 18, 62)
        with self.assertRaisesRegex(ValueError, 'AArch64'):
            verify_compiled_identity(image, IDENTITY)

    def test_truncated_elf_rejected(self):
        for image in (self.image[:63], self.image[:-100]):
            with self.assertRaises(ValueError): verify_compiled_identity(image, IDENTITY)


if __name__ == '__main__':
    unittest.main()

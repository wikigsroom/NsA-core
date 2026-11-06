"""Exercise the actual C rewrite, including live cull distances and unfamiliar pointer uses."""
import ctypes
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
NDK = ROOT / 'tools/player/toolchains/android-ndk-r27c'


def ins(op, *args):
    return [(len(args) + 1) << 16 | op, *args]


def module(extra=()):
    w = [0x07230203, 0x10300, 0, 30, 0]
    for x in [ins(17, 1), ins(17, 32), ins(17, 33), ins(14, 0, 1), ins(15, 0, 20, 0x6e69616d, 0, 12),
              ins(6, 10, 2, 0x6c6c7563, 0), ins(71, 10, 2), ins(72, 10, 0, 11, 0), ins(72, 10, 1, 11, 3), ins(72, 10, 2, 11, 4),
              ins(19, 1), ins(22, 2, 32), ins(23, 3, 2, 4), ins(21, 4, 32, 0), ins(43, 4, 5, 0), ins(43, 4, 6, 8),
              ins(43, 4, 7, 1), ins(43, 4, 8, 2), ins(28, 9, 2, 6), ins(28, 11, 2, 7), ins(30, 10, 3, 9, 11),
              ins(32, 13, 3, 10), ins(32, 14, 3, 3), ins(32, 15, 3, 2), ins(43, 2, 16, 0), ins(44, 3, 17, 16, 16, 16, 16),
              ins(59, 13, 12, 3), ins(33, 18, 1), ins(54, 1, 20, 0, 18), ins(248, 21), ins(65, 14, 22, 12, 5),
              ins(62, 22, 17), *extra, ins(253), ins(56)]:
        w += x
    return w


def instructions(w):
    i = 5
    while i < len(w):
        n = w[i] >> 16
        yield w[i:i + n]
        i += n


class CullDistance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        p = Path(cls.tmp.name)
        src = ROOT / 'nxnative/dm/android/gfx_vk_cull.c'
        if os.name == 'nt':
            tools = NDK / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
            cc, ld = tools / 'clang.exe', tools / 'ld.lld.exe'
            if not cc.is_file() or not ld.is_file():
                cls.tmp.cleanup()
                raise unittest.SkipTest('the local NDK is required on Windows')
            lib, obj = p / 'cull.dll', p / 'cull.obj'
            subprocess.run([str(cc), '--target=x86_64-pc-windows-msvc', '-ffreestanding', '-O0', '-Wall', '-Wextra', '-Werror', '-c', str(src), '-o', str(obj)], check=True, capture_output=True)
            subprocess.run([str(ld), '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/export:spv_trim_unused_cull', f'/out:{lib}', str(obj)], check=True, capture_output=True)
        else:
            cc = shutil.which('cc') or shutil.which('clang')
            if not cc:
                cls.tmp.cleanup()
                raise unittest.SkipTest('a C compiler is required')
            lib = p / 'cull.so'
            subprocess.run([cc, '-shared', '-fPIC', '-O2', '-Wall', '-Wextra', '-Werror', str(src), '-o', str(lib)], check=True, capture_output=True)
        cls.lib = ctypes.CDLL(str(lib))
        cls.rewrite = cls.lib.spv_trim_unused_cull
        cls.rewrite.argtypes = [ctypes.POINTER(ctypes.c_uint32), ctypes.c_uint32]
        cls.rewrite.restype = ctypes.c_uint32
        cls.val = os.environ.get('SPIRV_VAL') or shutil.which('spirv-val')
        if not cls.val and os.name == 'nt':
            v = NDK / 'shader-tools/windows-x86_64/spirv-val.exe'
            cls.val = str(v) if v.is_file() else None

    @classmethod
    def tearDownClass(cls):
        if os.name == 'nt':
            ctypes.windll.kernel32.FreeLibrary(ctypes.c_void_p(cls.lib._handle))
        del cls.rewrite, cls.lib
        cls.tmp.cleanup()

    def run_rewrite(self, w):
        a = (ctypes.c_uint32 * len(w))(*w)
        n = self.rewrite(a, len(w))
        return list(a[:n])

    def validate(self, w):
        if self.val:
            r = subprocess.run([self.val, '--target-env', 'vulkan1.1', '-'], input=struct.pack(f'<{len(w)}I', *w), capture_output=True)
            self.assertEqual(r.returncode, 0, (r.stdout + r.stderr).decode(errors='replace'))

    def test_unused_member_removed_and_clip_distances_retained(self):
        w = module()
        self.validate(w)
        out = self.run_rewrite(w)
        self.validate(out)
        self.assertLess(len(out), len(w))
        self.assertIn(ins(30, 10, 3, 9), list(instructions(out)))
        self.assertIn(ins(43, 4, 6, 8), list(instructions(out)))
        self.assertNotIn(ins(72, 10, 2, 11, 4), list(instructions(out)))
        self.assertIn(ins(65, 14, 22, 12, 5), list(instructions(out)))

    def test_live_cull_distances_unchanged(self):
        w = module([ins(65, 15, 23, 12, 8, 5), ins(62, 23, 16)])
        self.validate(w)
        self.assertEqual(self.run_rewrite(w), w)

    def test_literal_equal_to_an_id_does_not_look_like_a_pointer_use(self):
        w = module()
        pos = next(i for i in range(5, len(w)) if w[i:i + 4] == ins(59, 13, 12, 3))
        w[pos:pos] = ins(43, 4, 26, 12)
        self.validate(w)
        out = self.run_rewrite(w)
        self.validate(out)
        self.assertLess(len(out), len(w))
        self.assertIn(ins(43, 4, 26, 12), list(instructions(out)))

    def test_whole_block_load_unchanged(self):
        w = module([ins(61, 10, 24, 12)])
        self.validate(w)
        self.assertEqual(self.run_rewrite(w), w)

    def test_extended_instruction_number_is_a_literal(self):
        w = module([ins(12, 2, 24, 27, 50, 16, 16, 16)])
        xs = list(instructions(w))
        for x in xs:
            op = x[0] & 0xffff
            if op == 15:
                x[-1] = 50
            elif op == 59 and x[2] == 12:
                x[2] = 50
            elif op == 65 and x[3] == 12:
                x[3] = 50
        s = b'GLSL.std.450\0'
        s += b'\0' * (-len(s) % 4)
        words = struct.unpack(f'<{len(s) // 4}I', s)
        xs.insert(3, ins(11, 27, *words))
        w = [0x07230203, 0x10300, 0, 60, 0] + [v for x in xs for v in x]
        self.validate(w)
        out = self.run_rewrite(w)
        self.validate(out)
        self.assertLess(len(out), len(w))
        self.assertIn(ins(12, 2, 24, 27, 50, 16, 16, 16), list(instructions(out)))

    def test_pointer_copy_unchanged(self):
        w = module([ins(83, 13, 24, 12)])
        self.assertEqual(self.run_rewrite(w), w)

    def test_nonfinal_member_unchanged(self):
        w = module()
        pos = w.index(ins(30, 10, 3, 9, 11)[0])
        w[pos:pos + 5] = ins(30, 10, 3, 9, 11, 2)
        self.assertEqual(self.run_rewrite(w), w)

    def test_arrayed_block_unchanged(self):
        w = module()
        pos = next(i for i in range(5, len(w)) if w[i:i + 4] == ins(32, 13, 3, 10))
        w[pos:pos + 4] = ins(28, 25, 10, 7) + ins(32, 13, 3, 25)
        self.assertEqual(self.run_rewrite(w), w)

    def test_truncated_module_unchanged(self):
        w = module()[:-1] + [8 << 16 | 54]
        self.assertEqual(self.run_rewrite(w), w)

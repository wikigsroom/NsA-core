// The translator declares eight clip distances plus an unused cull distance in gl_PerVertex. Adreno 650 allows eight combined (VUID-00710,
// Breath of the Wild's opening). Remove only an unused final CullDistance member of a directly accessed output block; keep every live member and index.
#include <stdint.h>

static int cull_literal(uint32_t op) {
    // These declarations cannot read a block or pointer. Their literal numbers may equal an unrelated ID (capability 64 and output ID 64, for example).
    return op == 5 || op == 6 || op == 10 || op == 14 || op == 16 || op == 17 || op == 19 || op == 20 || op == 21 || op == 22 || op == 43 || op == 71 || op == 72;
}
static int cull_uses(const uint32_t *x, uint32_t n, uint32_t id) {
    uint32_t op = x[0] & 0xffff;
    if (cull_literal(op)) return 0;
    for (uint32_t a = 1; a < n; a++) if (!(op == 12 && a == 4) && x[a] == id) return 1;   // OpExtInst's instruction number is a literal, not an ID.
    return 0;
}
static uint32_t cull_const(const uint32_t *w, uint32_t nw, uint32_t id) {
    for (uint32_t i = 5; i < nw; i += w[i] >> 16)
        if ((w[i] & 0xffff) == 43 && w[i] >> 16 == 4 && w[i + 2] == id) return w[i + 3];
    return UINT32_MAX;
}
uint32_t spv_trim_unused_cull(uint32_t *w, uint32_t nw) {
    if (nw < 5 || w[0] != 0x07230203) return nw;
    uint32_t sid = 0, member = 0, st = 0, vars = 0;
    for (uint32_t i = 5; i < nw;) {
        uint32_t n = w[i] >> 16, op = w[i] & 0xffff;
        if (!n || n > nw - i) return nw;
        if (op == 72 && n == 5 && w[i + 3] == 11 && w[i + 4] == 4) {   // OpMemberDecorate ... BuiltIn CullDistance
            if (sid) return nw; sid = w[i + 1]; member = w[i + 2];
        }
        i += n;
    }
    if (!sid) return nw;
    for (uint32_t i = 5; i < nw; i += w[i] >> 16) {
        uint32_t n = w[i] >> 16, op = w[i] & 0xffff;
        if (op == 30 && n >= 3 && w[i + 1] == sid) { if (member != n - 3) return nw; st = i; continue; }
        if ((op == 5 || op == 6 || op == 71 || op == 72) && n >= 2 && w[i + 1] == sid) continue;
        if (op == 32 && n == 4 && w[i + 3] == sid) {
            if (w[i + 2] != 3) return nw;   // Direct Output blocks only; arrayed geometry inputs stay unchanged.
            uint32_t ptr = w[i + 1];
            for (uint32_t j = 5; j < nw; j += w[j] >> 16) {
                uint32_t m = w[j] >> 16, jo = w[j] & 0xffff;
                if ((jo == 5 || jo == 32) && m >= 2 && w[j + 1] == ptr) continue;
                if (jo == 59 && m == 4 && w[j + 1] == ptr && w[j + 3] == 3) {
                    uint32_t var = w[j + 2]; vars++;
                    for (uint32_t k = 5; k < nw; k += w[k] >> 16) {
                        uint32_t z = w[k] >> 16, ko = w[k] & 0xffff;
                        if ((ko == 5 || ko == 71) && z >= 2 && w[k + 1] == var) continue;
                        if (ko == 15 || k == j) continue;   // EntryPoint lists interface IDs but does not read the block.
                        if ((ko == 65 || ko == 66) && z >= 5 && w[k + 3] == var) {
                            if (cull_const(w, nw, w[k + 4]) >= member) return nw;
                            continue;
                        }
                        if (cull_uses(&w[k], z, var)) return nw;   // Whole-block loads, pointer copies or unfamiliar uses: retain it.
                    }
                    continue;
                }
                if (cull_uses(&w[j], m, ptr)) return nw;
            }
            continue;
        }
        if (cull_uses(&w[i], n, sid)) return nw;
    }
    if (!st || !vars) return nw;
    uint32_t out = 5;
    for (uint32_t i = 5; i < nw;) {
        uint32_t n = w[i] >> 16, op = w[i] & 0xffff;
        if ((op == 6 || op == 72) && n >= 3 && w[i + 1] == sid && w[i + 2] == member) { i += n; continue; }
        if (i == st) { w[out++] = (n - 1) << 16 | op; for (uint32_t a = 1; a < n - 1; a++) w[out++] = w[i + a]; i += n; }
        else for (uint32_t a = 0; a < n; a++) w[out++] = w[i++];
    }
    return out;
}

# ADR 0009: compiled runtime identity and Unicode dependency inputs

Status: implemented source; consolidated verification and a new device candidate required. Date: 2026-11-07.

## Observed failure

The installed `d8c51a…79af` APK rejects BotW with `MODULE_MISMATCH` before gameplay. Its registry and generated identity header name core `f1c68f7`, but the actual library contains the older `e6ee168` core. The adapter object predates its identity header. Clang wrote its dependency file in UTF-8; Python's Windows locale decoded that Chinese checkout path as cp936. The old dependency reader ignored paths that consequently did not exist and reused the stale object. Hashing those old bytes and placing their hash in a new registry did not prove the compiled identity.

This failure does not imply missing game data or a need to download BotW again. Preserve the rejected APK, header, object, dependency file, log and existing saves. The previous APK's successful BotW run remains historical evidence with its own identity.

## Decisions

1. Read Clang Make dependency files with strict UTF-8. Parse drive colons, escaped paths and continuations. Missing, unreadable, malformed or undecodable dependencies invalidate the object. Use nanosecond modification times. Report checked, actually compiled, cached and failed source counts separately.
2. Generate runtime identity macros through one shared writer. Each hall runtime exports a NUL-terminated UTF-8 JSON record in an allocated ELF `.nsa_identity` section, at most 16 KiB. The record and the runtime's `get_info` share the generated header and translation unit. Its format is [compiled-slot-identity schema 1](../contracts/compiled-slot-identity.schema.json).
3. The record contains `identity_schema` and the ten registry identity fields, excluding `library_sha256`. It cannot contain its own final library hash. It excludes private runtime environment mappings. The trusted APK registry continues to bind the final complete library bytes separately.
4. Before collecting any selected slot, inspect its actual ELF64/AArch64 shared-object sections, unique metadata, strict JSON and actual `DT_SONAME`. Compare every field with the intended registry. Copy the exact bytes that passed. The packaged-APK guard repeats these checks in addition to its existing hash/class checks.
5. Keep device bootstrap ABI and actual `get_info` checks. Metadata is an additional build-provenance check, not permission to relax runtime compatibility, process, driver, session or content verification. Standalone native behavior is unchanged.

## Compatibility and migration

Bootstrap ABI remains v1, Room remains schema 2, and game-save formats remain unchanged. Older hall libraries without compiled metadata must be rebuilt for this collection tool; historical APK observations retain their original narrower verification scope. The additional exported metadata symbol is optional to the runtime ABI.

The 2048 source hash changes, so publish a new immutable `nsa-2048-local-v4` release with the existing save compatibility group. Preserve v1/v2/v3 releases and their signatures. BotW resource/profile/module/shader identities remain unchanged; rebuild its host without replacing or downloading its installed content. Do not overwrite published releases or change the expected core to make the old binary pass.

## Verification obligations

Finish the complete source, tests and documentation before one consolidated verification batch. Twenty regression cases include real NDK compilation under a Unicode/spaced path, changed-header rebuilding, stale-core rejection, malformed ELF/JSON, missing/duplicate metadata, incorrect version types, architecture and actual SONAME. Skipped compiler cases are not passes. Run the native collection and final APK guard on the resulting two-slot candidate, and retain rejection of the old artifact.

Then repeat overlay/data-preservation checks, Android instrumentation, actual 2048 upgrade/input/audio/normal exit/reload, BotW movement/new-save/normal Back/reload and five cross-game rounds on that exact candidate. Record results independently from source intent and from the previous APK.

中文：本决策修复中文路径依赖误读与旧对象误复用，并增加编译产物身份核对。不会降低手机上的模块校验，也不会改写原始失败。新候选必须完成整批构建与实际运行验证；完整可玩性仍按原门槛验收。

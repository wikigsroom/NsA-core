# ADR 0001 — Integrated implementation before consolidated verification

Date: 2026-11-06. Status: accepted by the user's explicit development instruction.

The user requires every development round to implement its complete planned scope before testing and debugging, instead of cycling through individual modules. This supersedes the earlier ordering that required G0 before writing the broad hall. It does not relax G0 acceptance: two real games, actual save/reload, process/data isolation and safe return are still mandatory before claiming the platform is verified.

Round 1 includes the Android hall and runtime boundary, signed catalogue/download/install/save paths, service and operations workflows, native Windows development tooling, automated integration checks and bilingual usage documentation. Verification starts after that source batch is present. Discovered defects are corrected as an integrated batch, then checked together. Existing Vulkan/device adaptation edits are preserved separately.

The pinned build set is AGP 8.13.2, Gradle 8.13, Kotlin/Compose compiler 2.2.21, Compose BOM 2025.11.00 and Room 2.8.4. Java 21 runs Gradle; Android bytecode targets Java 17. SDK 36, minimum 31 and arm64-v8a follow baseline 2.0. KAPT is used for Room to avoid an unpinned KSP compatibility combination.

References: [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler plugin](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.plugin.compose/2.2.21), [Room releases](https://developer.android.com/jetpack/androidx/releases/room).

SQLite and a private local object directory are explicit native Windows development providers. Production uses PostgreSQL and S3, OIDC and HTTPS; local development cannot be mistaken for a public deployment. Local signing keys, CA keys, game material, credentials and runtime build outputs are ignored. No virtualization is used.

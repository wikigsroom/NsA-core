# Development and reproducible local setup

[简体中文](development.zh-CN.md) · [Project](../README.md) · [Deployment](deployment.md) · [Technical baseline](engineering/README.md)

## Source layout and prerequisites

| Path | Responsibility |
|---|---|
| `android/app/` | Compose UI, Room, API client, transfers, installation, saves and game coordinator |
| `android/native-games/2048/` | Original MIT-licensed native demo and tone output |
| `nxnative/dm/android/` | Native host, Vulkan adaptation, hall ABI and slot identity validation |
| `porter/`, `gui/` | Standalone desktop port pipeline and GUI |
| `content_tools/` | Indexed ZIP64 packaging and resumable publication through real APIs |
| `services/catalog/` | FastAPI application, auth, policy, storage and operations endpoints |
| `services/worker/`, `services/admin/` | Object verification worker and separate staff website |
| `devtools/` | Native Windows setup/build/device/capture tools |
| `packaging/`, `.github/workflows/` | Desktop bundles and verified release publication |
| `docs/product/`, `docs/engineering/`, `docs/design/` | Requirements, contracts, constraints and accepted visual design |

The verified hall development environment is Windows, Python 3.13, Java 21, Gradle 8.13, Android SDK 36 / build-tools 36.0.0 and NDK r27c (`27.2.12479018`). A physical arm64 Android API 31+ device is required for device checks. FFmpeg/ffprobe is required only for media export/validation. The desktop bundle has its own Python runtime and is not the Android hall build environment.

Keep checkout, new downloads, environment, caches and outputs under the user's selected `游戏移植/` root. Do not launch local Docker, WSL, virtual machines or Android emulators. Close unused tabs after any browser review. No browser is needed for the USB flow below.

## Fresh open-source demo

Set `JAVA_HOME` to Java 21. Install the required SDK/NDK components first; optionally point `NSA_SDK_SOURCE` and `NSA_NDK_SOURCE` to them. `hall_setup.py` copies components into project-local directories, verifies the downloaded Gradle checksum and writes ignored `android/local.properties`.

```powershell
git clone git@github.com:wikigsroom/NsA-core.git
Set-Location NsA-core
./devtools/hall_bootstrap.ps1
./devtools/hall.ps1 native
./devtools/hall.ps1 build
```

Bootstrap creates `services/.venv`, installs development requirements, prepares local TLS/content-signing keys and distinct editor/reviewer credentials, creates one internal invitation, and generates the selected open-source slot identity. `native` actually compiles the slot/bootstrap and validates the compiled metadata before collecting libraries. `build` assembles the debug APK and verifies its final packaged registry/libraries.

Local secrets and generated assets remain ignored: `services/local/`, debug CA/invitation, runtime registry, trust public document, native `.so` files, APKs, SDK/Gradle caches and `.gamedump/`.

Start API and worker in separate terminals:

```powershell
./devtools/hall.ps1 serve
```

```powershell
./devtools/hall.ps1 worker
```

Then run:

```powershell
./devtools/hall.ps1 content
./devtools/hall.ps1 publish-2048
./devtools/hall.ps1 install
```

Publication performs real upload, storage read-back, build/rights/release registration, worker verification and separate reviewer approval before internal publication. Keep API and worker running. USB installation overlays `org.nsa.hall.debug`, preserves its data and creates `adb reverse tcp:8443 tcp:8443`. Unlock and authorize the phone first. Use the experimental catalogue filter for the invited demo. The separate standalone game APK is not removed.

The original demo does not read a commercial game or require `prod.keys`.

## Repeat builds and immutable identities

A normal Android-only edit can reuse exact compiled native bytes. Record Android and native source revisions separately. Do not regenerate an identity just to label a cached library with a new commit.

Native/profile/driver edits require regenerated identity, actual compilation, collection and a new immutable resource release. Collection checks metadata compiled into the ELF and the expected SONAME; final APK verification checks selected libraries and registry hashes again. Missing or unreadable compiler dependencies force a rebuild. Unicode paths use UTF-8 dependency parsing.

Never overwrite an already published content release ID. The package source must match its prepared identity. Use the publication client's `--release-id` for a newly prepared release and preserve its private resume journal. A changed publication input cannot reuse old idempotency keys.

To renew a consumed development invitation, run only local initialization, then rebuild/overlay. Do not rerun slot identity generation for unchanged published content:

```powershell
services/.venv/Scripts/python.exe devtools/hall_local.py init
./devtools/hall.ps1 build
./devtools/hall.ps1 install
```

This command is development provisioning, not production credential issuance. Never print or upload the invitation, service secrets, signing keys or staff tokens.

## Explicit private BotW integration

The existing local profile is BotW 1.9.0. It expects private prepared input/output beneath `.gamedump/BOTW/`; `-WithBotw` is not a downloader or an automatic commercial-game preparation service.

```powershell
./devtools/hall_bootstrap.ps1 -WithBotw
./devtools/hall.ps1 native
./devtools/hall.ps1 content
./devtools/hall.ps1 build
```

With API/worker running, publish the newly prepared internal content and install. Use new immutable IDs if the profile differs from a previously published release. The profile includes module IDs, shader/driver bindings and game-specific save compatibility. On the tested device the internal profile uses a Turnip driver and reduced render scale; the exact registry and observations belong to their candidate records.

Keep game input, extracted data, native game code, private APKs and player saves under ignored `.gamedump/`. Public captures may show reviewed game imagery, but the commercial program itself is not a release attachment.

## Adding a supported game

1. Obtain the required local inputs and a supported port profile. Inspect the module/build IDs and current host limitations.
2. Implement a named native slot using the documented ABI; isolate library SONAME, environment, content, settings, cache and save paths.
3. Compile it with actual profile/core metadata, select it explicitly for the APK and run collection/package guards.
4. Prepare a separate immutable resource package: archive, full file index, block index, capability rules and save compatibility.
5. Register rights/build/release information, upload through scoped grants, complete worker verification and independent review, then publish to the intended scope.
6. Execute input, sound, save/reload, lifecycle/exit, repeated switching and representative sustained-device cases. Record exact device, driver, module/profile, content release and APK.
7. Offer a verified label only for the accepted combination. Missing native support requires an APK update; catalogue metadata cannot create it.

## Consolidated checks

Finish a complete source/test/contract/documentation batch before starting round verification, as required by ADR 0001. Do not replace failures or skips with passes.

```powershell
./devtools/hall.ps1 verify
```

This runs service integration, core unit checks, JVM tests and lint. For the complete Android candidate include app/test assembly and final APK inspection:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'tools/hall/gradle-home'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) 'tools/hall/android-user'
tools/hall/gradle-8.13/bin/gradle.bat -p android --continue assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
services/.venv/Scripts/python.exe devtools/hall_verify_apk.py
```

Only install/test APKs after normal game exit. Do not clear data or uninstall to make tests pass. Preserve a backup and exact APK digest first. The Android suite's disposable roots isolate regression data from real players. Use `adb shell am instrument -w -r org.nsa.hall.debug.test/androidx.test.runner.AndroidJUnitRunner` after installing the matching test APK. Check the actual output count/failures; the current source includes 22 cases.

`devtools/hall_smoke.py all` performs a real UI/download/input/save/reload scenario and deliberately removes/reinstalls only the demo's resources while checking save preservation. It stops on foreign foreground focus. Do not run it concurrently with another device project. Use the [device matrix](hall/device-validation.md) for remaining recovery/performance/fault cases.

## Captures and release

Capture PNG/MP4 privately with `hall_device.py` or the guarded USB harness. Review actual gameplay and private information. Export reviewed excerpts with `hall_media.py`; record the installed APK, source hash, dimensions, duration and transformation. Preserve failed attempts. Public clips are silent. Design prototype images stay in `docs/design/`.

The [release procedure](releases/README.md) fixes the source tag, builds desktop bundles on Windows/macOS, checks public contents and verifies uploaded byte hashes before publishing. Public game screenshots do not authorize uploading the internal commercial-game APK.

## Subsequent development constraints

Keep 90 requirement IDs, 360 rules and acceptance identities stable. Update affected normative documents, contracts and verification with behavior changes. Runtime ABI is 1, Room schema is 2; incompatible changes require an explicit migration/ADR. The operation mutex and actual kernel lease, not an old row or missing PID alone, govern recovery. Preserve the original events/save counters and game bytes during session reconciliation.

[Engineering baseline](engineering/README.md) · [ADR 0009](engineering/decisions/0009-compiled-slot-identity-and-unicode-builds.md) · [ADR 0010](engineering/decisions/0010-stopped-session-reconciliation.md) · [Current evidence](hall/implementation-status.md)

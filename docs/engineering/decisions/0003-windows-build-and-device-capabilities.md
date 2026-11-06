# ADR 0003 — Native Windows build paths and measured device capabilities

Status: accepted for the integrated implementation round.

All project outputs remain below `游戏移植/`. Gradle 8.13 runs with
`-Dfile.encoding=COMPAT` so JDK argument files use the Windows native charset;
Java source compilation explicitly remains UTF-8. This avoids moving the project
or changing Windows system settings. See [Gradle issue 30304](https://github.com/gradle/gradle/issues/30304).
Kotlin compilation runs in the Gradle process, avoiding a separate daemon and its
default run markers outside the project. Temporary files and tool caches use the
paths set by `devtools/hall.ps1` and `devtools/hall_job_worker.ps1`.

The hall queries the system Vulkan loader for physical-device properties and
features without creating a logical device or loading a game runtime. The GPU
driver digest binds the returned vendor/device/driver/cache UUID to the Android
build fingerprint. It describes the **system driver**; a bundled runtime driver
has a separate slot identity and is not certified by this probe.

API, ABI, page size, RAM, GPU identity, driver digest, Vulkan version and required
features are checked against all declared release requirements on the server and
again before local launch. Missing measurements cannot satisfy a requirement.
Minimum capability is an eligibility filter, not proof of gameplay performance;
compatibility remains experimental until the required physical-device evidence
has been reviewed.

Android 12 does not expose the Java Ed25519 provider required by the release
contract. The app uses Bouncy Castle's lightweight Ed25519 verifier with fixed
SPKI and signature lengths, without installing a global security provider.

After changing the Windows compiler encoding or moving generated classes, perform
a clean Gradle build. A consolidated run exposed an APK containing older DEX code
despite updated Kotlin class files and passing JVM tests. Installation now checks
the packaged hall classes, registry bytes and runtime library digests. The
`DeviceContractTest` instrumentation check exercises the actual Android JSON
implementation and packaged device probe; page size must remain an integer and
RAM size must remain a decimal string. A successful source compilation alone
does not establish that the installed artifact contains that source.

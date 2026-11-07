# Desktop tool installation

[简体中文](INSTALL.zh-CN.md) · [NsA overview](../README.md) · [Development guide](../docs/development.md)

This archive contains the **nx2android desktop authoring GUI/CLI**, its native engine and complete NsA documentation/media. It is not an Android APK. To build the unified portrait hall, check out the tagged source and follow the development guide. Standalone game APK production and the hall's compiled-slot/content workflow are separate.

## Download and verify

Choose the Windows x64 or matching macOS archive from [NsA Core Releases](https://github.com/wikigsroom/NsA-core/releases), together with that release's `SHA256SUMS.txt`. Compare the ZIP's SHA-256 before extracting:

```powershell
Get-FileHash ./nx2android-v0.4.0-windows-x64.zip -Algorithm SHA256
```

On macOS use `shasum -a 256 <archive.zip>`. `VERSION` identifies the exact tag. Keep the whole extracted folder, including `_internal`, `nxnative` and documentation. Put new tools/caches/game preparation within the selected `游戏移植/` root and allow room for extraction/compilation. No Docker, WSL or emulator is required.

Executables are not code-signed/notarized. Inspect their origin and hashes when the OS requests trust, then use its normal application approval or build from source. A warning alone does not establish a false positive; do not disable antivirus or global OS protections.

## Set up the desktop tool

Start `nx2android.exe` on Windows, or `./nx2android` on the matching Mac. This is the original authoring interface, rather than the phone's Obsidian hall.

1. In **Setup**, inspect missing components. **Download missing tools** retrieves Android NDK, .NET SDK and platform-tools from their publishers into the local tool directory; alternatively select existing installations.
2. For Switch porting, select your local `prod.keys`, then **Save and check**. Game input/keys are not included. The original native 2048 hall demo needs neither.
3. In **Games**, inspect standalone profiles. A profile does not guarantee every game version, GPU or phone; hall slots are maintained separately.

## Prepare and install a standalone port

1. In **Port**, select the game-input/update/DLC folder. NSP/XCI and compressed NSZ/XCZ are supported inputs; compressed input needs decompression space.
2. Check the detected profile/version/options and select **Port**. Logs show extraction, native rewriting, shaders and packaging. Preserve original input.
3. Output APK and `<Game>-data` appear beneath the selected working folder. Time/storage vary by game and cache; a menu frame does not establish playability.
4. On an authorized physical phone, enable USB debugging, unlock and approve the computer. Use **Install on device** for the standalone output. Export important progress first; do not clear data to make installation pass.

The standalone installer can verify/reuse an accessible matching device RomFS rather than copy another resource set. Consult [upstream guidance](https://github.com/shirai91/nx2android#readme) and CLI help before doing so; Android app-private directories have access restrictions. This is not the hall's signed download installer.

## CLI, hall and diagnosis

```powershell
./nx2android.exe -m porter -h
./nx2android.exe -m porter doctor
./nx2android.exe -m porter profiles
```

On macOS replace `./nx2android.exe` with `./nx2android`. For portrait discovery → download/prepare → landscape play, check out the same tag and follow [development](../docs/development.md), [deployment](../docs/deployment.md) and [player instructions](../docs/hall/README.md). This archive contains no public service, private two-game APK or commercial game program.

Keep error lines, exact `VERSION`, profile, game version, GPU and Android version when reporting to [Issues](https://github.com/wikigsroom/NsA-core/issues). Exclude keys, credentials, saves and game files. Graphics support depends on the host/driver; Mali/PowerVR and untested combinations are not accepted by this local record. Unpack updates separately and preserve inputs/outputs/configuration/progress. Read the [release notes](../docs/releases/v0.4.0.md); BotW remains experimental and sustained-performance/device/production gates remain open.

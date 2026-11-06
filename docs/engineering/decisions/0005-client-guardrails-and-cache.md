# ADR 0005 — Client filters, network policy, cache and bounded logs

Status: implemented in the integrated source batch; device acceptance is recorded separately.

The catalogue's default filter shows evidence for the current device. Every fetched page is evaluated against the actual capability and APK registry; an explicit experimental filter exposes invited test content. Search revisions discard superseded responses. A missing compiled slot leads to an App update action before a resource task is created. Authorization is checked again before the durable task is written.

Cancellation explains removal of incomplete data. Progress includes actual bytes and percent; speed and ETA are sampled locally over three-second windows and are shown as estimates. An unmetered Wi-Fi/Ethernet connection satisfies the default policy; a metered connection requires the task's explicit override. Each chunk/file checkpoint also checks for a live game session.

The lifecycle-owned Compose recomposer receives a mutable motion-duration scale, so the preference affects actual component animation. Activity interruption, loss of audio focus and noisy-output changes pause the native runtime; continuation reacquires focus. No audible-output acceptance is inferred from these implementations.

The main-process Application owns the session coordinator. Activity/ViewModel recreation reattaches a weak Activity reference without restarting initialization or creating a second runtime connection. A prepared launch opens only when the current Activity is resumed. Session creation, save export/import, installation and pre-update backups share one non-reentrant repository mutex; helpers for existing lock owners are explicitly named. Import rechecks the active save compatibility group at commit time. An App update backs up each installed game's existing saves after APK identity verification and before opening the system installer. A failed backup prevents that hand-off. Cancellation invalidates the pending attempt before service binding; cleanup waits for its database checkpoint, and delayed IPC/death callbacks are bound to the original session/generation rather than a later game.

Runtime caches are separate from immutable resources, under `files/runtime-cache/<game>/<binding>/`. The binding includes manifest runtime identity, actual APK library digest, graphics driver digest and Android build fingerprint. Clearing it retains resources and saves. `DM_HALL_CACHE_ROOT` redirects the Vulkan pipeline cache only for unified-hall builds; standalone behavior is retained.

The hall native adapter drains stderr through a bounded pipe into four files of at most 10 MiB per active session. Before launch, old session logs are trimmed to 10 MiB, allowing a 50 MiB total budget. Startup, normal completion and a local periodic maintenance worker enforce size/age retention; Android determines when periodic work executes. Logs older than seven days are removed at a maintenance opportunity. Native logs stay private and are not automatically attached to feedback. Temporary, cache, settings, resource and save actions retain separate scopes and confirmations.

Configure a production service at build time with `-Pnsa.catalogUrl=https://<service-host>`; the default localhost endpoint is for development. This decision does not provision a domain, signing material, content rights or production infrastructure.

Windows launchers initialize the global native exit variable. PowerShell 7 otherwise retains a script-local assigned value while native commands update the global value, which can falsely report a successful native compilation as failed. Exit status and packaged APK verification are both required.

No HTTP operation, schema identity or ABI version changes. Existing private internal manifests remain experimental; a production release must rebuild from a clean, committed revision and issue matching immutable release records.

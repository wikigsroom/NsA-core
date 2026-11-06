> Archived review record; source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`. Initial candidate/pending labels are historical. Final dispositions, including the superseding APP-05-D03 clarification, are in the [consolidated review](../../../source-review.md).
> 归档核对记录；初核中的候选/待复核状态保留历史，最终结论以汇总及独立复核的追加判定为准。

## APP-05 — Independent code refutation

Commit: cdedc7179ec1842592243a052525e021d8cf70f1. The reviewer did not author APP-05.md/.json. Both author records were read, then the service/client enforcement and actual caller chains were inspected directly. This is code refutation only; normative interpretation belongs to the separate specification reviewer.

No product edits, tests, builds, phone reads/input, browser, virtualization or additional agents.

| Candidate | Result | Scope retained |
| --- | --- | --- |
| D01 | survives | Equal-epoch/different-payload acceptance and real service generation confirmed. |
| D04 | survives | A successful mandatory second policy refresh can persist blocked after the last block comparison. |
| D03 | unverified | Source path confirmed; initial preparation and readiness wording need clarification. See appended adjudication. |
| D02 | narrowed | Deterministic supported-client revocation branch; current service only issues empty lists. |
| D05 | narrowed | False no-cache banner/context survives; some first-connect/network explanation already exists. |

---
## APP-05-D01 — survives

**Retained claim:** Client accepts equal-epoch policies with differing signed payloads; the bundled service can generate them, so signature verification and older-epoch rejection do not preserve a known block against a stale equal-epoch response.

**Call chain:** GET /v1/policies -> endpoint auth/dispatch -> current release rows -> Signer.envelope -> Api.request -> Trust.verify -> updatePolicies >= compare -> replace policy fact -> preflight exact-release check

**Refutation attempts:**

- Signer canonicalization/signature might stabilize snapshot identity: canonicalization sorts keys but includes a newly generated issued_at and current release states.
- Release revision CAS might make policy epoch strictly monotonic: change increments revision but epoch ignores it and uses integer-second updated.
- HTTP cache control/credential mutex might prevent replay: no-store addresses caches, not equal-epoch acceptance; credentialLock ends before protected resource send.
- A later runtime check might preserve the block: preflight uses the policy fact that was overwritten; slot/hardware/native configuration has no independent policy authority.

**Direct evidence:**

- services/catalog/app.py:38-39 utc includes current fractional time
- services/catalog/app.py:263-266 policy epoch=max(updated), new issued_at, current release states
- services/catalog/db.py:19-20,56-64 timestamps are integer seconds; independent revision increment
- services/catalog/app.py:453-466 approved publisher can block/unblock through normal mutation
- services/catalog/crypto.py:31-39 canonical payload digest and signature include those changing fields
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:140-145 equal epoch accepted and payload digest discarded
- android/app/src/main/java/org/nsa/hall/data/Api.kt:76-107 credential lock is not a whole-request policy lock
- services/catalog/app.py:485-486 no-store does not pin policy identity
- android/app/src/main/java/org/nsa/hall/data/Database.kt:37-38 fact read and Upsert are separate

**Qualification:** The stale-policy rollback impact requires an older valid signed response to be delivered/replayed after the newer one; no HTTPS bypass or attacker-controlled signature is established. UI action serialization is not independently reported as a proven concurrency exploit.

**Later verification need:** Same second publish/block snapshots and no-mutation repeated GET identity; equal-epoch/different-digest rejection retaining the block; later consolidated concurrency checks.

---
## APP-05-D04 — survives

**Retained claim:** For offline_permitted=false, the mandatory second updatePolicies call can persist a block after the sole release-block comparison, then return a launch selection.

**Call chain:** preflight -> optional policy A -> load/check A -> mandatory policy B(blocked) -> local module/files/space checks -> selection -> SessionRecord -> PREPARE

**Refutation attempts:**

- The policy endpoint might itself reject blocked releases: it returns a signed policy entry with state=blocked.
- operationLock might prevent a server-side block: it coordinates local operations only; publisher mutations are independent.
- trust.slot/SessionController or runtime handshake might repeat the release-state check: they validate module identity, cancellation and session identity, not the newly persisted policy.
- A failed second update might still launch: it propagates and fails; the candidate only concerns a successful second response with a block.

**Direct evidence:**

- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:157-167 final block comparison precedes second update
- services/catalog/app.py:263-266 blocked entry is returned in signed policy
- android/app/src/main/java/org/nsa/hall/security/Trust.kt:66-79 slot has module identity checks only
- android/app/src/main/java/org/nsa/hall/security/Capability.kt:6-26 hardware checks only
- android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:64-80 selection followed by cancellation check and SessionRecord
- android/app/src/main/java/org/nsa/hall/runtime/SessionController.kt:91-109 sends trusted selection context without policy replay
- android/app/src/main/java/org/nsa/hall/runtime/GameProcessService.kt:24-29 PREPARE is context/handshake only
- android/app/src/main/java/org/nsa/hall/runtime/GameActivity.kt:41-61 validates session/library, not release policy

**Qualification:** The installed module, files, hardware and other preconditions must pass; failure there can stop a launch but does not enforce policy.

**Later verification need:** Two successful snapshots published then blocked; require no SessionRecord or PREPARE. Separately assert mandatory-refresh failure stops online-only content.

---
## APP-05-D03 — prior code-path result; adjudicated below

**Retained claim:** A valid offline-permitted installation can be committed without any policy fact; later offline preflight rejects it because a nonnull policy is required before consulting offline_permitted.

**Call chain:** initial refresh -> policy failure swallowed -> detail/enqueue -> worker/install activate ready -> disconnect -> preflight best-effort refresh fails -> null policy rejected

**Refutation attempts:**

- First catalogue initialization might require policy success: refresh persists catalogue before wrapping updatePolicies in runCatching.
- Manifest/grant or installation might require/cache policy: they enforce current server visibility/signatures/capability but do not write the local policy fact.
- The initial-failure scenario might require tampering: route-specific policy failure followed by successful content operations suffices; no database deletion is needed.
- Existing local blocks might be removed as part of fallback: no such fallback exists; this candidate is strictly the never-synced-policy case.

**Direct evidence:**

- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:77-84 catalogue persists, policy errors swallowed
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:86-118 detail/enqueue/grant have no local policy prerequisite
- android/app/src/main/java/org/nsa/hall/transfer/TransferWorker.kt:97-150 existing content path has no updatePolicies call
- android/app/src/main/java/org/nsa/hall/transfer/Installation.kt:98-103 ready activation contains no policy condition
- services/catalog/app.py:237-255 grant checks server visibility/capability but carries no policy snapshot
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:157-161 require(policy != null) occurs before offline_permitted branch
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:210 promises offline after initial preparation

**Qualification:** Usually a successful initial policy GET prevents this state; the retained claim is a partial outage/never-synced-policy path. Whether product security should instead require a prepared offline authorization belongs to the independent specification review.

**Later verification need:** Fail only first policy retrieval, complete all content steps normally, disconnect, and check offline launch; keep allow/block snapshots as controls.

---
## APP-05-D02 — narrowed

**Retained claim:** For an already persisted valid policy with a nonempty revoked_key_ids list, preflight rejects an existing installation signed by that key before reading the exact release status.

**Call chain:** initialize reads persisted policy.revoked_key_ids -> preflight installed Trust.verify -> revoked key check throws -> exact release state unreachable

**Refutation attempts:**

- Expected installed manifest digest might bypass key revocation: Trust.verify rejects revoked key first, before expected digest and signature checks.
- A fresh release policy might override the key rejection: preflight verifies installed envelope before updatePolicies/read release state.
- Current service might not produce the prerequisite: confirmed; the sole production issuer hardcodes revoked_key_ids=[] and no revocation mutation was found.
- The candidate might require trusting new policy signed by a revoked key: it does not; use a still-trusted policy signing key, as the author already qualifies.

**Direct evidence:**

- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:56 loads persisted revocation state
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:153-160 installed envelope verified before exact release-policy read
- android/app/src/main/java/org/nsa/hall/security/Trust.kt:52-64 generic verify rejects revoked key at line 56 before digest check
- services/catalog/app.py:263-266 current only policy issuer hardcodes empty revocation list
- docs/engineering/contracts/policy.schema.json:51-57 accepts a nonempty revocation list

**Qualification:** Retain as a conditional consumer/contract branch, not a demonstrated current end-to-end production failure. The original author already discloses this limitation; narrowing only makes the current-source reachability distinction explicit.

**Later verification need:** Later client integration fixture signed by a still-trusted policy key; key-revoked new content versus digest-pinned existing allow/unlisted/blocked installations.

---
## APP-05-D05 — narrowed

**Retained claim:** Once first-launch refresh fails, the discovery error banner falsely says cached data is displayed even when no catalogue cache exists; the network-error route has no cache-presence/time projection.

**Call chain:** fresh local DB -> enter hall -> refresh API failure -> networkError nonempty -> unconditional line-153 banner; optional network-error route

**Refutation attempts:**

- Fresh offline may be unable to enter hall: Welcome writes welcome=true before network refresh, so entry and local library remain reachable.
- Some genuine first-connect/network explanation may already exist: Welcome line 349 says first connection needs network and the empty card line 155 reports catalogue failure.
- An empty local library or online entries might be fabricated: neither; local library uses actual games and catalogue starts empty.
- The existing first-connect note might fix the cache claim: it does not change the unconditional cache banner at line 153.

**Direct evidence:**

- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:30-32 initial empty catalogue/time
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:51-53 no cache leaves both empty
- android/app/src/main/java/org/nsa/hall/data/HallRepository.kt:84 sets network error
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:147 permits entering hall before refresh success
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:153 unconditional cached-data banner
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:155 existing catalogue-failure explanation
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:164-168 real local empty library
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:306-312 network page lacks cache status/time
- android/app/src/main/java/org/nsa/hall/ui/HallApp.kt:349 genuine first-connect network note

**Qualification:** Do not describe all first-offline explanation as absent or the app as an offline-entry/login wall. Keep the low-impact inaccurate cache fact and missing contextual cache status only.

**Later verification need:** Later consolidated first-launch/no-cache, populated cache, cached-empty catalogue and server-failure UI review; no new mirror unit tests needed.

---

**Additional scope controls:** D01 does not establish attacker-controlled HTTPS injection. Cache-Control:no-store is present but cannot implement policy identity checks. API credentialLock protects credential rotation only; protected resource requests execute after it. D03 is not generalized to all offline installs. D04's second-refresh failure correctly stops an online-only launch, and cancellation checks remain in SessionController; neither refutes successful final synchronization of a block. D02 is not described as an observed current-server revocation incident. D05 does not imply fabricated catalogue rows or denied local-library access.

**Search record:** Global policy writes/consumers and nonempty revocation production were searched in services/catalog, services/tests, android main and content_tools. Actual service endpoint registration/auth/dispatch, release mutation CAS, timestamp generation, signer canonicalization, client HTTP locking/error handling, Room fact operations, content activation, launch coordinator, runtime handshake, module/hardware checks, and the fresh-offline UI were read. Full structured paths, line evidence and counterarguments are in APP-05-code.json.

**Unverified layer:** Runtime scheduling, route-specific outage tests and physical gameplay were intentionally not executed. All retained findings still require the independent specification disposition and the user's later consolidated verification.

---

## APP-05-D03 — adjudication after specification refutation

**Effective verdict: unverified; unresolved documentation and UX clarification, not a confirmed defect.** This supersedes the earlier D03 disposition. The initial trusted-policy gate is not disproved and removing it is unsupported.

The source path still exists: installation may finish without a local policy, and launch rejects a missing initial policy (`HallRepository.kt:L157-L161`). However, `install.ready` does not establish that all necessary offline preparation is complete.

The strongest UI evidence is generic: `HallApp.kt:L402` maps a completed transfer to “游戏已准备好”; L221-L225 places “开始游戏” under download/validation/install progress. Neither explicitly confirms offline preparation. L210 says “首次准备完成后支持” conditionally, and L153 qualifies availability as “按离线策略运行”. Launch still checks policy and gives “首次启动需要连接目录获取策略” at `HallRepository.kt:L159`.

A scoped search of Android main UI/resources for offline-ready/completed-offline-preparation wording found no explicit offline-complete state beyond that conditional text. Therefore the source facts support clarifying which initial preparation remains, and how generic “prepared” wording/retry should expose it; they do not independently establish the narrowed APP-05 violation. Define that UI contract before accepting a preparation-projection defect. Preserve the distinction between no initial trusted policy, an existing allowed policy and a known block. No tests, product changes or device activity were performed.

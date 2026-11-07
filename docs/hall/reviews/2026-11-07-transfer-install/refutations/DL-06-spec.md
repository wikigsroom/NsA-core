> Archived independent_spec_refutation; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

## DL-06 — 独立规格反证

**Review type:** independent specification refutation. **Pinned source:** `189169fded9cd6557b3cb146b56d8b2e5243d705`. The reviewer authored DL-07, not DL-06. Inputs are the completed `../requirements/DL-06.md` and `../requirements/DL-06.json` in this output directory; those inputs are not edited.

**Result:** D01 survives as a mandatory protocol constraint with conditional availability impact. Corrupt-content and automatic transparent-decompression allegations are refuted. O01 is a real narrower requirement gap, but no separate integrity-impact finding is established; O02–O04 remain bounded observations/adjacent requirements.

All citations are repository-relative. No test/build, network, browser, phone/ADB, Docker, WSL, VM or emulator action was used. Only this report and its paired JSON are written. None of the described controlled-response scenarios was executed.

---

### Why the engineering encoding clause is binding

- `docs/product/data/requirements.json:L913-L947` identifies DL-06 as P0 required, lists all four rules, and explicitly points to `../../engineering/04-transfer-install-save.md` at L945.
- `docs/engineering/README.md:L29` states that technical chapters fix implementation constraints; a narrower product summary is not permission to ignore them.
- `docs/engineering/04-transfer-install-save.md:L13` explicitly requires “请求Accept-Encoding: identity”; L17 requires checking identity on 206. These are imperative protocol statements, not examples, future work or optional RFC commentary.
- Independently, `docs/product/技术方案与接口约定.md:L44` requires identity/Range requests, valid 206/strong ETag, no nonzero-200 append, no direct 416 completion and same-object expiry renewal.
- Searches of applicable ADRs found no encoding/416 waiver. The ADR 0007 recovery supplement does not replace these HTTP constraints.

The source requirement is therefore more specific than “wrong bytes must not install.” Correct SHA checks provide important integrity protection but do not satisfy the independent request/response encoding instruction.

---

### D01 — Identity request/response constraint

**Disposition:** survives with the original impact limits. **Severity:** Medium / P2, conditional availability/protocol gap. **Confidence:** high for the textual/source divergence; operational effect conditional and not executed.

Attempted specification refutations:

1. **Only the four JSON subrules count, and they do not mention a literal encoding header.** Rejected: the JSON links this technical chapter, the engineering baseline declares the chapter's implementation authority, and product technical text independently repeats identity.
2. **Existing fixed length, ETag and signed hashes implement an equivalent guarantee.** They implement no wrong-byte success. They do not request the intended representation or reject a response specifically for nonidentity coding, as the chapter requires.
3. **A supplied origin always serves identity, so the rule is optional.** This can limit consequence on that origin; it cannot waive the explicit client protocol contract. The local origin indeed streams ordinary bytes (`services/catalog/app.py:L603-L621`), so no present local-origin failure is established.
4. **OkHttp injects gzip and automatically decompresses archive ranges.** Refuted, not used to support D01. The declared dependency is 4.12.0 (`android/app/build.gradle.kts:L56`); read-only inspection of the cached `BridgeInterceptor.intercept` independently confirms that auto-gzip is enabled only when both Accept-Encoding and Range are absent (offsets 154–184), and transparent decoding is gated by that flag (290–423). The archive supplies Range.
5. **Missing Content-Encoding check allows a different archive to install.** Refuted by the incoming signed chunk SHA, exact byte/range checks and whole archive SHA before installation (`TransferWorker.kt:L130-L147`). A response that fails those checks is rejected before the archive write/activation path.

**Accepted impact:** a controlled origin/intermediary that selects an encoded representation when the request omits explicit identity, while serving identity when requested, can cause repeated rejection/failure even though a usable representation exists. That is a conditional availability scenario; it is not evidence that the inspected local origin or a deployed CDN currently does this. The mandatory wire-header/response-policy mismatch itself is visible in `Api.kt:L141-L145` and `TransferWorker.kt:L129-L135`.

**Accepted scope for remediation:** constrain object transfer requests to identity and check applicable content responses before consuming them; preserve signed identities, byte bounds and hash gates. Range archive requests are the concrete DL-06 path. Full-object signed indexes use the same helper and should have consistent object-representation policy. API metadata JSON, diagnostic upload and unrelated UI/cryptography changes are outside this finding. Do not turn compressed/mismatched representations into a silent full-download fallback or corruption claim.

The proposed P2 is justified only as a conditional interoperability/availability issue. If reporting solely the absent header without an origin capable of triggering an effect, label that portion a protocol compliance gap rather than measured user failure.

---

### O01 — Missing specialized 416 length/full-hash branch

**Disposition:** retain as a mandatory source compliance observation; no separate corrupt-success finding.

`产品需求细则.md:L409` / `requirements.json:L927` explicitly require length verification and complete validation on 416, and the engineering table repeats it at `04-transfer-install-save.md:L19`. “必须处理” is the table heading at L15. No exception saying generic rejection is equivalent was found. Thus the author correctly leaves DL-06.03 partial.

The severity claim must remain narrow. The client rejects 416 before reading/writing any error body (`TransferWorker.kt:L129-L135`). A complete valid local archive is already rehashed chunk by chunk, issues no missing-range request and reaches the whole SHA (`L113-L119,L143-L147`). The local service's 416 occurs for an out-of-range request (`services/catalog/app.py:L607-L610`). No conforming same-object path was established in which missing this special branch corrupts success, discards a complete valid archive, or forces an avoidable failure. An injected arbitrary 416 can show the missing branch; it does not prove such a consequence.

AC-D04 requires controlled 416 and final file integrity (`acceptance.json:L147-L154`), so later concentrated protocol validation should cover it. Not_run alone is not an implementation defect or evidence of runtime failure. The observation must remain traceable rather than disappearing because hash checks protect integrity.

---

### O02 — Client grant-field/strong-ETag validation and persistence

**Disposition:** retain client-defense observation; reject an arbitrary-object mixing finding against the checked system.

DL-06.01/04 require fixed identity and same-object renewal, not a named client-only implementation. `DownloadGrant` requires a strong quoted ETag (`docs/engineering/contracts/domain.schema.json:L693-L716`). The client does not independently validate all grant fields or persist ETag (`Database.kt:L9-L10`; `HallRepository.kt:L140-L142`; `TransferWorker.kt:L110-L131`), but the service create path binds the object to the signed manifest and stores its original metadata (`services/catalog/app.py:L239-L257`). Renewal checks owner/object/manifest and returns that same data (`L258-L264,L293-L295`).

This supplies real enforcement across the caller/service boundary. A rogue changed grant requires a backend/storage contract violation beyond the checked successful renewal path. The original signed chunk/full-file hashes independently prevent wrong bytes from completing. No reviewed specification requires ETag to be a field of this particular Room row, and no deployed weak-ETag response was established. A missing independent client defense should not be presented as an already reachable cross-object append exploit.

A later client check can strengthen the boundary, but the finding scope must state the extra faulty/replaced-service precondition. Production S3 metadata/deployment acceptance remains separate.

---

### O03 — All object 401/403 renew, and 404/410 use generic errors

**Disposition:** mandatory handling detail, primarily DL-09 overlap; retain no-authorization-bypass conclusion.

`04-transfer-install-save.md:L20-L23` requires distinguishing credential/eligibility/URL expiry, renewing only URL expiry, refreshing policy for 404/410, and classifying bounded transient/integrity failures. `产品需求细则.md:L455-L458` explicitly assigns that distinction and retry classification to DL-09. This is not optional just because it is outside D01.

The checked code renews once after every object 401/403 (`TransferWorker.kt:L124-L127`), and local object denial returns bare 403 (`services/catalog/app.py:L622`). The authenticated renewal still checks scope/visibility (`app.py:L262-L263`), so retrying authorization does not bypass it. Generic retry/classification shortcomings should be handed to the DL-09 review and not double-counted as another DL-06 corrupt-content finding. Possible extra round trips or stale policy presentation are the bounded consequences; this review does not claim unlimited traffic or unauthorized content.

---

### O04 — Deployed object-store/CDN immutability

**Disposition:** deployment evidence boundary, not a source-proven mutable-content defect.

The same-object/immutable rule is mandatory (`DL-06.04`, `产品需求细则.md:L410,L1222`). Source presigning for the stored logical key (`services/catalog/app.py:L78-L85`; `services/catalog/storage.py:L80-L81`) does not by itself prove a deployed bucket/CDN policy, nor does absence of inspected deployment evidence prove mutable public content. No deployment was inspected in this audit and none is asserted. Keep the open acceptance question; do not invent a production misconfiguration.

---

### Searches, paths and evidence boundaries

- Read the entire DL-06 author report/JSON, all four exact machine/detail subrules, linked engineering protocol table, product technical protocol paragraph, AC-D04 and related contract sections.
- Searched `identity / Accept-Encoding / Content-Encoding / 416 / ETag / 同一对象 / 不可变` over the relevant product/engineering files. The identity requirement appears in both `04-transfer-install-save.md:L13,L17` and `技术方案与接口约定.md:L44`. No applicable ADR supplies a waiver.
- Re-read `Api.kt:L52-L75,L141-L145`, `TransferWorker.kt:L99-L154`, and object create/renew/grant/local GET at `services/catalog/app.py:L239-L264,L293-L295,L598-L622` to test the specification interpretations against actual enforcing paths.
- Rechecked the cached declared OkHttp 4.12.0 class using read-only disassembly. No HTTP call or application execution was performed.
- Followed request → exact 206/range/ETag → bounded body → signed chunk SHA → archive write/sync → whole SHA → install, and expiry/denial → renew → same stored metadata. These controls defeat stronger integrity allegations.
- AC-D04 remains `not_run`; DL-06 remains `not_accepted` (`requirements.json:L946`). The refutation records source/spec reasoning and conditional impact; it does not upgrade device, service deployment or fault-injection acceptance.

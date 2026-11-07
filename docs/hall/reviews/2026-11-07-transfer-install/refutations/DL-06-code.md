> Archived independent_code_refutation; reviewed source `189169fded9cd6557b3cb146b56d8b2e5243d705`. Original candidate/pending labels are historical; read the [final dispositions](../../../transfer-install-review-2026-11-07.md).
> 归档核对证据；原始候选标签保留，最终结论与限定见汇总。

## DL-06 — Independent code refutation

**Reviewer role:** independent code reviewer; original assigned requirement was PKG-06, not DL-06. **Pinned source:** `189169fded9cd6557b3cb146b56d8b2e5243d705`. **Result:** keep DL-06-D01 only as the already-narrowed, conditional availability/protocol gap. Reject content-corruption, automatic transparent-decompression and arbitrary-object-mixing escalation. No execution or device acceptance is claimed.

Inputs: `../requirements/DL-06.md` / `../requirements/DL-06.json` in this output directory; the pinned Api/worker/repository/trust/database/reconciliation/installation source; download session creation/renewal and local object serving in the service; the declared and locally cached OkHttp 4.12.0 dependency. Paths below are repository-relative.

### DL-06-D01 — Missing identity encoding constraint

**Disposition:** survives code refutation with narrowed impact. **Severity:** Medium / P2, conditional protocol/availability impact. **Source confidence:** high. **Actual deployed impact:** unverified.

The required header is not secretly supplied by an application caller or interceptor. `android/app/src/main/java/org/nsa/hall/data/Api.kt:L141-L145` constructs the object request with only URL, optional Range and optional If-Range. The full HTTP-client construction at L52-L75 has no application or network interceptor setting encoding constraints. Archive requests at `android/app/src/main/java/org/nsa/hall/transfer/TransferWorker.kt:L123,L127` pass both range and grant ETag. The response acceptance at L130-L135 checks status, exact Content-Range, ETag, byte length and signed chunk SHA, but no Content-Encoding. Independent search for `Accept-Encoding|Content-Encoding|GZip|gzip|Interceptor|addInterceptor|addNetworkInterceptor|416|If-Range` across app main/service located the If-Range write and service 416 response, with no application identity-encoding enforcement. A separate bounded search over Api/worker/service found no compression middleware supplying it.

This is a concrete omission from `docs/engineering/04-transfer-install-save.md:L13,L17`, which requires the explicit identity request and matching 206 representation. It does not establish that the current server serves compressed archives: `services/catalog/app.py:L603-L621` reads the ordinary stored byte range directly, emits the range/length/ETag, and has no Content-Encoding transformation. Thus the original controlled-origin reproducer remains a proposed conditional availability demonstration, not a reproduced normal-server failure.

I independently disassembled the already-cached declared dependency using read-only `javap -c -p`: `tools/hall/gradle-home/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.12.0/2f4525d4a200e97e1b87449c2cd9bd2e25b7e8cd/okhttp-4.12.0.jar`, class `okhttp3.internal.http.BridgeInterceptor`, method `intercept`. Offsets 151–184 set the transparent-gzip flag only when **both** Accept-Encoding and Range are absent; a Range header branches past that flag/header setup at offset 169. Offsets 290–423 gate transparent decompression on the same flag. Both inspected archive calls supply Range, so a claim that this pinned archive path automatically negotiates gzip and silently decompresses is refuted. This bytecode check did not execute the application, tests or network calls.

### Why wrong content cannot be promoted by this omission

`TransferWorker.kt:L130-L135` rejects non-206 or changed range/total/ETag before reading a body into the archive. Even an origin using matching headers must provide the exact signed chunk length and SHA before `output.write`. The manifest-bound chunk index is length/hash checked in `auxiliary` at L77-L97; its archive object identity is checked at L106. The whole archive must satisfy the original manifest digest at L143-L147 before `Installation.install` at L150. `Trust.kt:L73-L86` verifies the manifest envelope signature and fixed payload digest at enqueue (`HallRepository.kt:L147-L152`). Therefore encoded/different bytes do not become a successful archive merely because the identity header is absent. A future controlled-origin example may demonstrate retries/failure; it must not be described as demonstrated content pollution.

### Independent checks of the other narrowed allegations

| Allegation considered | Counter-evidence and disposition |
|---|---|
| Nonzero-offset 200 is appended | Refuted. Both initial and renewed range responses enter the exact 206 check at `TransferWorker.kt:L130` before body/write at L132-L135. |
| 416 can be treated as successful completion | Refuted. It fails that 206 check. There is no specialized local-length/full-hash response branch, so the exact prescribed response handling remains partial, but no 416 success is possible on this path. Complete local archives rehash all chunks without issuing another range (`L113-L119`) and then check whole SHA (`L147`). Do not add a severity-bearing 416 integrity finding without a concrete consequence. |
| Renewal can switch the successful archive to any object | Refuted against the inspected backend. `services/catalog/app.py:L239-L257` stores the requested manifest-owned object identity. Renewal at L258-L264 checks owner, expiry and stored object/manifest. `download_grant` L293-L295 returns those stored identity fields and only creates a new URL/expiry. Client chunk/full hashes remain bound to the original task. Missing client grant-field comparisons can be an extra validation requirement; they do not prove corruption here. |
| Every 401/403 is proven URL expiry | Not established. Worker L124 renews on either status, including nonexpiry denials; service renewal still rechecks authorization. This is error classification/extra request behavior, not an authorization bypass or changed-object success. |

### Checks performed and limitations

Read Api.kt L1-L155, worker L29-L154, repository grant/enqueue/actions L140-L177, Trust manifest/hash/path helpers L13-L15,L53-L101, task persistence L9-L10, reconciliation L44-L72 and installation/activation/recovery L27-L131. Independently read service create/renew/grant L239-L264,L293-L295 and local object serving L565-L622, rather than accepting the original report's conclusions on function names. The surrounding original PKG-06 work had already read these whole Android enforcement files.

No production origin/CDN behavior, phone, APK execution, process-kill scenario, HTTP fixture, build or test was run. No network, browser or virtualization was used. Only this independent report and JSON were written. Proposed report language is: **“The specified explicit identity request/response constraints are absent. A representation-negotiating origin can cause safe retries/failure; the inspected hashes and pinned Range behavior prevent an established content-corruption claim.”** Formal AC-D04/CON-04 states remain unchanged.

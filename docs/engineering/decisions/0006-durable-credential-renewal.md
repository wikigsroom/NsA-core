# ADR 0006 — Durable anonymous credential renewal

Status: implemented; execution results are recorded separately.

The physical-device walkthrough exposed an unavailable internal enrollment after credentials had been lost. The client previously cleared its vault after any refresh failure, and an asynchronous preference write could leave an older refresh token on disk after process termination. A consumed invitation cannot enroll another installation. No saved game or content directory needs to be deleted to recover access.

The client synchronously commits encrypted credential changes before sending a renewal. It persists a pending idempotency key and reuses the same refresh body/key after a transport failure. Bootstrap likewise retains its exact request body and receipt key, including through a locale change; a different provisioned invitation starts a new bootstrap attempt. Successful responses use the server's actual expiry. A late unauthorized request reuses credentials another request has already renewed.

The service returns the existing contract's `AUTH_EXPIRED` / HTTP 401 for invalid or expired installation and refresh credentials. Missing internal invitation/scope and staff permissions remain HTTP 403 and do not trigger blind rotation. Refresh receipts last 24 hours, matching the baseline idempotency retention; bootstrap receipts last ten minutes. An interrupted request beyond its receipt window may require a fresh invitation. These are access-recovery bounds, not permanent content entitlements.

Connectivity, rate limiting and server failures preserve the pending credential attempt. Only an authoritative `AUTH_EXPIRED` refresh response resets identity. Debug provisioning may generate a new one-time invitation and rebuild the local debug APK; it preserves app data and does not change content scopes or create a public entitlement. Credential and invitation values stay in ignored local files or the encrypted vault and must not enter logs/media/Git.

No endpoint, schema identity or native ABI changes. The service regression scenarios cover receipt replay after a committed rotation, internal scope retention, expired credentials and separate permission denial. Actual player access and download are verified on the physical phone.

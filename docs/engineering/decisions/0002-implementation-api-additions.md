# ADR 0002 — Explicit app-update and role-management operations

Date: 2026-11-06. Status: accepted implementation decision within the authorized scope.

The baseline signs an app-update payload containing a private APK artifact, but does not provide a player operation to obtain its short-lived download URL. Role management is described but also lacks an HTTP operation. Implementing these flows requires four additive operations, described by `contracts/extensions-v1.1.json` and its request schema. The original 43 operations and their identities remain intact. The combined service has 47 operations; `/healthz`, OIDC public metadata, static operations assets and private object transport are infrastructure routes.

The update download grant is authenticated. A publisher must register an APK that already passed size/hash read-back verification before publishing signed metadata. The Android client independently verifies hash, package name, version and the currently installed signing certificate before handing the file to the system installer. The system retains the final install decision. Anonymous callers cannot fetch the update grant.

Only administrators can read or change role assignments. Changes require an expected revision, a reason, an idempotency key, an audit entry and a different subject from the acting administrator. Existing OIDC signature, issuer, audience and expiry checks continue to apply. Public client configuration contains no secrets.

This is an additive contract revision. It is not a claim that the original G0 or device acceptance matrix has passed. SQLite/local-object development mode and PostgreSQL/S3 production mode remain separate providers.

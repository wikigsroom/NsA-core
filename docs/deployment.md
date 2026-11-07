# Deployment and content operations

[简体中文](deployment.zh-CN.md) · [Development](development.md) · [Release procedure](releases/README.md)

v0.4.0 publishes source and desktop authoring tools. No public catalogue is deployed by this release. The following is the implementation's deployment configuration and operating sequence, not a claim that production infrastructure already exists.

## Services and production prerequisites

Run the FastAPI API and verification worker separately. The staff website is served by the API at `/operations/`. Production needs PostgreSQL, private S3-compatible storage, an HTTPS public origin, OIDC and an Ed25519 content-signing key. Native Windows development instead uses SQLite/local objects and isolated development credentials.

Install `services/requirements.txt` into a dedicated environment. Supply secrets through the deployment's secret store or process environment, not tracked files. Run from the repository root with `PYTHONPATH` containing `services` and the repository.

| Variable | Meaning |
|---|---|
| `NSA_MODE=production` | Enforce production provider/endpoint requirements |
| `NSA_DATABASE_URL` | PostgreSQL connection URL |
| `NSA_LOCAL_ROOT` | Private working/key location; default is `services/local` |
| `NSA_SERVICE_SECRET` | At least 32 random bytes represented as the configured secret value |
| `NSA_PUBLIC_URL` | Actual HTTPS API origin; used for scoped object delivery URLs |
| `NSA_SIGNING_KEY`, `NSA_KEY_ID` | Ed25519 private key path and published trust identifier |
| `NSA_S3_BUCKET`, optional `NSA_S3_ENDPOINT` | Private object bucket/provider endpoint |
| Standard AWS credential chain | Credentials for the chosen S3 provider |
| `NSA_OIDC_ISSUER`, `NSA_OIDC_AUDIENCE`, `NSA_OIDC_JWKS_URL` | Staff token signature/issuer/audience validation |
| `NSA_OIDC_CLIENT_ID`, `NSA_OIDC_AUTHORIZATION_ENDPOINT`, `NSA_OIDC_TOKEN_ENDPOINT` | Staff website Authorization Code + PKCE configuration |

Use your actual values; example domains in documentation are placeholders. Configure TLS termination/reverse proxy, storage access, backups, diagnostic retention and process supervision for the chosen host. Never expose local development staff credentials as production login.

## Initialize and run

```powershell
$env:PYTHONPATH = (Join-Path (Get-Location) 'services') + ';' + (Get-Location)
# Set the production variables above through the process/secret configuration.
services/.venv/Scripts/python.exe services/migrate.py
services/.venv/Scripts/python.exe -m uvicorn catalog.app:create_app --factory --host 127.0.0.1 --port 8443
```

Run the worker as a separate supervised process with the same production database/storage configuration:

```powershell
services/.venv/Scripts/python.exe -m worker.main
```

`migrate.py` currently bootstraps schema version 1 and the policy sequence without deleting existing records. It is not an automatic arbitrary-version migration system. Back up and review migrations before changing an existing production schema.

The API is bound to localhost above; an operator-managed HTTPS proxy supplies the public origin. The worker must be able to read stored uploads and renew its ownership lease. Configure the storage provider's actual permissions, multipart lifecycle and diagnostic deletion policy.

## Configure the Android distribution

Freeze package/display identity, version progression, distribution signing custody and the intended catalogue. App signing and content signing are separate keys. The current internal app version is `0.1.0-dev`; repository release `0.4.0` does not relabel that internal binary.

Set `-Pnsa.catalogUrl=https://YOUR_REAL_CATALOGUE` when assembling the operator's release build; supply the intended `trusted-keys.json` public trust document and only approved compiled native slots. Configure Android signing for the operator's protected key and verify the completed APK/certificate before distribution. The current Gradle release build is unsigned unless signing is supplied; do not present its unsigned APK as an installable production asset.

The local debug CA and one-use invitation belong only to debug assets. Production uses system HTTPS trust. Keep the public release free of private game modules/resources, server secrets and player data. A hosted catalogue must match the exact registry/content identities in the distributed app.

## Publishing a game

1. Register game metadata, allowed delivery scope and rights record.
2. Compile the selected runtime/profile from a recorded source revision; register the build, SBOM and actual artifact digests.
3. Package the resource tree with complete file/block indexes and immutable release ID.
4. Obtain scoped upload grants and upload actual bytes. Large objects use 64 MiB multipart parts; complete ordered, unique, contiguous parts and storage read-back.
5. Register the proposed release manifest in the build SBOM as `nsa:release-manifest`. The worker verifies archive/index consistency before a signed release can be issued.
6. Complete independent review with a distinct reviewer, then publish the intended public/internal scope.
7. Register real device evidence for the precise app/content/profile/driver combination. Experimental status and verified status remain distinct.

The supplied publication client implements the local invited route with separate editor/reviewer identities. It preserves idempotency, uploaded parts and operation receipts in a private journal. In production use the configured staff authorization and actual approved records; do not fabricate verified evidence. Blocking/unlisting a release requires an explicit operation and does not delete saves.

## Runtime policy and offline behavior

Policies are signed, scoped and monotonic. Known blocks/revoked keys survive catalogue-cache clearing; lower policy epochs are rejected. Prepared content may operate offline only under its valid policy. A permanently offline phone cannot receive an unseen revocation immediately.

The download grant binds immutable object identity, device/capability evaluation and intended release. Short-lived URLs can be renewed only for the same identity. Feedback diagnostics are explicitly selected, filtered, bounded and subject to retention.

## Operations, backups and updates

| Area | Required operational treatment |
|---|---|
| Database | Back up and verify restore; retain policy/audit/ownership history |
| Object storage | Keep immutable published objects, verify read-back and clean incomplete multipart uploads |
| Signing | Protect private keys, retain app-signing continuity and distribute matching content public trust |
| Staff access | OIDC plus authoritative role assignments, independent reviewer and revision/idempotency checks |
| Monitoring | API/worker health, verification failures, lease expiry, queue/storage pressure and feedback retention |
| Player updates | Separate APK/content releases, backup/migration compatibility and rollback readiness |
| Publication failure | Preserve draft/journal, inspect exact error and resume the same immutable operation where valid |

A first frame is not a healthy/verified label. App update validation requires exact package and signing continuity. Avoid changing already published bytes or moving tags to repair a release; publish a new version when artifacts change.

[Production constraints](engineering/07-build-release-operations.md) · [Contracts](engineering/contracts/README.md) · [Current acceptance](hall/implementation-status.md)

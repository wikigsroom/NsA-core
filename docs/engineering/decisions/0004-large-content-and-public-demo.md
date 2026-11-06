# ADR 0004 — Large content verification and a reproducible public demo

The default local build selects the original MIT 2048 game and has no dependency
on private game inputs. `-WithBotw` explicitly selects the existing local two-game
profile. The native collector removes unselected generated libraries so a later
demo build cannot accidentally carry a previous private runtime. SDK/NDK sources
are explicit installed prerequisites; copied tools and all generated outputs stay
under the task directory. Development checks use one pinned Python environment.

Large artifact read-back and OIDC/database work run in the API thread pool. Rate
windows use a lock; upload writes/fsync yield the ASGI loop. Verification owns a
renewable lease with an unpredictable owner token. Long reads make no database
writes. A fresh transaction checks ownership and the original release revision
before atomically committing its manifest/result. Losing a lease or observing an
administrator's newer release state cannot overwrite that state.

Packaging computes the archive and chunk digests in the same sequential pass.
Publication verification combines its archive digest with its existing chunk
pass, while still checking every expanded file and all other build artifacts.
No digest or path check is removed. These choices bound memory and avoid an
extra full read of multi-gigabyte archives.

The publication CLI saves private, atomic checkpoints before requests, after
uploaded parts and after stage completion. Resuming reuses the same idempotency
keys and immutable object identities. A changed package/profile/metadata requires
a new release ID. A published release returns its existing record; blocked,
unlisted or rejected releases need explicit operator handling. Scoped grants in
these checkpoints are private. Server-side expiry continues to apply.

These are implementation decisions. Passing concurrency or publication checks is
not game-playability, production S3/OIDC, or G0 acceptance evidence.

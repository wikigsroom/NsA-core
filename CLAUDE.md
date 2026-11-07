# Rules for Claude in this repository

The skills in `.claude/skills/` (nx2android-dev, switch-android-port) hold the full guide. These rules hold in every session, with or without a skill.

## Game files go only in `.gamedump/`

- Every port, test, check or bundle trial uses `.gamedump/<App>/` in the main checkout (git-ignored; `<App>` is the APK's name, e.g. `HadesII`), also from a worktree. Never make a game, bundle or player folder elsewhere on the PC, and never port into a session's scratchpad (`%TEMP%\claude\...`): `C:\nx2a-test` once kept 263 GB, and old scratchpads 66 GB of ports and pass dumps, that nobody deleted.
- Debug output too big for a few files (pass dumps, frame shots, screen recordings, a port's output) goes in `.gamedump/<App>/` beside the game, and is deleted with it or when the debugging ends.
- Fill it with hard links to the downloaded NSP / NSZ / XCI files (`ln`, same drive, no copy). Never write into the download folders; porter decompresses NSZ and XCZ itself.
- Delete `.gamedump/<App>/` when the work is done (port committed, test app uninstalled). First check that each dump file has 2 or more links (`fsutil hardlink list <file>`); a file with one link is the only copy, so ask first. The PC has little disk space.
- Before ending a session that made a `.gamedump` folder or wrote more than 1 GB anywhere, say which folders are left and why.

## Worktrees

- Always clean up a worktree after its branch is merged into main: remove the worktree and delete the merged branch (`git branch -d`). A session's own worktree goes when the session is archived; say so before ending the session.
- `tools/` (the NDK, the .NET SDK, platform-tools and `prod.keys`) exists only in the main checkout. It is git-ignored, and porter's config points to it by absolute path, so a worktree needs no copy of it. Never make a junction or link to it inside a worktree.
- A link to `tools/` inside a worktree is the main checkout's real folder: a recursive delete of the worktree goes through it and deletes the real files. On 2026-10-31, removing a worktree in Fork deleted the NDK, the .NET SDK and `prod.keys` through a `tools` junction.
- Before you remove a worktree (with git, Fork or any other tool), list its links (`Get-ChildItem -Force <worktree> -Recurse -Attributes ReparsePoint`) and remove each link alone with `cmd /c rmdir <link>` (no `/s`: that removes only the link). Then remove the worktree.

## Releases

- Destination is `wikigsroom/NsA-core`, branch `main`, as required by AGENTS.md. Every `gh` command explicitly includes `--repo wikigsroom/NsA-core`.
- Version `0.MINOR.PATCH` is in `porter/__init__.py`: fixes/new games only increment patch; a core behavior/feature increments minor. Major stays 0. Repository/tool version, Android app version, ABI and content identities are distinct.
- Finish the complete source/test/docs/media batch and consolidated checks, commit it, then commit `chore(release): version <x.y.z>` and create an annotated `v<x.y.z>` tag. Push main/tag without force. Never move a published tag.
- `.github/workflows/release.yml` validates the tag and calls Windows/macOS CI builds directly. Both artifacts, complete bilingual docs/media, safe public contents, exact VERSION and uploaded read-back hashes must pass before the draft becomes a normal release. A tag/draft alone is not completion. Watch the complete workflow and verify both ZIPs/checksums on the public release.
- Never upload locally built bundle ZIPs by hand. Windows/macOS runners build and upload their artifacts. The workflow uses its own scoped token; release events from that token cannot be relied upon to trigger another workflow. See ADR 0011 and docs/releases/README.md.
- Public scope is source and authoring tools. Keep commercial game programs, private APKs, keys, provisioning and player saves private. Reviewed real game imagery may be published under the user's screenshot request. A formal source/tool version does not promote experimental games or bypass open G0/performance/fault/production gates.
- A release checks the games it adds/fixes; do not rerun unrelated historical games. Keep actual source/APK identities and failures. The former upstream Games table has been removed from the root README; use immutable verification/media records and retained upstream links instead.
- The hook `.claude/hooks/release_guard.py` enforces the explicit destination, version-matching tag and no manual local ZIP attachment. If source/artifact fixes are required after a public version, create a new patch release.

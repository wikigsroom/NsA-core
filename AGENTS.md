# Workspace and repository instructions

These standing instructions were provided by the user on 2026-11-06.

- The destination for all subsequent code updates is `git@github.com:wikigsroom/NsA-core.git`. Commit and push future code updates to that repository.
- `origin` is `wikigsroom/NsA-core`, the default push destination. `upstream` is `https://github.com/shirai91/nx2android.git`, retained for reading and tracking the original project.
- GitHub CLI operations for this project must explicitly use `--repo wikigsroom/NsA-core`. This user-selected destination overrides hardcoded `shirai91/nx2android` destinations in inherited documentation, skills and release instructions.
- Keep new work, downloads, build outputs, caches and diagnostics under `E:\dev\sandbox\游戏移植`. Continue to follow `.gitignore` and the game's `.gamedump/` layout.
- Do not invoke local Docker, WSL, virtual machines or other virtualization environments. Use the USB-connected physical Android device for device checks.
- When using a browser, close every tab that is no longer needed after the operation.

Read `CLAUDE.md` and applicable instructions under `nxnative/` for implementation rules, subject to the user instructions above.

## Game hall product and engineering baseline

- For hall-related development, read [the product baseline](docs/product/README.md), [the detailed requirements](docs/product/产品需求细则.md), [the page and interaction specification](docs/product/页面与交互验收规格.md), and [the engineering baseline](docs/engineering/README.md). These v2.0 documents constrain subsequent implementation; they are not implementation or device acceptance evidence.
- Follow the selected D Obsidian portrait design: non-game flows remain portrait; the actual game runtime boundary is landscape; cancellation, failure and exit restore the source portrait flow. Use the finalized design tokens and page catalogue.
- Preserve requirement, work-item and acceptance identities. When behavior, persistence, HTTP contracts, runtime ABI or design changes, update the affected specification, machine contracts and appropriate verification together; record incompatible decisions and migration plans.
- Follow G0 before broad hall implementation: prove two real games in the same APK with independent runtime processes/data, safe return to the hall, and actual save/reload. A prototype, video, first frame or documentation check does not satisfy this gate.
- Apply the documented engineering defaults without repeatedly seeking permission. Resolve release/content/device/business decisions before their dependent release gates; continue independent authorized work. Do not treat undecided branding or deployment details as a reason to stop all development.

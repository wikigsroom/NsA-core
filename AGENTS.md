# Workspace and repository instructions

These standing instructions were provided by the user on 2026-11-06.

- The destination for all subsequent code updates is `git@github.com:wikigsroom/NsA-core.git`. Commit and push future code updates to that repository.
- `origin` is `wikigsroom/NsA-core`, the default push destination. `upstream` is `https://github.com/shirai91/nx2android.git`, retained for reading and tracking the original project.
- GitHub CLI operations for this project must explicitly use `--repo wikigsroom/NsA-core`. This user-selected destination overrides hardcoded `shirai91/nx2android` destinations in inherited documentation, skills and release instructions.
- Keep new work, downloads, build outputs, caches and diagnostics under `E:\dev\sandbox\游戏移植`. Continue to follow `.gitignore` and the game's `.gamedump/` layout.
- Do not invoke local Docker, WSL, virtual machines or other virtualization environments. Use the USB-connected physical Android device for device checks.
- When using a browser, close every tab that is no longer needed after the operation.

Read `CLAUDE.md` and applicable instructions under `nxnative/` for implementation rules, subject to the user instructions above.

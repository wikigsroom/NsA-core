# Release process / 版本发布流程

[Project](../../README.md) · [中文项目](../../README.zh-CN.md) · [v0.4.0 notes / 版本说明](v0.4.0.md)

The user-selected destination is **`wikigsroom/NsA-core`**, branch **`main`**. Every `gh` operation explicitly selects that repository. Do not push a release to an inherited upstream destination.

## Version and publication

1. Complete the source, bilingual README/manuals, reviewed real gameplay media, required checks and release notes. Preserve failed attempts and exact candidate identities.
2. Increment `porter/__init__.py` as `0.MINOR.PATCH`: fixes/new games only increment patch; a new behavior/feature increments minor. Commit the version with `chore(release): version <version>`.
3. Create an annotated `v<version>` tag at the completed clean source. Push `main` and the tag, preserving remote changes and existing tags.
4. `.github/workflows/release.yml` validates the existing tag/version/notes, then invokes the reusable Windows and macOS release workflows.
5. Both operating systems build their own desktop bundles from the exact tag and upload CI artifacts. The documentation packer includes both READMEs, all tracked docs, reviewed media and licenses; it does not copy private working directories.
6. The publication job requires both bundles, scans paths/content exclusions, validates archive CRC/version and creates `SHA256SUMS.txt` plus `release-manifest.json`.
7. CI uploads to a draft, downloads all its assets again, verifies bytes and contents, then changes the completed draft to a normal published release. It does not set the prerelease flag.
8. Check actual job completion, both downloadable bundles and their checksums. A source tag or an incomplete draft is not a completed release.

The first v0.4.0 release contains public source and desktop authoring tools. It does not publish the private two-game Android APK, commercial game programs, signing credentials or player saves. The internal Android app still reports its own `0.1.0-dev` version. A hosted public catalogue and production Android distribution have their own deployment/signing/content gates.

## Automation detail

The tag-triggered workflow calls Windows/macOS builds directly. A release created with `GITHUB_TOKEN` does not automatically trigger a second release-event workflow, so the pipeline must not depend on that event chain. See [GitHub's workflow-trigger rules](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow). Existing standalone platform workflows also remain manually runnable.

A manual retry takes an existing annotated tag. If publication already exists, inspect it before replacing anything; the workflow fails closed rather than overwriting a public release. If artifact bytes/source need correction, issue a new patch version and tag. Do not move a published tag or upload locally assembled bundle ZIPs by hand.

The release token is the workflow's repository-scoped `GITHUB_TOKEN`. Local publishing credentials are not copied into the repository. The successful SSH push triggers the authorized job even if an unrelated saved local GitHub API login is unavailable.

## 中文说明

先完成完整源码、双语文档、实机游戏素材和集中检查，再递增版本、提交、创建注释标签并推送 `main` 与标签。发布工作流先验证，再分别构建 Windows/macOS，检查两个压缩包和许可/文档/游戏素材，生成摘要，上传草稿并回读确认，最后公开为普通正式 Release。

公开范围是源码和桌面制作工具，内部双游戏 APK 和商业游戏程序继续本机保留。正式 GitHub 版本不等于公共游戏服务已上线，也不会自动把实验游戏标记为已验证。失败、中断、开放验收及准确 APK 身份必须保留。

维护者检查 [Actions](https://github.com/wikigsroom/NsA-core/actions) 和 [Releases](https://github.com/wikigsroom/NsA-core/releases) 的实际结果。已有正式版本不得覆盖；修正产物使用新补丁版本，不移动已发布标签。

# ADR 0011: public source, authoring tools and reviewed gameplay media

Status: implemented; publication requires the verified tag pipeline. Date: 2026-11-07.

The user requested complete replacement of the old root README contents, systematic bilingual documentation, real-device gameplay screenshots/GIFs and a formal release to `wikigsroom/NsA-core`.

## Distribution boundary

Version 0.4.0 fixes source and native desktop authoring tools. The internal Android APK includes a compiled commercial game module and local development provisioning. It remains private. A public hosted catalogue and an operator-signed, approved-content APK require deployment/signing/content configuration and are not fabricated to satisfy the release request. A formal GitHub version does not change experimental gameplay labels or open G0/performance/fault gates.

The repository release version, Android app version, runtime ABI, Room schema and immutable content identities remain separate. Existing actual native bytes retain `d427f08`; the current Android candidate retains `5c1bff0` / `3bd6c8…3451d`. Documentation or a new tag must not relabel those binaries as newly compiled.

## Documentation and captures

Both root README bodies are replaced. Bilingual indexes, player guides, development and deployment guides describe the same implementation; release notes document downloadable assets, setup, upgrades, validation and limits. Upstream attribution and licenses remain, while the former standalone game table is removed from the root entry point. Standalone references point to the upstream workflow or relevant retained technical documents.

Reviewed real USB game imagery may be exported in response to the user's explicit screenshot requirement. Personal saves, keys, game programs, internal APKs and raw diagnostics remain ignored. Every exported media record retains actual source/APK hashes, dimensions, encoded duration and transformations. New records include the source path relative to the repository. Historical captures keep their original identities. A source path is provenance, not permission to upload its private file.

## Automated release

Use the existing Windows/macOS release workflows as reusable builds, invoked directly from a tag-driven orchestrator. Validate the clean annotated tag against `porter/__init__.py` and complete bilingual notes. Each platform packages all tracked public documentation/media, rather than copying a private working directory. Both platform artifacts are required before publication.

Inspect required README/gameplay files, archive paths, exclusions, CRC and exact clean VERSION; create checksums and a manifest. Upload to a draft, download the assets again, verify actual bytes/contents and only then publish a normal release. Keep failure drafts unpublished; do not overwrite an existing public release or move its tag. The workflow uses its scoped `GITHUB_TOKEN` and explicit destination. A token-created release does not trigger another release-event chain, as documented in [GitHub's trigger rules](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow).

中文：本次正式公开源码、桌面制作工具、双语文档及经过检查的实机游戏画面。内部双游戏 APK、商业游戏程序和凭据不公开。先完成文档/素材和集中验证，再固定版本注释标签；双平台自动构建、检查、草稿上传和回读通过后才公开。开放验收和原失败保持真实。

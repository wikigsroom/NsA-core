# Download/install evidence / 下载与安装核对证据

Reviewed source: `189169fded9cd6557b3cb146b56d8b2e5243d705`, 2026-11-07. [Source snapshot](https://github.com/wikigsroom/NsA-core/tree/189169fded9cd6557b3cb146b56d8b2e5243d705).

[Final findings and limits / 最终汇总](../../transfer-install-review-2026-11-07.md) · [Structured dispositions / 机器记录](../../verification/2026-11-07-transfer-install-review.json) · [Actual execution / 真实执行](../../implementation-status.md)

This collection contains three independent requirement reviews and six separate refutations. Each requirement's code and specification were re-read by authors who did not produce its initial review. It covers DL-06, DL-07 and PKG-06, twelve detailed rules. Seven bounded defect cores survive both refutations; no reproducer was dynamically executed. No application source change, application test, build, phone query/input or new media occurred in this documentation round.

本集合包含三份独立需求核对和六份独立反证，共十八个 Markdown/JSON 证据文件。三项需求的十二条细则分别核对代码与规范，原作者不代替独立反证。最终保留七项限定影响的缺陷核心，全部仍为源码推导；未执行故障复现、程序测试、构建或手机操作，未产生新素材。

| Requirement | Original review / 初核 | Independent code refutation / 代码反证 | Independent specification refutation / 规范反证 |
|---|---|---|---|
| DL-06 · Range and object identity | [Markdown](requirements/DL-06.md) · [JSON](requirements/DL-06.json) | [Markdown](refutations/DL-06-code.md) · [JSON](refutations/DL-06-code.json) | [Markdown](refutations/DL-06-spec.md) · [JSON](refutations/DL-06-spec.json) |
| DL-07 · Queue and task ownership | [Markdown](requirements/DL-07.md) · [JSON](requirements/DL-07.json) | [Markdown](refutations/DL-07-code.md) · [JSON](refutations/DL-07-code.json) | [Markdown](refutations/DL-07-spec.md) · [JSON](refutations/DL-07-spec.json) |
| PKG-06 · Install publication/recovery | [Markdown](requirements/PKG-06.md) · [JSON](requirements/PKG-06.json) | [Markdown](refutations/PKG-06-code.md) · [JSON](refutations/PKG-06-code.json) | [Markdown](refutations/PKG-06-spec.md) · [JSON](refutations/PKG-06-spec.json) |

Read the final dispositions first. Original candidate/pending labels remain historical; published copies normalize presentation paths and add provenance metadata. Each JSON identifies the original record SHA-256 and reviewed commit. The structured summary binds all eighteen published artifacts by SHA-256. Code/specification line references refer to the pinned snapshot, including when later documentation adds paragraphs. The original private report files remain unchanged.

先读最终汇总。初核标签保留原状，不把初核视为最终裁定。公开副本仅规范化路径、尾部空格并附加出处；机器记录绑定十八个公开文件摘要，JSON 同时记录原始摘要。行号属于固定提交，不能拿后续插入段落后的当前行号反推初核引用错误。

DL-06-D01 is retained as explicit identity-protocol/conditional-availability incompleteness. OkHttp's Range gzip gating, signed chunk/full hashes and safe non-206 rejection refute stronger corruption claims. The specialized 416 branch remains a narrower protocol observation. Backend create/renew identity binding refutes arbitrary-object mixing against the inspected service.

DL-07-F02 proves lost cancellation and same-game active-record duplication under .01/CON-07, not .02 same-file parallel writers. The unique WorkManager work and Processor deduplication provide meaningful framework ownership; a custom application lease is not inherently required. Complete stop/rebuild handoff remains undecidable without additional evidence. F03 preserves deterministic task-ID ordering and effective-order explanation; it does not prohibit all newest-first history or require one particular numbered UI.

PKG-06-F01 is conditional on power-loss/filesystem persistence, not a demonstrated target-device loss after ordinary process death. F02 gains a stronger process-exit-only same-release repair trigger in the independent code record. F03 concerns losing the eventual cleanup obligation, not requiring deletion at the exact DB activation instant. Conservative retention refutes early previous-release GC; incomplete health/rollback is separate scope.

P1/P2/P3 label severity, not the baseline's P0/P1 roadmap scope. Every runtime acceptance remains open. The other 87 requirements were not individually audited in this source snapshot; the earlier three-requirement review is a separate historical collection and is not silently merged into current acceptance.

缺陷严重度不改变 P0 要求。所有实际验收继续开放；本快照的其余 87 项未逐条审查，先前不同快照的三项核对和修正另外保留，不能合并声称当前全部满足。后续先完成整批修正及必要用例，再按用户约束集中验证；保留另一项目的手机测试。

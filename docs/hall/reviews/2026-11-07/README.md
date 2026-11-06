# Source-review evidence / 源码核对证据

Source snapshot: `cdedc7179ec1842592243a052525e021d8cf70f1`, 2026-11-07.

[Consolidated findings / 汇总结论](../../source-review.md) · [Structured final dispositions / 最终机器记录](../../verification/2026-11-07-source-review.json) · [Actual execution / 真实执行结果](../../implementation-status.md)

This collection contains three independent requirement reviews and six separate refutations: one follows the code again and one re-reads the authoritative specification for each requirement. It covers APP-05, APP-08 and SAVE-04, twelve detailed rules. The other 87 requirements were not individually checked here. No application source was changed and no build, application test or device operation was performed for this review.

本集合包含三份独立需求核对和六份独立反证：每项需求分别重新追踪代码、重新判断规范。只覆盖三个需求的十二条细则；其余 87 项未在这里逐条检查。未修改程序、构建应用、执行程序测试或操作设备。

| Requirement | Original candidates / 初核候选 | Independent code review / 代码反证 | Independent specification review / 规范反证 |
|---|---|---|---|
| APP-05 · offline/policy | [Markdown](requirements/APP-05.md) · [JSON](requirements/APP-05.json) | [Markdown](refutations/APP-05-code.md) · [JSON](refutations/APP-05-code.json) | [Markdown](refutations/APP-05-spec.md) · [JSON](refutations/APP-05-spec.json) |
| APP-08 · reconstruction/state | [Markdown](requirements/APP-08.md) · [JSON](requirements/APP-08.json) | [Markdown](refutations/APP-08-code.md) · [JSON](refutations/APP-08-code.json) | [Markdown](refutations/APP-08-spec.md) · [JSON](refutations/APP-08-spec.json) |
| SAVE-04 · save exchange | [Markdown](requirements/SAVE-04.md) · [JSON](requirements/SAVE-04.json) | [Markdown](refutations/SAVE-04-code.md) · [JSON](refutations/SAVE-04-code.json) | [Markdown](refutations/SAVE-04-spec.md) · [JSON](refutations/SAVE-04-spec.json) |

Read the final dispositions first. Original reports preserve their candidate/pending labels as historical evidence; they are not final adjudications. Each published JSON copy includes its original record hash, stage and normalization note; the final structured record binds the 18 published artifacts by SHA-256. Checkout paths are normalized, and file references in evidence remain relative to the reviewed repository root. Line references describe the pinned source snapshot, even if later documentation moves a paragraph.

请先读最终汇总。初核报告保留候选和待复核标签，不能把初核结论直接当作最终确认。JSON 副本记录原始摘要、阶段和路径规范化说明，最终记录绑定十八个公开证据文件的摘要。工作区绝对路径已改为相对形式；代码/规范行号对应上述固定提交。

APP-05-D03 is **unverified** after the appended independent code clarification. The original install-ready argument does not prove an unconditional offline-ready promise. The initial trusted-policy gate must remain unless the product/security contract is explicitly changed. APP-08-F01 is narrowed to committed query/filter consistency rather than an immutable old result set. SAVE-04-F7 requires a verified import index/provenance update; it does not authorize fabricated native save events. SAVE-04-F2 and APP-08-F03 share one surviving-process defect.

APP-05-D03 经追加复核后未获确认，不计入确认缺陷；不能直接取消首次可信策略检查。APP-08-F01 仅要求查询/筛选语义一致，不强求旧结果不变。SAVE-04-F7 要求导入登记与来源一致，不伪造原生提交。SAVE-04-F2 与 APP-08-F03 按同一根因去重。

The native player menu's scope is resolved in the SAVE-04 code/specification reviews. Unified player-facing import/export must follow the hall contract; deferred import applies on a later launch. Standalone and internal private snapshots are separate scopes. A future dual-mount runtime's current data loss remains unverified.

`implemented` in an individual rule is a source-inspection verdict. It is not a physical-device pass, production acceptance or proof that every failure boundary was exercised. Corrective work and consolidated fault/lifecycle testing remain open under the [next complete batch](../../source-review.md).

# NsA documentation

[简体中文](README.zh-CN.md) · [Project home](../README.md) · [Release notes](releases/v0.4.0.md)

This documentation describes the unified Android game hall and its native content-production pipeline. Player behavior, development setup, deployment, design and actual verification have separate entry points.

| Reader | Read first | Continue with |
|---|---|---|
| Player or reviewer | [Player guide](hall/README.md) | [Screenshots and gameplay](media/README.md), [known limits](hall/implementation-status.md) |
| Android/native developer | [Development](development.md) | [Runtime constraints](engineering/03-runtime-integration.md), [architecture](engineering/01-architecture.md) |
| Service developer | [Development](development.md) | [API contracts](engineering/contracts/README.md), [service architecture](engineering/06-services-api.md) |
| Operator/release maintainer | [Deployment](deployment.md) | [v0.4.0 notes](releases/v0.4.0.md), [release process](releases/README.md) |
| Product/design | [Product baseline](product/README.md) | [Detailed requirements](product/产品需求细则.md), [finalized design](design/obsidian-portrait/README.md) |
| Tester | [Device procedure](hall/device-validation.md) | [Requirement map](hall/requirement-coverage.md), [candidate results](hall/session-recovery-round-2026-11-07.md) |
| Standalone port author | [Desktop installation](../packaging/INSTALL.md) | [Original engine architecture](ARCHITECTURE.md), [porter development](DEVELOPMENT_GUIDE.md) |

English and Chinese operating guides describe the same implementation. Normative product/design/engineering documents are currently primarily Chinese. Dates and human-facing execution records use Asia/Taipei; JSON timestamps explicitly marked UTC remain UTC.

The source tree is authoritative for implementation. Specifications define required behavior; execution records identify what was actually tested. A design screenshot, source map, first game frame or software release does not establish complete gameplay acceptance.

## Document families

- `hall/`: player behavior, measured storage, acceptance procedure and immutable historical execution records.
- `development*.md`: reproducible native Windows setup, actual commands, source layout and extension rules.
- `deployment*.md`: production providers, configuration, content/signing pipeline and operational duties.
- `media/`: reviewed real-device and staff-site captures, with per-file provenance.
- `releases/`: bilingual version notes and automated publication procedure.
- `product/`: stable requirement identities, detailed rules, acceptance cases and offline handbook.
- `engineering/`: implementation constraints, schemas, OpenAPI, state machines and ADRs.
- `design/obsidian-portrait/`: accepted dark portrait design and prototype, clearly identified as design material.

[English project README](../README.md) · [中文项目说明](../README.zh-CN.md)

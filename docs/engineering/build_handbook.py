"""Build one offline review handbook from the normative Markdown sources.

Uses the task-local Markdown dependency; no network or browser is required.
Markdown/JSON sources remain authoritative. Output is a review document.
"""
from __future__ import annotations

import argparse
import html
import os
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote, unquote, urlsplit

REPO = Path(__file__).resolve().parents[2]
DOCS = REPO / "docs"
OUTPUT = DOCS / "product/产品与技术开发基线.html"
SOURCES = [
    ("prd", "01 · 产品需求总纲", DOCS / "product/游戏大厅二次开发需求说明书.md"),
    ("rules", "02 · 90 项需求 / 360 条细则", DOCS / "product/产品需求细则.md"),
    ("screens", "03 · 32 页与补充交互", DOCS / "product/页面与交互验收规格.md"),
    ("plan", "04 · 计划与 78 项验收", DOCS / "product/开发计划与验收矩阵.md"),
    ("tech", "05 · 技术方案总纲", DOCS / "product/技术方案与接口约定.md"),
    *[(f"eng{i:02}", f"技术 {i:02} · {title}", DOCS / f"engineering/{name}.md") for i, title, name in [
        (1, "总体架构", "01-architecture"), (2, "Android 客户端", "02-android-client"),
        (3, "原生运行接入", "03-runtime-integration"), (4, "传输、安装和存档", "04-transfer-install-save"),
        (5, "数据与状态", "05-data-state"), (6, "服务与 API", "06-services-api"),
        (7, "制作、发行和运行保障", "07-build-release-operations"), (8, "质量与开发治理", "08-quality-governance")]],
    ("contracts", "附录 · 机器契约与验证", DOCS / "engineering/contracts/README.md"),
]
PREFIXES = {path.resolve(): "doc-" + key for key, _, path in SOURCES}


def local_url(path, query="", fragment=""):
    value = quote(Path(os.path.relpath(path, OUTPUT.parent)).as_posix(), safe="/.-_")
    return value + ("?" + query if query else "") + ("#" + quote(fragment, safe="-_") if fragment else "")


class Rewriter(HTMLParser):
    def __init__(self, source, prefix):
        super().__init__(convert_charrefs=False)
        self.source, self.prefix, self.output = source, prefix, []
        self.ids = set()

    def rewrite_url(self, value):
        url = urlsplit(value)
        if url.scheme or url.netloc:
            return value
        target = (self.source.parent / unquote(url.path)).resolve() if url.path else self.source.resolve()
        if target in PREFIXES and not url.query:
            return "#" + PREFIXES[target] + ("-" + unquote(url.fragment) if url.fragment else "")
        return local_url(target, url.query, unquote(url.fragment))

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self.output.append('<div class="table-scroll" tabindex="0" aria-label="可横向滚动的规范表格">')
        result = []
        for key, value in attrs:
            if key == "id":
                value = self.prefix + "-" + value
                base, suffix = value, 2
                while value in self.ids:
                    value = base + "-" + str(suffix)
                    suffix += 1
                self.ids.add(value)
            elif key in ("href", "src") and value:
                value = self.rewrite_url(value)
            result.append(key if value is None else key + '="' + html.escape(value, quote=True) + '"')
        self.output.append("<" + tag + (" " + " ".join(result) if result else "") + ">")

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def handle_endtag(self, tag):
        self.output.append("</" + tag + ">")
        if tag == "table":
            self.output.append("</div>")

    def handle_data(self, value):
        self.output.append(value)

    def handle_entityref(self, name):
        self.output.append("&" + name + ";")

    def handle_charref(self, name):
        self.output.append("&#" + name + ";")

    def handle_comment(self, value):
        self.output.append("<!--" + value + "-->")


STYLE = """
:root{color-scheme:dark;--bg:#101317;--surface:#1b2128;--accent:#81d8df;--text:#edf2f5;--muted:#a9b4bf;--line:#333e49}
*{box-sizing:border-box}html{scroll-padding-top:28px}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.85 'Segoe UI','Microsoft YaHei',sans-serif}
a{color:var(--accent);text-underline-offset:4px}a:focus-visible,summary:focus-visible,.table-scroll:focus-visible{outline:2px solid var(--accent);outline-offset:4px}
.skip{position:absolute;left:-9999px}.skip:focus{left:20px;top:10px;background:var(--surface);padding:12px;z-index:5}
aside{position:fixed;inset:0 auto 0 0;width:276px;padding:32px 22px;overflow:auto;border-right:1px solid var(--line);background:#13181e}
.brand{font-size:26px;letter-spacing:.03em;font-weight:700}.brand span{color:var(--accent)}aside p{font-size:13px;color:var(--muted)}nav a{display:block;padding:9px 8px;border-radius:7px;text-decoration:none;font-size:13px;line-height:1.6}nav a:hover{background:var(--surface)}nav strong{display:block;margin:22px 8px 8px;font-size:12px;color:var(--muted)}
main{margin-left:276px;padding:52px 5vw 90px;max-width:1700px}.eyebrow{color:var(--accent);font-size:13px;letter-spacing:.12em}.cover h1{font-size:clamp(32px,4vw,56px);line-height:1.25;margin:14px 0 18px}.lead{font-size:18px;max-width:890px}.muted{color:var(--muted)}.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:26px 0}.metric{padding:20px;background:var(--surface);border:1px solid var(--line);border-radius:12px}.metric b{display:block;color:var(--accent);font-size:29px}.metric span{font-size:13px;color:var(--muted)}
.notice{border-left:3px solid var(--accent);padding:13px 20px;background:var(--surface);margin:24px 0}.design{display:flex;gap:24px;align-items:center;padding:22px;background:var(--surface);border-radius:12px}.design img{width:110px;height:auto;display:block;border-radius:8px}.design p{margin:8px 0}.design a{flex:none}
article{margin-top:76px;padding-top:20px;border-top:1px solid var(--line);min-width:0}article h1{font-size:30px;line-height:1.4}h2{font-size:23px;margin:42px 0 14px}h3{font-size:19px;margin-top:32px}h1,h2,h3,h4{scroll-margin-top:25px}p,li,td,th{overflow-wrap:anywhere}.source{font-size:13px;color:var(--muted);margin-bottom:25px}
.table-scroll{max-width:100%;overflow:auto;border:1px solid var(--line);border-radius:9px;margin:20px 0}table{border-collapse:collapse;min-width:680px;width:100%;font-size:14px}td,th{padding:13px 15px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}th{background:#232c35;color:#e9f6f7;font-weight:600}tr:last-child td{border-bottom:0}td:first-child{min-width:96px}code{font-family:Consolas,monospace;font-size:.88em;background:#222a33;padding:2px 5px;border-radius:4px}pre{max-width:100%;overflow:auto;background:#171e25;padding:20px;border:1px solid var(--line);border-radius:8px;font-size:13px}pre code{background:none;padding:0;white-space:pre}blockquote{margin-left:0;border-left:3px solid var(--line);padding-left:20px;color:var(--muted)}.backtop{display:inline-block;margin-top:26px;font-size:13px}
@media(max-width:950px){aside{position:static;width:auto;padding:22px;border-bottom:1px solid var(--line);border-right:0}aside nav{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));max-height:200px;overflow:auto}nav strong{grid-column:1/-1;margin-top:12px}main{margin-left:0;padding:30px 22px 70px}.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}article{margin-top:50px}.design{gap:16px;padding:16px}.design img{width:76px}}
@media print{aside,.backtop,.skip{display:none}main{margin:0;padding:0}article{break-before:page}.table-scroll{overflow:visible}table{min-width:0}a{color:inherit}pre{white-space:pre-wrap}}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deps", type=Path)
    args = parser.parse_args()
    if args.deps:
        sys.path.insert(0, str(args.deps.resolve()))
    import markdown

    navigation, articles = [], []
    for key, title, path in SOURCES:
        prefix = PREFIXES[path.resolve()]
        navigation.append('<a href="#' + prefix + '">' + html.escape(title) + '</a>')
        parser = Rewriter(path, prefix)
        parser.feed(markdown.markdown(path.read_text("utf-8"), extensions=["tables", "fenced_code", "toc", "sane_lists"]))
        articles.append('<article id="' + prefix + '"><p class="source">规范源文件 · <a href="' + local_url(path) + '">' + html.escape(path.name) + '</a></p>' + ''.join(parser.output) + '<a class="backtop" href="#top">返回目录 ↑</a></article>')
    design = DOCS / "design/hall-options/single-home-round-2/images/D-obsidian-portrait.png"
    review = DOCS / "engineering/data/baseline-review.json"
    document = '''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>NsA · 产品与技术开发基线 v2.0</title><style>''' + STYLE + '''</style></head><body id="top"><a class="skip" href="#content">跳到正文</a><aside><div class="brand">NsA <span>CORE</span></div><p>产品与技术开发约束<br>v2.0 · 2026.11.06</p><nav><strong>完整阅读目录</strong>''' + ''.join(navigation) + '''</nav><p>离线文档 · 无外部脚本或字体<br>可使用浏览器查找定位需求编号</p></aside><main id="content"><section class="cover"><div class="eyebrow">PRODUCT × ENGINEERING BASELINE</div><h1>从找到游戏<br>到在同一个 App 中游玩。</h1><p class="lead">面向 Android 的游戏发现、下载、校验安装、运行、存档与管理产品。以已定稿的 D · 曜黑极简为设计基准，建立后续开发共同遵守的需求和技术约束。</p><div class="metrics"><div class="metric"><b>90 / 360</b><span>需求 / 实施细则</span></div><div class="metric"><b>32 + 10</b><span>页面 / 补充交互组</span></div><div class="metric"><b>8</b><span>完整技术章节</span></div><div class="metric"><b>58 + 20</b><span>原验收 / 补充案例</span></div></div><div class="notice">非游戏默认竖屏，仅实际游戏运行边界横屏。本文是开发基线；统一 App、服务与正式可玩验证仍需实现和实测。当前 BotW 为实验运行。</div><div class="design"><a href="''' + local_url(design) + '''"><img src="''' + local_url(design) + '''" alt="用户已选择的 D 曜黑极简竖屏首页参考"></a><div><strong>设计已定稿 · D 曜黑极简</strong><p class="muted">沿用暗色、冰青重点、清晰卡片层级及四入口导航。具体视觉以设计源和 tokens 为准。</p><p><a href="''' + local_url(DOCS / "design/obsidian-portrait/gallery.html") + '''">打开完整设计图册</a> · <a href="''' + local_url(review) + '''">查看本次文档校验</a></p></div></div><p class="muted">Markdown / JSON / C ABI 为规范源，本手册为生成的阅读副本。工程默认参数可执行并经变更记录调整；正式内容、设备、品牌、渠道和预算按阶段确定。</p></section>''' + ''.join(articles) + '''</main></body></html>'''
    OUTPUT.write_text(document, "utf-8", newline="\n")
    print(f"Generated {OUTPUT.relative_to(REPO)} from {len(SOURCES)} sources ({len(document.encode('utf-8')):,} bytes)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Subset the bundled Sarasa Mono SC regular + bold faces.

The full faces are ~25 MB each; the Bible corpus + UI only needs ~2 MB per
weight.  Re-run this after changing the corpus or adding UI copy, otherwise new
glyphs fall back to the system font and break the monospace look.
"""
from __future__ import annotations

import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
SOURCES = [
    (
        Path("/tmp/sarasa/sarasa-mono-sc-regular.ttf"),
        ROOT / "app" / "src" / "main" / "res" / "font" / "sarasa_mono_sc_regular.ttf",
    ),
    (
        Path("/tmp/sarasa/sarasa-mono-sc-bold.ttf"),
        ROOT / "app" / "src" / "main" / "res" / "font" / "sarasa_mono_sc_bold.ttf",
    ),
]

# Copy used by the Compose UI.  Keep this in sync with the Kotlin strings.
UI_TEXT = """
圣经 BIBLE 旧约 新约 卷 章 节 页 共 第 本 部 篇
继续阅读 从头开始 开始阅读 选择章节 切换书卷 选择书卷
目录 阅读设置 设置 返回 关闭 取消 确定 重试 载入中 载入失败
字号 字距 行距 主题 浅色 深色 跟随系统 自动 手动 字体 预览
译本 牧灵 思高 英文 版本 书卷 章节 位置 进度
上一章 下一章 上一页 下一页 已读完 暂无 内容 加载失败
当前 默认 保存 恢复 应用 更多 书卷列表 全部
零一二三四五六七八九十百
0123456789:.,;!?()[]{}<>/\\|-–—…·•“”‘’「」『』《》〈〉【】〔〕、。，；：！？％＋
←→↑↓×✓✕◀▶▸▾◆◇●○■□▍│
⁰¹²³⁴⁵⁶⁷⁸⁹
Aa Bb Cc 中文 英文 等宽 字体 更纱黑体
""" + " ".join(chr(c) for c in range(0x20, 0x7F))


def subset_face(src: Path, out: Path, codepoints: set[int]) -> int:
    if not src.is_file():
        print(f"missing source font: {src}", file=sys.stderr)
        return 1
    font = TTFont(src)
    cmap = font.getBestCmap()
    missing = sorted((chr(cp) for cp in codepoints if cp not in cmap), key=ord)
    if missing:
        print(f"{src.name} is missing:", "".join(missing))

    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.layout_features = ["*"]
    options.glyph_names = False
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.legacy_cmap = True
    options.symbol_cmap = True
    options.hinting = True
    options.drop_tables += ["DSIG"]

    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)
    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(out)

    size = out.stat().st_size
    print(f"wrote {out.relative_to(ROOT)} ({size / 1024 / 1024:.2f} MiB)")

    check = TTFont(out).getBestCmap()
    still_missing = [chr(cp) for cp in codepoints if cp not in check]
    if still_missing:
        print("still missing after subset:", "".join(sorted(still_missing)))
        return 2
    return 0


def main() -> int:
    charset = (ROOT / "tools" / "font_charset.txt").read_text(encoding="utf-8")
    chars = set(charset) | set(UI_TEXT)
    chars = {c for c in chars if c.strip()}
    chars.update(" ")
    codepoints = {ord(c) for c in chars}
    print(f"chars requested: {len(chars)}")

    result = 0
    for src, out in SOURCES:
        result = max(result, subset_face(src, out, codepoints))
    return result


if __name__ == "__main__":
    sys.exit(main())

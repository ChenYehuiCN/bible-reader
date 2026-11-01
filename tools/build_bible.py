#!/usr/bin/env python3
"""Convert the three XML Bible sources into compact JSON assets used by the app.

The XML files keep commentary in the `tips` attribute of <text> elements; the
element body itself is only the footnote reference number.  We therefore drop
the body of every <text> element and keep its tail.  This also removes the
stray verse/paragraph numbers that are sprinkled through the print editions.

Outputs:
  app/src/main/assets/bible/manifest.json
  app/src/main/assets/bible/<version>/<book>.json
  tools/font_charset.txt       (characters the bundled font must cover)
"""
from __future__ import annotations

import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "bible"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "bible"

VERSIONS = [
    # id, display title, short label, language, UI order
    ("muling", "牧灵圣经", "牧灵", "zh"),
    ("sigao", "思高圣经", "思高", "zh"),
    ("ccb", "CCB 英文", "CCB", "en"),
]

# Catholic canon: 46 OT books, then 27 NT books.
OT_BOOKS = 46
TOTAL_BOOKS = 73

WS_RE = re.compile(r"[\s\u00a0\u200b\ufeff]+")
TRAILING_HEADING_NUMBER_RE = re.compile(r"(?<=[\u3400-\u9fff])\d{1,3}\s*$")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def extract_text(element: ET.Element, language: str) -> str:
    """Element text with <text> footnote bodies omitted, tails preserved.

    CCB uses <br/> for poetry and print line breaks; dropping it would glue
    "one" and "who" together.  Chinese doesn't need those spaces.
    """
    parts: list[str] = []
    if element.text:
        parts.append(element.text)
    for child in element:
        tag = local_name(child.tag)
        if tag == "text":
            # Body is only a footnote reference number; drop it, but keep a
            # word separator if the marker sat between two words.
            if language == "en" and child.tail:
                previous = parts[-1][-1:] if parts and parts[-1] else ""
                following = child.tail[:1]
                if previous.isalnum() and following.isalnum():
                    parts.append(" ")
        elif tag == "br":
            if language == "en":
                parts.append(" ")
        else:
            parts.append(extract_text(child, language))
        if child.tail:
            parts.append(child.tail)
    return "".join(parts)


def normalize(text: str) -> str:
    text = text.replace("\u00a0", " ").replace("\u200b", "").replace("\ufeff", "")
    text = WS_RE.sub(" ", text).strip()
    return text


def clean_english(text: str) -> str:
    """The CCB source often loses spaces at inline markup boundaries."""
    text = re.sub(r"([,;:])(?=[A-Za-z“\"‘])", r"\1 ", text)
    text = re.sub(r"([.!?])(?=[A-Z“\"‘])", r"\1 ", text)
    text = re.sub(r"([.!?][”\"’])(?=[A-Z])", r"\1 ", text)
    # A handful of print-edition joins survive without punctuation
    # (TubalCain, inJerusalem, ...).  There are no legitimate camelCase
    # words in this corpus, so this is safe here.
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def clean_heading(text: str) -> str:
    text = normalize(text)
    # Footnote markers sometimes sit outside a <text> node in headings.
    text = TRAILING_HEADING_NUMBER_RE.sub("", text)
    text = text.strip(" *")
    return text.strip()


def clean_verse(text: str, language: str) -> str:
    text = normalize(text)
    if language == "en":
        text = clean_english(text)
    # A lone asterisk marks an omitted verse in some editions.
    if not text or not re.search(r"[0-9A-Za-z\u3400-\u9fff]", text):
        return ""
    if set(text) <= set("* .·"):
        return ""
    text = text.strip(" *")
    return text.strip()


def chapter_blocks(chapter: ET.Element, language: str) -> list[list]:
    blocks: list[list] = []
    pending_break = False
    last_was_text = False
    for child in chapter:
        tag = local_name(child.tag)
        if tag == "br":
            # Muling places footnotes as direct <text> children followed by a
            # <br/>. That break belongs to the footnote block, not to the
            # scripture paragraph (sigao puts the note inside the <p>).
            if blocks and not last_was_text:
                pending_break = True
            last_was_text = False
        elif tag == "text":
            last_was_text = True
        elif tag in ("h3", "h4"):
            pending_break = False
            last_was_text = False
            heading = clean_heading(extract_text(child, language))
            if heading:
                blocks.append([tag, heading])
        elif tag == "p":
            last_was_text = False
            value = child.get("value")
            if value is None:
                continue
            try:
                number = int(value)
            except ValueError:
                continue
            text = clean_verse(extract_text(child, language), language)
            if text:
                if pending_break and blocks and blocks[-1][0] == "v":
                    blocks.append(["br"])
                pending_break = False
                blocks.append(["v", number, text])
    return blocks


def load_index(version: str) -> dict:
    with (SOURCE / version / "index.json").open(encoding="utf-8") as fh:
        return json.load(fh)


def build_version(version: str, language: str) -> tuple[list[dict], dict]:
    index = load_index(version)
    templates = index.get("templates", [])
    if len(templates) != TOTAL_BOOKS:
        raise SystemExit(f"{version}: expected {TOTAL_BOOKS} books, got {len(templates)}")

    out_dir = ASSETS / version
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.json"):
        old.unlink()

    books: list[dict] = []
    stats = {"chapters": 0, "verses": 0, "headings": 0, "empty_verses": 0}

    for pos, template in enumerate(templates, start=1):
        book_id = int(template["key"])
        if book_id != pos:
            raise SystemExit(f"{version}: non-sequential book key {book_id} at {pos}")
        xml_path = SOURCE / version / f"{book_id}.xml"
        root = ET.parse(xml_path).getroot()

        chapters = []
        for chapter in root:
            if local_name(chapter.tag) != "chapter":
                continue
            value = chapter.get("value")
            if value is None:
                continue
            number = int(value)
            if number == 0:
                continue  # foreword / introduction, not scripture
            blocks = chapter_blocks(chapter, language)
            if not blocks:
                continue
            chapters.append({"n": number, "b": blocks})
            stats["chapters"] += 1
            stats["verses"] += sum(1 for b in blocks if b[0] == "v")
            stats["headings"] += sum(1 for b in blocks if b[0] != "v")

        if not chapters:
            raise SystemExit(f"{version}/{book_id}: no chapters with content")
        chapter_numbers = [c["n"] for c in chapters]
        if chapter_numbers != list(range(1, len(chapters) + 1)):
            raise SystemExit(
                f"{version}/{book_id}: chapter numbers not 1..N: {chapter_numbers[:10]}"
            )

        title = template["title"]
        short = template["stitle"]
        data = {"id": book_id, "title": title, "chapters": chapters}
        (out_dir / f"{book_id}.json").write_text(
            json.dumps(data, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        books.append(
            {
                "id": book_id,
                "title": title,
                "short": short,
                "testament": 0 if book_id <= OT_BOOKS else 1,
                "chapters": len(chapters),
            }
        )

    return books, stats


def chars_in(obj, out: set[str]) -> None:
    if isinstance(obj, str):
        out.update(obj)
    elif isinstance(obj, list):
        for item in obj:
            chars_in(item, out)
    elif isinstance(obj, dict):
        for key, value in obj.items():
            chars_in(key, out)
            chars_in(value, out)


def main() -> int:
    ASSETS.mkdir(parents=True, exist_ok=True)
    manifest_versions = []
    all_chars: set[str] = set()
    total_stats = {"chapters": 0, "verses": 0, "headings": 0}

    for version, title, short, language in VERSIONS:
        books, stats = build_version(version, language)
        manifest_versions.append(
            {
                "id": version,
                "title": title,
                "short": short,
                "language": language,
                "books": books,
            }
        )
        for key in total_stats:
            total_stats[key] += stats[key]
        for book in books:
            all_chars.update(book["title"])
            all_chars.update(book["short"])
            path = ASSETS / version / f"{book['id']}.json"
            chars_in(json.loads(path.read_text(encoding="utf-8")), all_chars)
        print(
            f"{version:7s} books={len(books):2d} chapters={stats['chapters']:4d} "
            f"verses={stats['verses']:5d} headings={stats['headings']:4d}"
        )

    manifest = {"versions": manifest_versions}
    (ASSETS / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    # ASCII + Latin-1 + common punctuation must always render, plus UI strings.
    for cp in range(0x20, 0x7F):
        all_chars.add(chr(cp))
    all_chars.update("·•—–…“”‘’「」『』《》〈〉【】〔〕、。，；：！？（）－％＋／")
    all_chars.update("圣经旧约新约卷章页字号行距目录设置浅色深色跟随系统译本切换书卷阅读继续")
    all_chars.update("牧灵思高英文第共三本")
    all_chars.update("0123456789")

    charset = "".join(sorted(all_chars, key=ord))
    charset_path = ROOT / "tools" / "font_charset.txt"
    charset_path.write_text(charset, encoding="utf-8")
    print(
        f"total  books={TOTAL_BOOKS} chapters={total_stats['chapters']} "
        f"verses={total_stats['verses']} headings={total_stats['headings']} "
        f"charset={len(charset)}"
    )
    print(f"wrote {ASSETS.relative_to(ROOT)}")
    print(f"wrote {charset_path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Generate the launcher icon: pure black field, pure white Latin cross."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

BG = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
SS = 4  # supersampling factor


def draw_cross(img: Image.Image, canvas: int) -> None:
    draw = ImageDraw.Draw(img)
    c = float(canvas)
    bar = c * 0.112
    vertical_h = c * 0.56
    horizontal_w = c * 0.40
    cx = c / 2.0
    cy = c / 2.0
    crossbar_y = cy - c * 0.06

    # Horizontal arm, then vertical arm. Both are pure white rectangles.
    draw.rectangle(
        [
            cx - horizontal_w / 2,
            crossbar_y - bar / 2,
            cx + horizontal_w / 2,
            crossbar_y + bar / 2,
        ],
        fill=WHITE,
    )
    draw.rectangle(
        [
            cx - bar / 2,
            cy - vertical_h / 2,
            cx + bar / 2,
            cy + vertical_h / 2,
        ],
        fill=WHITE,
    )


def render_foreground(canvas: int) -> Image.Image:
    high = Image.new("RGBA", (canvas * SS, canvas * SS), (0, 0, 0, 0))
    draw_cross(high, canvas * SS)
    return high.resize((canvas, canvas), Image.LANCZOS)


def render_legacy(canvas: int, round_icon: bool = False) -> Image.Image:
    high = Image.new("RGBA", (canvas * SS, canvas * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(high)
    c = canvas * SS
    if round_icon:
        d.ellipse((0, 0, c - 1, c - 1), fill=BG)
    else:
        r = int(c * 0.22)
        d.rounded_rectangle((0, 0, c - 1, c - 1), radius=r, fill=BG)
    draw_cross(high, c)
    return high.resize((canvas, canvas), Image.LANCZOS)


def main() -> None:
    for density, scale in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        fg = int(108 * scale)
        legacy = int(48 * scale)
        render_foreground(fg).save(out / "ic_launcher_foreground.png")
        render_legacy(legacy).save(out / "ic_launcher.png")
        render_legacy(legacy, round_icon=True).save(out / "ic_launcher_round.png")

    anydpi = RES / "mipmap-anydpi"
    anydpi.mkdir(parents=True, exist_ok=True)
    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    (anydpi / "ic_launcher.xml").write_text(adaptive, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(adaptive, encoding="utf-8")
    print("generated black/white cross launcher icons")


if __name__ == "__main__":
    main()

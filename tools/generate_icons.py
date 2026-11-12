#!/usr/bin/env python3
"""Original clean-room SVG glyphs for HonorFreeClip2.

No source artwork or vector paths are copied from Huawei APKs. SVGs are the
editable source of truth. CairoSVG is a development-only dependency.
  python -m pip install cairosvg
  python tools/generate_icons.py
"""
from pathlib import Path
import io
from PIL import Image
try:
    import cairosvg
except ImportError as exc:
    raise SystemExit("Install development-only dependency: python -m pip install cairosvg") from exc

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets" / "icons"
DEST = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
SRC.mkdir(parents=True, exist_ok=True)
DEST.mkdir(parents=True, exist_ok=True)

COLORS = {
    'teal': '#08C5AD', 'pink': '#FC648C', 'red': '#EB4166', 'blue': '#4585F8',
    'gray': '#92939A', 'selected': '#1C63F4'
}

def svg(inner, bg=None):
    background = f'<circle cx="48" cy="48" r="45" fill="{bg}"/>' if bg else ''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96">\n{background}\n{inner}\n</svg>\n'''

STROKE = 'stroke="white" stroke-linecap="round" stroke-linejoin="round" stroke-width="5" fill="none"'
icons = {
    'hf2_gesture': ('teal', f'''<g {STROKE}>
        <path d="M38 51V26a7 7 0 0 1 14 0v23"/>
        <path d="M52 44V37a6 6 0 0 1 12 0v12"/>
        <path d="M64 46a6 6 0 0 1 11 4v14c0 11-9 19-20 19H45c-7 0-12-4-16-11L22 58a6 6 0 0 1 11-6l5 7"/>
        <path d="M31 23a23 23 0 0 1 36 0" stroke-width="3"/></g>'''),
    'hf2_head': ('teal', f'''<circle cx="48" cy="35" r="13" fill="white"/>
        <path d="M27 74c1-15 8-22 21-22s21 7 21 22" fill="white"/>
        <path d="M26 35q0-21 22-23 22 2 22 23" stroke="white" stroke-width="3" fill="none" opacity=".95"/>'''),
    'hf2_ic_charging_case_setting': ('pink', f'''<rect x="24" y="34" width="48" height="31" rx="13" fill="white"/>
        <path d="M25 48h46" stroke="{COLORS['pink']}" stroke-width="3"/>
        <rect x="43" y="54" width="10" height="3" rx="1.5" fill="{COLORS['pink']}"/>'''),
    'hf2_ic_eq': ('red', f'''<path d="M14 52c6 0 7-15 14-15s9 24 17 24c8 0 9-30 17-30s8 21 20 21" {STROKE}/>'''),
    'hf2_ic_laboratory': ('blue', '''<rect x="24" y="24" width="21" height="21" rx="6" fill="white" opacity=".65"/>
        <rect x="52" y="24" width="21" height="21" rx="6" fill="white"/>
        <rect x="24" y="52" width="21" height="21" rx="6" fill="white"/>
        <rect x="52" y="52" width="21" height="21" rx="6" fill="white" opacity=".65"/>'''),
    'hf2_ic_settings': ('blue', '''<path d="M48 19 70 32v32L48 77 26 64V32Z" fill="white"/>
        <path d="M48 30 59 36v24L48 66 37 60V36Z" fill="#C8DCFF"/>
        <circle cx="48" cy="48" r="8" fill="#4585F8"/>'''),
    'hf2_ic_update': ('blue', '''<circle cx="48" cy="48" r="26" fill="white"/>
        <path d="M48 65V34m-11 12 11-12 11 12" stroke="#4585F8" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        <path d="M37 64h22" stroke="#4585F8" stroke-width="4" stroke-linecap="round"/>'''),
    'hf2_ic_wear_detection': ('teal', f'''<path d="M55 76c-10 0-13-9-12-18 0-5-9-8-9-19 0-13 10-22 22-22 13 0 21 10 21 23 0 12-5 18-10 23-3 5-4 13-12 13Z" {STROKE}/>
        <path d="M57 31c-7-2-12 4-12 11 0 6 5 8 7 12" {STROKE}/>
        <path d="M27 36q-8 12 0 24M19 29q-14 21 0 39" {STROKE}/>'''),
    'hf2_voice': ('teal', '''<ellipse cx="48" cy="48" rx="28" ry="24" fill="white"/>
        <g fill="#08C5AD"><rect x="32" y="44" width="4" height="8" rx="2"/>
        <rect x="40" y="34" width="4" height="28" rx="2"/>
        <rect x="48" y="41" width="4" height="14" rx="2"/>
        <rect x="56" y="37" width="4" height="22" rx="2"/></g>'''),
}

for filename,(color,body) in icons.items():
    graphic = svg(body, COLORS[color])
    (SRC / (filename + '.svg')).write_text(graphic, encoding='utf-8')

# Three status variants, each with an intentionally consistent user-and-audio symbol.
# No extracted pictograms or converted vector paths are used.
def spatial(mode,selected):
    fg = COLORS['selected'] if selected else COLORS['gray']
    mid = '#E8EEFF' if selected else '#E6E7EA'
    graphic = f'''<circle cx="48" cy="44" r="9" fill="{fg}"/>
    <path d="M25 78c0-15 10-24 23-24s23 9 23 24" fill="{fg}"/>
    <g fill="{fg}"><rect x="11" y="34" width="12" height="25" rx="6" transform="rotate(-18 17 46)"/>
    <rect x="73" y="34" width="12" height="25" rx="6" transform="rotate(18 79 46)"/></g>
    <g stroke="{mid}" stroke-width="2.5" stroke-linecap="round" fill="none">
    <path d="M19 40q-4 7 0 13M77 40q4 7 0 13"/></g>'''
    if mode == 'fixed':
        graphic += f'<path d="M37 21q11-11 22 0" stroke="{fg}" stroke-width="4" stroke-linecap="round" fill="none"/>'
        graphic += f'<circle cx="48" cy="12" r="6" fill="{fg}" opacity=".75"/>'
    elif mode == 'follow':
        graphic += f'<path d="M29 25q19-22 38 0" stroke="{fg}" stroke-width="4" stroke-linecap="round" fill="none"/>'
        graphic += f'<path d="M63 18l5 7-8 3" fill="none" stroke="{fg}" stroke-linejoin="round" stroke-linecap="round" stroke-width="3"/>'
    return svg(graphic)

for mode in ('off','fixed','follow'):
    for active in ('normal','selected'):
        fname=f'hf2_spatial_{mode}_{active}'
        (SRC/(fname+'.svg')).write_text(spatial(mode,active == 'selected'),encoding='utf-8')

all_svgs = sorted(SRC.glob('hf2_*.svg'))
assert len(all_svgs) == 15, f'Expected 15 icons, found {len(all_svgs)}'
for f in all_svgs:
    high_res = cairosvg.svg2png(bytestring=f.read_bytes(), output_width=384,output_height=384)
    image = Image.open(io.BytesIO(high_res)).convert('RGBA')
    image.resize((96,96), Image.Resampling.LANCZOS).save(DEST/(f.stem+'.png'),optimize=True)
print(f'Regenerated {len(all_svgs)} icons from project-authored SVGs.')

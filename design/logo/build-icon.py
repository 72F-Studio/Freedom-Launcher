"""Builds the Eigen icon: a small saffron orb, your own light, resting on the rim of a frosted sphere.

Writes eigen-own{,-background,-foreground}.svg (108x108 adaptive-icon canvas, visible area is the middle 72)
and, with --export, the WebP layers in app/src/main/res/mipmap-*. Needs rsvg-convert and ImageMagick.
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "../../app/src/main/res")
BG = "#0E0C0A"
bx, by, br = 54, 54, 22     # sphere, centred
ox, oy, orr = 70, 38, 8     # orb, sitting on the sphere's upper-right rim

defs = f'''
 <radialGradient id="ground" cx=".45" cy=".4" r=".75"><stop offset="0" stop-color="#1E1B18"/><stop offset="1" stop-color="{BG}"/></radialGradient>
 <filter id="blur3" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"/></filter>
 <filter id="blur6" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="6"/></filter>
 <filter id="blur12" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="12"/></filter>
 <clipPath id="sphere"><circle cx="{bx}" cy="{by}" r="{br}"/></clipPath>
 <radialGradient id="body" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#050505" stop-opacity=".92"/><stop offset=".72" stop-color="#2A2826" stop-opacity=".75"/><stop offset="1" stop-color="#8E8A86" stop-opacity=".55"/></radialGradient>
 <linearGradient id="rim" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".9"/><stop offset=".45" stop-color="#fff" stop-opacity=".25"/><stop offset=".7" stop-color="#FFB27A" stop-opacity=".35"/><stop offset="1" stop-color="#FF8A1F" stop-opacity=".9"/></linearGradient>
 <linearGradient id="inner" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".75"/><stop offset=".45" stop-color="#fff" stop-opacity=".2"/><stop offset=".7" stop-color="#fff" stop-opacity="0"/></linearGradient>
 <radialGradient id="orb" cx=".38" cy=".34" r=".7"><stop offset="0" stop-color="#FFD2A0"/><stop offset=".35" stop-color="#FF9A3A"/><stop offset=".8" stop-color="#F26410"/><stop offset="1" stop-color="#C84300"/></radialGradient>
 <radialGradient id="spill" cx="{ox}" cy="{oy}" r="32" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FF7A1A" stop-opacity=".95"/><stop offset=".45" stop-color="#FF6A10" stop-opacity=".35"/><stop offset="1" stop-color="#FF6A10" stop-opacity="0"/></radialGradient>
'''
background = f'''<rect width="108" height="108" fill="url(#ground)"/>
 <circle cx="96" cy="96" r="18" fill="#FF5A0A" opacity=".35" filter="url(#blur12)"/>
 <circle cx="{ox}" cy="{oy}" r="16" fill="#FF6A10" opacity=".35" filter="url(#blur12)"/>'''
# Lit from two sides: a white inner glow on the bottom left, the orb's saffron on the top right.
foreground = f'''
 <circle cx="{bx+2}" cy="{by+4}" r="{br}" fill="#000" opacity=".55" filter="url(#blur6)"/>
 <g clip-path="url(#sphere)">
  <circle cx="{bx}" cy="{by}" r="{br}" fill="url(#body)"/>
  <circle cx="{bx}" cy="{by}" r="{br+30}" fill="url(#spill)"/>
  <circle cx="{bx}" cy="{by}" r="{br}" fill="none" stroke="url(#inner)" stroke-width="6" filter="url(#blur3)"/>
 </g>
 <circle cx="{bx}" cy="{by}" r="{br-.35}" fill="none" stroke="url(#rim)" stroke-width=".7"/>
 <circle cx="{bx}" cy="{by}" r="{br-.6}" fill="none" stroke="url(#spill)" stroke-width="1.4"/>
 <circle cx="{ox}" cy="{oy}" r="{orr+3}" fill="#FF7A1A" opacity=".7" filter="url(#blur3)"/>
 <circle cx="{ox}" cy="{oy}" r="{orr}" fill="url(#orb)"/>
 <circle cx="{ox}" cy="{oy}" r="{orr-.3}" fill="none" stroke="#FFE3C4" stroke-opacity=".5" stroke-width=".5"/>'''

head = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
for name, body in [("eigen-own", background + foreground), ("eigen-own-background", background), ("eigen-own-foreground", foreground)]:
    with open(os.path.join(HERE, name + ".svg"), "w") as f:
        f.write(f"{head}<defs>{defs}</defs>{body}</svg>\n")

if "--export" in sys.argv:
    for density, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
        out = os.path.join(RES, "mipmap-" + density)
        for layer in ("foreground", "background"):
            png = os.path.join(out, f"ic_launcher_{layer}.png")
            subprocess.run(["rsvg-convert", "-w", str(px), os.path.join(HERE, f"eigen-own-{layer}.svg"), "-o", png], check=True)
            subprocess.run(["magick", png, "-quality", "90", "-define", "webp:alpha-quality=100", png[:-4] + ".webp"], check=True)
            os.remove(png)
    subprocess.run(["rsvg-convert", "-w", "512", os.path.join(HERE, "eigen-own.svg"), "-o", os.path.join(HERE, "eigen-own-512.png")], check=True)

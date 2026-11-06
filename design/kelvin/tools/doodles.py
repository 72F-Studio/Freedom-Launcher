#!/usr/bin/env python3
"""Convert Open Doodles SVGs (CC0, opendoodles.com) into compact path lists Kelvin draws in theme colours (KelvinDoodle).

Each output line is: <role> <evenodd 0|1> <translateX> <translateY> <pathData>
role is "ink" for the black line art and "accent" for the pink highlight. Numbers are rounded to one decimal.
Usage: python3 -I tools/doodles.py <svg-dir> <res/raw dir> name...
"""
import re, sys, xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
ROLES = {"#000000": "ink", "#FF5678": "accent", "#ff5678": "accent"}


def translate(attr):
    m = re.match(r"translate\(([-\d.]+)[ ,]+([-\d.]+)\)", attr or "")
    return (float(m[1]), float(m[2])) if m else (0.0, 0.0)


def walk(node, tx, ty, fill, evenodd, out):
    dx, dy = translate(node.get("transform"))
    tx, ty = tx + dx, ty + dy
    fill = node.get("fill", fill)
    evenodd = node.get("fill-rule", evenodd)
    if node.tag == NS + "path":
        role = ROLES.get(fill)
        if role:
            d = re.sub(r"-?\d+\.\d+", lambda m: f"{float(m[0]):.1f}".rstrip("0").rstrip("."), node.get("d"))
            out.append(f"{role} {1 if evenodd == 'evenodd' else 0} {tx:.1f} {ty:.1f} {' '.join(d.split())}")
    for child in node:
        walk(child, tx, ty, fill, evenodd, out)


src, dst, *names = sys.argv[1:]
for name in names:
    root = ET.parse(f"{src}/{name}.svg").getroot()
    assert root.get("viewBox") == "0 0 1024 768", name
    lines = []
    walk(root, 0.0, 0.0, "#000000", "nonzero", lines)
    with open(f"{dst}/kelvin_doodle_{name.replace('-', '_')}.txt", "w") as f:
        f.write("\n".join(lines) + "\n")
    print(name, len(lines), "paths")

#!/usr/bin/env python3
"""Accessibility checks: every clickable 72F control has a label and a 48dp touch target."""
from smoke import *

DENSITY = int(shell('wm', 'density').split()[-1]) / 160
WIDTH = int(shell('wm', 'size').split()[-1].split('x')[0])

def audit(screen, nodes=None, min_dp=48):
    problems = []
    for n in nodes if nodes is not None else ui():
        if n.get('package') != 'dev.seventwo.launcher': continue
        if n.get('clickable') != 'true' and n.get('long-clickable') != 'true': continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        label = n.get('text') or n.get('content-desc') or ''
        if not label and not any((c.get('text') or c.get('content-desc')) for c in n.iter('node')):
            problems.append(f'unlabeled {n.get("class")} at {n.get("bounds")}')
        w, h = (x2 - x1) / DENSITY, (y2 - y1) / DENSITY
        clipped = x1 <= 1 or x2 >= WIDTH - 1  # partly scrolled into view; full size when visible
        if (w < min_dp or h < min_dp) and w > 0 and h > 0 and not clipped:
            problems.append(f'small target {w:.0f}x{h:.0f}dp: {label or n.get("class")}')
    assert not problems, screen + ':\n  ' + '\n  '.join(problems)
    print('PASS a11y', screen, flush=True)

reset()
audit('home')
open_settings(); audit('quick settings')
swipe(540, 1950, 540, 1000, 250); time.sleep(.6); audit('all settings')
tap('● everyday'); time.sleep(.8); audit('context editor')
swipe(540, 1900, 540, 700, 300); time.sleep(.6); audit('context editor, items')
home(); search(); time.sleep(.6); audit('search'); back(); back()
print('A11Y SMOKE PASS', flush=True)

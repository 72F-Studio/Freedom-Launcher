#!/usr/bin/env python3
"""UI smoke checks on an emulator. Edits only 72F's test preferences."""
import os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB = os.environ.get('ADB', '/home/pk/Android/Sdk/platform-tools/adb')
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
ROOT = Path(__file__).resolve().parents[1]
def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *map(str,args)])
def shell(*args): return adb('shell', *args).decode()
def ui():
    for attempt in range(3):  # uiautomator is occasionally killed or returns no root; retry
        try:
            shell('uiautomator', 'dump', '/sdcard/72f-test.xml')
            return list(ET.fromstring(shell('cat', '/sdcard/72f-test.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError):
            if attempt == 2: raise
            time.sleep(1)
def find(label, nodes=None, prefix=False):
    for n in nodes if nodes is not None else ui():
        t = n.get('text') or ''; d = n.get('content-desc') or ''
        if label in (t, d) or prefix and (t.startswith(label) or d.startswith(label)): return n
    raise AssertionError('Missing UI: '+label)
def center(n):
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',n.get('bounds'))); return (x1+x2)//2,(y1+y2)//2
def tap(label, prefix=False):
    x,y = center(find(label, prefix=prefix)); shell('input','tap',x,y); time.sleep(.25)
def press(label, prefix=False, ms=900):
    x,y = center(find(label, prefix=prefix)); shell('input','swipe',x,y,x,y,ms); time.sleep(.6)
def home(): shell('input','keyevent','3'); time.sleep(.8)
def open_settings():
    """Quick settings: hold the dock's search button."""
    home(); press('Search apps', prefix=True); find('context')
def settings(row):
    """Open the full settings page and tap a row, scrolling the sheet as needed."""
    open_settings()
    for _ in range(10):
        if any(n.get('text') == row for n in ui()): return tap(row)
        swipe(540,1950,540,1000,250); time.sleep(.3)
    raise AssertionError('Missing setting: '+row)
def search(): tap('Search apps', prefix=True)
def reset():
    """Fresh 72F data (only this app's), set as Home, welcome dismissed."""
    shell('pm','clear','dev.seventwo.launcher'); shell('cmd','package','set-home-activity','dev.seventwo.launcher/.MainActivity')
    start()
    for _ in range(10):  # a cold start after pm clear can take a few seconds to show the first-run tour
        nodes = ui()
        if any(n.get('content-desc') == 'Skip setup' for n in nodes): tap('Skip setup'); time.sleep(.6); return
        time.sleep(.6)
    raise AssertionError('First-run tour did not appear')
def pick_file(name):
    """Choose a file in Android's document picker: Downloads, list view, scroll until visible."""
    time.sleep(1)
    def match(nodes): return next((n for n in nodes if n.get('text')==name or (n.get('content-desc') or '').startswith(name+', ')),None)
    nodes=ui()
    if match(nodes) is None and not any(n.get('text')=='Downloads' for n in nodes):
        tap('Show roots'); time.sleep(.5); roots=[n for n in ui() if n.get('text')=='Downloads']
        x,y=center(roots[-1]); shell('input','tap',x,y); time.sleep(1)
    if any(n.get('content-desc')=='List view' for n in ui()): tap('List view'); time.sleep(.6)
    for _ in range(15):
        node=match(ui())
        if node is not None:
            x,y=center(node); shell('input','tap',x,y); time.sleep(1); return
        swipe(540,1900,540,900,300); time.sleep(.4)
    raise AssertionError('Missing file '+name)
def prefs_xml(): return shell('run-as','dev.seventwo.launcher','cat','shared_prefs/launcher.xml')
def check(label): find(label); print('PASS', label, flush=True)
def back(): shell('input','keyevent','4'); time.sleep(.25)
def start(): shell('am','start','-n','dev.seventwo.launcher/.MainActivity'); time.sleep(.4)
def swipe(x1,y1,x2,y2,duration=300): shell('input','swipe',x1,y1,x2,y2,duration); time.sleep(.25)
def shot(name): (ROOT/'docs'/name).write_bytes(adb('exec-out','screencap','-p'))

def run():
    reset()
    check('favorites category, selected')
    shot('home.png')
    swipe(850,1200,180,1200)
    check('social category, selected')
    swipe(180,1200,850,1200)
    check('favorites category, selected')
    search(); shell('input','text','camera'); time.sleep(.3)
    assert any(n.get('text','').lower() == 'camera' for n in ui()), 'Search must find camera'
    print('PASS search finds installed Camera',flush=True)
    shot('search.png')
    tap('Close search'); check('favorites category, selected')
    settings('categories & folders'); tap('create category')
    shell('input','text','beta-test'); tap('save')
    settings('categories & folders'); tap('show or hide categories'); check('beta-test'); tap('done')
    print('PASS custom category creation',flush=True)
    settings('categories & folders'); tap('turn categories off'); home()
    nodes=ui(); cats=[n.get('content-desc','') for n in nodes if ' category' in n.get('content-desc','')]
    assert not any('social category' in c for c in cats), cats
    swipe(850,1200,180,1200); check('all apps category, selected'); shot('all-apps.png')
    print('PASS two-category mode',flush=True)
    settings('categories & folders'); tap('turn categories on')
    settings('themes'); tap('paper · light'); home(); shot('paper.png')
    settings('themes'); tap('retro · cyan')
    settings('scrubber side'); assert '<boolean name="left" value="true"' in prefs_xml()
    settings('scrubber side'); assert '<boolean name="left" value="false"' in prefs_xml()
    print('PASS palettes and handedness',flush=True)
    settings('categories & folders'); tap('delete custom category'); tap('beta-test'); tap('delete')
    home(); check('favorites category, selected')
    # Dock: search button opens search; swipe up on the category bar opens quick settings.
    x,y = center(find('Category switcher', prefix=True)); swipe(x,y,x,y-500,200); time.sleep(.6); check('context')
    print('PASS dock gestures',flush=True); home()
    shot('home.png')
    print('SMOKE PASS',flush=True)

if __name__ == "__main__": run()

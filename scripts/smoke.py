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
    shell('uiautomator', 'dump', '/sdcard/72f-test.xml')
    return list(ET.fromstring(shell('cat', '/sdcard/72f-test.xml')).iter('node'))
def find(label, nodes=None):
    for n in nodes if nodes is not None else ui():
        if n.get('text') == label or n.get('content-desc') == label: return n
    raise AssertionError('Missing UI: '+label)
def tap(label):
    n = find(label); x1,y1,x2,y2 = map(int,re.findall(r'\d+',n.get('bounds')))
    shell('input','tap',(x1+x2)//2,(y1+y2)//2); time.sleep(.25)
def check(label): find(label); print('PASS', label, flush=True)
def back(): shell('input','keyevent','4'); time.sleep(.25)
def start(): shell('am','start','-n','dev.seventwo.launcher/.MainActivity'); time.sleep(.4)
def swipe(x1,y1,x2,y2,duration=300): shell('input','swipe',x1,y1,x2,y2,duration); time.sleep(.25)
def shot(name): (ROOT/'docs'/name).write_bytes(adb('exec-out','screencap','-p'))

def run():
    start()
    nodes=ui()
    if any(n.get('text') == 'LET’S GO' for n in nodes): tap('LET’S GO')
    check('favorites category, selected')
    shot('home.png')
    swipe(850,1200,180,1200)
    check('social category, selected')
    swipe(180,1200,850,1200)
    check('favorites category, selected')
    tap('Search apps'); shell('input','text','camera'); time.sleep(.3)
    assert any(n.get('text','').lower() == 'camera' for n in ui()), 'Search must find camera'
    print('PASS search finds installed Camera',flush=True)
    shot('search.png')
    tap('Close search'); check('favorites category, selected')
    tap('Launcher settings'); tap('categories & folders'); tap('create category')
    shell('input','text','beta-test'); tap('SAVE')
    tap('Launcher settings'); tap('categories & folders'); tap('show or hide categories'); check('beta-test'); tap('DONE')
    print('PASS custom category creation',flush=True)
    tap('Launcher settings'); tap('categories & folders'); tap('turn categories off')
    nodes=ui(); cats=[n.get('content-desc','') for n in nodes if ' category' in n.get('content-desc','')]
    assert not any('social category' in c for c in cats), cats
    swipe(850,1200,180,1200); check('all apps category, selected'); shot('all-apps.png')
    print('PASS two-category mode',flush=True)
    tap('Launcher settings'); tap('categories & folders'); tap('turn categories on')
    tap('Launcher settings'); tap('appearance & themes'); tap('paper · light'); shot('paper.png')
    tap('Launcher settings'); tap('appearance & themes'); tap('retro · cyan')
    tap('Launcher settings'); tap('scrubber side · right')
    tap('Launcher settings'); check('scrubber side · left'); tap('scrubber side · left')
    print('PASS palettes and handedness',flush=True)
    tap('Launcher settings'); tap('categories & folders'); tap('delete custom category'); tap('beta-test'); tap('DELETE')
    start(); check('favorites category, selected')
    shot('home.png')
    print('SMOKE PASS',flush=True)

if __name__ == "__main__": run()

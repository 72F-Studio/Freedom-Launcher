#!/usr/bin/env python3
"""
UI smoke checks for the Kelvin UI, Eigen Plus included, on an emulator. Clears Eigen's app data first (pm clear), so
never point it at a phone you use. Needs a debuggable build installed (it reads prefs with run-as).

    ANDROID_SERIAL=emulator-5554 ./scripts/eigen-smoke.py
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', os.path.expanduser('~/Android/Sdk/platform-tools/adb'))
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PKG = 'com.studio72f.eigen'
passed = 0


def shell(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, 'shell', *map(str, args)]).decode()


def nodes():
    for attempt in range(3):  # uiautomator now and then returns nothing; retry
        try:
            shell('uiautomator', 'dump', '/sdcard/eigen-smoke.xml')
            return list(ET.fromstring(shell('cat', '/sdcard/eigen-smoke.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError):
            if attempt == 2: raise
            time.sleep(1)


def label(n): return n.get('text') or n.get('content-desc') or ''


def find(text, required=True):
    for n in nodes():
        if label(n).startswith(text): return n
    if required: fail('Missing UI: ' + text)


def fail(why):
    """Saves what the screen showed, then stops."""
    with open('eigen-smoke-failure.png', 'wb') as f:
        f.write(subprocess.check_output([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p']))
    print('Screen saved to eigen-smoke-failure.png. On screen:', [label(n) for n in nodes() if label(n)])
    raise AssertionError(why)


def has(text): return find(text, required=False) is not None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds'))); return (x1 + x2) // 2, (y1 + y2) // 2


def tap(text): x, y = center(find(text)); shell('input', 'tap', x, y); time.sleep(1.2)
def wait_for(text, seconds=15):
    """Polls until text is on screen: a cold start after pm clear can take a while on a debug build."""
    end = time.time() + seconds
    while time.time() < end:
        if has(text): return True
        time.sleep(1)
    return False


def scroll_to(text):
    """Scrolls a page up in small steps (at the left edge, clear of sliders) until text is on screen."""
    for _ in range(8):
        if has(text): return
        swipe(60, 1800, 60, 1200)
    raise AssertionError('Not found after scrolling: ' + text)


def hold(text): x, y = center(find(text)); shell('input', 'swipe', x, y, x, y, 900); time.sleep(1.2)
def swipe(x1, y1, x2, y2): shell('input', 'swipe', x1, y1, x2, y2, 300); time.sleep(1.2)
def key(code): shell('input', 'keyevent', code); time.sleep(1.2)
def prefs(): return shell('run-as', PKG, 'cat', 'shared_prefs/launcher.xml')


def check(name, ok):
    global passed
    if not ok: fail(name)
    passed += 1; print('ok', name)


def plus_sheet_names(reason):
    check(f'Plus sheet names "{reason}"', has(f'{reason} is part of Plus'))
    key(4)


def open_clock_sheet():
    key(3); wait_for('Friday'); hold('Friday'); tap('Change style'); wait_for('Clock style'); time.sleep(.6)


def show_binary():
    """Scrolls the clock styles until Binary, the last one, is on screen."""
    for _ in range(4):
        if has('Binary'): return
        swipe(900, 2053, 300, 2053)
    fail('Binary never came into view')


def fresh_install():
    shell('pm', 'clear', PKG)
    shell('cmd', 'package', 'set-home-activity', f'{PKG}/.MainActivity')
    key(3)


# A new install: the tour shows only free clocks, and Plus is off.
fresh_install()
check('tour starts', wait_for('Eigen.'))
tap('Continue'); tap('Continue')
check('tour offers free clocks', all(has(n) for n in ('Heavy', 'Thin', 'Stacked')))
check('tour hides Plus clocks', not has('Binary'))
tap('Continue'); tap('Continue'); tap('Start using Eigen')
check('home shows drawers', has('All apps'))
check('new install is not grandfathered', 'plus_grandfathered' not in prefs())

# Left page opens on the free assistant; Conversations explains Plus.
swipe(100, 1200, 950, 1200)
check('left page opens on Assistant', has('Message Eigen'))
tap('Conversations')
check('Conversations offers Plus', has('See Eigen Plus'))
tap('See Eigen Plus')
check('Plus sheet shows a price', has('Get Plus'))
plus_sheet_names('Conversations')

# Clocks: a Plus face can be tried on and goes back on close.
open_clock_sheet()
show_binary()
tap('Binary')
check('trying Binary offers Plus', has('Keep Binary with Plus'))
check('trying Binary saves nothing', 'clock_face' not in prefs())
tap('Close')
check('closing puts the clock back', any(re.fullmatch(r'\d{1,2}:\d\d', label(n)) for n in nodes()))

# Widgets: Plus widgets are marked and open the sheet instead of being added.
key(3); swipe(950, 1200, 100, 1200); tap('Add widget')
check('Plus widgets are marked', has('Plus'))
tap('Notes')
check('Notes was not added', 'widget_order' not in prefs())
plus_sheet_names('Notes')

# Contexts: one besides everyday is free, the next opens Plus.
open_clock_sheet(); tap('Launcher settings'); scroll_to('Contexts and widgets'); tap('Contexts and widgets')
tap('Add a context'); tap('Morning'); key(4)
check('first context is free', has('Morning'))
tap('Add a context')
plus_sheet_names('Another context')

# Card styles: Stained can be tried on, not saved; wide cards open Plus.
key(4); key(4); key(3); key(3)
tap('Tools'); hold('Chrome'); tap('Details')
tap('Stained')
check('trying Stained offers Plus', has('Keep Stained with Plus'))
check('trying Stained saves nothing', 'card_styles' not in prefs())

# An install from before Plus keeps it: drop the one-time check and relaunch.
shell('am', 'force-stop', PKG)
shell('run-as', PKG, 'sed', '-i', '/plus_checked/d', 'shared_prefs/launcher.xml')
key(3); wait_for('Friday')
check('older install is grandfathered', 'plus_grandfathered" value="true' in prefs())
open_clock_sheet(); show_binary(); tap('Binary'); tap('Close')
check('grandfathered Binary is saved', '>binary<' in prefs())
swipe(100, 1200, 950, 1200); tap('Conversations')
check('grandfathered Conversations is open', not has('See Eigen Plus'))

crashes = subprocess.run([ADB, '-s', SERIAL, 'logcat', '-d', '-b', 'crash'], capture_output=True, text=True).stdout
check('no crashes', PKG not in crashes)
print(f'{passed} Eigen smoke checks passed')

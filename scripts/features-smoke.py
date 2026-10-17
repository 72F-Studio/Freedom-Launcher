#!/usr/bin/env python3
"""Launcher features: predictive back, app shortcuts, rename, hide, category order, notification dots."""
from smoke import *

def hold(label, prefix=False):
    press(label, prefix=prefix, ms=900)

def app_menu(name):
    """Opens an app's long-press menu from search, so the app is found wherever it lives."""
    home(); search(); shell('input', 'text', name[:4].lower()); time.sleep(.8)
    rows = [n for n in ui() if (n.get('content-desc') or '').startswith(name + '.') or (n.get('content-desc') or '').startswith(name + ',')]
    assert rows, 'No row for ' + name
    x, y = center(rows[0]); shell('input', 'swipe', x, y, x, y, 900); time.sleep(.8)

reset()
shell('cmd', 'package', 'set-home-activity', 'dev.seventwo.launcher/.MainActivity')

# Predictive back (SDK 36): search closes, then the settings sheet steps back and closes.
search(); time.sleep(.6)
for _ in range(3):  # the first Back may only close the keyboard
    back(); time.sleep(.4)
    if any(n.get('content-desc') == 'favorites category, selected' for n in ui()): break
check('favorites category, selected')
open_settings(); back(); time.sleep(.4)
assert not any(n.get('text') == 'CONTEXT' or n.get('text') == 'context' for n in ui()), 'Back should close quick settings'
print('PASS predictive back', flush=True)

# App shortcuts: Chrome publishes manifest shortcuts; the default launcher may read them.
app_menu('Chrome')
labels = [n.get('text') or '' for n in ui()]
assert any(l.endswith(' · shortcut') for l in labels), 'Chrome shortcuts missing: %s' % labels[:20]
print('PASS app shortcuts:', [l for l in labels if l.endswith(' · shortcut')], flush=True)
back(); back()

# Rename, then restore the original name.
app_menu('Maps'); tap('rename', prefix=True); time.sleep(.4)
shell('input', 'keyevent', 'KEYCODE_MOVE_END')
for _ in range(10): shell('input', 'keyevent', 'KEYCODE_DEL')
shell('input', 'text', 'Directions'); tap('save'); time.sleep(1.2)
assert any((n.get('text') or '') == 'Directions' for n in ui()), 'Renamed label should show'
app_menu('Directions'); tap('rename', prefix=True); time.sleep(.4); tap('original name'); time.sleep(1.2)
home(); assert any((n.get('text') or '') == 'Maps' for n in ui()), 'Original name should return'
print('PASS rename and restore', flush=True)

# Hide: leaves favorites and lists, stays in search, and comes back from settings.
app_menu('Camera'); tap('hide from lists', prefix=True); time.sleep(.8)
home(); assert not any((n.get('text') or '') == 'Camera' for n in ui()), 'Hidden app should leave favorites'
search(); shell('input', 'text', 'came'); time.sleep(.8)
assert any((n.get('text') or '') == 'Camera' for n in ui()), 'Hidden app should stay in search'
back(); settings('hidden apps'); tap('Camera · show again'); time.sleep(.6)
print('PASS hide, search, unhide', flush=True)

# Category order: move the second category up; the title strip follows.
settings('category order'); time.sleep(.6)
before = [n.get('text') for n in ui() if n.get('content-desc', '').startswith('Move ') and n.get('content-desc', '').endswith(' up')]
up = [n for n in ui() if (n.get('content-desc') or '').startswith('Move ') and n.get('content-desc').endswith(' up')]
assert up, 'No move-up controls'
moved = up[0].get('content-desc')[5:-3]
x, y = center(up[0]); shell('input', 'tap', x, y); time.sleep(.8)
order = re.findall(r'&quot;([a-z ]+)&quot;', prefs_xml().split('name="order"')[1].split('</string>')[0]) if 'name="order"' in prefs_xml() else []
assert order and order[0] == moved, 'Order should start with %s: %s' % (moved, order)
tap('reset to default order'); time.sleep(.5)
print('PASS category order', flush=True)

# Notification dots from a real notification: an SMS to the emulator lands in Messages.
shell('cmd', 'notification', 'allow_listener', 'dev.seventwo.launcher/dev.seventwo.launcher.MediaAccessService')
time.sleep(1.5)
subprocess.run([ADB, '-s', SERIAL, 'emu', 'sms', 'send', '5551234', 'dot test'], check=False)
dot = False
for _ in range(20):
    home(); time.sleep(1)
    if any('Messages' in (n.get('content-desc') or '') and 'has notifications' in (n.get('content-desc') or '') for n in ui()): dot = True; break
assert dot, 'Messages should show a notification dot'
shell('service', 'call', 'notification', '1')  # clear all notifications
print('PASS notification dots', flush=True)
print('FEATURES SMOKE PASS', flush=True)

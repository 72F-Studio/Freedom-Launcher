#!/usr/bin/env python3
"""Wallpaper import and persistence checks on the isolated beta emulator."""
from smoke import *

def prefs():
    tree=ET.fromstring(shell('run-as','dev.seventwo.launcher','cat','shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value',n.text) for n in tree}
def wallpaper_menu():
    settings('wallpaper')
pick_download=pick_file

reset()
adb('push',str(ROOT/'tests'/'fixtures'/'wallpaper.png'),'/sdcard/Download/72f-wallpaper.png')
wallpaper_menu(); tap('choose a photo'); pick_download('72f-wallpaper.png')
assert prefs().get('wallpaperMode')=='photo'
assert int(shell('run-as','dev.seventwo.launcher','stat','-c','%s','files/wallpaper.jpg'))>1000
shot('wallpaper.png'); print('PASS wallpaper import through document picker',flush=True)
shell('am','force-stop','dev.seventwo.launcher'); start(); check('favorites category, selected'); time.sleep(.5)
assert prefs().get('wallpaperMode')=='photo'
shot('wallpaper-restored.png'); print('PASS wallpaper persists across process restart',flush=True)
wallpaper_menu();tap('wallpaper dimming')
n=find('Wallpaper dimming');x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
shell('input','tap',int(x1+(x2-x1)*.38),(y1+y2)//2); tap('DONE')
assert 20<int(prefs()['wallpaperDim'])<50
shot('wallpaper-dimmed.png');print('PASS adjustable dimming',flush=True)
wallpaper_menu();tap('use phone wallpaper');assert prefs()['wallpaperMode']=='system';shot('system-wallpaper.png')
wallpaper_menu();tap('solid background');assert prefs()['wallpaperMode']=='solid'
wallpaper_menu();tap('use saved photo');assert prefs()['wallpaperMode']=='photo'
print('PASS system / solid / saved-photo selection',flush=True)
adb('push',str(ROOT/'themes'/'retro-cyan.json'),'/sdcard/Download/72f-theme.json')
settings('themes');tap('import theme');pick_download('72f-theme.json')
assert '#39B9D4' in prefs()['theme']; print('PASS theme import',flush=True)
home();shot('beta2-home.png');open_settings();shot('beta2-settings.png');back()
print('WALLPAPER SMOKE PASS',flush=True)

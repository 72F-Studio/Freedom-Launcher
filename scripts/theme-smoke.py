#!/usr/bin/env python3
"""Theme, effect, icon-pack and file-picker round trips on the beta emulator."""
from smoke import *
base_tap=tap
def tap(label):
    for attempt in range(5):
        try:return base_tap(label)
        except AssertionError:
            if attempt==4:raise
            swipe(700,1750,700,800)

import json, zipfile, io, tempfile
THEME_FILE=os.environ.get('THEME_FILE','72f-roundtrip-'+str(int(time.time()))+'.72ftheme.zip')
BACKUP_FILE='72f-roundtrip-'+str(int(time.time()))+'.72fbackup.zip'
def pull_bytes(name):
    with tempfile.NamedTemporaryFile() as target:
        adb('pull','/sdcard/Download/'+name,target.name)
        return Path(target.name).read_bytes()


def prefs():
    tree=ET.fromstring(shell('run-as','dev.seventwo.launcher','cat','shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value',n.text) for n in tree}
def appearance(): tap('Launcher settings');tap('appearance & themes')
def choose_file(name):
    nodes=ui()
    if not any(n.get('text')=='Downloads' for n in nodes):
        base_tap('Show roots')
        nodes=ui();roots=[n for n in nodes if n.get('text')=='Downloads'];node=roots[-1]
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        shell('input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(1)
    for attempt in range(4):
        nodes=ui();matches=[n for n in nodes if n.get('text')==name or n.get('content-desc','').startswith(name+', ')]
        if matches:break
        time.sleep(.5)
    assert matches,'Missing file '+name
    node=matches[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    shell('input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(1)
def save_file(name):
    nodes=ui();node=next(n for n in nodes if n.get('class')=='android.widget.EditText')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    shell('input','tap',(x1+x2)//2,(y1+y2)//2);shell('input','keyevent','123')
    for _ in range(len(node.get('text',''))+5):shell('input','keyevent','67')
    shell('input','text',name);tap('SAVE');time.sleep(1)
    nodes=ui()
    if any(n.get('text')=='REPLACE' for n in nodes):tap('REPLACE');time.sleep(1)
def theme():return json.loads(prefs()['theme'])

if os.environ.get('THEME_RESUME')!='1':
    start();back();start();appearance();tap('Wave');check('favorites category, selected')
    assert theme()['layout']=='xmb';assert prefs()['wallpaperMode']=='solid'
    shot('wave.png');print('PASS Wave layout and waves',flush=True)
    swipe(850,1200,180,1200);check('social category, selected');swipe(180,1200,850,1200)
    appearance();tap('Liquid Retro');assert theme()['glass']==50 and theme()['blur']==18
    adb('push',str(ROOT/'tests'/'fixtures'/'wallpaper.png'),'/sdcard/Download/72f-wallpaper.png')
    tap('Launcher settings');tap('wallpaper');tap('choose a photo');choose_file('72f-wallpaper.png')
    assert prefs()['wallpaperMode']=='photo'
    shot('liquid-retro.png');appearance();tap('effects');tap('glass surfaces');tap('liquid glass')
    assert theme()['glass']==65 and theme()['liquid'];appearance();tap('launcher icon set');tap('Material');assert theme()['uiIconSet']=='material';appearance();tap('launcher icon set');tap('Retro');print('PASS effect controls',flush=True)
    appearance();tap('app icon packs');tap('72F Test Icons');time.sleep(.5);assert prefs()['iconPack']=='dev.seventwo.iconfixture';print('PASS installed icon-pack picker',flush=True)
    appearance();tap('export theme package');save_file(THEME_FILE)
exported=pull_bytes(THEME_FILE)
with zipfile.ZipFile(io.BytesIO(exported)) as z:
    definition=json.loads(z.read('theme.json'));assert definition['glass']==65 and definition['wallpaper'] in z.namelist()
appearance();tap('Wave');appearance();tap('import theme');choose_file(THEME_FILE)
assert theme()['glass']==65 and theme()['layout']=='metro';print('PASS SAF theme export/import',flush=True)
tap('Launcher settings');tap('backup & restore');tap('export launcher backup');save_file(BACKUP_FILE)
backup=pull_bytes(BACKUP_FILE)
with zipfile.ZipFile(io.BytesIO(backup)) as z:assert json.loads(z.read('backup.json'))['kind']=='72f-backup'
before=prefs();appearance();tap('Wave')
tap('Launcher settings');tap('backup & restore');tap('restore launcher backup');choose_file(BACKUP_FILE)
check('restore launcher?');tap('RESTORE');time.sleep(1)
assert theme()['glass']==65 and theme()['layout']=='metro'
assert prefs()['favorites']==before['favorites'];time.sleep(2);shot('liquid-retro.png');print('PASS SAF backup/restore',flush=True)
adb('push',str(ROOT/'themes'/'wave.72ftheme'),'/sdcard/Download/wave.72ftheme')
appearance();tap('import theme');choose_file('wave.72ftheme');assert theme()['layout']=='xmb';print('PASS shared Wave package import',flush=True)
appearance();tap('app icon packs');tap('system icons');appearance();tap('retro · cyan')
print('THEME SMOKE PASS',flush=True)

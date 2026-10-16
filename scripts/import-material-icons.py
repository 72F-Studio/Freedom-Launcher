#!/usr/bin/env python3
"""Vendor selected Google Material Icons as native paths; no runtime downloads."""
from pathlib import Path
import urllib.request, xml.etree.ElementTree as ET
BASE='https://raw.githubusercontent.com/google/material-design-icons/master/'
ICONS={'favorites':'toggle/star','social':'communication/chat','music':'image/music_note','video':'av/movie','photos':'image/photo','games':'hardware/videogame_asset','travel':'maps/navigation','tools':'action/work','all_apps':'navigation/apps','search':'action/search','close':'navigation/close','more':'navigation/more_horiz','arrow':'navigation/arrow_forward','chevron':'navigation/chevron_right','back':'navigation/arrow_back','play':'av/play_arrow','pause':'av/pause','previous':'av/skip_previous','next':'av/skip_next','folder':'file/folder','settings':'action/settings'}
folder=Path('app/src/main/res/drawable');folder.mkdir(parents=True,exist_ok=True)
for name,source in ICONS.items():
    url=BASE+'src/'+source+'/materialicons/24px.svg'
    data=urllib.request.urlopen(url,timeout=30).read()
    Path('third-party/material-icons',name+'.svg').write_bytes(data)
    root=ET.fromstring(data);paths=[]
    for p in root.iter('{http://www.w3.org/2000/svg}path'):
        if p.get('fill')=='none':continue
        paths.append('  <path android:fillColor="#FFFFFFFF" android:pathData="'+p.attrib['d']+'" />')
    assert paths,name
    (folder/('ui_material_'+name+'.xml')).write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'+'\n'.join(paths)+'\n</vector>\n')
Path('third-party/material-icons/LICENSE').write_bytes(urllib.request.urlopen(BASE+'LICENSE',timeout=30).read())
print('Vendored',len(ICONS),'Google Material icons')

#!/usr/bin/env python3
"""Original Retro/Wave vector packs. Geometry, never font glyphs."""
from pathlib import Path
RETRO={
'favorites':'M12,2 L15,9 L23,9 L17,14 L19,22 L12,17 L5,22 L7,14 L1,9 L9,9 Z',
'social':'M2,3 H22 V17 H9 L3,22 V17 H2 Z M6,7 V9 H18 V7 Z M6,11 V13 H15 V11 Z',
'music':'M9,4 L21,2 V17 H17 V7 L13,9 V20 H5 V16 H9 Z',
'video':'M2,4 H22 V20 H2 Z M9,8 V16 L17,12 Z',
'photos':'M2,3 H22 V21 H2 Z M5,17 H19 L14,11 L11,14 L8,10 Z M16,6 H19 V9 H16 Z',
'games':'M4,6 H20 L24,19 H17 L14,16 H10 L7,19 H0 Z M6,9 V11 H4 V13 H6 V15 H8 V13 H10 V11 H8 V9 Z M16,9 V12 H19 V9 Z M19,13 V16 H22 V13 Z',
 'travel':'M12,2 L23,22 L12,17 L1,22 Z',
 'tools':'M8,2 H16 V6 H22 V22 H2 V6 H8 Z M10,4 V6 H14 V4 Z M2,11 V13 H22 V11 Z',
 'all_apps':'M2,2 H7 V7 H2 Z M10,2 H15 V7 H10 Z M18,2 H23 V7 H18 Z M2,10 H7 V15 H2 Z M10,10 H15 V15 H10 Z M18,10 H23 V15 H18 Z M2,18 H7 V23 H2 Z M10,18 H15 V23 H10 Z M18,18 H23 V23 H18 Z',
 'search':'M2,2 H16 V16 H2 Z M5,5 V13 H13 V5 Z M14,17 L17,14 L23,20 L20,23 Z',
 'close':'M5,3 L12,10 L19,3 L21,5 L14,12 L21,19 L19,21 L12,14 L5,21 L3,19 L10,12 L3,5 Z',
 'more':'M3,10 H7 V14 H3 Z M10,10 H14 V14 H10 Z M17,10 H21 V14 H17 Z',
 'arrow':'M2,10 H16 L10,4 L12,2 L22,12 L12,22 L10,20 L16,14 H2 Z',
 'chevron':'M7,3 L16,12 L7,21 L5,19 L12,12 L5,5 Z',
 'back':'M22,10 H8 L14,4 L12,2 L2,12 L12,22 L14,20 L8,14 H22 Z',
 'play':'M6,2 L22,12 L6,22 Z',
 'pause':'M5,3 H10 V21 H5 Z M14,3 H19 V21 H14 Z',
 'previous':'M3,3 H6 V21 H3 Z M21,3 V21 L7,12 Z',
 'next':'M18,3 H21 V21 H18 Z M3,3 L17,12 L3,21 Z',
 'folder':'M2,4 H10 L13,7 H22 V21 H2 Z',
 'settings':'M9,1 H15 V5 L18,7 L22,6 L24,12 L20,14 V18 L16,22 L12,20 L8,22 L4,18 V14 L0,12 L2,6 L6,7 L9,5 Z M9,9 V15 H15 V9 Z'}
WAVE={
 'favorites':'M12,21 C7,17 2,13 2,8 C2,2 9,1 12,6 C15,1 22,2 22,8 C22,13 17,17 12,21 Z',
 'social':'M3,5 C3,2 21,2 21,5 V14 C21,17 18,17 11,17 L5,21 V17 C3,17 3,16 3,14 Z M7,8 H17 M7,12 H14',
 'music':'M9,18 V5 L20,3 V16 M9,5 L20,3 M3,18 C3,14 9,14 9,18 C9,22 3,22 3,18 Z M14,16 C14,12 20,12 20,16 C20,20 14,20 14,16 Z',
 'video':'M2,8 H17 V20 H2 Z M17,11 L22,8 V20 L17,17 Z M3,3 H17 V8 H3 Z M6,3 L9,8 M12,3 L15,8',
 'photos':'M6,3 H22 V18 H6 Z M2,7 V22 H18 M6,15 L11,9 L15,14 L18,11 L22,17 M18,7 A1,1 0,1 0,18,9 A1,1 0,1 0,18,7',
 'games':'M7,7 C3,7 0,16 3,20 C6,23 9,16 12,16 C15,16 18,23 21,20 C24,16 21,7 17,7 Z M5,12 H11 M8,9 V15 M17,10 A1,1 0,1 0,17,12 A1,1 0,1 0,17,10 M20,13 A1,1 0,1 0,20,15 A1,1 0,1 0,20,13',
 'travel':'M12,2 C2,2 2,12 12,22 C22,12 22,2 12,2 Z M12,6 A3,3 0,1 0,12,12 A3,3 0,1 0,12,6',
 'tools':'M15,3 C20,0 24,6 20,10 L10,20 C6,24 1,19 4,16 L14,6 Z M5,18 L7,20',
 'all_apps':'M3,4 H8 V9 H3 Z M10,4 H15 V9 H10 Z M17,4 H22 V9 H17 Z M3,12 H8 V17 H3 Z M10,12 H15 V17 H10 Z M17,12 H22 V17 H17 Z',
 'search':'M10,3 A7,7 0,1 0,10,17 A7,7 0,1 0,10,3 M15,15 L22,22',
 'close':'M5,5 L19,19 M19,5 L5,19',
 'more':'M5,11 A1,1 0,1 0,5,13 A1,1 0,1 0,5,11 M12,11 A1,1 0,1 0,12,13 A1,1 0,1 0,12,11 M19,11 A1,1 0,1 0,19,13 A1,1 0,1 0,19,11',
 'arrow':'M3,12 H21 M14,5 L21,12 L14,19',
 'chevron':'M8,4 L16,12 L8,20',
 'back':'M21,12 H3 M10,5 L3,12 L10,19',
 'play':'M7,3 L22,12 L7,21 Z',
 'pause':'M7,4 H10 V20 H7 Z M15,4 H18 V20 H15 Z',
 'previous':'M4,4 V20 M20,4 L6,12 L20,20 Z',
 'next':'M20,4 V20 M4,4 L18,12 L4,20 Z',
 'folder':'M2,7 Q2,4 5,4 H10 L13,8 H20 Q22,8 22,10 V19 Q22,21 20,21 H4 Q2,21 2,19 Z',
 'settings':'M9,3 H15 L16,6 L20,6 L22,11 L19,14 L19,18 L14,21 L11,18 L7,19 L3,15 L6,11 L5,7 Z M12,8 A4,4 0,1 0,12,16 A4,4 0,1 0,12,8'}
folder=Path('app/src/main/res/drawable')
for pack,icons in [('retro',RETRO),('wave',WAVE)]:
    for name,path in icons.items():
        style='android:fillColor="#FFFFFFFF" android:fillType="evenOdd"' if pack=='retro' else 'android:fillColor="#00FFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.3" android:strokeLineCap="round" android:strokeLineJoin="round"'
        (folder/('ui_'+pack+'_'+name+'.xml')).write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n <path '+style+' android:pathData="'+path+'" />\n</vector>\n')
print('Created original Retro and Wave packs')

#!/usr/bin/env python3
from smoke import *

def bounds(label): return list(map(int,re.findall(r'\d+',find(label).get('bounds'))))
def hold(label):
    x1,y1,x2,y2=bounds(label); swipe((x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,800)
def all_apps():
    x1,y1,x2,y2=bounds('Category switcher'); shell('input','tap',x2-45,(y1+y2)//2); time.sleep(.3)

start()
x1,y1,x2,y2=bounds('favorites category, selected')
swipe(min(x2-20,850),(y1+y2)//2,100,(y1+y2)//2)
check('social category, selected'); print('PASS title-strip swipe',flush=True)
all_apps(); check('all apps category, selected')
swipe(1025,1150,1025,1750)
assert any(n.get('text')=='YouTube' for n in ui()), 'Alphabet scrub must reach Y'
print('PASS horizontal and alphabetical scrubbers',flush=True)
tap('Search apps'); shell('input','text','clock'); time.sleep(.2)
tap('Clock'); time.sleep(.5)
assert 'dev.seventwo.launcher' not in shell('dumpsys','activity','activities').split('topResumedActivity=')[-1].split('\n')[0], 'Clock should launch'
print('PASS launches installed app',flush=True)
start()
tap('Launcher settings'); tap('categories & folders'); tap('create category'); shell('input','text','beta-check'); tap('SAVE')
tap('Search apps'); shell('input','text','calendar'); time.sleep(.2); hold('Calendar'); tap('move to category'); tap('beta-check')
hold('Calendar'); tap('add to favorites'); tap('Close search')
shell('am','force-stop','dev.seventwo.launcher'); start(); check('Calendar')
print('PASS favorites persist after process restart',flush=True)
tap('Launcher settings'); tap('categories & folders'); tap('merge categories'); tap('beta-check'); tap('tools')
prefs=shell('run-as','dev.seventwo.launcher','cat','shared_prefs/launcher.xml')
assert 'beta-check' in prefs and 'tools' in prefs
print('PASS custom assignment and merge persistence',flush=True)
start()
print("EXTENDED CORE PASS",flush=True)

"""Small ADB-only helper for replaying native emulator smoke checks; never touches a physical device."""
import subprocess, sys, re, time
from pathlib import Path
from xml.etree import ElementTree as ET
ADB = r'C:\Users\hanan\AppData\Local\Android\Sdk\platform-tools\adb.exe'
ROOT = Path(__file__).resolve().parent
def adb(*args):
    return subprocess.check_output([ADB, '-s', 'emulator-5554', *args], encoding='utf-8', errors='replace')
def nodes():
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/workout-ui.xml')
    if 'UI hierchary dumped to' not in result:
        raise RuntimeError('No fresh UI hierarchy: ' + result)
    adb('pull', '/sdcard/workout-ui.xml', str(ROOT/'ui.xml'))
    return list(ET.parse(ROOT/'ui.xml').iter('node'))
def click(text):
    found = [n for n in nodes() if n.get('text') == text or n.get('content-desc') == text]
    if not found: raise ValueError(f'No visible UI node: {text}')
    a,b,c,d = map(int,re.findall(r'\d+', found[0].get('bounds')))
    if c <= a or d <= b: raise ValueError('Node has empty bounds')
    adb('shell', 'input', 'tap', str((a+c)//2), str((b+d)//2))
    time.sleep(.5)
for arg in sys.argv[1:]:
    if arg == 'dump': print([(n.get('text') or n.get('content-desc'),n.get('bounds')) for n in nodes() if n.get('text') or n.get('content-desc')])
    elif arg.startswith('click='): click(arg[6:])
    elif arg == 'down': adb('shell','input','swipe','530','1800','530','650','400'); time.sleep(.5)
    elif arg == 'up': adb('shell','input','swipe','530','650','530','1800','400'); time.sleep(.5)
    elif arg == 'back': adb('shell','input','keyevent','4'); time.sleep(.5)
    elif arg.startswith('shot='):
        name=arg[5:]
        if not re.fullmatch(r'[a-z0-9_-]+', name): raise ValueError('Unsafe screenshot name')
        adb('shell','screencap','-p','/sdcard/workout-screen.png')
        adb('pull','/sdcard/workout-screen.png',str(ROOT/(name+'.png')))
    elif arg == 'state':
        (ROOT/'emulator-state.json').write_text(adb('shell','run-as','com.hananel.workout.rebuilt','cat','files/datastore/workout-v3.json'),encoding='utf-8')
    else: raise ValueError(arg)

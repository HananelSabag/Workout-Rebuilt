"""Seed only the dedicated emulator with example ABC data for visual review."""
import json, subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ADB = r'C:\Users\hanan\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PACKAGE = 'com.hananel.workout.rebuilt'
def adb(*args, **kwargs):
    return subprocess.run([ADB, '-s', 'emulator-5554', *args], check=True, capture_output=True, **kwargs)

def exercise(id, name, english, muscle, weight=20):
    return dict(id=id, name=name, nameEn=english, muscle=muscle, sets=3, target=12, targetMax=12, restSeconds=60, weight=weight)

routines = [
    dict(id='A', name='חזה ויד אחורית', shortLabel='A', days=[1], exercises=[exercise('a1','לחיצת חזה','Chest press','CHEST'), exercise('a2','פשיטת מרפקים','Triceps extension','ARMS')]),
    dict(id='B', name='גב ויד קדמית', shortLabel='B', days=[3], exercises=[exercise('b1','משיכת פולי עליון','Lat pulldown','BACK'), exercise('b2','כפיפת מרפקים','Biceps curl','ARMS')]),
    dict(id='C', name='רגליים וכתפיים', shortLabel='C', days=[5], exercises=[exercise('c1','סקוואט','Squat','LEGS',40), exercise('c2','לחיצת כתפיים','Shoulder press','SHOULDERS')])
]
today = datetime.now().astimezone().date()
start = today - timedelta(days=(today.weekday()+1)%7)
history = []
for routine, offset in zip(routines[:2], (1,3)):
    date = start + timedelta(days=offset)
    wall = int(datetime.combine(date, datetime.min.time(), timezone.utc).timestamp()*1000) + 15*3600000
    history.append(dict(id='preview-'+routine['id'], routineId=routine['id'], name=routine['name'], date=str(date), startedAt=wall, lastActionAt=wall+1800000, endedAt=wall+1800000,
        planMode='FLEXIBLE', exercises=[dict(exercise=e,sets=[dict(id=f"{e['id']}-{i}",value=12,weight=e['weight'],at=wall+i*100000) for i in range(3)]) for e in routine['exercises']]))
state = dict(preferences=dict(onboarded=True,mode='FLEXIBLE',language='he',theme='light',notificationsAsked=True,appearanceVersion=1,programName='התוכנית שלי · ABC',flexibleOrder='ORDERED'),
    routines=routines,history=list(reversed(history)),planProgress=dict(completedIds=['A','B'],round=1))
payload=json.dumps(state,ensure_ascii=False).encode('utf-8')
(ROOT/'planning-preview.json').write_bytes(payload)
adb('shell','am','force-stop',PACKAGE)
adb('shell','run-as',PACKAGE,'tee','files/datastore/workout-v3.json', input=payload)
adb('shell','am','start','-n',PACKAGE+'/com.hananel.workout.MainActivity')
print('Seeded ABC preview on emulator-5554 only.')

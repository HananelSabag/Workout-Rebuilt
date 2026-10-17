# Workout 3.4 — exercise-first glass interface

אפליקציית Android חדשה ונפרדת, המבוססת על חוויית Workout Tracker 2.2. הפרויקט המקורי לא שונה.

גרסה 3.2 ממשיכה את רצף התוכנית גם בשבוע חדש, מאפשרת סדר קבוע או בחירה חופשית, ומציגה סימון של האימון מתחת לכל יום. ניהול התוכנית וההיסטוריה נמצאים במסכים נפרדים. סיום כל הסטים מסמן בבירור שהתרגיל הושלם. [התנהגות, מסכים ובדיקות](docs/PROGRAM-3.2.md)

גרסה 3.3: סיום ידני כברירת מחדל, תיקון ערך ומשקל של סט, סבבים יציבים בזמן עריכת תוכנית וניסוי זכוכית עדינה לבחירה. [סקירת המוצר](docs/REVIEW-3.3-HE.md) · [מסירה ובדיקות](docs/DELIVERY-3.3.md)

גרסה 3.4: מסך אימון ממוקד בתרגילים, ניהול תוכנית נפרד, כרטיסים עם משקל שמור ועריכה ישירה, זכוכית כברירת מחדל, בקשת התראות בפתיחה וסגירה אחרי 20 דקות ללא פעילות. [צ׳קליסט ותיעוד](docs/REFINEMENT-3.4-HE.md) · [מסירה ובדיקות](docs/DELIVERY-3.4.md)

## What's included

- Hebrew RTL by default, English or device language; dark, light and system appearance.
- Named programs with customizable body-group or A/B/C workouts; flexible ordered/free selection, or scheduled weekdays.
- Calendar labels distinguish actual complete/partial sessions from scheduled workouts. Each scheduled date is independent.
- Flexible rounds continue across weeks, with persisted progress and a new round after every workout is complete.
- Automatic weekly summary rollover without deleting history, plans or working weights.
- Exercise builder and a 41-exercise bilingual catalogue, custom exercises, ordering and notes.
- Per-set reps/time and weight, target ranges, progress bars, undo, whole-exercise confirmation, last performance and complete workout history.
- Background rest timer with pause/resume, ±15 seconds, skip, notifications and next-set action.
- Optional precise alarms, recurring reminders, backup export/import.
- The original user's logo, and a separate app ID for side-by-side installation.

## Build

Windows PowerShell, with the installed Android Studio JBR and Android SDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

`local.properties` points to the local Android SDK and is excluded from version control. The checked-in wrapper downloads Gradle 8.13. The app compiles/targets SDK 36 and supports API 26+.

The delivery APK is copied to `dist/Workout-3.4.0.apk`. It is an optimized, locally signed release build for direct installation, using the machine's debug key. Do not replace that key between test updates if existing installations must upgrade in place. A store publication would use a separate production signing configuration.

## Documentation

- [Original source audit](docs/ORIGINAL-AUDIT.md)
- [השוואה מלאה בין הגרסאות](docs/COMPARISON-HE.md)
- [Architecture and behavior contract](docs/ARCHITECTURE.md)
- [Verification evidence](docs/VERIFICATION.md)

The new installation starts with a new dataset. It does not migrate or overwrite the old app's private data. Both apps can be installed together.


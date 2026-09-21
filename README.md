# ParkStop

אפליקציית Android (Capacitor, לא PWA) שמתריעה לעצור חניה פעילה (בפנגו,
סלופארק, או כל אפליקציית חניה אחרת) ברגע שאתם חוזרים לרכב. כל הלוגיקה
והנתונים נשארים על המכשיר בלבד — אין שרת, אין backend, אין ענן.

- **Package name:** `com.vplusstudio.parkstop`
- **מזהה חזרה לרכב:** חיבור מחדש לבלוטות׳ הרכב (איתות ראשי, ודאות גבוהה)
  + Geofence סביב מיקום החניה (איתות גיבוי, ודאות נמוכה יותר).
- **הורדת APK מוכן:** ראו [Releases](../../releases/latest) או עמוד
  ההורדה ב-GitHub Pages (`docs/index.html`).

## מבנה הפרויקט

```
www/                     קוד הצד-לקוח (HTML/CSS/JS, RTL עברית)
  index.html / app.js       מסך ראשי — הפעלת/עצירת חניה
  settings.html / settings.js   הגדרות: בלוטות׳ רכב, geofence, אפליקציות חניה
  onboarding.html / onboarding.js  הרשאות + Prominent Disclosure + סוללה
  debug.html / debug.js     יומן Debug בזמן אמת
  js/native.js, storage.js  גשרים ל-Capacitor plugin ול-Preferences

android/                   פרויקט Android נטיבי (Capacitor)
  .../engine/               הפלאגין הנטיבי (Kotlin): Foreground Service,
                             BLE + Geofence, התראות, Quick Settings Tile

.github/workflows/build-apk.yml   בנייה אוטומטית של APK חתום ב-CI
docs/                       עמוד הורדה (GitHub Pages) + מסמכים משפטיים
```

## פיתוח מקומי

```bash
npm install
npx cap sync android
```

לפתיחה ב-Android Studio: `npx cap open android`.
לבנייה ידנית (חתום, דורש הגדרת keystore — ראו למטה):

```bash
cd android
PARKSTOP_KEYSTORE_PATH=... PARKSTOP_KEYSTORE_PASSWORD=... \
PARKSTOP_KEY_ALIAS=... PARKSTOP_KEY_PASSWORD=... \
./gradlew assembleRelease
```

ללא משתני הסביבה האלה, `assembleRelease` ייבנה **ללא חתימה** (לא ניתן
להתקין/לעדכן דרכו). לבנייה מקומית לא-חתומה לבדיקה בלבד: `./gradlew
assembleDebug`.

## בנייה אוטומטית + APK חתום (GitHub Actions)

כל push ל-`main` מריץ בנייה אוטומטית שמפיקה APK **חתום**, זמין כ:
1. Artifact להורדה מדף ה-Run ב-GitHub Actions.
2. GitHub Release בשם "latest" עם קישור קבוע:
   `https://github.com/<owner>/parkstop/releases/latest/download/ParkStop.apk`

**להגדרה חד-פעמית של ה-keystore לחתימה (חובה לפני הבנייה הראשונה) — ראו
את [`docs/RELEASE_SIGNING.md`](docs/RELEASE_SIGNING.md).** אובדן ה-keystore
אחרי פרסום ב-Play Store = לא ניתן יהיה לעדכן את האפליקציה יותר — גבו אותו
גם מקומית.

## מגבלות ידועות / המשך פיתוח מומלץ

- לא נבדק על מכשיר פיזי בסביבת הפיתוח הזו (אין Android SDK/מכשיר זמינים
  כאן) — הבנייה הראשונה דרך GitHub Actions היא גם בדיקת הקומפילציה
  הראשונה. יש להתקין את ה-APK שיופק ולבדוק את כל הזרימה על ה-S25FE בפועל.
- אייקון האפליקציה (`mipmap/ic_launcher*`) הוא עדיין האייקון הגנרי שיצר
  Capacitor — מומלץ להחליף באייקון מותאם בערכת הנושא הסגולה.
- אין boot-safe persistence לזמן ה-anchor של ה-geofence מול טלטולי GPS
  (drift) — אם המרחק הראשוני קופץ, ייתכן שהתראת הגיבוי לא תיפעל בול. אפשר
  לשפר בעתיד עם ממוצע כמה קריאות מיקום.

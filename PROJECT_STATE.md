# PROJECT_STATE.md – ParkStop

## סטטוס כללי

בנייה ראשונית מלאה של האפליקציה בוצעה בשיחה אחת (implementation מקיף).
מאז נבדקה בפועל על מכשיר S25FE פיזי, ועברה ביקורת מוכנות ל-Play Store.
הסביבה עצמה עדיין ללא Android SDK/מכשיר — הבנייה מתבצעת ב-GitHub Actions.

## מה הושלם [x]

- [x] מבנה פרויקט Capacitor (`package.json`, `capacitor.config.json`,
      `www/`), appId `com.vplusstudio.parkstop`.
- [x] 4 מסכי UI מלאים ב-RTL עברית:
      `index.html` (ראשי), `settings.html`, `onboarding.html`, `debug.html`.
      ערכת הנושא עברה מסגול (#7367f0) לאדום+זהב ("Ember & Gold") כדי
      להתאים לאייקון — ראו סעיף היסטוריית תקלות למטה.
- [x] שכבת JS: `native.js` (גשר ל-plugin), `storage.js` (Preferences),
      `common.js` (עזרי UI), + לוגיקה per-screen.
- [x] פרויקט Android נטיבי נוצר אמיתית דרך `npx cap add android` (לא
      נכתב ידנית) — Kotlin support נוסף.
- [x] State machine נטיבי מלא ב-`android/.../engine/`:
      `EngineStore` (SharedPreferences), `EngineEvents` (pub/sub ל-JS),
      `ParkStopForegroundService` (BLE + geofence + watchdog),
      `ParkStopEnginePlugin` (Capacitor plugin), `AlertActionReceiver`,
      `CarDisconnectReceiver` (בונוס: הצעת "התחל חניה?" בניתוק בלוטות'),
      `BootReceiver`, `ParkStopQuickTileService` (Quick Settings Tile),
      `NotificationHelper`, `AppLauncher` (deep link / fallback).
- [x] AndroidManifest: כל ההרשאות הנדרשות + service/receivers/tile.
- [x] `.github/workflows/build-apk.yml` — בנייה אוטומטית, חתימה דרך
      GitHub Secrets, artifact + GitHub Release "latest" עם APK.
- [x] `docs/RELEASE_SIGNING.md` — הנחיות יצירת keystore + הוספת Secrets.
- [x] `docs/index.html` — עמוד הורדה RTL עם כפתורי "פתח את האפליקציה"
      (custom URL scheme) ו-"הורדת ה-APK האחרון" (קישור latest release).
- [x] `docs/legal/*.md` — 4 המסמכים הנדרשים (privacy, terms, cookies,
      accessibility) בעברית פורמלית עם placeholders.
- [x] מעבר נגישות ממוקד (ARIA רק היכן שפונקציונלי: aria-live על סטטוס/
      Toast/Alert, aria-label על switches, aria-hidden על אייקונים
      דקורטיביים).
- [x] בנייה אמיתית ב-GitHub Actions הצליחה (APK + AAB חתומים), Secrets
      הוגדרו, נבדק בפועל על מכשיר S25FE פיזי.
- [x] אייקון מותאם אישית (אוקטגון STOP אדום + P) — `design/icon/`,
      גרסת adaptive icon (foreground/background) עם תיקון קרופ אגרסיבי
      של Samsung One UI, וגרסת store (hi-res 512, ללא alpha).
- [x] GitHub Pages פעיל (`docs/`), עמוד נחיתה מחודש (hero/features/
      how-it-works), README עם קישור הורדה בולט.
- [x] מסך "אודות" ב-`settings.html` עם מספר גרסה אמיתי (דרך
      `@capacitor/app` `getInfo()`).
- [x] Placeholders במסמכים המשפטיים מולאו בפרטים אמיתיים (מייל, שם
      מפתח "vplus studio", מחוז שיפוט תל אביב).
- [x] Feature graphic (1024x500) + טיוטת טקסטים לרישום ב-Play Store
      (`docs/PLAY_STORE_LISTING.md`), ביקורת מוכנות מלאה ל-Play Store
      (כולל `<queries>` ל-package visibility, בניית AAB).
- [x] אייקון האפליקציה מוצג נכון ב-my-site (אגרגטור האפליקציות של
      המשתמש ב-Vercel) — דרך Edge Function צד-שרת ב-my-site שפותר
      אייקונים בלי חסימת CORS; אומת ע"י המשתמש שעובד.
- [x] כלי אבחון ובדיקה על המכשיר (לפי `PARKSTOP_TESTING_SPEC.md` שהמשתמש
      סיפק): יומן אירועים נטיבי משודרג (500 רשומות, `type` מובנה,
      `ALERT_SKIPPED` עם סיבה מוגבלת-שכפול, `GEOFENCE_ENTER/EXIT`,
      `SERVICE_KILLED` מ-`onDestroy`/`onTaskRemoved`, `HEARTBEAT` כל 30
      דק', `BOOT_COMPLETED`, כפתור שיתוף יומן); מסך `status.html` חדש —
      רשימת מוכנות עם כפתורי "תקן" לכל שורה (כולל בדיקת הרשאות פרטנית
      חדשה, לא רק ה-alias המאוחד של Capacitor), כרטיס Samsung עם
      checkbox מתמיד, וכלי בדיקה (התראת בדיקה מיידית/מושהית ב-30 שניות
      דרך AlarmManager, הדמיית חיבור בלוטות', חניית בדיקה בלחיצה אחת).
- [x] כתובת מיקום החניה + כפתור "נווט לרכב": מיקום ההתחלה של החניה
      עובר Reverse Geocoding (`android.location.Geocoder`, בעברית)
      לכתובת רחוב+מספר+עיר, מוצג במסך הראשי כל עוד החניה פעילה. לכידת
      המיקום כבר לא תלויה ב-Geofence מופעל — כך שגם חניה עם בלוטות'
      בלבד מקבלת כתובת. כפתור "נווט לרכב" פותח את אפליקציית הניווט
      ברירת המחדל של המכשיר (`geo:` intent) ישר למיקום החניה.
- [x] הצהרת הנגישות (`docs/legal/accessibility-statement.md`) הושלמה:
      נוספו טלפון (054-6829213), התחייבות זמן טיפול (14 ימי עסקים)
      וסעיף "בדיקת הנגישות" עם תאריך בדיקה עצמית מפורש (25.09.2026,
      מנוסח כבדיקה עצמית של הצוות ולא כבדיקה חיצונית מוסמכת). הטלפון
      הוסר שוב בהמשך לפי בקשת המשתמש (לא חובה חוקית — מייל מספיק).
- [x] תיקון דיוק כתובת החניה: הבקשה למיקום העוגן משתמשת כעת ב-
      `setMaxUpdateAgeMillis(0)` כדי לכפות פיקס טרי (לא מטמון), נשמר
      גם דיוק המיקום (`anchorAccuracyMeters`), וכשדיוק המיקום ירוד
      (>40 מ') הכתובת המוצגת מסומנת "מיקום משוער" עם רדיוס הדיוק —
      חשוב בחניונים סגורים/תת-קרקעיים שבהם GPS לא אמין. נוסף גם
      "שיפור עוגן" אוטומטי: אם תוך 3 הדקות הראשונות מגיע פיקס מיקום
      משמעותית יותר מדויק וקרוב לעוגן המקורי, העוגן והכתובת מתעדכנים
      בשקט לפיקס הטוב יותר.
- [x] אבחון בלוטות׳ משופר: כל אירוע ACL_CONNECTED/DISCONNECTED שמגיע
      ממכשיר שאינו תואם למכשיר הרכב שהוגדר בהגדרות נרשם כעת ביומן
      (שם+כתובת המכשיר שהתקבל מול הכתובת השמורה), במקום שהמקלט יתעלם
      בשקט. גם נוספה אזהרת יומן אם לא נבחר מכשיר רכב כלל בהגדרות בזמן
      הפעלת השירות. זה בתגובה לדיווח שהבלוטות׳ "לעולם לא מזוהה כמחובר"
      — מטרת השינוי היא לאתר את הסיבה האמיתית (מכשיר לא נבחר? כתובת
      לא תואמת? האירוע לא מגיע כלל?) בבדיקת שטח הבאה. גם נוספה הגנה
      מפני SecurityException בגישה ל-`device.address`/`.name` בשני
      המקלטים (הפעלה בלי הרשאת BLUETOOTH_CONNECT).

## מה נשאר / דורש תשומת לב [ ]

- [ ] המשך בדיקת שטח על ה-S25FE לתרחישי קצה (battery optimization
      deep link ספציפי ל-One UI, אמינות ארוכת-טווח של BLE/geofence).
- [ ] צילומי מסך אמיתיים מהמכשיר (מינימום 2) — נדרשים ל-Store Listing,
      עדיין לא קיימים (`docs/PLAY_STORE_LISTING.md`).
- [ ] העלאה בפועל ל-Play Console: closed testing track, Data Safety
      form, הצהרת Background Location (כולל סרטון הדגמה — נדרש ע"י
      Google לכל אפליקציה עם הרשאת מיקום ברקע), והצהרת שימוש-ליבה
      עבור `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (הרשאה מוגבלת שדורשת
      נימוק בטופס ה-Permissions declaration).
- [ ] לוודא ש-targetSdk (כרגע 34) עדיין עומד בדרישת ה-API המינימלית
      העדכנית של Play Console בזמן ההעלאה בפועל — Google מעדכנת את הרף
      הזה מדי שנה; אם ההעלאה תיחסם, להעלות ל-35 ב-`android/variables.gradle`.

## ביקורת מוכנות ל-Play Store (בוצעה)

עברתי על המניפסט, ה-CI, האייקונים והמסמכים המשפטיים מול דרישות Play
Store. ממצאים ותיקונים:

- **תוקן:** מדיניות הפרטיות לא הזכירה את הרשאת `INTERNET` שמוצהרת
  במניפסט (נדרשת טכנית ע"י ה-WebView של Capacitor, ללא שום קריאת רשת
  בפועל בקוד הנטיבי — אומת בחיפוש בקוד) — נוסף הבהרה בטבלת ההרשאות.
- **תקין:** hi-res icon (512x512, RGB ללא alpha) ו-feature graphic
  (1024x500, RGB) — שניהם עומדים בדרישות הפורמט.
- **תקין:** כותרת ותיאור קצר ב-`PLAY_STORE_LISTING.md` בתוך מגבלות
  התווים (22/30 ו-71/80 בהתאמה).
- **תקין:** `<queries>` ל-package visibility, `targetSdk`/`compileSdk`
  34, versionCode אוטומטי (run_number) + versionName תקין (1.0.x),
  build חתום + AAB עולים כ-artifacts מה-CI.
- **תוקן (בשלב כלי האבחון):** ה-foreground service היה מוצהר עם
  `foregroundServiceType="location"` בלבד, למרות שהוא גם מאזין באופן
  שוטף לאירועי בלוטות' ברקע. שונה ל-`"location|connectedDevice"` +
  נוספה הרשאת `FOREGROUND_SERVICE_CONNECTED_DEVICE`, כנדרש ב-Android 14+.

## תקלות שתוקנו (היסטוריה קצרה)

- דפי המשפטי (`docs/legal/*.md`) יצאו מיושרים לשמאל ב-GitHub Pages —
  לא היה להם Jekyll layout, אז ה-build הגולמי לא הוסיף `dir="rtl"`.
  תוקן ע"י `docs/_layouts/legal.html` (RTL + עיצוב תואם לאתר), מאומת
  ע"י build מקומי של Jekyll.
- המשתמש שם לב שהאייקון אדום אבל תוך-האפליקציה סגול. הוצגו 5 כיווני
  צבע כ-Design Artifact, המשתמש בחר ב-"Ember & Gold" (אדום + זהב חם).
  יושם ב-`www/css/style.css` (משתני `:root` + כל ה-`rgba()` הקשיחים
  שהתאימו לסגול/קורל הישן ב-status pills ו-banners; `--color-danger`
  הוזז לגוון בהיר/חם יותר מה-primary כדי שההתראה הדחופה עדיין תבלוט).
  גם נוסף `android/app/src/main/res/values/colors.xml` — התגלה שהקובץ
  לא היה קיים בכלל למרות ש-`styles.xml` מפנה אליו (`@color/colorPrimary`
  וכו'), כך שגם צבע ה-status bar הנטיבי יתאים. אומת ויזואלית עם
  Playwright headless על 4 המסכים לפני ה-commit.
- המשתמש ציין שני באגי עיצוב נוספים: "ParkStop" (טקסט אנגלי) הופיע
  מימין תחת RTL במקום משמאל, וכל הכפתורים/הטקסטים ענקיים ולא מעוצבים.
  הוצגו 5 כיווני לייאאוט, נבחר "Icon Medallion". יושם: סדר ה-DOM
  בכותרת הוחלף (סטטוס ראשון → ימין, brand שני → שמאל תחת dir=rtl) +
  נוסף סמל אוקטגון+P (SVG מוטבע) ליד המילה, ולמעלה בכרטיס הראשי
  במקום כותרת "חניה" רגילה. גם כווצו האלמנטים ה"ענקיים" בפועל
  (`.btn`/`.btn-lg` padding+font, `.nav-tile`, `--radius` 16→14px) —
  משותף לכל המסכים, לא רק למסך הראשי. אומת עם Playwright על 4 המסכים.
- כמה תיקוני UX למסך הסטטוס מבדיקות שטח: הכרטיס של Samsung פתח את
  ההגדרה הכללית של אנדרואיד (כבר מאושרת) במקום את אפליקציית "טיפול
  במכשיר" של Samsung עצמה — תוקן ל-`openSamsungDeviceCare()` (פותח את
  `com.samsung.android.lool` ישירות) + הוראות צעד-אחר-צעד מפורטות יותר
  (כולל אפשרות חלופית: נעילת האפליקציה דרך מסך האפליקציות האחרונות, אם
  ParkStop לא מופיעה ברשימת ה"הוספה" של Samsung כי המערכת עוד לא
  "הכירה" אותה). גם תוקנה גלישת כפתור מחוץ לכרטיס בשורת "פטור
  מאופטימיזציית סוללה" (קלאסיקה של flexbox: `flex-shrink:0` בלי
  `min-width:0` בכותרת השורה).
- **באג משמעותי:** לחיצה על "התחל חניה עכשיו" הפעילה מיד התראת "חזרת
  לרכב" — עוד לפני שהמשתמש זז מהמקום. הסיבה: בדיקת ה-Geofence בדקה
  "האם המרחק כרגע ≤ הרדיוס" בלי לדרוש יציאה קודם מהרדיוס — ומכיוון
  שמפעילים חניה כשעומדים ליד הרכב, התנאי מתקיים כבר בקריאת המיקום
  הראשונה. תוקן ב-`ParkStopForegroundService.kt`: ההתראה מבוססת-Geofence
  מופעלת עכשיו רק על מעבר אמיתי של כניסה-אחרי-יציאה (משתמש ב-tracking
  של GEOFENCE_ENTER/EXIT), ורגע לכידת העוגן "זורע" מצב "בפנים" מיידי כך
  שהקריאה הראשונה לא תיראה כמו כניסה. גם שופר דיוק לכידת העוגן
  (PRIORITY_HIGH_ACCURACY במקום BALANCED, זו קריאה חד-פעמית לא סקר
  חוזר) והתווסף סינון עדכוני מיקום גסים (מעל 100 מ' דיוק).

## החלטות ארכיטקטורה מרכזיות

- **מקור אמת למצב המנוע:** SharedPreferences נטיבי נפרד
  (`EngineStore`, קובץ `parkstop_engine`) — לא תלוי ב-WebView/JS bridge,
  כדי שה-foreground service וה-BroadcastReceivers יעבדו גם כשהאפליקציה
  סגורה.
- **הגדרות משתמש** (מכשיר בלוטות', רדיוס geofence, רשימת אפליקציות)
  נשמרות דרך `@capacitor/preferences` בצד ה-JS, ונשלחות לנתיב הנטיבי רק
  בקריאה ל-`startMonitoring`.
- **State machine נטיבי מפושט:** אין state נפרד ל-PARKING_ACTIVE — ברגע
  שמופעלת חניה המנוע עובר ישר ל-MONITORING (כך שה-JS, שמציג את שניהם
  זהה, תמיד מסונכרן).
- **Anchor ל-geofence נלכד בתוך ה-Service** (לא ב-plugin) כדי ש-quick
  start מ-Notification/Quick Tile יעבוד זהה להפעלה מה-UI.

## Current Focus

באג ה-Geofence הקריטי (התראה מיידית עם הפעלת חניה) תוקן ואומת ע"י
המשתמש בפועל מתוך לוג אמיתי מהמכשיר (ALERT_SKIPPED בתוך רדיוס, מרחק
0 מטר). תוקן גם באג סדר יומן האירועים (האחרון לא הופיע ראשון). מסמכי
המשפטי עודכנו: הצהרת הנגישות כוללת כעת טלפון, זמן טיפול בפניות (14
ימי עסקים) ותאריך בדיקה עצמית מפורש. **תזכורת קבועה: לדווח למשתמש על
מספר הגרסה החדש (`1.0.<run_number>`) אחרי כל תיקון/שינוי.**

השלב הבא: המשך פרוטוקול בדיקות השטח (`PARKSTOP_TESTING_SPEC.md` § 6)
על ה-S25FE — נבדקו עד כה: התראת בדיקה + תיקון ה-Geofence (חלקית, חסר
עדיין מחזור יציאה+כניסה מלא). נותרו: בדיקת ניתוק/חיבור בלוטות' בלבד
(ללא geofence), משלוח התראה עם מסך נעול, שרידות חניה ארוכה (שעה+),
והתאוששות אחרי הפעלה מחדש של המכשיר. בנוסף עדיין פתוחות פעולות שרק
המשתמש יכול לבצע: לצלם מסכי אמת מהמכשיר, ולהעלות בפועל ל-Play Console
(closed testing, Data Safety form, הצהרת background location + סרטון,
נימוק ל-battery optimization permission).

# PROJECT_STATE.md – ParkStop

## סטטוס כללי

בנייה ראשונית מלאה של האפליקציה בוצעה בשיחה אחת (implementation מקיף).
מאז נבדקה בפועל על מכשיר S25FE פיזי, ועברה ביקורת מוכנות ל-Play Store.
הסביבה עצמה עדיין ללא Android SDK/מכשיר — הבנייה מתבצעת ב-GitHub Actions.

## מה הושלם [x]

- [x] מבנה פרויקט Capacitor (`package.json`, `capacitor.config.json`,
      `www/`), appId `com.vplusstudio.parkstop`.
- [x] 4 מסכי UI מלאים ב-RTL עברית, ערכת נושא סגולה (#7367f0):
      `index.html` (ראשי), `settings.html`, `onboarding.html`, `debug.html`.
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
- **להשאיר תחת מעקב (לא באג ודאי):** ה-foreground service מוצהר עם
  `foregroundServiceType="location"` בלבד, אך גם מאזין לאירועי בלוטות'
  ברקע. ברוב המקרים זה תקין (BroadcastReceiver לא דורש הצהרת FGS type
  נפרדת), אבל Play Console מריץ Pre-launch report אוטומטי שיתריע אם
  Google חושבת אחרת — כדאי לשים לב לדוח הזה בהעלאה הראשונה.

## תקלות שתוקנו (היסטוריה קצרה)

- דפי המשפטי (`docs/legal/*.md`) יצאו מיושרים לשמאל ב-GitHub Pages —
  לא היה להם Jekyll layout, אז ה-build הגולמי לא הוסיף `dir="rtl"`.
  תוקן ע"י `docs/_layouts/legal.html` (RTL + עיצוב תואם לאתר), מאומת
  ע"י build מקומי של Jekyll.

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

ביקורת המוכנות ל-Play Store הושלמה (קוד/מניפסט/אייקונים/מסמכים) — ראו
סעיף למעלה. מה שנשאר הוא פעולות שרק המשתמש יכול לבצע: לצלם מסכי אמת
מהמכשיר, ולהעלות בפועל ל-Play Console (closed testing, Data Safety
form, הצהרת background location + סרטון, נימוק ל-battery optimization
permission).

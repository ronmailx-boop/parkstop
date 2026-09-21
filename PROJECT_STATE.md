# PROJECT_STATE.md – ParkStop

## סטטוס כללי

בנייה ראשונית מלאה של האפליקציה בוצעה בשיחה אחת (implementation מקיף).
עדיין **לא נבדק על מכשיר פיזי** — הסביבה הזו ללא Android SDK/מכשיר, כך
שהבנייה הראשונה ב-GitHub Actions היא גם בדיקת הקומפילציה הראשונה.

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

## מה נשאר / דורש תשומת לב [ ]

- [ ] **קריטי:** להריץ בנייה אמיתית ב-GitHub Actions ולוודא שהיא
      מצליחה (אין כאן Android SDK מקומי לבדיקה מקדימה). אם יש שגיאות
      קומפילציה של Kotlin — לתקן על בסיס לוג ה-CI.
- [ ] להגדיר GitHub Secrets לחתימה: `PARKSTOP_KEYSTORE_BASE64`,
      `PARKSTOP_KEYSTORE_PASSWORD`, `PARKSTOP_KEY_ALIAS`,
      `PARKSTOP_KEY_PASSWORD` (ראו `docs/RELEASE_SIGNING.md`) — בלי זה
      ה-workflow ייכשל בכוונה עם הודעת שגיאה ברורה.
- [ ] להתקין את ה-APK על ה-S25FE ולבדוק בפועל: הרשאות, זיהוי בלוטות',
      geofence, התראה + צליל, Quick Settings Tile, battery optimization
      deep link ספציפי ל-One UI.
- [ ] להפעיל GitHub Pages (Settings → Pages → Source: `main` /`docs`)
      כדי ש-`docs/index.html` יהיה נגיש בפועל.
- [ ] להחליף אייקון האפליקציה הגנרי (Capacitor default) באייקון מותאם
      בערכת הנושא הסגולה.
- [ ] לעדכן placeholders במסמכים המשפטיים (תאריך, איש קשר, וכו').

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

השלב הבא: לוודא שה-CI מצליח לבנות APK חתום, ואז להתקין בפועל על ה-S25FE
ולעבור על כל תרחיש (בלוטות', geofence, סוללה, quick tile) ולתקן על בסיס
מה שנצפה במסך ה-Debug Log.

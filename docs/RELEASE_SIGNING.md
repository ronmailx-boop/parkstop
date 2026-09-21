# חתימת APK ל-ParkStop — הגדרה חד-פעמית

ה-workflow ב-`.github/workflows/build-apk.yml` בונה APK **חתום** (signed)
בכל push ל-`main`, ומעלה אותו כ-GitHub Release בשם "latest" (קישור קבוע
להורדה: `https://github.com/<owner>/<repo>/releases/latest/download/ParkStop.apk`).

כדי שזה יעבוד צריך ליצור **פעם אחת** keystore לחתימה, ולשמור אותו כ-GitHub
Secret. **שימו לב: אובדן ה-keystore אחרי שהאפליקציה מפורסמת ב-Play Store
משמעו שלא ניתן יהיה לעדכן את האפליקציה יותר לעולם — Google לא יכולה
לשחזר או להחליף אותו.** גבו אותו מקומית במקום בטוח (לא ב-Git!) בנוסף
לשמירתו כ-Secret.

## שלב 1: יצירת ה-Keystore (פעם אחת בלבד)

יש להריץ בטרמינל מקומי (עם Java מותקן):

```bash
keytool -genkeypair -v \
  -keystore parkstop-release.keystore \
  -alias parkstop \
  -keyalg RSA -keysize 2048 -validity 10000
```

תתבקשו למלא סיסמה ל-keystore, סיסמה למפתח (key), ופרטי בעלים (שם, ארגון
וכו' — אפשר למלא ערכים כלליים, הם לא קריטיים).

**גבו את הקובץ `parkstop-release.keystore` וכן את שתי הסיסמאות במקום בטוח
ופרטי (מנהל סיסמאות / כספת מוצפנת) — לא בתוך ה-Repository.**

## שלב 2: קידוד הקובץ ל-Base64

```bash
base64 -i parkstop-release.keystore | tr -d '\n' > parkstop-release.keystore.base64.txt
```

(ב-macOS אפשר גם `base64 -i parkstop-release.keystore -o out.txt`)

## שלב 3: הוספת GitHub Secrets

ב-GitHub: **Settings → Secrets and variables → Actions → New repository
secret**, והוסיפו 4 secrets:

| שם ה-Secret | ערך |
|---|---|
| `PARKSTOP_KEYSTORE_BASE64` | תוכן הקובץ מ-שלב 2 (כל המחרוזת) |
| `PARKSTOP_KEYSTORE_PASSWORD` | סיסמת ה-keystore שהגדרתם בשלב 1 |
| `PARKSTOP_KEY_ALIAS` | `parkstop` (או alias אחר אם שיניתם) |
| `PARKSTOP_KEY_PASSWORD` | סיסמת ה-key שהגדרתם בשלב 1 |

## שלב 4: הרצה

כל push ל-`main` יריץ בנייה אוטומטית. אפשר גם להריץ ידנית דרך טאב
**Actions → Build signed APK → Run workflow**.

לאחר סיום הבנייה, ה-APK החתום זמין:
1. כ-artifact להורדה ישירות מדף ה-Run ב-Actions.
2. כקובץ מצורף ל-GitHub Release בשם "latest" — קישור קבוע:
   `https://github.com/<owner>/<repo>/releases/latest/download/ParkStop.apk`

## אם ה-Secrets לא מוגדרים

ה-workflow ייכשל במפורש עם הודעת שגיאה ברורה במקום לבנות APK לא חתום —
כדי למנוע בטעות פרסום גרסה ש**לא ניתן יהיה לעדכן** בעתיד (חתימה שונה בין
גרסאות = לא ניתן להתקין כעדכון על גרסה קיימת).

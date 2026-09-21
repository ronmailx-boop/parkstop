package com.vplusstudio.parkstop.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vplusstudio.parkstop.MainActivity
import com.vplusstudio.parkstop.R

object NotificationHelper {
    const val CHANNEL_SERVICE = "parkstop_service"
    const val CHANNEL_ALERT = "parkstop_alert"

    const val SERVICE_NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID = 1002
    const val SUGGEST_START_NOTIFICATION_ID = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "מעקב חניה פעיל",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "התראה קבועה בזמן שהמעקב אחרי חזרה לרכב פעיל"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "תזכורות חניה",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "התראה כשמזוהה שחזרת לרכב"
            enableVibration(true)
            val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildServiceNotification(context: Context, appName: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ParkStop פעיל")
            .setContentText("עוקב אחרי החזרה לרכב (חניה: $appName)")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent(context))
            .build()
    }

    fun buildAlertNotification(
        context: Context,
        confidence: String,
        appName: String,
        appPackage: String,
        deepLink: String
    ): Notification {
        val title = if (confidence == EngineStore.Confidence.HIGH) {
            "הגעת לרכב! זמן לעצור את החניה"
        } else {
            "נראה שחזרת לאזור החניה?"
        }
        val text = "לא לשכוח לעצור את החניה ב-$appName"

        val stopIntent = Intent(context, AlertActionReceiver::class.java).setAction(Actions.ACTION_STOP_PARKING)
        val stopPending = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlertActionReceiver::class.java)
            .setAction(Actions.ACTION_SNOOZE_ALERT)
            .putExtra(Actions.EXTRA_SNOOZE_MINUTES, 5)
        val snoozePending = PendingIntent.getBroadcast(
            context, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, "עצרתי את החניה", stopPending)
            .addAction(0, "עוד 5 דקות", snoozePending)

        val openAppIntent = AppLauncher.buildLaunchIntent(context, appPackage, deepLink)
        if (openAppIntent != null) {
            val openAppPending = PendingIntent.getActivity(
                context, 3, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "פתח את $appName", openAppPending)
        }

        return builder.build()
    }

    fun buildSuggestStartNotification(context: Context): Notification {
        val startIntent = Intent(context, AlertActionReceiver::class.java).setAction(Actions.ACTION_QUICK_START)
        val startPending = PendingIntent.getBroadcast(
            context, 4, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(context, AlertActionReceiver::class.java).setAction(Actions.ACTION_DISMISS_SUGGESTION)
        val dismissPending = PendingIntent.getBroadcast(
            context, 5, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("התנתקת מבלוטות׳ הרכב")
            .setContentText("התחלת חניה עכשיו?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, "כן, התחל חניה", startPending)
            .addAction(0, "לא, תודה", dismissPending)
            .build()
    }

    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}

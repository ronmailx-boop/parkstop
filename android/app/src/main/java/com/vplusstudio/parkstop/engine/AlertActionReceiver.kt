package com.vplusstudio.parkstop.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles taps on notification action buttons (works even if the app process was killed). */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Actions.ACTION_STOP_PARKING -> EngineActions.stopParking(context)
            Actions.ACTION_SNOOZE_ALERT -> EngineActions.snooze(context, intent.getIntExtra(Actions.EXTRA_SNOOZE_MINUTES, 5))
            Actions.ACTION_QUICK_START -> {
                NotificationHelper.cancel(context, NotificationHelper.SUGGEST_START_NOTIFICATION_ID)
                EngineActions.startWithLastUsedApp(context)
            }
            Actions.ACTION_DISMISS_SUGGESTION -> NotificationHelper.cancel(context, NotificationHelper.SUGGEST_START_NOTIFICATION_ID)
            Actions.ACTION_TEST_ALERT -> {
                NotificationHelper.ensureChannels(context)
                val notification = NotificationHelper.buildAlertNotification(
                    context,
                    EngineStore.Confidence.HIGH,
                    EngineStore.getParkingAppName(context).ifBlank { "אפליקציית חניה" },
                    EngineStore.getParkingAppPackage(context),
                    EngineStore.getParkingAppDeepLink(context)
                )
                NotificationHelper.notify(context, NotificationHelper.ALERT_NOTIFICATION_ID, notification)
                val entry = EngineStore.appendLog(context, "info", "התראת בדיקה מושהית נשלחה", EngineStore.EventType.TEST_ALERT)
                EngineEvents.postLogAdded(entry)
            }
        }
    }
}

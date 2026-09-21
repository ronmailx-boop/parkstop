package com.vplusstudio.parkstop.engine

/** Broadcast action names + intent extra keys shared across the engine. */
object Actions {
    const val ACTION_STOP_PARKING = "com.vplusstudio.parkstop.action.STOP_PARKING"
    const val ACTION_SNOOZE_ALERT = "com.vplusstudio.parkstop.action.SNOOZE_ALERT"
    const val ACTION_QUICK_START = "com.vplusstudio.parkstop.action.QUICK_START"
    const val ACTION_DISMISS_SUGGESTION = "com.vplusstudio.parkstop.action.DISMISS_SUGGESTION"

    const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
}

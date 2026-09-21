package com.vplusstudio.parkstop.engine

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Resolves the best way to jump into the user's chosen parking app. */
object AppLauncher {
    fun buildLaunchIntent(context: Context, packageName: String, deepLink: String): Intent? {
        if (deepLink.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    return intent
                }
            } catch (e: Exception) {
                // fall through to package-based launch below
            }
        }

        if (packageName.isNotBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launchIntent
            }
            return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return null
    }
}

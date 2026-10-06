package com.gigbudget.app.autoimport

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gigbudget.app.BudgetApp
import com.gigbudget.app.MainActivity
import com.gigbudget.app.R
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.Money
import java.util.concurrent.TimeUnit

/** Heads-up alerts: after pay comes in, say how much the Savings Scout thinks you can safely save. */
object ScoutNotifier {
    private const val CHANNEL_ID = "savings_scout"
    private const val NOTIFICATION_ID = 1001
    /** Dasher pay can arrive per order; don't nag more than once every few hours. */
    private val MIN_GAP_MS = TimeUnit.HOURS.toMillis(3)

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    suspend fun afterPayout(app: BudgetApp, income: Income) {
        val settings = app.settings.state.value
        val now = System.currentTimeMillis()
        if (!settings.scoutAlerts || !canPost(app) || now - settings.lastScoutAlertAt < MIN_GAP_MS) return
        val db = app.db
        val scout = BudgetMath.savingsScout(settings, db.incomeDao().list(), db.expenseDao().list(), db.billDao().list(), db.debtDao().list())
        if (scout.amountCents == 0L) return
        app.settings.update { it.copy(lastScoutAlertAt = now) }
        post(
            app,
            title = "+${Money.format(income.amountCents)} from ${income.source} 💜",
            text = "Savings Scout: you can safely save ${Money.format(scout.amountCents)} today. Tap to put it toward a goal.",
        )
    }

    private fun post(context: Context, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Savings Scout", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Suggests a safe amount to save after you get paid"
            }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_GOALS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post; nothing to do.
        }
    }
}

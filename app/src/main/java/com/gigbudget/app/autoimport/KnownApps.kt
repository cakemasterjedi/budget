package com.gigbudget.app.autoimport

import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.SettingsStore
import com.gigbudget.app.data.WatchedApp

/**
 * [incomeSource] tags money coming into that app even when the notification doesn't name the gig.
 * [sinceVersion] is the known-apps list version that added the app, so existing installs pick it up.
 */
data class KnownApp(
    val packageName: String,
    val label: String,
    val role: String,
    val incomeSource: String? = null,
    val sinceVersion: Int = 1,
)

/**
 * Apps pre-loaded into the auto-import list. Any other app that posts a notification with a
 * dollar amount also shows up in Settings (switched off) so it can be turned on there.
 */
object KnownApps {
    val defaults = listOf(
        // Gig pay
        KnownApp("com.doordash.driverapp", "DoorDash Dasher", Roles.DOORDASH),
        // DoorDash's debit card: payouts in are DoorDash income, card purchases are spending.
        KnownApp("com.payfare.doordash", "DasherDirect", Roles.BANK, IncomeSources.DOORDASH),
        KnownApp("com.walmart.sparkdriver", "Spark Driver", Roles.SPARK),

        // Banks & payment apps -> spending out, deposits / money received in
        KnownApp("com.squareup.cash", "Cash App", Roles.BANK),
        KnownApp("com.onedebit.chime", "Chime", Roles.BANK),
        KnownApp("com.paypal.android.p2pmobile", "PayPal", Roles.BANK),
        KnownApp("com.venmo", "Venmo", Roles.BANK),
        KnownApp("com.google.android.apps.walletnfcrel", "Google Wallet", Roles.BANK),
        KnownApp("com.chase.sig.android", "Chase", Roles.BANK),
        KnownApp("com.konylabs.capitalone", "Capital One", Roles.BANK),
        KnownApp("com.wf.wellsfargomobile", "Wells Fargo", Roles.BANK),
        KnownApp("com.infonow.bofa", "Bank of America", Roles.BANK),
        KnownApp("com.usaa.mobile.android.usaa", "USAA", Roles.BANK),
        KnownApp("com.varomoney.bank", "Varo", Roles.BANK),
        // Walmart's OnePay (formerly ONE), where many Spark drivers get paid. Deposits that name
        // Spark or Walmart count as Spark pay; card purchases are spending.
        KnownApp("com.onefinance.one", "OnePay", Roles.BANK, sinceVersion = 2),

        // Bank text alerts arrive through the messaging app. Off by default; turn on if your bank texts you.
        KnownApp("com.google.android.apps.messaging", "Messages (bank texts)", Roles.IGNORE),
        KnownApp("com.samsung.android.messaging", "Samsung Messages (bank texts)", Roles.IGNORE),
    )

    /** Bump when adding to [defaults]; tag the new entries with this number as [KnownApp.sinceVersion]. */
    const val VERSION = 2

    fun incomeSourceFor(packageName: String): String? = defaults.firstOrNull { it.packageName == packageName }?.incomeSource

    /**
     * Brings an existing install's list up to date with apps added since it was created. A new app is
     * added with its default setting; if it had already shown up on its own (switched off), it's
     * switched to its default once. After that, whatever Niome picks for it is left alone.
     */
    suspend fun sync(db: AppDatabase, settings: SettingsStore) {
        val seen = settings.state.value.knownAppsVersion
        if (seen >= VERSION) return
        val dao = db.watchedAppDao()
        defaults.filter { it.sinceVersion > seen }.forEach { known ->
            val existing = dao.get(known.packageName)
            when {
                existing == null -> dao.insertIgnore(WatchedApp(known.packageName, known.label, known.role))
                existing.role == Roles.IGNORE -> dao.update(existing.copy(label = known.label, role = known.role))
            }
        }
        settings.update { it.copy(knownAppsVersion = VERSION) }
    }
}

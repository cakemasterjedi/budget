package com.gigbudget.app.autoimport

import com.gigbudget.app.data.Roles

import com.gigbudget.app.data.IncomeSources

/** [incomeSource] tags money coming into that app even when the notification doesn't name the gig. */
data class KnownApp(val packageName: String, val label: String, val role: String, val incomeSource: String? = null)

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

        // Bank text alerts arrive through the messaging app. Off by default; turn on if your bank texts you.
        KnownApp("com.google.android.apps.messaging", "Messages (bank texts)", Roles.IGNORE),
        KnownApp("com.samsung.android.messaging", "Samsung Messages (bank texts)", Roles.IGNORE),
    )

    fun incomeSourceFor(packageName: String): String? = defaults.firstOrNull { it.packageName == packageName }?.incomeSource
}

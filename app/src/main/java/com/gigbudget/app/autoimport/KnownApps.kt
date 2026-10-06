package com.gigbudget.app.autoimport

import com.gigbudget.app.data.Roles

data class KnownApp(val packageName: String, val label: String, val role: String)

/**
 * Apps pre-loaded into the auto-import list. Any other app that posts a notification with a
 * dollar amount also shows up in Settings (switched off) so it can be turned on there.
 */
object KnownApps {
    val defaults = listOf(
        // Gig pay
        KnownApp("com.doordash.driverapp", "DoorDash Dasher", Roles.DOORDASH),
        // DasherDirect usually repeats the same payouts as the Dasher app, so it starts off
        // to avoid counting money twice. Switch it on if you'd rather track pay from there.
        KnownApp("com.payfare.doordash", "DasherDirect", Roles.IGNORE),
        KnownApp("com.walmart.sparkdriver", "Spark Driver", Roles.SPARK),

        // Banks & payment apps -> spending
        KnownApp("com.squareup.cash", "Cash App", Roles.SPENDING),
        KnownApp("com.onedebit.chime", "Chime", Roles.SPENDING),
        KnownApp("com.paypal.android.p2pmobile", "PayPal", Roles.SPENDING),
        KnownApp("com.venmo", "Venmo", Roles.SPENDING),
        KnownApp("com.google.android.apps.walletnfcrel", "Google Wallet", Roles.SPENDING),
        KnownApp("com.chase.sig.android", "Chase", Roles.SPENDING),
        KnownApp("com.konylabs.capitalone", "Capital One", Roles.SPENDING),
        KnownApp("com.wf.wellsfargomobile", "Wells Fargo", Roles.SPENDING),
        KnownApp("com.infonow.bofa", "Bank of America", Roles.SPENDING),
        KnownApp("com.usaa.mobile.android.usaa", "USAA", Roles.SPENDING),
        KnownApp("com.varomoney.bank", "Varo", Roles.SPENDING),

        // Bank text alerts arrive through the messaging app. Off by default; turn on if your bank texts you.
        KnownApp("com.google.android.apps.messaging", "Messages (bank texts)", Roles.IGNORE),
        KnownApp("com.samsung.android.messaging", "Samsung Messages (bank texts)", Roles.IGNORE),
    )
}

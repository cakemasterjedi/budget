package com.gigbudget.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gigbudget.app.autoimport.KnownApps
import com.gigbudget.app.autoimport.NotificationParser
import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.SettingsStore
import com.gigbudget.app.data.WatchedApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnownAppsSyncTest {
    private val onePay = "com.onefinance.one"
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
    }

    @After fun tearDown() = db.close()

    @Test fun existingInstallGetsOnePay() = runBlocking {
        KnownApps.sync(db, settings)
        assertEquals(Roles.BANK, db.watchedAppDao().get(onePay)!!.role)
        assertEquals("OnePay", db.watchedAppDao().get(onePay)!!.label)
        assertEquals(KnownApps.VERSION, settings.state.value.knownAppsVersion)
    }

    @Test fun installOnTheOnePayVersionGetsHuntington() = runBlocking {
        settings.update { it.copy(knownAppsVersion = 2) }
        KnownApps.sync(db, settings)
        assertEquals(Roles.BANK, db.watchedAppDao().get("com.huntington.m")!!.role)
        assertEquals(null, db.watchedAppDao().get(onePay)) // already handled by version 2, not re-added
    }

    @Test fun huntingtonPurchaseIsSpending() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Huntington", "A $18.42 debit card purchase was made at SHELL OIL")!!
        assertEquals(NotificationParser.Kind.EXPENSE, r.kind)
        assertEquals(1_842L, r.amountCents)
    }

    @Test fun autoDetectedOnePayIsSwitchedOnOnceThenLeftAlone() = runBlocking {
        db.watchedAppDao().insertIgnore(WatchedApp(onePay, "com.onefinance.one", Roles.IGNORE, 5, "You spent $4"))
        KnownApps.sync(db, settings)
        assertEquals(Roles.BANK, db.watchedAppDao().get(onePay)!!.role)

        // Niome turns it off afterwards: later starts don't flip it back.
        db.watchedAppDao().update(db.watchedAppDao().get(onePay)!!.copy(role = Roles.IGNORE))
        KnownApps.sync(db, settings)
        assertEquals(Roles.IGNORE, db.watchedAppDao().get(onePay)!!.role)
    }

    @Test fun existingChoiceForOnePayIsKept() = runBlocking {
        db.watchedAppDao().insertIgnore(WatchedApp(onePay, "OnePay", Roles.SPENDING))
        KnownApps.sync(db, settings)
        assertEquals(Roles.SPENDING, db.watchedAppDao().get(onePay)!!.role)
    }

    @Test fun onePaySparkDepositsCountAsSpark() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "OnePay", "You received $142.18 from Walmart Spark Driver",
            KnownApps.incomeSourceFor(onePay) ?: IncomeSources.OTHER)!!
        assertEquals(IncomeSources.SPARK, r.incomeSource)
        assertEquals(14_218L, r.amountCents)
    }
}

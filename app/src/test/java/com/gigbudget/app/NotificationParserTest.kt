package com.gigbudget.app

import com.gigbudget.app.autoimport.NotificationParser
import com.gigbudget.app.autoimport.NotificationParser.Kind
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Roles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    @Test fun amounts() {
        assertEquals(123450L, NotificationParser.firstAmountCents("total $1,234.5 today"))
        assertEquals(2500L, NotificationParser.firstAmountCents("You paid $25 to Bob"))
        assertEquals(799L, NotificationParser.firstAmountCents("$ 7.99"))
        assertNull(NotificationParser.firstAmountCents("no money here"))
    }

    @Test fun doordashEarnings() {
        val r = NotificationParser.parse(Roles.DOORDASH, "Dash complete", "You earned $45.67 this dash")!!
        assertEquals(Kind.INCOME, r.kind)
        assertEquals(4567L, r.amountCents)
        assertEquals(IncomeSources.DOORDASH, r.incomeSource)
    }

    @Test fun doordashOfferIsNotIncome() {
        assertNull(NotificationParser.parse(Roles.DOORDASH, "New order", "$8.50 est. pay for 3.2 mi"))
        assertNull(NotificationParser.parse(Roles.DOORDASH, "Peak Pay", "Earn an extra $2.00 per delivery"))
    }

    @Test fun sparkDeposit() {
        val r = NotificationParser.parse(Roles.SPARK, "Earnings deposited", "$120.00 has been deposited to your account")!!
        assertEquals(IncomeSources.SPARK, r.incomeSource)
        assertEquals(12000L, r.amountCents)
    }

    @Test fun sparkOfferIsNotIncome() {
        assertNull(NotificationParser.parse(Roles.SPARK, "New offer", "Shopping & Delivery · $24.50 guaranteed"))
    }

    @Test fun bankGasPurchase() {
        val r = NotificationParser.parse(Roles.SPENDING, "Chase", "You made a $12.34 debit card transaction at SHELL OIL 57444 on Oct 4")!!
        assertEquals(Kind.EXPENSE, r.kind)
        assertEquals(1234L, r.amountCents)
        assertEquals("SHELL OIL 57444", r.merchant)
        assertEquals(Categories.GAS, r.category)
    }

    @Test fun dispensaryIsPreroll() {
        val r = NotificationParser.parse(Roles.SPENDING, "Cash App", "You paid $25 to Green Dragon Dispensary")!!
        assertEquals(Categories.PREROLL, r.category)
        assertEquals("Green Dragon Dispensary", r.merchant)
    }

    @Test fun liquorStoreIsBottle() {
        val r = NotificationParser.parse(Roles.SPENDING, "Card used", "Purchase at Total Wine & More for $32.10")!!
        assertEquals(Categories.BOTTLE, r.category)
        assertEquals(3210L, r.amountCents)
    }

    @Test fun moneyInIsNotSpending() {
        assertNull(NotificationParser.parse(Roles.SPENDING, "Chime", "Deposit received: $300.00"))
        assertNull(NotificationParser.parse(Roles.SPENDING, "Venmo", "Jake paid you $20.00"))
        assertNull(NotificationParser.parse(Roles.SPENDING, "Cash App", "Mom sent you $50"))
        assertNull(NotificationParser.parse(Roles.SPENDING, "Capital One", "Your payment of $40.00 is due Oct 10"))
    }

    @Test fun ignoredRole() {
        assertNull(NotificationParser.parse(Roles.IGNORE, "Chase", "You spent $10 at Shell"))
    }

    @Test fun categoryWordBoundaries() {
        assertEquals(Categories.OTHER, NotificationParser.guessCategory("Mobile accessories store"))
        assertEquals(Categories.OTHER, NotificationParser.guessCategory("Entire Home Decor"))
        assertEquals(Categories.GAS, NotificationParser.guessCategory("BP #1234"))
    }
}

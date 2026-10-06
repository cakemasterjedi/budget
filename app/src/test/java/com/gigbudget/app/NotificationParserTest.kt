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
        val r = NotificationParser.parseOrNull(Roles.DOORDASH, "Dash complete", "You earned $45.67 this dash")!!
        assertEquals(Kind.INCOME, r.kind)
        assertEquals(4567L, r.amountCents)
        assertEquals(IncomeSources.DOORDASH, r.incomeSource)
    }

    @Test fun doordashOfferIsNotIncome() {
        assertNull(NotificationParser.parseOrNull(Roles.DOORDASH, "New order", "$8.50 est. pay for 3.2 mi"))
        assertNull(NotificationParser.parseOrNull(Roles.DOORDASH, "Peak Pay", "Earn an extra $2.00 per delivery"))
    }

    @Test fun sparkDeposit() {
        val r = NotificationParser.parseOrNull(Roles.SPARK, "Earnings deposited", "$120.00 has been deposited to your account")!!
        assertEquals(IncomeSources.SPARK, r.incomeSource)
        assertEquals(12000L, r.amountCents)
    }

    @Test fun sparkOfferIsNotIncome() {
        assertNull(NotificationParser.parseOrNull(Roles.SPARK, "New offer", "Shopping & Delivery · $24.50 guaranteed"))
    }

    @Test fun bankGasPurchase() {
        val r = NotificationParser.parseOrNull(Roles.SPENDING, "Chase", "You made a $12.34 debit card transaction at SHELL OIL 57444 on Oct 4")!!
        assertEquals(Kind.EXPENSE, r.kind)
        assertEquals(1234L, r.amountCents)
        assertEquals("SHELL OIL 57444", r.merchant)
        assertEquals(Categories.GAS, r.category)
    }

    @Test fun dispensaryIsPreroll() {
        val r = NotificationParser.parseOrNull(Roles.SPENDING, "Cash App", "You paid $25 to Green Dragon Dispensary")!!
        assertEquals(Categories.PREROLL, r.category)
        assertEquals("Green Dragon Dispensary", r.merchant)
    }

    @Test fun liquorStoreIsBottle() {
        val r = NotificationParser.parseOrNull(Roles.SPENDING, "Card used", "Purchase at Total Wine & More for $32.10")!!
        assertEquals(Categories.BOTTLE, r.category)
        assertEquals(3210L, r.amountCents)
    }

    @Test fun spendingOnlyIgnoresMoneyIn() {
        assertNull(NotificationParser.parseOrNull(Roles.SPENDING, "Chime", "Deposit received: $300.00"))
        assertNull(NotificationParser.parseOrNull(Roles.SPENDING, "Venmo", "Jake paid you $20.00"))
        assertNull(NotificationParser.parseOrNull(Roles.SPENDING, "Cash App", "Mom sent you $50"))
        assertNull(NotificationParser.parseOrNull(Roles.SPENDING, "Capital One", "Your payment of $40.00 is due Oct 10"))
        val skip = NotificationParser.parse(Roles.SPENDING, "Chime", "Deposit received: $300.00") as NotificationParser.Parsed.Skip
        assertEquals(30000L, skip.amountCents)
    }

    @Test fun bankDepositFromDoorDash() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Chime", "Direct deposit of $312.45 from DOORDASH INC has posted")!!
        assertEquals(Kind.INCOME, r.kind)
        assertEquals(31245L, r.amountCents)
        assertEquals(IncomeSources.DOORDASH, r.incomeSource)
        assertEquals("DOORDASH INC", r.merchant)
    }

    @Test fun bankDepositFromWalmartIsSpark() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Deposit", "You received $88.10 from Walmart Spark")!!
        assertEquals(IncomeSources.SPARK, r.incomeSource)
    }

    @Test fun moneySentToYouIsOtherIncome() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Venmo", "Jake paid you $20.00")!!
        assertEquals(Kind.INCOME, r.kind)
        assertEquals(IncomeSources.OTHER, r.incomeSource)
        assertEquals("Jake", r.merchant)
    }

    @Test fun dasherDirectPayUsesDefaultSource() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Payment", "You've been paid $54.20", IncomeSources.DOORDASH)!!
        assertEquals(IncomeSources.DOORDASH, r.incomeSource)
    }

    @Test fun bankAppStillTracksSpending() {
        val r = NotificationParser.parseOrNull(Roles.BANK, "Cash App", "You paid $25 to Green Dragon Dispensary")!!
        assertEquals(Kind.EXPENSE, r.kind)
        assertEquals(Categories.PREROLL, r.category)
    }

    @Test fun bankSkipsNonTransactions() {
        assertNull(NotificationParser.parseOrNull(Roles.BANK, "Capital One", "Your payment of $40.00 is due Oct 10"))
        assertNull(NotificationParser.parseOrNull(Roles.BANK, "Capital One", "We received your payment of $40.00"))
        assertNull(NotificationParser.parseOrNull(Roles.BANK, "Chase", "$200.00 transferred from your savings account"))
        assertNull(NotificationParser.parseOrNull(Roles.BANK, "Chime", "Your card was declined for $9.99 at Shell"))
        assertNull(NotificationParser.parseOrNull(Roles.BANK, "Cash App", "Jake requested $15"))
        val skip = NotificationParser.parse(Roles.BANK, "Chase", "Your balance is $120.00") as NotificationParser.Parsed.Skip
        assertEquals("Reminder, promo or declined charge", skip.reason)
    }

    @Test fun ignoredRole() {
        assertNull(NotificationParser.parseOrNull(Roles.IGNORE, "Chase", "You spent $10 at Shell"))
    }

    @Test fun categoryWordBoundaries() {
        assertEquals(Categories.OTHER, NotificationParser.guessCategory("Mobile accessories store"))
        assertEquals(Categories.OTHER, NotificationParser.guessCategory("Entire Home Decor"))
        assertEquals(Categories.GAS, NotificationParser.guessCategory("BP #1234"))
    }
}

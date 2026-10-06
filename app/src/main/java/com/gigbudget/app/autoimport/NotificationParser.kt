package com.gigbudget.app.autoimport

import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Roles

/**
 * Turns a notification's title/text into an income or expense, based on what role the posting
 * app has. Pure Kotlin so it can be unit tested without a device.
 */
object NotificationParser {

    enum class Kind { INCOME, EXPENSE }

    data class Result(
        val kind: Kind,
        val amountCents: Long,
        val merchant: String = "",
        val category: String = Categories.OTHER,
        val incomeSource: String = IncomeSources.OTHER,
    )

    private val AMOUNT = Regex("""\$\s?(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?""")

    // Each entry is a regex fragment matched at a word start (prefix match: "deposit" also hits "deposited").
    private val GIG_PAY = words("earned", "you made", "you've made", "paid", "pay is in", "payout", "payment",
        "deposit", "transfer", "cash out", "cashed out", "tip")
    private val GIG_NOT_PAY = words("offer", "est\\b", "estimated", "guaranteed", "accept", "new order",
        "earn up to", "earn an extra", "earn extra", "could earn", "can earn", "challenge", "promo",
        "peak pay", "incentive", "bonus opportunit", "schedule", "busy", "available")

    private val SPEND = words("spent", "purchase", "you paid", "paid", "payment", "charge", "debit",
        "transaction", "you sent", "sent", "withdraw", "card was used", "used your card", "used at",
        "pending", "approved", "authorized")
    private val SPEND_NOT = words("received", "deposit", "paid you", "sent you", "credited", "refund",
        "declined", "due\\b", "request", "reward", "cash back", "cashback", "get \\$", "save \\$", "statement",
        "reminder", "direct dep")

    private val MERCHANT = Regex(
        """\b(?:at|to|@)\s+([A-Za-z0-9&'*#.\- ]{2,40}?)(?=\s+(?:on|for|with|using|was|is|has|in)\b|[.,!;:]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val CATEGORY_KEYWORDS = listOf(
        Categories.PREROLL to listOf("dispensary", "cannabis", "pre-roll", "preroll", "pre roll", "trulieve",
            "curaleaf", "muv", "zen leaf", "sunnyside", "planet 13", "the botanist", "verano", "green dragon",
            "beyond hello", "smoke shop", "weed", "420"),
        Categories.BOTTLE to listOf("liquor", "wine", "spirits", "total wine", "bevmo", "abc store", "abc fine",
            "package store", "beer", "bottle shop", "binny", "spec's"),
        Categories.GAS to listOf("shell", "exxon", "mobil", "chevron", "bp", "speedway", "circle k", "quiktrip",
            "wawa", "sheetz", "marathon", "sunoco", "valero", "racetrac", "raceway", "murphy", "love's",
            "pilot", "casey", "7-eleven", "7 eleven", "citgo", "fuel", "gas"),
        Categories.FOOD to listOf("mcdonald", "wendy", "burger", "taco bell", "chick-fil-a", "chick fil a",
            "starbucks", "dunkin", "subway", "pizza", "domino", "popeyes", "kfc", "sonic", "arby", "chipotle",
            "panera", "restaurant", "cafe", "grill", "uber eats", "grubhub"),
        Categories.GROCERIES to listOf("walmart", "kroger", "aldi", "publix", "target", "safeway", "food lion",
            "h-e-b", "heb", "meijer", "winco", "grocery", "costco", "sam's club", "dollar general", "family dollar"),
        Categories.CAR to listOf("autozone", "o'reilly", "advance auto", "jiffy lube", "valvoline", "car wash",
            "tire", "geico", "progressive", "state farm", "allstate"),
        Categories.PHONE to listOf("t-mobile", "verizon", "at&t", "metro by", "cricket", "boost mobile", "mint mobile"),
        Categories.BILLS to listOf("electric", "utility", "comcast", "xfinity", "spectrum", "netflix", "spotify", "hulu"),
    )

    fun mentionsMoney(text: String): Boolean = AMOUNT.containsMatchIn(text)

    fun firstAmountCents(text: String): Long? {
        val match = AMOUNT.find(text) ?: return null
        val dollars = match.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val fraction = match.groupValues[2]
        val cents = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return dollars * 100 + cents
    }

    fun parse(role: String, title: String, text: String): Result? {
        val full = "$title. $text"
        val amount = firstAmountCents(full)?.takeIf { it > 0 } ?: return null
        val lower = full.lowercase()
        return when (role) {
            Roles.DOORDASH, Roles.SPARK -> {
                if (GIG_NOT_PAY.containsMatchIn(lower) || !GIG_PAY.containsMatchIn(lower)) return null
                val source = if (role == Roles.DOORDASH) IncomeSources.DOORDASH else IncomeSources.SPARK
                Result(Kind.INCOME, amount, incomeSource = source)
            }
            Roles.SPENDING -> {
                if (SPEND_NOT.containsMatchIn(lower) || !SPEND.containsMatchIn(lower)) return null
                val merchant = MERCHANT.find(text)?.groupValues?.get(1)?.trim()
                    ?: MERCHANT.find(title)?.groupValues?.get(1)?.trim()
                    ?: ""
                Result(Kind.EXPENSE, amount, merchant = merchant, category = guessCategory("$merchant $lower"))
            }
            else -> null
        }
    }

    fun guessCategory(text: String): String {
        val haystack = " ${text.lowercase()} "
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { Regex("""(?<![a-z])${Regex.escape(it)}(?![a-z])""").containsMatchIn(haystack) }) {
                return category
            }
        }
        return Categories.OTHER
    }

    private fun words(vararg fragments: String) = Regex("""\b(?:${fragments.joinToString("|")})""")
}

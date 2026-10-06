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
    private val NOT_A_TRANSACTION = words("declined", "failed", "due\\b", "request", "reward", "cash back", "cashback",
        "get \\$", "save \\$", "statement", "reminder", "refund", "low balance", "balance is", "upcoming")
    private val BILL_PAYMENT = words("received your payment", "payment received", "payment posted", "thank you for your payment",
        "autopay")
    private val OWN_TRANSFER = words("transfer(?:red)? from your", "from (?:your )?savings", "from (?:your )?checking",
        "between your accounts", "transfer(?:red)? to your (?:savings|checking)")
    private val MONEY_IN = words("received", "deposit", "paid you", "sent you", "credited", "direct dep",
        "you've been paid", "you have been paid", "you got paid", "got paid", "added to your", "incoming", "money in")
    private val DOORDASH_WORDS = words("doordash", "door dash", "dasher", "payfare")
    private val SPARK_WORDS = words("spark\\b", "walmart")

    /** "Deposit of $300.00 from DOORDASH INC." -> "DOORDASH INC" */
    private val PAYER_FROM = Regex("""\bfrom\s+([A-Za-z0-9&'.\- ]{2,40}?)(?=\s+(?:on|for|to|into|in|was|has)\b|[.,!;:]|\s*$)""", RegexOption.IGNORE_CASE)
    /** "Jake paid you $20" -> "Jake" */
    private val PAYER_PAID_YOU = Regex("""^\W*([A-Za-z][A-Za-z.' ]{0,30}?)\s+(?:paid|sent) you""", RegexOption.IGNORE_CASE)

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

    /** Either an income/expense to record, or why the notification was skipped. */
    sealed interface Parsed {
        data class Match(val result: Result) : Parsed
        data class Skip(val reason: String, val amountCents: Long?) : Parsed
    }

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

    fun parseOrNull(role: String, title: String, text: String, defaultIncomeSource: String = IncomeSources.OTHER): Result? =
        (parse(role, title, text, defaultIncomeSource) as? Parsed.Match)?.result

    /**
     * @param defaultIncomeSource source for money coming into a bank app when the text doesn't name
     *   DoorDash or Spark (e.g. DasherDirect deposits are always DoorDash).
     */
    fun parse(role: String, title: String, text: String, defaultIncomeSource: String = IncomeSources.OTHER): Parsed {
        val full = "$title. $text"
        val amount = firstAmountCents(full)?.takeIf { it > 0 } ?: return Parsed.Skip("No dollar amount", null)
        val lower = full.lowercase()
        fun skip(reason: String) = Parsed.Skip(reason, amount)
        return when (role) {
            Roles.DOORDASH, Roles.SPARK -> when {
                GIG_NOT_PAY.containsMatchIn(lower) -> skip("Looks like an offer or promo, not pay")
                !GIG_PAY.containsMatchIn(lower) -> skip("Didn't say you were paid")
                else -> Parsed.Match(
                    Result(Kind.INCOME, amount, incomeSource = if (role == Roles.DOORDASH) IncomeSources.DOORDASH else IncomeSources.SPARK)
                )
            }
            Roles.BANK, Roles.SPENDING -> when {
                NOT_A_TRANSACTION.containsMatchIn(lower) -> skip("Reminder, promo or declined charge")
                BILL_PAYMENT.containsMatchIn(lower) -> skip("Card or bill payment (not new spending)")
                OWN_TRANSFER.containsMatchIn(lower) -> skip("Transfer between your own accounts")
                MONEY_IN.containsMatchIn(lower) ->
                    if (role == Roles.SPENDING) skip("Money coming in (this app is set to Spending only)")
                    else Parsed.Match(Result(Kind.INCOME, amount, merchant = payer(text).ifBlank { payer(title) }, incomeSource = gigSource(lower) ?: defaultIncomeSource))
                SPEND.containsMatchIn(lower) -> {
                    val merchant = MERCHANT.find(text)?.groupValues?.get(1)?.trim()
                        ?: MERCHANT.find(title)?.groupValues?.get(1)?.trim()
                        ?: ""
                    Parsed.Match(Result(Kind.EXPENSE, amount, merchant = merchant, category = guessCategory("$merchant $lower")))
                }
                else -> skip("Couldn't tell if money went in or out")
            }
            else -> skip("App is switched off")
        }
    }

    /** DoorDash / Spark when a deposit names them, e.g. "Direct deposit from DOORDASH INC". */
    fun gigSource(lower: String): String? = when {
        DOORDASH_WORDS.containsMatchIn(lower) -> IncomeSources.DOORDASH
        SPARK_WORDS.containsMatchIn(lower) -> IncomeSources.SPARK
        else -> null
    }

    private fun payer(text: String): String =
        PAYER_FROM.find(text)?.groupValues?.get(1)?.trim()
            ?: PAYER_PAID_YOU.find(text)?.groupValues?.get(1)?.trim()
            ?: ""

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

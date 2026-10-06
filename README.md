# Gig Budget

An Android budget app for gig drivers: it tracks **DoorDash** and **Spark** pay, shows how much to
save each week for the things you're saving for, has one-tap **Bottle** and **Preroll** spending
buttons, and can **log pay and spending automatically** from your apps' notifications.

## Features

- **Home** – income this week/month split by DoorDash / Spark / Other, spending by category,
  bottle & preroll totals (with optional weekly limits), a tax set-aside, and what's actually left over.
- **Income** – add pay by source; 4-week average weekly income.
- **Spending** – 🍾 Bottle and 🌿 Preroll quick-add buttons that remember your usual price, plus
  regular spending in categories (Gas, Food, Groceries, Car, Rent, Bills, Phone, Fun, Other).
  Tap any entry to edit it or fix its category.
- **Savings** – goals with an amount and an optional "need it by" date. Each goal shows how much to
  save per week and per day, and what percent of your average pay that is. The top card adds up
  the weekly amount across all goals.
- **Settings** – auto-import setup, which apps to watch, tax %, and weekly bottle/preroll limits.

## Automatic import (how "pulling from apps" works)

DoorDash, Spark and banks don't offer a public API an app on your phone can use, so Gig Budget
reads the **notifications** those apps already send you instead:

| App notification | Becomes |
| --- | --- |
| DoorDash Dasher: "You earned $45.67…" | Income · DoorDash |
| Spark Driver: "$120.00 has been deposited…" | Income · Spark |
| Chase / Cash App / Chime / etc.: "You spent $12.34 at SHELL…" | Spending · Gas |
| "…at Green Dragon Dispensary" / "…at Total Wine" | Spending · Preroll / Bottle |
| Bank: "Direct deposit of $312.45 from DOORDASH INC" | Income · DoorDash |
| Bank: "You received $88.10 from Walmart" | Income · Spark |
| Venmo / Cash App: "Jake paid you $20" | Income · Other |
| DasherDirect: "You've been paid $54.20" | Income · DoorDash |

Skipped: delivery offers ("$8.50 est. pay", "Earn an extra $2"), bill reminders, card/bill
payments, transfers between your own accounts, money requests, balances and declined cards.

**No double counting:** a DoorDash or Spark deposit in a bank app is skipped if the DoorDash/Spark
app itself logged pay in the last 2 weeks. If the gig app doesn't notify you about pay, the bank
deposits are counted instead.

**Captured notifications log** (Settings → View captured notifications) lists every money
notification from the apps you watch, what it became, or why it was skipped. Skipped ones
have **Add as income** / **Add as spending** buttons so nothing real gets lost.

To turn it on: **Settings → Allow notification access → Gig Budget**. Make sure the DoorDash,
Spark and bank apps have their own notifications (and purchase alerts) turned on.

- Each watched app is set to one of: **DoorDash pay**, **Spark pay**, **Money in & out** (purchases
  are spending, deposits and money sent to you are income), **Spending only**, or **Off**.
- DasherDirect, Cash App, Chime, PayPal, Venmo, Google Wallet, Chase, Capital One, Wells Fargo,
  Bank of America, USAA and Varo are pre-set to Money in & out. Any other app that sends a money notification shows up
  in Settings automatically — switch it on there.
- If your bank texts you instead, turn on "Messages (bank texts)".
- Imported entries are marked **auto**; tap one to fix the amount or category.
- It only catches notifications from after you enable it. Add older pay/spending by hand.
- Notification wording differs between app versions. If something isn't picked up, the
  captured notifications log shows exactly what each app sent and why it was skipped.

**Privacy:** the app has no internet permission. Your data never leaves your phone.

## Install

**Easiest:** open this repo's **Actions** tab on GitHub → latest "Build APK" run → download the
`gig-budget-apk` artifact, unzip it, copy `app-debug.apk` to your phone and open it (allow
"install unknown apps" when asked).

**From source:** open the folder in Android Studio and press Run, or:

```sh
./gradlew assembleDebug      # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # parser, auto-import, database upgrade and savings math tests
```

Requires Android 8.0+.

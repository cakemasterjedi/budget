# Stack It, Niome 💜

An Android budget app for gig drivers: it tracks **DoorDash** and **Spark** pay, shows how much to
save each week for the things you're saving for, has one-tap **Bottle** and **Preroll** spending
buttons, and can **log pay and spending automatically** from your apps' notifications.

## Features

Pink & purple design, five tabs:

- **Home** – what's left over this month on a pink→purple card, one-tap **Pay / Spend / 🍾 Bottle /
  🌿 Preroll** buttons, a **pie chart of where your money went** (by bucket, with every category
  listed underneath with its $ and %), bills coming up, income by DoorDash / Spark, bottle &
  preroll totals with weekly limits, savings progress and a daily budgeting tip.
- **✂️ Split a paycheck** (Home, or tap any income) – type in a payout and see where every dollar
  goes: taxes off the top, then an envelope per bucket, with the savings envelope divided between
  your goals. One tap puts the savings into your goals.
- **Plan** – your monthly split as a pie chart: **50% bills & needs, 5% debt**, 25% wants (incl.
  bottles & prerolls), 15% savings, 5% giving. Drag sliders to change it, or pick a preset
  (My plan · 55 / 30 / 15 paycheck breakdown · 50 / 30 / 20). Flip back through past months with ‹ ›.
  **Spending budgets** per category (Groceries $400, Gas $200…) show spent and what's left.
  Last month's leftover **rolls over** (can be turned off in Settings). Shows how each bucket is
  doing this month, a budget summary (income − taxes − savings − expenses − debt = remaining),
  monthly bills with due dates and a Pay button, and notes & reminders. The plan is built on what
  you expect to make (or your last 4 weeks of pay), after the tax set-aside.
- **Money** – spending and income lists. Tap any entry to edit it or fix its category.
- **Goals** – savings goals by category (☂️ emergency fund, 🚗 car, ✈️ trip, 🎁 holidays…) with how
  much to save per week / month / day, celebrations at 25 / 50 / 75 / 100%, a one-tap
  **emergency fund** (3 months of bills), and a **debt tracker** (balance, minimum, interest,
  payments this month, and when you'll be debt-free).
- **Savings Scout** (Home and Goals) – looks at what's left this month, bills and minimum debt
  payments still due, and your everyday spending pace, then suggests a safe $5–$50 to save today.
  Tap "How?" to see the math. With auto-import on, you get a heads-up notification after a payout
  (at most every 3 hours). Inspired by Huntington's Money Scout and Savings Goal Getter.
- **Settings** – auto-import setup, which apps to watch, tax %, weekly bottle/preroll limits,
  **✏️ Edit categories** (rename the five parts of the plan, change emojis, move or hide categories,
  add your own like Nails or Hair), **☁️ Google Drive backup** (connect once; the app updates its
  backup file in Drive every day and when you leave the app after 6+ hours) and **💾 Backup file**
  (save or restore a copy anywhere).

## Keeping data safe across updates

- Install each new APK *over* the old one; don't uninstall first. Data and settings stay.
- Android only accepts an update signed with the same key as the installed app. Builds use the key
  in `app/signing/stackit.keystore`, which is kept out of git; put the same file there on any
  machine that builds releases, or updates will be refused.
- Turn on Google Drive backup in Settings. It works no matter how the app was installed, and
  "Restore" brings everything back after a reinstall or on a new phone.

Categories and buckets: **Bills & needs** = Rent, Bills, Phone, Groceries, Gas, Car, Health ·
**Wants** = Food (eating out), Fun, Shopping, Subscriptions, Bottle, Preroll, Other ·
**Giving** · **Savings** (money moved into goals) · **Debt** (debt payments).

## Automatic import (how "pulling from apps" works)

DoorDash, Spark and banks don't offer a public API an app on your phone can use, so Stack It
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

To turn it on: **Settings → Allow notification access → Stack It, Niome**. Make sure the DoorDash,
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

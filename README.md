# Hisaab

**A private, offline expense tracker for Android.** Hisaab reads your bank SMS, pops up right after each payment to ask which category it belongs to, and builds a monthly *Projected / Actual / Difference* budget that mirrors a Google Sheet layout.

> The app declares **no `INTERNET` permission**, so your financial data physically cannot leave the phone.

## Features

- **Automatic payment capture.** Parses debit and credit SMS from HDFC, KVB and Federal Bank with exact rules. Other banks go through a cautious generic fallback, and those entries are flagged "verify".
- **One-tap categorisation.** A popup appears over whatever you are doing. If the overlay permission is off, a notification with category buttons is used instead.
- **Learns your habits.** Pick the same category for a merchant three times and future payments to it are filed automatically.
- **Monthly budget.** Projected vs. actual vs. difference per category and group, with dashboard charts and a transaction list.
- **Google Sheet import / export.** Round-trips the "Monthly Budget" CSV layout.
- **App lock.** PIN with optional fingerprint/face unlock, auto-lock after 30 seconds in the background.
- **Safe by design.** OTPs, promos, collect requests and bill reminders are rejected. Duplicate SMS are skipped.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · kotlinx.serialization · AndroidX Biometric · Gradle (Kotlin DSL, version catalog)

Min SDK 26 (Android 8.0), target/compile SDK 35.

## Download

Grab the latest APK from the [Releases page](https://github.com/pawan895/hissab/releases/latest). It is signed with a debug key, so it is meant for personal sideloading.

## Getting started

**Requirements:** JDK 21, Android SDK (set `sdk.dir` in `local.properties`), and a phone with USB debugging for `adb install`.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # adjust to your JDK 21 path
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also copy the APK to the phone and open it there. On Android 13+ a sideloaded APK has the SMS permission blocked at first. Unblock it under **App info → ⋮ → Allow restricted settings**. Installing with `adb install` avoids this.

### First launch

Allow all four permissions: **SMS**, **display over other apps**, **notifications**, and **run in background**. On Xiaomi, Oppo, Vivo, Realme and OnePlus phones, also enable **Autostart** in App info, otherwise the popup only appears while the app is open.

## App lock

Hisaab asks for a PIN when it opens and again after 30 seconds in the background. The initial PIN is **`0809`**, so change it first under **Settings → Security**. Changing the PIN or turning the lock off requires the current PIN.

- Five wrong attempts lock the keypad for 30 seconds.
- The PIN is stored only as a salted PBKDF2 hash.
- The payment popup still appears while locked, but shows only the payment that just happened.

## How it works

| Path | Responsibility |
| --- | --- |
| `sms/SmsReceiver.kt`, `sms/SmsParser.kt` | Receive SMS and parse bank-specific formats |
| `sms/MerchantNormalizer.kt` | Collapse payee names to a key, so `zomatoltd32.rzp@hdfcbank` and `ZOMATO` both become `zomato` |
| `sms/InboxBackfill.kt` | Recover SMS missed while a battery manager blocked the app. Runs on every app open and de-duplicates by a hash of the SMS body plus UPI reference |
| `popup/` | Overlay card and notification fallback for categorising a payment |
| `data/` | Room database, repository, month reports, and `SheetIo.kt` for CSV import/export |
| `ui/` | Compose screens: dashboard, budget, transactions, settings, lock, onboarding |
| `security/AppLock.kt` | PIN and biometric lock |

### Importing your budget sheet

In Google Sheets choose **File → Download → .csv** for a month's tab, then use **Settings → Import budget sheet**. **Export** from Settings writes the same layout back out.

## Testing

```sh
./gradlew :app:testDebugUnitTest
```

`SheetIoTest` reads a personal budget export (`Monthly Budget - October.csv`) from the repo root. That file is git-ignored, so those tests are skipped on a fresh clone unless you add your own export with that name.

## Adding a bank

1. Add a `Rule` to `SmsParser.rules`.
2. Add a test case with a real, **masked** SMS to `SmsParserTest`.
3. Run the tests as above.

## Credits

UI font and the ₹ in the app icon: [Inter](https://github.com/rsms/inter), SIL Open Font License 1.1. See [`licenses/Inter-OFL.txt`](licenses/Inter-OFL.txt).

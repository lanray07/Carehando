# Carehando

Carehando is a native, offline-first family care planner for Fire tablets. The app centres on a structured handover that the next caregiver can acknowledge on the same device.

## Build

Install JDK 17, Android SDK 35 and Android Build Tools 35.0.0. Point Android Studio or `ANDROID_HOME` at the SDK.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease :app:lintRelease
```

The debug application ID is `app.carehando.debug`. The release ID is `app.carehando`. Install the debug APK using `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Run UI and database tests with `.\gradlew.bat :app:connectedDebugAndroidTest` on a dedicated test device. The test suite replaces records in the debug application with fictional test data.

## Architecture

Compose screens consume the Android ViewModel’s Room and DataStore flows. Daily, weekly and selected-weekday schedules and assistant intent parsing use local Kotlin rules. A Room transaction saves each handover header, activity snapshot and original revision. Later corrections and acknowledgements are appended. WorkManager checks opted-in reminders periodically and uses generic notification text. Snoozing schedules a replay after 15 minutes subject to OS scheduling and quiet hours. Completed or removed occurrences are suppressed. Android’s Storage Access Framework gives users control of PDF and CSV destinations. Speech input and speech output use the engines available on the device.

## Amazon Premium

The app can display Amazon’s localised price and activate a verified entitlement for additional profiles and encrypted backup export. Set the public HTTPS verifier endpoint using `-PcarehandoReceiptVerifierUrl=https://YOUR_HOST/verify`. Create an Amazon entitled item with SKU `carehando_premium` and configure the receipt verifier with its Cloudflare Secret. After installing both debug APKs and Amazon App Tester, run `python billing/run_app_tester.py` to check product data and purchase updates. Add `--purchase` to restore or buy the sandbox item, validate the receipt through RVS and send fulfillment. To check revocation, cancel the Carehando sandbox transaction in App Tester and rerun with `--revoked`. A build without a verifier leaves purchasing disabled.

To reproduce the on-device paywall restore after completing an App Tester purchase, install Python’s `cryptography` package and build both debug APKs using `-PcarehandoReceiptVerifierUrl=https://127.0.0.1:18765/verify`. Install them and run `python billing/run_app_tester.py --paywall`. The test uses a temporary loopback HTTPS server and trusts its certificate only inside the test process. Rebuild the normal APK without the loopback URL after testing.

The [Cloudflare deployment guide](billing/cloudflare/DEPLOY.md) lists the deployed endpoints, secret setup and Amazon Live App Testing steps. The [AWS template and guide](billing/DEPLOY.md) are also available.

## Encrypted backups

Premium users can create a passphrase-protected archive from Settings and choose where to save it. A fresh installation can restore it from onboarding or Settings using the same passphrase. The archive contains care records and supported attachments and has a 16 MiB payload limit. Keep the passphrase separately. Carehando cannot recover it. This build writes database schema 5 backups and can also restore backups from schema 4. App preferences and purchase entitlements stay with the installation.

## Localisation

`python scripts/localization.py` checks the 19 Android catalogues for coverage, format placeholders and plural quantities. The app’s language picker uses Android’s `locales_config.xml`. The 14 added catalogues retain their source text and Codex review record in `deliverables/localization/staged`. `--extract` exports the base catalogue. The online staging command accepts `CAREHANDO_TRANSLATION_URL` and `CAREHANDO_TRANSLATION_KEY`. The endpoint receives app copy, a locale and glossary and returns a JSON object with a `strings` property. Alternatively, `python scripts/stage_local_translations.py PATH_TO_MODEL` uses a local CTranslate2 M2M-100 model. Review the copy and voice aliases before approving a staged catalogue and running `--generate`. Add reviewed locales to the Android configuration after testing.

## Screenshots

Install the debug and instrumentation APKs on a dedicated device. Run `python scripts/capture_fire.py --device fire-hd10-2021` for the original five storefront sets or specify `--locale` for another app or storefront locale. Pass `--adb` for a custom ADB path and `--serial` when multiple devices are connected. Instrumentation replaces the debug app records with localized fictional data. The phone-width captures used a temporary 1080 × 1920 viewport at 420 dpi on the Fire HD 10. Store artwork is composed from device captures for the 15 locales with listing copy. Run `python scripts/compose_screenshots.py deliverables/screenshots/fire-hd10-2021 --device fire-hd10-2021` and then `python scripts/validate_screenshots.py fire-hd10-2021` to check those assets.

## Release

The release build produces an unsigned APK and AAB. Amazon applies the Appstore signature to APK submissions. Use the registered Carehando certificate for Amazon IAP DevTest and keep private signing keys, passwords and the Amazon shared secret outside the repository. Complete the store, privacy, device and billing checks in [the release report](deliverables/reports/release.md) before submitting.

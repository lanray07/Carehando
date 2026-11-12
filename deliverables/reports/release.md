# Carehando release verification

Verification date 12 November 2026. Carehando 0.1.1 (version code 2) is **SUBMITTED** for public Amazon Appstore review. Publication and verification of a production receipt are pending.

| Area | Status | Evidence or dependency |
| --- | --- | --- |
| Debug APK | PASS | `deliverables/android/debug/Carehando-debug.apk`. Android v2 debug signature verified by apksigner. |
| Release APK and bundle | PASS | Unsigned APK and AAB built. Amazon accepted, signed, and distributed the Live App Testing APK on Fire hardware. Amazon validated the public APK with production billing configured. APK SHA-256 `a41340962b997cc8789941747cfbcdbb64a185494406473e864f04e454127669`. |
| Cloudflare Live App Testing APK | BUILT | `deliverables/android/release/Carehando-live-app-testing-unsigned.apk`, package `app.carehando`, version `0.1.0` (1). The packaged Amazon public key and test verifier URL were checked. Release lint and all 12 release unit tests passed. SHA-256 `3f504907abc5741168e3fa2c17e5641f5ce2213364532e4239869887c0db467a`. |
| Signed Amazon IAP DevTest build | BLOCKED | This testing route needs the certificate registered in the Carehando Developer Console. |
| Kotlin unit tests | PASS | Twelve tests cover backup encryption, scheduling, reminders, activity corrections, handovers, localized commands, CSV escaping and attachment signatures. XML results are in `deliverables/android/build-logs`. |
| Receipt verifier unit tests | PASS | Five Python tests cover valid, invalid, cancelled and revoked receipts, temporary upstream errors, and the opt-in policy for Amazon beta receipts. |
| Amazon App Tester product and purchase updates | PASS | The Fire HD 10 returned the local `carehando_premium` entitled product with a price and a successful purchase-updates response. See `fire-app-tester-product-restore.log`. |
| Amazon App Tester purchase, recovery and revocation | PASS | The sandbox harness recovered an unfulfilled receipt, verified it through Amazon RVS and sent fulfillment. App Tester displayed the fulfilled state. After cancellation, the SDK returned the receipt as cancelled. A fresh purchase then completed, returned a receipt through purchase updates, and verified and sent fulfillment again. See `fire-app-tester-recovery.log`, `fire-app-tester-revoked.log` and `fire-app-tester-fresh-purchase.log`. |
| Carehando paywall restore in App Tester | PASS | Carehando’s Settings restored an actual sandbox receipt over HTTPS, verified it with Amazon RVS, recorded the entitlement and enabled encrypted backup export. See `fire-paywall-sandbox-current.log`. |
| Cached entitlement during verifier outage | PASS | The Fire HD 10 reopened Carehando after losing trust in the sandbox verifier’s TLS certificate. Restore displayed a retry message, kept the verified receipt and left Premium backup export available. See `fire-paywall-outage.log`. |
| Production verifier packaging | PASS | The CloudFormation template passed cfn-lint for eu-west-2. The handler ZIP imported successfully and rejected an invalid request in a local smoke test. See [deployment guide](../../billing/DEPLOY.md). The production deployment uses Cloudflare Workers. |
| Cloudflare Workers | DEPLOYED | Both endpoints passed initial route, method and request validation checks. The active Live App Testing Worker uses a Cloudflare Secret binding for `AMAZON_RVS_SECRET`. After deployment it returned 400 for an empty request and 502 for a fabricated receipt. Three Worker tests and five Python verifier tests passed. See [Cloudflare setup](../../billing/cloudflare/DEPLOY.md). |
| Amazon Live App Testing 0.1.0 | PURCHASE AND RESTORE VERIFIED | Amazon Appstore installed `app.carehando` version 0.1.0 (1) on the Fire HD 10 (2021). The UK Appstore supplied a £3.76 list price. Checkout appeared only after backgrounding and reopening the app. After checkout, Amazon issued and fulfilled the entitlement, the Cloudflare LAT Worker returned HTTP 200, and the app showed Premium active. Restore Purchases fetched the entitlement again and the verifier accepted it. |
| Amazon Live App Testing 0.1.1 | PASS | Amazon Appstore installed version 0.1.1 (2) on the Fire HD 10. After Amazon reset the tester entitlement, a fresh launch removed local Premium access and checkout opened on the first tap. Amazon confirmed the purchase and fulfillment, the LAT Worker returned HTTP 200, and Carehando showed Premium active. Restore Purchases retrieved and verified the new entitlement. A transient Appstore integrity error immediately after updating cleared after reopening the Appstore and relaunching Carehando. Release lint and all 12 release unit tests passed. APK SHA-256 `3daa69c93c751412f742e4178a1a0e2e0d18169e82474cc05bc7ab733c055b3e`. |
| Production IAP | CONFIGURED | The production Cloudflare Worker has the encrypted Amazon RVS Secret and uses live receipt mode. The production APK contains the production verifier URL and the Amazon public key. Amazon approved `carehando_premium` for production at a US$4.99 base price. A real production receipt can be verified after Amazon distributes the public build. |
| Public Appstore submission | SUBMITTED | [Amazon Developer Console](https://developer.amazon.com/apps-and-games/console/app/amzn1.devportal.mobileapp.82f7cec6962b415aa1b3220b7de5a428/release/amzn1.devportal.apprelease.3c1ea298443449f683f5454eef1c2419/submission/submission-status.html) confirmed successful submission on 12 November 2026 and estimates publication before 4:00 a.m. PST on 17 November 2026. The release targets 12 compatible Fire tablets and adults. Privacy responses, the public policy URL, reviewer instructions and the owner-confirmed export certification were included. |
| Room persistence and PDF generation on Fire | PASS | Four instrumentation checks passed on KFTRWI, covering the installed schema 1 to 2 upgrade, attachment metadata cascade deletion, history retention, PDF pagination and speech capability detection. See `fire-final-tests.log`. |
| Handover journey on Fire hardware | PASS | The Fire HD 10 (2021), KFTRWI, Fire OS 7, Android API 28 ran task completion, handover review, acknowledgement, correction and reopening. All five instrumentation tests passed with Wi-Fi disabled and no default network. Wi-Fi was restored. |
| Fire 7, Fire HD 8, Fire OS 8 and physical phones | NOT TESTED | Phone-width screenshots were produced on the Fire HD 10 at 411 dp. A real phone and additional Fire hardware remain unverified. |
| Speech input on KFTRWI | BLOCKED | Android’s service query returned no recognition service on the connected device. The text assistant is the fallback. |
| English speech synthesis on Fire | PASS | The installed en-GB voice generated an audio file with samples. The default engine reports en-US and en-AU at language level. The other 16 app locales have no voice in the installed engine. See [voice capability results](voice.csv). |
| Audio playback | NOT TESTED | Synthesis to a file does not exercise the tablet speaker or playback controls. |
| App localisation | PASS | Codex reviewed the 14 added catalogues. All 19 configured locales pass checks for 241 source keys, matching format placeholders and required plural quantities. See `deliverables/localization/coverage.txt`. |
| Localised runtime | PASS | Fire OS 7 loaded translated resources and parsed every localized text command example in all 19 locales. The Arabic resource configuration was right-to-left, and the in-app language picker switched to Arabic. See `fire-locales-verified.log`. |
| Store metadata | PASS | Fifteen listing drafts meet local checks for store fields and screenshot copy. See `deliverables/store-assets/metadata-validation.txt`. All 15 listing descriptions were entered, and Amazon accepted the submission. |
| Store screenshot packages | PASS | Each of the 15 listing locales has eight Fire HD 10 compositions and eight phone-width compositions captured on the Fire HD 10. All 240 PNGs passed file and dimension checks. Amazon accepted eight tablet screenshots for each of its 11 selectable image locales. Dutch, Polish, European Portuguese and Swedish have translated descriptions and use the US English artwork because Amazon offers no separate image slot for those locales. |
| Optional remote AI | BLOCKED | No provider endpoint or consented remote data workflow is configured. The local assistant uses deterministic intents and structured handover drafts. |
| Privacy controls on Fire | PASS | Instrumentation confirmed Android backup and cleartext HTTP are disabled, the document provider is scoped to the attachment directory, and the app lock hides records and sets Android’s secure window flag. Credential entry, device-to-device transfer and the final privacy assessment remain pending. |
| Release lint | PASS | Zero errors, 22 warnings and one hint. The lint report records SDK, dependency and maintenance warnings. |
| Database upgrade and profile persistence | PASS | Fire instrumentation opened the installed database after upgrades through schema 5. Earlier profile tests saved and reread the expanded recipient and caregiver fields. |
| Snooze scheduling and replay | PASS | Fire instrumentation verified the generic notification, action enqueue, replay for a pending task and suppression after completion. It triggered replay directly, so delivery after 15 minutes was not measured. |
| Updated touch flows | PASS | The Fire HD 10 verified selected-weekday task creation through handover acknowledgement and correction, month navigation and opening a selected appointment, and profile edits with Emergency info display. |
| Encrypted backup and restore on Fire HD 10 | PASS | Device tests restored records, follow-up status, history, attachments and ID sequences, rejected an incorrect passphrase and verified rollback of a truncated authenticated backup. A schema 4 archive restored into schema 5. See `fire-release-followup.log` and `fire-backup-final.log`. |
| Appointment follow-up checklist | PASS | A Fire device UI test added an item, marked it complete and verified the status and original preparation notes after reopening the appointment. |
| Developer Console listing and declarations | BLOCKED | Publisher identity, support contact, policy URL, questionnaire answers and Console validation are outstanding. |
| GitHub delivery | BLOCKED | Push is gated on completion of the requested release criteria. |

## Implemented scope

Recipient creation, editing, archive and deletion. Optional recipient fields include communication language, mobility, diet, daily routines, health notes and preferred clinic. Caregiver profiles and assignments include optional contact details and preferred language. Tasks with daily, weekly or selected-weekday recurrence, a pause flag and occurrence history. Appointments with location, provider, preparation notes, follow-up checklists, time and a month calendar. Medication schedules, separate taken/skipped/unconfirmed statuses and explicit confirmation before recording a dose. Care journal and local emergency information. Private profile images and supported file attachments for existing appointments, journal entries and handovers. Handovers built from activity snapshots, with acknowledgements and appended corrections. PDF, CSV and text exports. Text commands and Android speech integration with runtime detection. Opt-in WorkManager reminders with quiet hours, generic notification previews and a 15-minute snooze action.

## Remaining feature work

Broader reminder coverage, expanded administrative intent handling, optional remote AI consent and provider validation, and Premium features beyond multiple profiles and encrypted backup export remain open. The care report includes a preview of the first rendered PDF page and an accessible text preview. The icon set, onboarding illustration and Amazon promotional image are available. Additional requested campaign and feature illustrations are outstanding.

## Reproduce the build

```powershell
.\gradlew.bat --offline --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease :app:lintRelease
```

See `release-followup-final.log`, `fire-release-followup.log`, `fire-paywall-sandbox-current.log` and the unit test XML in `deliverables/android/build-logs`. Earlier device results are in `fire-profiles-final.log`, `fire-weekdays-ui.log`, `fire-calendar-ui.log` and `fire-profile-ui.log`. Remove `--offline` for a first build without cached dependencies. The artifact manifest records SHA-256 hashes.

## Verified screenshot packages

- [Fire HD 10 landscape set](../store-assets/packages/Carehando-Fire-HD10-screenshots.zip)
- [Phone-width portrait set captured on Fire HD 10](../store-assets/packages/Carehando-phone-width-screenshots.zip)
- [Image dimensions and file validation](../store-assets/screenshot-validation.txt)
- [Fire HD 10 localisation QA captures](../screenshots/Carehando-Fire-HD10-localisation-QA.zip)
- [Localisation capture validation](../screenshots/locale-qa-validation.txt)

Each store artwork set contains eight screens per listing locale. The tablet set meets the published dimensions and per-locale count for Amazon tablet listings. The phone-width set is for mobile presentation and layout QA. The QA package contains raw device captures for the 19 app locales, including Arabic right-to-left views. All care records in the images are fictional. [Amazon listing image guidelines](https://developer.amazon.com/docs/app-submission/appstore-details.html)

## Remaining release checks

1. Monitor the Amazon Appstore review until publication.
2. Once published, exercise purchase, restore and offline restart in the Amazon-signed production build, and confirm the live verifier accepts a production receipt.
3. Expand physical-device, screen-reader and speech-service coverage.

[Amazon release submission](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html) · [Amazon device identification](https://www.developer.amazon.com/docs/device-specs/ft-identify-tablet-devices.html)


[Amazon Appstore APK signing](https://developer.amazon.com/docs/in-app-purchasing/iap-get-started.html) · [Amazon screenshot requirements](https://developer.amazon.com/docs/app-submission/appstore-details.html)

[App Tester transaction management](https://developer.amazon.com/docs/in-app-purchasing/iap-app-tester-user-guide.html) · [Amazon RVS sandbox](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html)

[Amazon listing and app language options](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)



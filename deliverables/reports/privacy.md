# Privacy and data handling

Carehando stores care profiles, tasks, reminder statuses, handovers and notes in its private Android application directory. Android's app sandbox and device protections control access to that storage. The database has no separate application-level encryption. The Android manifest disables backup. Carehando uses no account for local care planning and includes no analytics or advertising SDK.

People using the same unlocked installation can view and edit its records. An optional in-app lock uses the device screen credential and hides app content from system screenshots while enabled. Caregiver names and acknowledgements are self-reported. They do not establish an authenticated identity. Profile deletion cascades to its associated records and handovers. Removing an individual scheduled item retains its prior activity and saved handover references. Removing a caregiver retains their name in historical handovers. Files saved or shared through other apps are outside Carehando’s deletion controls. PDF and image attachments are copied into the private app directory, restricted to supported signatures and limited to 10 MB. An external viewer receives temporary read access only after the user confirms.

The device's speech service processes audio only after the user starts listening and grants microphone permission. The service may use the internet. Carehando does not keep raw audio and offers text entry. Spoken feedback is opt-in and user-triggered. Optional Amazon purchase verification sends the Amazon user ID and receipt ID to the configured verifier. This request contains no care records. The verifier contacts Amazon and the shared secret stays on the server.

Optional profile details, including caregiver contacts and recipient health notes, stay in the local database. Reminders and snooze replays use generic notification text and are subject to system scheduling. Medication status requires user confirmation and carries no dosing guidance.

An encrypted backup contains care records and supported attachments. Carehando writes it to the location the user chooses. The archive uses AES-256-GCM with a fresh nonce and a key derived using PBKDF2-HMAC-SHA256 with a fresh salt. Restore authenticates the archive before importing it and requires an installation with no recipient or caregiver profiles. Carehando does not retain the passphrase. A storage provider selected by the user can retain a copy of the encrypted archive.

Before release, add the publisher identity and support contact, review data retention and the voice service disclosures, assess the applicable privacy obligations for the intended territories, and complete store privacy declarations. No legal compliance assessment has been completed.

## Amazon privacy questionnaire draft

| Data | Source and handling to confirm for the shipping build |
| --- | --- |
| User IDs and purchase history | The app sends the Amazon account identifier and receipt ID over HTTPS to the publisher’s verifier. The verifier sends those fields to Amazon for purchase validation. Record the hosting provider and its log retention. |
| Payment details | Amazon handles payment entry and processing. Carehando’s verifier receives receipt and user identifiers. |
| Audio | The user initiates speech input. Review the installed recognition service’s handling of microphone input before choosing the collection and transfer answers. |
| Health notes, names, contacts, photos and documents | Stored in the app’s private directory. The user can export selected information or an encrypted archive to a destination they choose. |
| Diagnostics, advertising and analytics | Carehando has no advertising or analytics integration. Review the release Amazon SDK and any operational logging on the verifier for disclosures. |
| Deletion | Settings can delete all local care records and care preferences. Carehando also supports individual profile and record deletion. Purchase entitlement details remain available to the installation. The publisher should explain Amazon transaction retention and management of externally exported files. |

Confirm the publisher name, support address, publicly reachable policy URL, data retention and final answers before completing the Developer Console questionnaire. [Amazon Appstore Privacy Labels](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html)


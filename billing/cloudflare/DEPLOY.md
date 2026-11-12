# Carehando on Cloudflare Workers

The test Worker accepts receipts Amazon marks as beta for Live App Testing. The production Worker accepts live receipts. Both call Amazon’s production Receipt Verification Service and keep the shared key in a Worker Secret. [Amazon receipt verification](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html) · [Cloudflare Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/)

| Worker | HTTPS endpoint |
| --- | --- |
| Live App Testing | [carehando-receipt-verifier-lat](https://carehando-receipt-verifier-lat.lanraybanks.workers.dev/verify) |
| Production | [carehando-receipt-verifier](https://carehando-receipt-verifier.lanraybanks.workers.dev/verify) |

## Configure the deployed Workers

In the Cloudflare dashboard, select **Workers & Pages**, open the Worker, and go to **Settings → Variables and Secrets**. Add a **Secret** called `AMAZON_RVS_SECRET` with the Amazon Developer Console shared key. Start with the Live App Testing Worker and repeat for production before release. [Cloudflare dashboard setup](https://developers.cloudflare.com/workers/configuration/secrets/#via-the-dashboard) · [Amazon shared key](https://developer.amazon.com/sdk/shared-key.html)

You can also add the secrets from the repository root using Wrangler. Enter the key in its interactive prompt.

```powershell
npx wrangler@4.160.1 secret put AMAZON_RVS_SECRET --config billing/cloudflare/wrangler.jsonc --env lat
npx wrangler@4.160.1 secret put AMAZON_RVS_SECRET --config billing/cloudflare/wrangler.jsonc --env=""
```

## Test the Amazon-signed build

1. Download the public `AppstoreAuthenticationKey.pem` for the Carehando app in the Amazon Developer Console. Place it at `app/src/main/assets/AppstoreAuthenticationKey.pem`.
2. Build the release APK for Live App Testing from the repository root.

   ```powershell
   .\gradlew.bat :app:assembleRelease -PcarehandoReceiptVerifierUrl=https://carehando-receipt-verifier-lat.lanraybanks.workers.dev/verify
   ```

3. In Amazon’s Console, submit the `carehando_premium` entitled item for testing and upload the APK to a Live App Test. On the Fire HD 10, install the Amazon-distributed build through the tester account. Verify the price, purchase, cancellation, restore, revocation and offline restart. Record the signed build version and results in the release report. [Amazon IAP testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html)
4. For the public release build, pass the production Worker URL to the same Gradle property.

Run `node --test billing/cloudflare/worker.test.mjs` to validate the verifier locally. Each Worker limits requests per IP and Cloudflare location to protect Amazon RVS from repeated requests. [Cloudflare rate limits](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)

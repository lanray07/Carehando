# Deploy the Carehando receipt verifier

The stack exposes one HTTPS route, `POST /verify`. It invokes the production Amazon RVS endpoint and returns an entitlement decision. API Gateway starts with a steady limit of two requests per second and a burst of four. The Lambda log group retains operational logs for 14 days. The handler has no application logging of receipt or account identifiers. The template enables no API Gateway request logging.

## Set up the release environment

1. In the Amazon Developer Console, register the Carehando application and the one-time entitled product `carehando_premium`. Obtain the Appstore SDK public authentication key for that application and the separate RVS shared key.
2. In your AWS account, choose a deployment region and an existing private S3 artifact bucket in that region. Store the raw RVS shared key as a plaintext-value secret in AWS Secrets Manager in the same region. Copy its ARN. Give the stack execution role access to read that secret. Keep the value out of source control, shell history and Android build inputs.
3. Use your authenticated AWS CLI profile. Replace the four nonsecret placeholder values in the example and run it from the repository root. The first deployment command creates a CloudFormation change set for inspection.

```powershell
$carehandoAwsProfile = '<AWS CLI profile>'
$carehandoAwsRegion = '<AWS region>'
$carehandoArtifactBucket = '<private S3 artifact bucket>'
$carehandoSecretArn = '<Secrets Manager secret ARN>'
$carehandoArchive = '.gradle/carehando-verifier.zip'

New-Item -ItemType Directory -Force -Path .gradle | Out-Null
python -m zipfile -c $carehandoArchive billing/handler.py
if ($LASTEXITCODE) { throw 'Verifier packaging failed' }
$carehandoArtifactHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $carehandoArchive).Hash.ToLowerInvariant()
$carehandoArtifactKey = "carehando/verifier/$carehandoArtifactHash.zip"

aws --profile $carehandoAwsProfile --region $carehandoAwsRegion s3 cp $carehandoArchive "s3://$carehandoArtifactBucket/$carehandoArtifactKey"
if ($LASTEXITCODE) { throw 'Verifier artifact upload failed' }
aws --profile $carehandoAwsProfile --region $carehandoAwsRegion cloudformation deploy `
  --stack-name carehando-receipt-verifier `
  --template-file billing/production-verifier.yaml `
  --capabilities CAPABILITY_IAM `
  --parameter-overrides "VerifierCodeBucket=$carehandoArtifactBucket" "VerifierCodeKey=$carehandoArtifactKey" "AmazonRvsSecretArn=$carehandoSecretArn" `
  --no-execute-changeset
if ($LASTEXITCODE) { throw 'CloudFormation change set creation failed' }
```

Review the change set and execute it in CloudFormation. Copy the `ReceiptVerifierUrl` output. When rotating the RVS shared key, increment the stack’s `VerifierConfigurationRevision` parameter to refresh the Lambda configuration. Keep earlier S3 artifacts available for rollback until the updated version has passed testing.

For a test-only IAP item in Live App Testing, submit `carehando_premium` through **Submit to Test** and add `AllowLiveAppTestingReceipts=1` to the verifier stack’s parameter overrides. The verifier still uses production RVS and checks that Amazon marks the receipt as a beta product. Use a separate verifier stack for the beta if the public app already uses a production endpoint. For a first release, reset the test entitlements and return the setting to `0` before publishing to the public Appstore. [Amazon IAP item submission](https://developer.amazon.com/docs/in-app-purchasing/iap-create-and-submit-iap-items.html) · [Amazon receipt response fields](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html)

## Verify the release connection

1. POST an empty JSON object to the new endpoint and verify it returns HTTP 400. Check that a GET request does not invoke the verifier. Use only synthetic input for these checks.
2. Add the app-specific Amazon public authentication key at `app/src/main/assets/AppstoreAuthenticationKey.pem`. Build with `-PcarehandoReceiptVerifierUrl=<ReceiptVerifierUrl>`. The Gradle value contains the public HTTPS URL. The RVS shared key stays on the server.
3. Use the Amazon-signed build in Live App Testing. Verify store price, a successful purchase, cancelled checkout, restore, revoked entitlement and startup with the network unavailable. Confirm cached access survives temporary verifier errors. Check that CloudWatch logs contain no purchase identifiers or credentials.
4. Complete the Developer Console privacy questionnaire with the deployed provider and retention details. Record test results and the tested APK version in the release report.

[AWS HTTP API Lambda integration](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html) · [CloudFormation Secrets Manager references](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/dynamic-references-secretsmanager.html) · [Amazon Appstore IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html)

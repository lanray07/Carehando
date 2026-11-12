"""AWS Lambda Function URL handler for Amazon receipt verification."""
import json
import os
import urllib.error
import urllib.parse
import urllib.request


class RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def reply(status, payload):
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Cache-Control": "no-store"},
        "body": json.dumps(payload, separators=(",", ":")),
    }


def handler(event, _context):
    if event.get("requestContext", {}).get("http", {}).get("method") != "POST":
        return reply(405, {})
    try:
        raw = event.get("body") or ""
        if event.get("isBase64Encoded") or len(raw) > 4096:
            return reply(400, {})
        data = json.loads(raw)
        user, receipt = data["userId"], data["receiptId"]
        if not all(isinstance(v, str) and 0 < len(v) <= 1024 for v in (user, receipt)):
            return reply(400, {})
    except (ValueError, KeyError, TypeError):
        return reply(400, {})

    sandbox = os.environ.get("AMAZON_RVS_SANDBOX") == "1"
    allow_beta = os.environ.get("AMAZON_RVS_ALLOW_BETA") == "1"
    secret = os.environ.get("AMAZON_RVS_SECRET")
    if not secret:
        return reply(503, {})
    quote = lambda value: urllib.parse.quote(value, safe="")
    prefix = "/sandbox" if sandbox else ""
    url = (
        f"https://appstore-sdk.amazon.com{prefix}/version/1.0/verifyReceiptId"
        f"/developer/{quote(secret)}/user/{quote(user)}/receiptId/{quote(receipt)}"
    )
    try:
        opener = urllib.request.build_opener(RejectRedirects())
        with opener.open(url, timeout=5) as response:
            purchase = json.loads(response.read(16_384))
        if not isinstance(purchase, dict) or "cancelDate" not in purchase:
            return reply(502, {})
        beta_product = purchase.get("betaProduct") is True
        live_purchase = purchase.get("testTransaction") is False and not beta_product
        valid = (
            purchase.get("receiptId") == receipt
            and purchase.get("productId") == "carehando_premium"
            and purchase.get("productType") == "ENTITLED"
            and purchase["cancelDate"] is None
            and (sandbox or live_purchase or (allow_beta and beta_product))
        )
        return reply(200, {"entitled": valid})
    except urllib.error.HTTPError as error:
        if error.code == 410:
            return reply(200, {"entitled": False})
        return reply(502, {})
    except (urllib.error.URLError, ValueError, TimeoutError):
        return reply(502, {})

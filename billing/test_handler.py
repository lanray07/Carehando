import json
import os
import unittest
from urllib.error import HTTPError
from unittest.mock import Mock, patch

from billing.handler import handler


class ReceiptVerificationTest(unittest.TestCase):
    def setUp(self):
        self.event = {
            "requestContext": {"http": {"method": "POST"}},
            "body": json.dumps({"userId": "test-user", "receiptId": "test-receipt"}),
        }
        self.receipt = {
            "receiptId": "test-receipt",
            "productId": "carehando_premium",
            "productType": "ENTITLED",
            "cancelDate": None,
            "testTransaction": False,
        }

    def verify(self, receipt, allow_beta="0"):
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = json.dumps(receipt).encode()
        with patch.dict(os.environ, {"AMAZON_RVS_SECRET": "fixture", "AMAZON_RVS_SANDBOX": "0",
                                    "AMAZON_RVS_ALLOW_BETA": allow_beta}):
            with patch("billing.handler.urllib.request.build_opener") as opener:
                opener.return_value.open.return_value = response
                return handler(self.event, None)

    def test_grants_only_matching_active_production_entitlements(self):
        self.assertEqual(json.loads(self.verify(self.receipt)["body"]), {"entitled": True})
        for change in ({"cancelDate": 1}, {"testTransaction": True}, {"productId": "other"}, {"receiptId": "other"}):
            with self.subTest(change=change):
                self.assertEqual(json.loads(self.verify(self.receipt | change)["body"]), {"entitled": False})

    def test_rejects_malformed_request_without_calling_amazon(self):
        self.event["body"] = json.dumps({"receiptId": 1})
        with patch("billing.handler.urllib.request.build_opener") as opener:
            self.assertEqual(handler(self.event, None)["statusCode"], 400)
            opener.assert_not_called()

    def test_accepts_only_amazon_marked_beta_products_in_live_app_testing_mode(self):
        beta = self.receipt | {"betaProduct": True, "testTransaction": True}
        self.assertEqual(json.loads(self.verify(beta)["body"]), {"entitled": False})
        self.assertEqual(json.loads(self.verify(beta, allow_beta="1")["body"]), {"entitled": True})
        for change in ({"cancelDate": 1}, {"betaProduct": False}, {"productId": "other"},
                       {"receiptId": "other"}, {"productType": "CONSUMABLE"}):
            with self.subTest(change=change):
                self.assertEqual(json.loads(self.verify(beta | change, allow_beta="1")["body"]), {"entitled": False})
        beta_with_live_flag = beta | {"testTransaction": False}
        self.assertEqual(json.loads(self.verify(beta_with_live_flag)["body"]), {"entitled": False})

    def test_unconfigured_verifier_is_unavailable(self):
        with patch.dict(os.environ, {"AMAZON_RVS_SECRET": ""}):
            self.assertEqual(handler(self.event, None)["statusCode"], 503)

    def test_revoked_receipt_is_denied_and_temporary_errors_remain_retryable(self):
        with patch.dict(os.environ, {"AMAZON_RVS_SECRET": "fixture", "AMAZON_RVS_SANDBOX": "0"}):
            with patch("billing.handler.urllib.request.build_opener") as opener:
                request = opener.return_value.open
                request.side_effect = HTTPError("https://example.invalid", 410, "revoked", {}, None)
                self.assertEqual(json.loads(handler(self.event, None)["body"]), {"entitled": False})
                request.side_effect = HTTPError("https://example.invalid", 429, "throttled", {}, None)
                self.assertEqual(handler(self.event, None)["statusCode"], 502)


if __name__ == "__main__":
    unittest.main()

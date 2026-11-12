"""Exercise Amazon App Tester while preserving the device's existing sandbox fixture."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from handler import handler

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = "/sdcard/amazon.sdktester.json"
SANDBOX_PROP = "debug.amazon.sandboxmode"
RECEIPT = "cache/carehando-sandbox-receipt.json"
APP = "app.carehando.debug"


def run(args):
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])

    def call(*command, timeout=20, check=True):
        return subprocess.run(adb + list(command), capture_output=True, timeout=timeout, check=check).stdout

    prior = call("shell", "getprop", SANDBOX_PROP).decode().strip()
    if args.purchase or args.paywall:
        trust = call("shell", "dumpsys", "trust").decode(errors="replace")
        if not re.search(r"\(current\):[^\r\n]*deviceLocked=0", trust):
            raise SystemExit("Unlock the tablet before starting a sandbox purchase.")
    present = call("shell", "if", "[", "-f", FIXTURE, "];", "then", "echo", "yes;", "fi").strip() == b"yes"
    original = call("exec-out", "cat", FIXTURE) if present else b""
    merged = json.loads(original.decode("utf-8-sig")) if present else {}
    merged.update(json.loads((ROOT / "billing/amazon.sdktester.carehando.json").read_text(encoding="utf-8")))
    with tempfile.TemporaryDirectory(dir=ROOT / ".gradle") as scratch:
        fixture = Path(scratch) / "amazon.sdktester.json"
        fixture.write_text(json.dumps(merged), encoding="utf-8")
        try:
            call("push", str(fixture), FIXTURE)
            call("shell", "setprop", SANDBOX_PROP, "debug")
            if call("shell", "getprop", SANDBOX_PROP).strip() != b"debug":
                raise SystemExit("Could not enable App Tester sandbox mode.")
            call("shell", "am", "force-stop", "com.amazon.sdktestclient")
            call("shell", "run-as", APP, "rm", "-f", RECEIPT, check=False)
            if args.purchase:
                print("Confirm the Carehando purchase in Amazon App Tester if prompted. This is a sandbox transaction.", flush=True)
            def instrument(*options, test="app.carehando.AppTesterDeviceTest"):
                output = call(
                    "shell", "am", "instrument", "-w", "-r", "-e", "class", test,
                    "-e", "carehandoAppTester", "true", *options,
                    f"{APP}.test/androidx.test.runner.AndroidJUnitRunner", timeout=140,
                ).decode("utf-8", errors="replace")
                print(output)
                if "OK (1 test)" not in output or "numtests=1" not in output or "AssumptionViolated" in output:
                    raise SystemExit("App Tester instrumentation did not pass.")

            if args.paywall:
                from sandbox_https import PORT, serve_sandbox_verifier
                port = f"tcp:{PORT}"
                with serve_sandbox_verifier(scratch) as certificate:
                    call("reverse", "--no-rebind", port, port)
                    try:
                        instrument("-e", "carehandoSandboxCertificate", base64.b64encode(certificate).decode("ascii"),
                                   test="app.carehando.CareJourneyTest#paywallRestoresVerifiedSandboxPurchase")
                    finally:
                        call("reverse", "--remove", port)
            else:
                instrument(
                    "-e", "carehandoSandboxPurchase", str(args.purchase).lower(),
                    "-e", "carehandoSandboxRevoked", str(args.revoked).lower(),
                )
            if args.purchase:
                body = call("exec-out", "run-as", APP, "cat", RECEIPT).decode("utf-8")
                os.environ["AMAZON_RVS_SANDBOX"] = "1"
                os.environ["AMAZON_RVS_SECRET"] = "carehando-sandbox"
                result = handler({"requestContext": {"http": {"method": "POST"}}, "body": body}, None)
                verified = result["statusCode"] == 200 and json.loads(result["body"]).get("entitled") is True
                print(f"Amazon RVS sandbox HTTP status {result['statusCode']}; entitlement verified: {verified}")
                if not verified:
                    raise SystemExit("The Amazon RVS sandbox did not verify the restored entitlement.")
                instrument("-e", "carehandoSandboxFulfill", "true")
                print("Sent fulfillment for the verified sandbox receipt.")
        finally:
            try:
                call("shell", "setprop", SANDBOX_PROP, prior or "''")
            finally:
                try:
                    if present:
                        fixture.write_bytes(original)
                        call("push", str(fixture), FIXTURE)
                    else:
                        call("shell", "rm", "-f", FIXTURE)
                finally:
                    call("shell", "run-as", APP, "rm", "-f", RECEIPT, check=False)
                    call("shell", "am", "force-stop", "com.amazon.sdktestclient", check=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    scenario = parser.add_mutually_exclusive_group()
    scenario.add_argument("--purchase", action="store_true")
    scenario.add_argument("--revoked", action="store_true", help="Check updates after cancelling the Carehando test transaction.")
    scenario.add_argument("--paywall", action="store_true", help="Restore a previous sandbox purchase through the app's Settings.")
    run(parser.parse_args())

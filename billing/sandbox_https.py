"""Loopback-only HTTPS adapter for testing the app against Amazon's RVS sandbox."""
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address
import os
from pathlib import Path
import ssl
from threading import Thread

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from handler import handler

PORT = 18765


@contextmanager
def serve_sandbox_verifier(scratch):
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Carehando QA loopback")])
    now = datetime.now(timezone.utc)
    certificate = (
        x509.CertificateBuilder().subject_name(name).issuer_name(name)
        .public_key(private_key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(hours=2))
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ip_address("127.0.0.1"))]), critical=False)
        .sign(private_key, hashes.SHA256())
    )
    pem = certificate.public_bytes(serialization.Encoding.PEM)
    cert_file, key_file = Path(scratch) / "sandbox.pem", Path(scratch) / "sandbox-key.pem"
    cert_file.write_bytes(pem)
    key_file.write_bytes(private_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                                     serialization.NoEncryption()))
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert_file, key_file)
    os.environ["AMAZON_RVS_SANDBOX"] = "1"
    os.environ["AMAZON_RVS_SECRET"] = "carehando-sandbox"

    class Verifier(BaseHTTPRequestHandler):
        def do_POST(self):
            size = self.headers.get("Content-Length", "")
            if self.path != "/verify" or not size.isdecimal() or not 0 < int(size) <= 4096:
                self.send_error(400)
                return
            body = self.rfile.read(int(size)).decode("utf-8", errors="replace")
            result = handler({"requestContext": {"http": {"method": "POST"}}, "body": body}, None)
            response = result["body"].encode("utf-8")
            self.send_response(result["statusCode"])
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response)))
            self.end_headers()
            self.wfile.write(response)
            print(f"Sandbox verifier HTTP {result['statusCode']} {result['body']}", flush=True)

        def log_message(self, format, *args):
            pass

    with ThreadingHTTPServer(("127.0.0.1", PORT), Verifier) as server:
        server.socket = context.wrap_socket(server.socket, server_side=True)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield certificate.public_bytes(serialization.Encoding.DER)
        finally:
            server.shutdown()
            thread.join()

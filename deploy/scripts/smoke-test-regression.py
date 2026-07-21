#!/usr/bin/env python3
"""Local regression harness for the authenticated deployment smoke test."""

from __future__ import annotations

import http.server
import os
import pathlib
import socketserver
import subprocess
import sys
import threading
import urllib.parse

VERSION = "27.4.2"
USERNAME = "smoke-admin"
PASSWORD = "correct-password-regression"
CSRF = "csrf-regression-token"
SESSION = "session-ok"
ROOT = pathlib.Path(__file__).resolve().parents[2]
SMOKE_TEST = ROOT / "deploy" / "scripts" / "smoke-test.sh"
STATIC_JS = [
    "js/10-core.js",
    "js/20-data.js",
    "js/30-calendar.js",
    "js/40-overtime.js",
    "js/50-tasks.js",
    "js/60-settings.js",
    "js/70-user-boot.js",
]


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "DutyLogSmokeRegression/1"

    def log_message(self, *_: object) -> None:
        return

    def _send(self, status: int, body: str = "", content_type: str = "text/plain", headers: dict[str, str] | None = None) -> None:
        payload = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        if headers:
            for key, value in headers.items():
                self.send_header(key, value)
        self.end_headers()
        self.wfile.write(payload)

    def _authenticated(self) -> bool:
        return f"JSESSIONID={SESSION}" in self.headers.get("Cookie", "")

    def do_GET(self) -> None:  # noqa: N802
        path = urllib.parse.urlsplit(self.path).path
        if path == "/actuator/health":
            self._send(200, '{"status":"UP"}', "application/json")
            return
        if path == "/login.html":
            body = f'<html><body>DutyLog<script src="/js/login.js?v={VERSION}"></script></body></html>'
            self._send(200, body, "text/html", {"Set-Cookie": f"XSRF-TOKEN={CSRF}; Path=/; Secure; SameSite=Lax"})
            return
        if path == "/js/login.js":
            self._send(200, "const payload = { languagePreference: currentLang };", "application/javascript")
            return
        if path == "/":
            if not self._authenticated():
                if "text/html" in self.headers.get("Accept", ""):
                    self._send(302, headers={"Location": "/login.html"})
                else:
                    self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
                return
            assets = "".join(f'<script src="/{asset}?v={VERSION}"></script>' for asset in STATIC_JS)
            # Large multiline output reproduces the former `echo "$APP_HTML" | grep -q`
            # SIGPIPE/141 failure under `set -o pipefail` after an early match.
            padding = "deployment-smoke-padding\n" * 20000
            body = (
                f'<html><head><link href="app.css?v={VERSION}" rel="stylesheet"></head>'
                f'<body>DutyLog\n{assets}\n{padding}</body></html>'
            )
            self._send(200, body, "text/html")
            return
        if path == "/manifest.json":
            self._send(200, '{"name":"DutyLog"}', "application/json")
            return
        if path == "/service-worker.js":
            self._send(200, f'const CACHE_NAME = "dutylog-shell-v{VERSION}";', "application/javascript")
            return
        if path == "/app.css":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, ":root { --ok: 1; }", "text/css")
            return
        if path == "/api/auth/registration-status":
            self._send(200, '{"enabled":false}', "application/json")
            return
        if path == "/api/admin/status":
            self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            return
        if path == "/api/profile":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, '{"username":"smoke-admin","workTimezone":"Europe/Moscow"}', "application/json")
            return
        if path == "/api/modules":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, '{"enabled":{"calendar":true}}', "application/json")
            return
        if path == "/api/profile/sessions":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, '[]', "application/json")
            return
        if path == "/api/auth/me":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, '{"username":"smoke-admin"}', "application/json")
            return
        if path == "/js/10-core.js":
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, f'const DUTYLOG_VERSION = "{VERSION}";', "application/javascript")
            return
        if path in {f"/{asset}" for asset in STATIC_JS[1:]}:
            if not self._authenticated():
                self._send(401, '{"code":"AUTH_REQUIRED"}', "application/json")
            else:
                self._send(200, "\"use strict\";", "application/javascript")
            return
        self._send(404, "not found")

    def do_POST(self) -> None:  # noqa: N802
        if urllib.parse.urlsplit(self.path).path != "/perform_login":
            self._send(404, "not found")
            return
        length = int(self.headers.get("Content-Length", "0"))
        form = urllib.parse.parse_qs(self.rfile.read(length).decode("utf-8"), keep_blank_values=True)
        valid = (
            form.get("username") == [USERNAME]
            and form.get("password") == [PASSWORD]
            and form.get("_csrf") == [CSRF]
            and f"XSRF-TOKEN={CSRF}" in self.headers.get("Cookie", "")
        )
        if valid:
            self._send(302, headers={"Location": "/", "Set-Cookie": f"JSESSIONID={SESSION}; Path=/; Secure; HttpOnly"})
        else:
            self._send(302, headers={"Location": "/login.html?error"})


class ThreadedServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def run_smoke(base_url: str, password: str, require_auth: bool = True) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(
        {
            "DUTYLOG_RELEASE_VERSION": VERSION,
            "DUTYLOG_ADMIN_USERNAME": USERNAME,
            "DUTYLOG_ADMIN_PASSWORD": password,
            "DUTYLOG_SMOKE_REQUIRE_AUTH": "true" if require_auth else "false",
        }
    )
    return subprocess.run(
        ["bash", str(SMOKE_TEST), base_url],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def main() -> int:
    with ThreadedServer(("127.0.0.1", 0), Handler) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}"

        good = run_smoke(base_url, PASSWORD)
        if good.returncode != 0 or "Smoke test passed." not in good.stdout:
            print(good.stdout, file=sys.stderr)
            print("authenticated smoke test regression failed", file=sys.stderr)
            return 1

        bad = run_smoke(base_url, "wrong-password")
        if bad.returncode == 0 or "Smoke-test login was rejected." not in bad.stdout:
            print(bad.stdout, file=sys.stderr)
            print("invalid credentials were not rejected", file=sys.stderr)
            return 1
        if "wrong-password" in bad.stdout:
            print("smoke test leaked a password", file=sys.stderr)
            return 1

        print("Authenticated smoke-test regression passed.")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())

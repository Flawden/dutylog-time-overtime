#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-26.6.1}"
ERRORS=0
STATIC_JS=(
  "js/10-core.js"
  "js/20-data.js"
  "js/30-calendar.js"
  "js/40-overtime.js"
  "js/50-tasks.js"
  "js/60-settings.js"
  "js/70-user-boot.js"
)

fail() {
  echo "ERROR: $*" >&2
  ERRORS=$((ERRORS + 1))
}

ok() {
  echo "OK:    $*"
}

need() {
  if command -v "$1" >/dev/null 2>&1; then
    ok "command available: $1"
  else
    fail "required command not found: $1"
  fi
}

contains() {
  local file="$1"
  local text="$2"
  if grep -Fq "$text" "$file"; then
    ok "$file contains: $text"
  else
    fail "$file does not contain expected text: $text"
  fi
}

not_contains() {
  local file="$1"
  local text="$2"
  if grep -Fq "$text" "$file"; then
    fail "$file contains forbidden text: $text"
  else
    ok "$file does not contain forbidden text: $text"
  fi
}

echo "DutyLog release check"
echo "Project: $PROJECT_ROOT"
echo "Version: $VERSION"
echo

need node
need python3
need bash

if (( ERRORS > 0 )); then
  echo "Missing required commands; aborting." >&2
  exit 1
fi

echo

echo "1) Version consistency"
contains src/main/resources/static/js/10-core.js "DUTYLOG_VERSION = \"$VERSION\""
contains src/main/resources/static/service-worker.js "dutylog-shell-v$VERSION"
contains src/main/resources/static/index.html "app.css?v=$VERSION"
for asset in "${STATIC_JS[@]}"; do
  contains src/main/resources/static/index.html "$asset?v=$VERSION"
done
contains src/main/resources/application.properties "info.app.version=$VERSION"
contains src/main/resources/application-prod.properties "info.app.version=$VERSION"
contains pom.xml "<version>${VERSION}.0</version>"
contains deploy/scripts/smoke-test.sh "VERSION=\"\${DUTYLOG_RELEASE_VERSION:-$VERSION}\""
contains deploy/scripts/smoke-test.sh "dutylog-shell-v\$VERSION"
contains deploy/scripts/smoke-test.sh "DUTYLOG_VERSION = \\\"\$VERSION\\\""
not_contains src/main/resources/static/index.html "app.js?v="
not_contains src/main/resources/static/index.html "<body class=\"appBooting\">"
contains src/main/resources/static/js/70-user-boot.js "armBootFailsafe"

if grep -R "result.put(\"version\", \"" -n src/main/java >/tmp/dutylog-version-hardcode.txt; then
  cat /tmp/dutylog-version-hardcode.txt >&2
  fail "hardcoded admin status version found; use info.app.version instead"
else
  ok "no hardcoded admin status version"
fi

echo

echo "2) Frontend static checks"
for f in src/main/resources/static/js/*.js; do
  node --check "$f" >/dev/null
  ok "node --check $f"
done
node --check src/main/resources/static/service-worker.js >/dev/null
ok "node --check service-worker.js"
python3 -m json.tool src/main/resources/static/manifest.json >/dev/null
ok "manifest.json is valid JSON"

python3 - "$VERSION" <<'PY'
from pathlib import Path
import re, sys
version = sys.argv[1]
html = Path('src/main/resources/static/index.html').read_text(encoding='utf-8')
expected = [
    'js/10-core.js',
    'js/20-data.js',
    'js/30-calendar.js',
    'js/40-overtime.js',
    'js/50-tasks.js',
    'js/60-settings.js',
    'js/70-user-boot.js',
]
actual = re.findall(r'<script\s+src="(js/\d+-[^"]+\.js)\?v=([^"]+)"', html)
actual_paths = [p for p, v in actual]
actual_versions = {v for p, v in actual}
if actual_paths != expected:
    raise SystemExit(f'static js order mismatch: {actual_paths}, expected {expected}')
if actual_versions != {version}:
    raise SystemExit(f'static js versions mismatch: {actual_versions}, expected {version}')
print('OK:    static js order and cache-busting version')
PY

python3 - <<'PY'
from pathlib import Path
import re
html = Path('src/main/resources/static/index.html').read_text(encoding='utf-8')
js = '\n'.join(p.read_text(encoding='utf-8') for p in sorted(Path('src/main/resources/static/js').glob('*.js')))
ids = re.findall(r'\bid=["\']([^"\']+)["\']', html)
idset = set(ids) | set(re.findall(r'id=\\?["\']([\w-]+)\\?["\']', js))
refs = set(re.findall(r'\$\(["\']([^"\']+)["\']\)', js))
refs |= set(re.findall(r'getElementById\(["\']([^"\']+)["\']\)', js))
refs |= set(re.findall(r'byId\(["\']([^"\']+)["\']\)', js))
dynamic = {'bdayBanner', 'headerAvatar', 'dayModulesSettingsBtn'}
missing = sorted(refs - idset - dynamic)
dups = sorted({x for x in ids if ids.count(x) > 1})
if missing:
    raise SystemExit(f'missing html ids: {missing}')
if dups:
    raise SystemExit(f'duplicate html ids: {dups}')
print('OK:    html/js id crosscheck')
PY

python3 - <<'PY'
from pathlib import Path
runtime_files = list(Path('src/main/resources/static').glob('**/*'))
legacy = []
for p in runtime_files:
    if not p.is_file() or p.suffix not in {'.html', '.js', '.css'}:
        continue
    text = p.read_text(encoding='utf-8')
    if 'app.js' in text:
        legacy.append(str(p))
if legacy:
    raise SystemExit(f'legacy app.js runtime references found: {legacy}')
print('OK:    no legacy app.js runtime references')
PY

echo

echo "3) Backend/static config checks"
python3 - <<'PY'
import xml.etree.ElementTree as ET
ET.parse('pom.xml')
print('OK:    pom.xml parses')
PY

for f in docker-compose.yml docker-compose.prod.yml; do
  grep -q '^services:' "$f" && ok "$f has services block" || fail "$f does not look like compose file"
done

bash -n deploy/scripts/*.sh
ok "bash scripts syntax"

python3 - <<'PY'
from pathlib import Path
bad=[]
for p in Path('src').rglob('*.java'):
    text=p.read_text(encoding='utf-8')
    if text.count('{') != text.count('}'):
        bad.append((str(p), text.count('{'), text.count('}')))
if bad:
    raise SystemExit(f'java brace mismatch: {bad[:10]}')
print('OK:    java brace balance')
PY

python3 - <<'PY'
from pathlib import Path
import re
root=Path('src/main/resources/db/migration/postgresql')
files=sorted(root.glob('V*__*.sql'))
versions=[]
for p in files:
    m=re.match(r'V(\d+)__.+\.sql$', p.name)
    if not m:
        raise SystemExit(f'invalid migration name: {p.name}')
    versions.append(int(m.group(1)))
ordered=sorted(versions)
if len(ordered) != len(set(ordered)):
    raise SystemExit(f'duplicate migration versions: {ordered}')
expected=list(range(1, max(ordered)+1)) if ordered else []
if ordered != expected:
    raise SystemExit(f'migration sequence is not gapless: {ordered}, expected {expected}')
print(f'OK:    flyway migrations gapless V1..V{max(ordered) if ordered else 0}')
PY

echo

echo "4) Production config safety"
if grep -Eq '^[[:space:]]+-[[:space:]]+"?8080:8080"?' docker-compose.prod.yml; then
  fail "docker-compose.prod.yml exposes app port 8080 publicly"
else
  ok "production compose does not publish app port 8080"
fi
contains deploy/caddy/Caddyfile.example "X-Content-Type-Options nosniff"
contains deploy/caddy/Caddyfile.example "Strict-Transport-Security"
contains deploy/nginx/dutylog.conf.example "limit_req_zone"


echo
echo "5) Security review guardrails"
contains src/main/java/ru/daniil/shifts/config/SecurityHeadersFilter.java "Content-Security-Policy"
contains src/main/java/ru/daniil/shifts/config/SecurityHeadersFilter.java "Strict-Transport-Security"
contains src/main/resources/application-prod.properties "server.servlet.session.cookie.secure=true"
contains src/main/resources/application-prod.properties "server.servlet.session.cookie.http-only=true"
contains src/main/resources/application-prod.properties "server.servlet.session.cookie.same-site=lax"
contains src/main/java/ru/daniil/shifts/web/MobileController.java "requireEnabledModulesForMobileDayChange"
contains src/main/java/ru/daniil/shifts/telegram/TelegramLinkService.java "moduleService.requireEnabled(owner, ModuleService.TELEGRAM)"
contains src/test/java/ru/daniil/shifts/web/ModuleSecurityTest.java "mobileSyncCannotWriteNotesWhenNotesModuleDisabled"
contains docs/SECURITY_REVIEW.md "v26.6.1"

echo

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

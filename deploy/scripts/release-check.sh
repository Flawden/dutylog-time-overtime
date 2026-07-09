#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-26.3}"
ERRORS=0

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
contains src/main/resources/static/index.html "js/10-core.js?v=$VERSION"
contains src/main/resources/application.properties "info.app.version=$VERSION"
contains src/main/resources/application-prod.properties "info.app.version=$VERSION"
contains deploy/scripts/smoke-test.sh "dutylog-shell-v$VERSION"
contains deploy/scripts/smoke-test.sh "DUTYLOG_VERSION = \"$VERSION\""

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

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

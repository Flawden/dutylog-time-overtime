#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-27.2.30}"
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
  if grep -Fq -- "$text" "$file"; then
    ok "$file contains: $text"
  else
    fail "$file does not contain expected text: $text"
  fi
}

not_contains() {
  local file="$1"
  local text="$2"
  if grep -Fq -- "$text" "$file"; then
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
contains src/main/resources/static/login.html "/js/login.js?v=$VERSION"
for asset in "${STATIC_JS[@]}"; do
  contains src/main/resources/static/index.html "$asset?v=$VERSION"
done
contains src/main/resources/application.properties "info.app.version=\${DUTYLOG_BUILD_VERSION:$VERSION}"
contains src/main/resources/application-prod.properties "info.app.version=\${DUTYLOG_BUILD_VERSION:$VERSION}"
contains src/main/resources/application.properties "info.app.release-version=$VERSION"
contains src/main/resources/application-prod.properties "info.app.release-version=$VERSION"
contains src/test/resources/application.properties "spring.jpa.open-in-view=false"
contains pom.xml "<version>${VERSION}</version>"
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
node --check playwright.config.js >/dev/null
ok "node --check playwright.config.js"
for f in e2e/*.js; do
  node --check "$f" >/dev/null
  ok "node --check $f"
done
python3 -m json.tool src/main/resources/static/manifest.json >/dev/null
ok "manifest.json is valid JSON"
python3 -m json.tool package.json >/dev/null
ok "package.json is valid JSON"

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
core = Path('src/main/resources/static/js/10-core.js').read_text(encoding='utf-8')
boot = core.find('applyAppearance(loadLocalAppearance());')
helper_dom = core.find('function $(id)')
helper_esc = core.find('function esc(')
late_const = core.find('const $ =')
if boot == -1:
    raise SystemExit('boot appearance call not found')
if helper_dom == -1 or helper_dom > boot:
    raise SystemExit('shared $ helper must be defined before boot-time appearance apply')
if helper_esc == -1 or helper_esc > boot:
    raise SystemExit('shared esc helper must be defined before boot-time appearance apply')
if late_const != -1 and late_const > boot:
    raise SystemExit('late const $ creates temporal dead zone for top-level boot code')
print('OK:    boot-time shared helpers are defined before use')
PY

python3 - <<'PY'
from pathlib import Path
import re
settings = Path('src/main/resources/static/js/60-settings.js').read_text(encoding='utf-8')
match = re.search(r'function initDiagnosticsEvents\(\)\{(?P<body>.*?)\n\}', settings, re.S)
if not match:
    raise SystemExit('initDiagnosticsEvents() not found')
body = match.group('body')
for forbidden in ['refreshRegistrationAdmin', 'refreshAdminUsers']:
    if re.search(r'^\s*' + re.escape(forbidden) + r'\(\);\s*$', body, re.M):
        raise SystemExit(f'admin endpoint auto-fetch still runs during settings init: {forbidden}()')
print('OK:    admin endpoints do not auto-fetch during generic settings init')
PY
contains src/main/resources/static/service-worker.js "url.origin !== self.location.origin"
contains src/main/resources/static/service-worker.js "includes(url.protocol)"
contains src/main/resources/static/service-worker.js "catch(() => {})"
contains src/main/resources/static/app.css "align-items:start"
contains src/main/resources/static/app.css "moduleDevDetails summary"
contains src/main/resources/static/js/20-data.js "dayModulesHintText"
contains src/main/resources/static/js/20-data.js "showDeveloperDetails = !!state.profile?.admin"
contains src/main/resources/static/js/20-data.js 'details.length ? `<details class="moduleDevDetails"'
contains src/main/resources/static/js/60-settings.js "const timeSettings = state.timeSettings"
not_contains src/main/resources/static/js/60-settings.js "const t = state.timeSettings"
contains src/main/resources/static/js/60-settings.js "function refreshAdminPanel()"
contains src/main/resources/static/js/70-user-boot.js "if (state.profile?.admin) refreshAdminPanel();"

python3 - <<'PY'
from pathlib import Path
import re
html = '\n'.join(
    Path(path).read_text(encoding='utf-8')
    for path in ['src/main/resources/static/index.html', 'src/main/resources/static/login.html']
)
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
bad=[]
for p in Path('src').rglob('*.properties'):
    text=p.read_text(encoding='utf-8')
    if any(ord(ch) > 127 for ch in text):
        bad.append(str(p))
if bad:
    raise SystemExit(f'non-ASCII text found in .properties files: {bad}')
print('OK:    .properties files use ASCII-safe comments')
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
contains deploy/caddy/Caddyfile.example "script-src 'self'"
not_contains deploy/caddy/Caddyfile.example "script-src 'self' 'unsafe-inline'"
contains deploy/nginx/dutylog.conf.example "limit_req_zone"
contains deploy/nginx/dutylog.conf.example "Strict-Transport-Security"
contains deploy/nginx/dutylog.conf.example "script-src 'self'"
not_contains deploy/nginx/dutylog.conf.example "script-src 'self' 'unsafe-inline'"
contains src/main/resources/application-prod.properties 'dutylog.registration.default-enabled=${DUTYLOG_REGISTRATION_DEFAULT_ENABLED:false}'
contains src/main/resources/application-prod.properties 'dutylog.security.rate-limit.enabled=${DUTYLOG_SECURITY_RATE_LIMIT_ENABLED:true}'
contains src/main/resources/application-prod.properties 'dutylog.security.trust-proxy-headers=${DUTYLOG_SECURITY_TRUST_PROXY_HEADERS:true}'
contains docker-compose.prod.yml 'DUTYLOG_REGISTRATION_DEFAULT_ENABLED: ${DUTYLOG_REGISTRATION_DEFAULT_ENABLED:-false}'
contains docker-compose.prod.yml 'DUTYLOG_SECURITY_RATE_LIMIT_ENABLED: ${DUTYLOG_SECURITY_RATE_LIMIT_ENABLED:-true}'
contains docker-compose.prod.yml 'DUTYLOG_SECURITY_TRUST_PROXY_HEADERS: ${DUTYLOG_SECURITY_TRUST_PROXY_HEADERS:-true}'
contains .env.production.example 'DUTYLOG_SECURITY_TRUST_PROXY_HEADERS=true'
contains deploy/compose/docker-compose.deploy.yml 'DUTYLOG_SECURITY_TRUST_PROXY_HEADERS: ${DUTYLOG_SECURITY_TRUST_PROXY_HEADERS:-true}'
contains deploy/nginx/dutylog.conf.example 'proxy_set_header X-Forwarded-For $remote_addr;'
contains deploy/caddy/Caddyfile.example 'header_up X-Real-IP {remote_host}'
contains deploy/caddy/Caddyfile.example 'header_up X-Forwarded-For {remote_host}'
contains deploy/caddy/Caddyfile.cicd 'header_up X-Real-IP {remote_host}'
contains deploy/caddy/Caddyfile.cicd 'header_up X-Forwarded-For {remote_host}'
contains docker-compose.prod.yml 'app_logs:/app/logs'
contains Dockerfile 'USER 10001:10001'
contains .github/dependabot.yml 'package-ecosystem: "maven"'
contains .github/dependabot.yml 'package-ecosystem: "github-actions"'
contains .github/dependabot.yml 'package-ecosystem: "docker"'


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
contains src/main/java/ru/daniil/shifts/service/ModuleService.java "cascadeDisableBrokenDependencies"
contains src/main/java/ru/daniil/shifts/service/ModuleService.java "explicitlyDisabled"
contains src/test/java/ru/daniil/shifts/telegram/TelegramLinkServiceTest.java "enableTelegram(user)"
contains src/test/java/ru/daniil/shifts/telegram/TelegramLinkServiceTest.java "DL-000001"
contains src/test/java/ru/daniil/shifts/web/RegistrationTest.java "status().isForbidden()"
contains docs/SECURITY_REVIEW.md "Status: v27.2.30."
contains docs/FINAL_PRODUCT_AUDIT_V27.2.29.md "## Launch decision"
contains docs/TEST_CONFIG_HOTFIX.md "v27.2.5"
contains .github/workflows/ci.yml "bash ./deploy/scripts/release-check.sh"
contains docs/CI_PERMISSION_HOTFIX.md "v27.2.5"
contains src/main/resources/static/index.html 'data-onboarding-preset="work"'
contains src/main/resources/static/index.html ">Стандарт</button>"
not_contains src/main/resources/static/index.html "Работа + переработки"
contains src/main/resources/static/js/20-data.js "renderOnboardingPresetState"
contains src/main/resources/static/js/20-data.js "aria-pressed"
contains src/main/resources/static/js/30-calendar.js "todayCell"
contains src/main/resources/static/app.css ".cell.todayCell:not(.sel)"
contains src/main/resources/static/app.css ".cell.todayCell::before"
contains src/main/resources/static/js/20-data.js "DAY_MODULES_HINT_DISMISSED_KEY"
contains src/main/resources/static/js/20-data.js "dayModulesHintCloseBtn"
contains src/main/resources/static/app.css ".dayModulesHintClose"
contains docs/ONBOARDING_TODAY_HOTFIX.md "v27.2.5"
contains docs/DAY_HINT_DISMISS_HOTFIX.md "v27.2.5"
contains docs/I18N_POLISH_HOTFIX.md "v27.2.5"
contains docs/LOGIN_LANGUAGE_HOTFIX.md "v27.2.5"
contains docs/UI_ALIGNMENT_TEST_HOTFIX.md "v27.2.5"
contains src/test/java/ru/daniil/shifts/web/RegistrationTest.java "private static String body(String username, String password, String languagePreference)"
contains src/main/resources/static/app.css "v27.2.5: stable right-side controls"
contains src/main/resources/static/app.css "#timeSettingsCard .settingsHead > .status"
contains src/main/resources/static/app.css "#profileCard .settingsHead > .avatarBig"
contains src/main/resources/static/js/login.js "languagePreference: currentLang"
contains src/main/java/ru/daniil/shifts/service/UserRegistrationService.java "user.setLanguagePreference(languagePreference)"
contains src/test/java/ru/daniil/shifts/web/RegistrationTest.java "языкСоСтраницыВходаСохраняетсяПриРегистрации"
contains src/main/resources/static/app.css 'html[lang="en"] .cell .num.today::after'
contains src/main/resources/static/js/10-core.js 'function shiftDisplayName'
contains src/main/resources/static/js/10-core.js "if (typeof renderSettingsPanels === 'function') renderSettingsPanels();"
contains src/main/resources/static/js/30-calendar.js 'esc(t("Итого:"))'
contains src/main/resources/static/js/60-settings.js 'const workLabel = state.language === "en" ? "work time"'
contains src/main/resources/static/js/60-settings.js 'esc(t("шт"))'
contains docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md "v27.2.5"
contains src/main/resources/static/app.css "v27.2.5: notifications header alignment"
contains src/main/resources/static/app.css "#notifyCard > .notifyHead"
contains src/main/resources/static/app.css ".adminShell.settingsShell"
contains src/main/resources/static/index.html 'data-admin-jump="users"'
contains src/main/resources/static/index.html 'data-admin-jump="registration"'
contains src/main/resources/static/index.html 'data-admin-jump="diagnostics"'
contains src/main/resources/static/js/60-settings.js "function initAdminNavigation"
contains src/main/resources/static/js/60-settings.js "notificationsActive"

# Android API v1 contract (introduced in v27.1.0, retained in v27.2.5)
contains src/main/resources/static/js/login.js "languagePreference: currentLang"
not_contains src/main/resources/static/login.html "<script>"
not_contains src/main/java/ru/daniil/shifts/config/SecurityHeadersFilter.java "script-src 'self' 'unsafe-inline'"
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java 'securityMatcher("/api/mobile/**", "/api/v1/mobile/**")'
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java 'SessionCreationPolicy.STATELESS'
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java 'FilterRegistrationBean<BearerTokenAuthenticationFilter>'
contains src/test/java/ru/daniil/shifts/web/MobileSecurityBoundaryTest.java "webSessionCannotAuthenticateMobileApi"
contains src/test/java/ru/daniil/shifts/web/MobileSecurityBoundaryTest.java "validBearerAuthenticatesMobileApi"
contains src/main/java/ru/daniil/shifts/service/NoteExportService.java "countByOwnerAndNoteIsNotNull"
contains src/main/java/ru/daniil/shifts/service/NoteExportService.java "maxUncompressedBytes"
contains src/main/java/ru/daniil/shifts/service/NoteExportService.java 'replace("\\", "\\\\")'
contains src/main/java/ru/daniil/shifts/web/ExportController.java "StreamingResponseBody"
contains src/main/java/ru/daniil/shifts/web/ExportController.java "CacheControl.noStore()"
contains src/test/java/ru/daniil/shifts/web/ExportControllerTest.java "чужиеЗаметкиНеУтекают"
contains src/test/java/ru/daniil/shifts/service/NoteExportServiceTest.java "countLimitRejectsBeforeRowsAreLoaded"
contains src/test/java/ru/daniil/shifts/service/OwnershipIsolationTest.java "assertEquals(HttpStatus.NOT_FOUND"
contains src/test/java/ru/daniil/shifts/service/OwnershipIsolationTest.java "RepeatMode.YEARLY"
contains src/main/java/ru/daniil/shifts/config/AuthenticationRateLimitFilter.java "AUTH_RATE_LIMITED"
contains src/test/java/ru/daniil/shifts/config/AuthenticationRateLimitFilterTest.java "authEndpointIsLimitedPerIp"
contains src/main/java/ru/daniil/shifts/config/SecurityEventLogger.java 'LoggerFactory.getLogger("SECURITY_AUDIT")'
contains src/main/java/ru/daniil/shifts/service/TaskService.java "AUTHZ_OWNERSHIP_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "AUTHZ_OWNERSHIP_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/UserRegistrationService.java "password.length() < 8"
contains docs/SECURITY_CONSOLIDATION.md "Status: v27.0-rc4."
contains docs/NOTES_EXPORT.md "GET /api/export/notes"
contains docs/SUPPLY_CHAIN.md "Dependabot"

# Android API v1 contract (introduced in v27.1.0, retained in v27.2.5)
contains src/main/java/ru/daniil/shifts/web/MobileV1Controller.java '@RequestMapping("/api/v1/mobile")'
contains src/main/java/ru/daniil/shifts/web/MobileV1AuthController.java '@RequestMapping("/api/v1/mobile/auth")'
contains src/main/java/ru/daniil/shifts/service/MobileSyncService.java 'ALREADY_APPLIED'
contains src/main/java/ru/daniil/shifts/service/MobileSyncService.java 'VERSION_CONFLICT'
contains src/main/java/ru/daniil/shifts/model/DayEntry.java '@Version'
contains src/main/java/ru/daniil/shifts/model/MobileSyncOperation.java 'uk_mobile_sync_owner_operation'
contains src/main/java/ru/daniil/shifts/web/ApiErrorResponse.java 'String code'
contains src/main/java/ru/daniil/shifts/web/ApiErrorResponse.java 'String requestId'
contains src/main/java/ru/daniil/shifts/config/ApiVersionFilter.java 'X-DutyLog-Api-Version'
contains src/main/resources/db/migration/postgresql/V22__android_api_contract.sql 'mobile_sync_operations'
contains src/main/resources/static/openapi/dutylog-v1.yaml '/api/v1/mobile/sync:'
contains docs/ANDROID_API_V1.md 'Version: **27.2.5**'
contains src/test/java/ru/daniil/shifts/web/MobileV1ContractTest.java 'ALREADY_APPLIED'
contains src/test/java/ru/daniil/shifts/web/MobileV1ContractTest.java 'VERSION_CONFLICT'
contains src/test/java/ru/daniil/shifts/web/ApiV1OpenApiContractTest.java 'OpenAPI v1 file must be packaged'
contains src/main/java/ru/daniil/shifts/service/MobileAuthService.java 'LAST_USED_WRITE_INTERVAL'
contains src/main/java/ru/daniil/shifts/model/DayEntry.java 'public long getSyncVersion() { return getRowVersion() + 1L; }'
contains src/main/java/ru/daniil/shifts/dto/Dtos.java 'e.getSyncVersion()'
contains src/main/java/ru/daniil/shifts/service/MobileSyncService.java 'current == null ? 0L : current.getSyncVersion()'
contains src/test/java/ru/daniil/shifts/web/MobileV1ContractTest.java 'op-android-stale-absent'
contains src/main/java/ru/daniil/shifts/web/ApiExceptionHandler.java '"INTERNAL_ERROR"'
contains docs/ANDROID_API_PLAN.md 'Current backend milestone: **v27.2.5 — Staging and CI/CD foundation**.'
contains src/main/resources/application.properties 'dutylog.mobile.sync.idempotency-retention-days=${DUTYLOG_MOBILE_SYNC_RETENTION_DAYS:90}'
contains src/main/resources/application-prod.properties 'dutylog.mobile.sync.idempotency-retention-days=${DUTYLOG_MOBILE_SYNC_RETENTION_DAYS:90}'
contains docker-compose.prod.yml 'DUTYLOG_MOBILE_SYNC_RETENTION_DAYS: ${DUTYLOG_MOBILE_SYNC_RETENTION_DAYS:-90}'

# v27.2.5 staging and CI/CD foundation
contains .github/workflows/deploy-staging.yml "branches: [test]"
contains .github/workflows/deploy-staging.yml 'refs/heads/test'
contains .github/workflows/deploy-staging.yml "staging-tested-tree-"
contains .github/workflows/deploy-staging.yml "docker/build-push-action@v6"
contains .github/workflows/deploy-staging.yml 'Verify the exact image on clean PostgreSQL'
contains .github/workflows/deploy-production.yml "branches: [main, master]"
contains .github/workflows/deploy-production.yml 'refs/heads/main'
contains .github/workflows/deploy-production.yml 'refs/heads/master'
contains .github/workflows/deploy-production.yml "staging-tested-tree-"
contains .github/workflows/deploy-production.yml "This exact source tree was not successfully deployed to staging."
contains .github/workflows/deploy-production.yml "needs: validate"
contains .github/workflows/deploy-production.yml "environment: production"
contains .github/workflows/deploy-production.yml 'DUTYLOG_BUILD_TREE: ${{ needs.validate.outputs.tree_sha }}'
not_contains .github/workflows/deploy-production.yml "docker/build-push-action"
contains deploy/compose/docker-compose.deploy.yml 'DUTYLOG_IMAGE:?DUTYLOG_IMAGE must be an immutable registry reference'
contains deploy/env/.env.staging.example 'ghcr.io/invalid/dutylog-bootstrap@sha256:0000000000000000000000000000000000000000000000000000000000000000'
contains deploy/env/.env.production.cicd.example 'ghcr.io/invalid/dutylog-bootstrap@sha256:0000000000000000000000000000000000000000000000000000000000000000'
contains deploy/compose/docker-compose.deploy.yml '"${DUTYLOG_BIND_ADDRESS:-127.0.0.1}:${DUTYLOG_BIND_PORT:?Set DUTYLOG_BIND_PORT in the environment file}:8080"'
not_contains deploy/compose/docker-compose.deploy.yml 'DUTYLOG_EDGE_NETWORK'
not_contains deploy/compose/docker-compose.deploy.yml 'DUTYLOG_APP_ALIAS'
contains deploy/compose/docker-compose.deploy.yml 'mem_limit: ${DUTYLOG_APP_MEMORY_LIMIT:-640m}'
contains deploy/compose/docker-compose.deploy.yml 'mem_limit: ${DUTYLOG_DB_MEMORY_LIMIT:-256m}'
contains deploy/compose/docker-compose.deploy.yml 'max-size: "10m"'
contains deploy/compose/docker-compose.deploy.yml 'database:'
contains deploy/compose/docker-compose.deploy.yml 'internal: true'
contains deploy/compose/docker-compose.deploy.yml 'outbound:'
not_contains deploy/compose/docker-compose.deploy.yml "container_name:"
contains deploy/scripts/deploy-environment.sh 'must be an immutable image digest reference'
contains deploy/scripts/deploy-environment.sh 'Creating verified pre-deploy backup'
contains deploy/scripts/deploy-environment.sh 'check-deploy-env.sh'
contains deploy/scripts/check-deploy-env.sh 'production requires DUTYLOG_BACKUP_BEFORE_DEPLOY=true'
contains deploy/scripts/check-deploy-env.sh 'production project name must be dutylog-production'
contains deploy/scripts/check-deploy-env.sh 'staging project name must be dutylog-staging'
contains deploy/scripts/bootstrap-cicd-host.sh 'currently publishes linux/amd64 images'
contains deploy/scripts/bootstrap-cicd-host.sh 'This bootstrap does not install or start Caddy and does not modify nginx.'
not_contains deploy/scripts/bootstrap-cicd-host.sh 'docker network create'
not_contains deploy/scripts/bootstrap-cicd-host.sh 'docker-compose.edge.yml'
contains deploy/scripts/deploy-environment.sh 'Database migrations were not rolled back.'
contains deploy/scripts/deploy-environment.sh 'Running container metadata does not match the requested immutable build.'
contains deploy/scripts/deploy-environment.sh '--tree must be the exact 40-character Git tree SHA used to build the image'
contains deploy/scripts/deploy-environment.sh '"$IMAGE_TREE" != "$BUILD_TREE"'
contains deploy/scripts/remote-deploy.sh 'DUTYLOG_BUILD_VERSION DUTYLOG_BUILD_TREE DUTYLOG_BUILD_COMMIT'
contains deploy/scripts/rollback-environment.sh 'PREVIOUS_TREE'
contains deploy/scripts/backup-postgres.sh 'pg_restore --list'
contains deploy/scripts/backup-postgres.sh 'sha256sum "$(basename "$OUT")"'
contains deploy/scripts/restore-postgres.sh 'Backup SHA-256 verification failed'
contains deploy/scripts/reset-staging.sh 'Refusing to reset a non-staging environment.'
not_contains deploy/scripts/reset-staging.sh 'rm -rf'
contains deploy/scripts/remote-deploy.sh 'StrictHostKeyChecking=yes'
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/check-deploy-env.sh'
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/local-smoke-test.sh'
contains deploy/scripts/deploy-environment.sh 'bash deploy/scripts/local-smoke-test.sh'
contains deploy/scripts/local-smoke-test.sh 'DUTYLOG_BIND_ADDRESS is not 127.0.0.1'
contains deploy/scripts/check-deploy-env.sh 'DUTYLOG_BIND_ADDRESS must be exactly 127.0.0.1'
contains deploy/scripts/check-deploy-env.sh 'DUTYLOG_SECURITY_TRUST_PROXY_HEADERS must be true'
contains deploy/env/.env.staging.example 'DUTYLOG_BIND_PORT=18082'
contains deploy/env/.env.production.cicd.example 'DUTYLOG_BIND_PORT=18083'
contains deploy/nginx/dutylog-staging.conf.example 'proxy_pass http://127.0.0.1:18082;'
contains deploy/nginx/dutylog-production.conf.example 'proxy_pass http://127.0.0.1:18083;'
contains deploy/nginx/dutylog-staging.conf.example 'proxy_set_header X-Forwarded-For $remote_addr;'
contains deploy/nginx/dutylog-production.conf.example 'proxy_set_header X-Forwarded-For $remote_addr;'
contains deploy/scripts/restore-postgres.sh 'CONFIRM_RESTORE'
contains deploy/scripts/restore-postgres.sh 'pre-restore'
contains deploy/scripts/migration-smoke-test.sh 'Clean PostgreSQL migration and container startup passed.'
contains deploy/scripts/smoke-test.sh '401|302|403)'
not_contains deploy/scripts/smoke-test.sh '200|401|302|403)'
contains Dockerfile 'DUTYLOG_BUILD_ID=local'
contains Dockerfile 'org.opencontainers.image.revision'
contains Dockerfile 'org.opencontainers.image.source-tree'
contains Dockerfile 'DUTYLOG_BUILD_TREE'
contains Dockerfile 'USER 10001:10001'
contains src/main/resources/static/service-worker.js '__DUTYLOG_BUILD_ID__'
contains src/main/resources/static/service-worker.js "dutylog-shell-v$VERSION-\${BUILD_ID}"
contains src/main/resources/static/js/70-user-boot.js 'updateViaCache: "none"'
contains src/main/resources/static/js/login.js 'updateViaCache: "none"'
contains docs/CICD.md 'Production does not rebuild source code.'
contains docs/STAGING.md 'Refusing to reset a non-staging environment.'
contains docs/MIGRATION_SAFETY.md 'Automatic database restore is intentionally forbidden'
contains docs/GIT_WORKFLOW.md 'feature/*  isolated work'

python3 - <<'PY_SECURITY'
from pathlib import Path
import re
for path in ['src/main/resources/static/login.html', 'src/main/resources/static/index.html']:
    html = Path(path).read_text(encoding='utf-8')
    inline = [m.group(0) for m in re.finditer(r'<script(?![^>]*\bsrc=)[^>]*>', html, re.I)]
    if inline:
        raise SystemExit(f'inline script tags remain in {path}: {inline}')
print('OK:    no inline script tags in runtime HTML')
PY_SECURITY

contains CHANGES.md "v27.2.5 — Calendar day identity hotfix"
contains README.md "v27.2.5 — Calendar day identity hotfix"
contains docs/RELEASE_CANDIDATE.md "v27.2.5 — Calendar day identity hotfix"
contains docs/USER_GUIDE.md "Status: v27.2.5."
contains docs/PRODUCTION_DEPLOY.md "same GHCR digest that already passed staging"
contains docs/BACKUP_RESTORE.md "Status: v27.2.30."
contains docs/RELEASE_CHECKLIST.md "git tag -a v27.2.5"

# v27.2.5 calendar persistence regression guards
contains src/main/resources/static/js/30-calendar.js "api.month(requestedYear, requestedMonth, { fresh:true })"
contains src/main/resources/static/js/20-data.js 'cache:fresh ? "no-store" : undefined'
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java "entityManager.clear()"
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java "График не сохранился для даты"
contains src/test/java/ru/daniil/shifts/web/CalendarFillPersistenceContractTest.java "fillThenFreshCalendarReadReturnsEveryPersistedDate"
contains src/main/resources/static/js/70-user-boot.js "calendarLoadGeneration"
contains src/test/java/ru/daniil/shifts/service/DayEntryServiceTest.java "массовыйГрафикСохраняетсяПослеОчисткиPersistenceContext"
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java "stale month responses are ignored"
contains docs/CALENDAR_AUTHORITATIVE_PERSISTENCE_HOTFIX.md "Status: v27.2.5."

# v27.2.5 calendar day identity regression guards
contains src/main/resources/static/js/20-data.js 'date: day.date ?? null'
contains src/main/resources/static/js/20-data.js 'updatedAt: day.updatedAt ?? null'
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'state.days[undefined]'
contains src/main/java/ru/daniil/shifts/web/ApiExceptionHandler.java '@ExceptionHandler(NoResourceFoundException.class)'
contains docs/CALENDAR_DAY_IDENTITY_HOTFIX.md 'Status: v27.2.5.'

# v27.2.6 module-isolated day saves and browser reminder guards
contains CHANGES.md "v27.2.6 — Module-isolated day saves and browser reminders"
contains docs/MODULE_DAY_SAVE_AND_BROWSER_NOTIFICATIONS_HOTFIX.md "Status: v27.2.6."
contains src/main/resources/static/js/20-data.js 'function dayUpsertPayload(day = {})'
contains src/main/resources/static/js/20-data.js 'if (moduleEnabled("overtime")) {'
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java 'boolean notesMutable'
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java 'boolean overtimeMutable'
contains src/main/java/ru/daniil/shifts/web/DayController.java 'isNonZero(req.overtimeHours())'
contains src/test/java/ru/daniil/shifts/web/DayModuleIsolationTest.java 'neutralLegacyFieldsDoNotBlockShiftAndMarkerOrEraseHiddenData'
contains src/test/java/ru/daniil/shifts/web/DayModuleIsolationTest.java 'disabledModulesStillRejectRealWrites'
contains src/main/resources/static/js/60-settings.js 'function browserNotificationTick()'
contains src/main/resources/static/js/60-settings.js 'BROWSER_NOTIFICATION_GRACE_MS'
contains src/main/resources/static/service-worker.js 'notificationclick'
contains src/main/resources/static/js/70-user-boot.js 'startBrowserNotificationScheduler();'
contains src/main/resources/static/js/70-user-boot.js '!state.modulesLoaded || !moduleEnabled("telegram")'
contains src/main/resources/static/js/50-tasks.js 'function updateTaskReminderControls()'

# v27.2.8 test compilation hotfix + retained regression baseline
contains CHANGES.md "v27.2.8 — Test compilation hotfix"
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'dataJs.contains("cache:fresh ? \"no-store\" : undefined")'
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'dataJs.contains("cache: opts.cache")'
not_contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java "dataJs.contains('cache:fresh"
not_contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java "dataJs.contains('cache: opts.cache')"
contains src/main/resources/static/js/20-data.js 'syncBrowserNotificationSchedulerForModules();'
contains src/main/resources/static/js/20-data.js 'err.moduleKey = moduleKey'
contains src/main/resources/static/js/60-settings.js 'function stopBrowserNotificationScheduler()'
contains src/main/resources/static/js/60-settings.js 'clearInterval(browserNotificationTimer)'
contains src/main/resources/static/js/60-settings.js 'err?.moduleKey === "notifications"'
contains src/test/java/ru/daniil/shifts/service/NotificationServiceTest.java 'calculatesExactShiftTaskImportantDayAndDigestTimes'
contains src/test/java/ru/daniil/shifts/web/NotificationControllerTest.java 'disabledModuleGuardsSettingsAndUpcomingEndpoints'
contains src/test/java/ru/daniil/shifts/service/ModuleDependencyTest.java 'disablingNotificationsCascadesToTelegram'
contains src/test/java/ru/daniil/shifts/service/TaskReminderServiceTest.java 'disablingReminderClearsStaleLeadMinutes'
contains src/test/java/ru/daniil/shifts/web/BrowserNotificationFrontendContractTest.java 'moduleToggleStopsPollingAndGuarded403CannotBecomeARecurringLoop'
contains pom.xml '<artifactId>jacoco-maven-plugin</artifactId>'
contains .github/workflows/ci.yml 'mvn -B --no-transfer-progress verify'
contains .github/workflows/ci.yml 'name: jacoco-report'

# v27.2.9 task regression suite and local coverage instructions
contains CHANGES.md "v27.2.9 — Task regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "Status: v27.2.30."
contains docs/TESTING.md "mvn clean verify"
contains docs/TESTING.md "target/site/jacoco/index.html"
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java "boardFiltersStatusCategoryPriorityQueryAndDateRange"
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java "boardPaginationUsesSafeBoundsAndStableMetadata"
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java "fullCrudWorksAcrossLegacyAndV1Aliases"
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java "disabledModuleGuardsAllTaskEndpointsWithoutDeletingExistingData"
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java "foreignTaskIdsRemainIndistinguishableFromMissingResources"

# v27.2.10 task board status validation hotfix
contains CHANGES.md "v27.2.10 — Task board status validation hotfix"
contains README.md "v27.2.10 — Task board status validation hotfix"
contains src/main/java/ru/daniil/shifts/service/TaskService.java 'String statusFilter = normalizeBoardStatus(status);'
contains src/main/java/ru/daniil/shifts/service/TaskService.java 'case "all", "open", "done", "overdue", "upcoming" -> normalized;'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java 'board("mystery", null, null, null, null, null, 0, 50)'
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java '.param("status", "mystery")'

# v27.2.11 task-priority regression test correction
contains CHANGES.md "v27.2.11 — Task priority regression test correction"
contains README.md "v27.2.11 — Task priority regression test correction"
contains src/main/java/ru/daniil/shifts/model/TaskPriority.java 'URGENT'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java 'board("all", null, "urgent", null, null, null, 0, 50)'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java 'board("all", null, "critical", null, null, null, 0, 50)'


# v27.2.12 important dates regression suite
contains CHANGES.md "v27.2.12 — Important dates regression suite"
contains README.md "v27.2.12 — Important dates regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "ImportantDayServiceTest"
contains src/test/java/ru/daniil/shifts/service/ImportantDayServiceTest.java "monthlyRecurrenceClampsTheThirtyFirstToTheLastDayOfShortMonths"
contains src/test/java/ru/daniil/shifts/service/ImportantDayServiceTest.java "yearlyLeapDayFallsBackToFebruaryTwentyEighthAndReturnsOnLeapYears"
contains src/test/java/ru/daniil/shifts/web/ImportantDayControllerTest.java "fullCrudWorksAcrossLegacyAndV1Aliases"
contains src/test/java/ru/daniil/shifts/web/ImportantDayControllerTest.java "disabledModuleGuardsEveryEndpointWithoutDeletingStoredDates"
contains src/test/java/ru/daniil/shifts/web/ImportantDayControllerTest.java "foreignIdsAreIndistinguishableFromMissingResources"

# v27.2.13 shift types and calendar patterns regression suite
contains CHANGES.md "v27.2.13 — Shift types and calendar patterns regression suite"
contains README.md "v27.2.13 — Shift types and calendar patterns regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "CalendarPatternServiceTest"
contains src/test/java/ru/daniil/shifts/service/CalendarPatternServiceTest.java "twoOnTwoOffRepeatsAcrossTheYearBoundary"
contains src/test/java/ru/daniil/shifts/service/CalendarPatternServiceTest.java "dayNightFortyEightCrossesLeapDayWithoutLosingThePattern"
contains src/test/java/ru/daniil/shifts/service/CalendarPatternServiceTest.java "overwriteChangesOnlyTheShiftAndPreservesDayMetadata"
contains src/test/java/ru/daniil/shifts/service/ShiftTypeServiceTest.java "deletingCustomShiftDetachesItAndPreservesOtherDayData"
contains src/test/java/ru/daniil/shifts/service/ShiftTypeServiceTest.java "ensureBuiltinsRepairsLegacyDefaultsWithoutCreatingDuplicates"
contains src/test/java/ru/daniil/shifts/web/CalendarPatternControllerTest.java "v1FillAndCalendarReadPreserveDayNightFortyEightAcrossLeapDay"
contains src/test/java/ru/daniil/shifts/web/ShiftTypeControllerTest.java "fullCrudWorksAcrossLegacyAndV1Aliases"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplateFrontendContractTest.java "weeklyTemplateIsRotatedBySelectedWeekdayAndTheEffectiveSequenceIsSentToTheServer"


# v27.2.14 quick scenarios and overtime API regression suite
contains CHANGES.md "v27.2.14 — Quick scenarios and overtime API regression suite"
contains README.md "v27.2.14 — Quick scenarios and overtime API regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "QuickScenarioServiceTest"
contains src/test/java/ru/daniil/shifts/service/ShiftTypeServiceTest.java "import java.util.Map;"
contains src/test/java/ru/daniil/shifts/service/ShiftTypeServiceTest.java "import java.util.stream.Collectors;"
contains src/test/java/ru/daniil/shifts/service/QuickScenarioServiceTest.java "firstListSeedsFiveOrderedDefaultsExactlyOnce"
contains src/test/java/ru/daniil/shifts/service/QuickScenarioServiceTest.java "deletingASeededScenarioDoesNotRestoreItOnLaterLists"
contains src/test/java/ru/daniil/shifts/web/QuickScenarioControllerTest.java "fullCrudWorksAcrossLegacyAndV1Aliases"
contains src/test/java/ru/daniil/shifts/web/QuickScenarioControllerTest.java "disabledModuleGuardsEveryEndpointWithoutDeletingStoredScenarios"
contains src/test/java/ru/daniil/shifts/service/OvertimeAccountQueryServiceTest.java "accountPageFiltersOpenPartialClosedDateAndSearch"
contains src/test/java/ru/daniil/shifts/service/OvertimeAccountQueryServiceTest.java "csvExportKeepsBomFiltersRowsAndEscapesSpreadsheetCells"
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java "creditAndUsageCrudKeepsFifoAcrossLegacyAndV1Aliases"
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java "disabledModuleGuardsAllEndpointsWithoutDeletingAccountData"


# v27.2.15 structured module-disabled error envelope hotfix
contains CHANGES.md "v27.2.15 — Structured module-disabled error envelope hotfix"
contains README.md "v27.2.15 — Structured module-disabled error envelope hotfix"
contains src/main/java/ru/daniil/shifts/web/ApiErrorResponse.java 'String moduleKey,'
contains src/main/java/ru/daniil/shifts/web/ApiErrorResponse.java 'moduleKey(safeCode, safeMessage)'
contains src/main/resources/static/openapi/dutylog-v1.yaml 'moduleKey:'
contains src/main/resources/static/js/20-data.js 'moduleKey = body?.moduleKey || null'
contains src/test/java/ru/daniil/shifts/web/QuickScenarioControllerTest.java 'jsonPath("$.moduleKey").value("scenarios")'
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java 'jsonPath("$.moduleKey").value("overtime")'

# v27.2.17 profile and administration regression suite
contains CHANGES.md "v27.2.16 — Profile and administration regression suite"
contains README.md "v27.2.16 — Profile and administration regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "ProfileControllerTest"
contains src/test/java/ru/daniil/shifts/web/ProfileControllerTest.java "fullUpdateTrimsNormalizesClampsAndPersistsAllowedThemeFields"
contains src/test/java/ru/daniil/shifts/web/ProfileSessionControllerTest.java "passwordChangeRevokesEveryMobileSessionButLeavesRowsForDeviceHistory"
contains src/test/java/ru/daniil/shifts/service/AppSettingsServiceTest.java "legacyTrueSpellingsRemainAcceptedAndEverythingElseIsFalse"
contains src/test/java/ru/daniil/shifts/service/UserAdminServiceTest.java "selfBootstrapAndLastAdministratorDemotionsAreRejectedIndependently"
contains src/test/java/ru/daniil/shifts/web/AdminControllerContractTest.java "malformedBodiesAndMissingUsersNeverBecomeServerErrors"
contains src/main/java/ru/daniil/shifts/web/SystemController.java 'throw ApiException.badRequest("Нужно передать role")'
contains src/main/java/ru/daniil/shifts/web/SystemController.java 'throw ApiException.forbidden("Диагностика доступна только администратору")'
not_contains src/main/java/ru/daniil/shifts/web/SystemController.java "ResponseStatusException"

# v27.2.17 admin test context bootstrap hotfix
contains CHANGES.md "v27.2.17 — Admin test context bootstrap hotfix"
contains README.md "v27.2.17 — Admin test context bootstrap hotfix"
contains src/test/java/ru/daniil/shifts/service/UserAdminServiceTest.java 'service = new UserAdminService(users, encoder, mobileAuthService, securityEvents, "bootstrap-root")'
not_contains src/test/java/ru/daniil/shifts/service/UserAdminServiceTest.java '@TestPropertySource(properties = "dutylog.admin.username=bootstrap-root")'

# v27.2.18 mobile auth and sync lifecycle regression suite
contains CHANGES.md "v27.2.18 — Mobile auth and sync lifecycle regression suite"
contains README.md "v27.2.18 — Mobile auth and sync lifecycle regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "MobileAuthServiceTest"
contains docs/REGRESSION_TEST_BASELINE.md "MobileSyncServiceTest"
contains src/main/java/ru/daniil/shifts/service/MobileSyncService.java 'catch (ApiException ex) {'
contains src/main/java/ru/daniil/shifts/service/MobileSyncService.java 'safeEntityKey(change.date())'
contains src/test/java/ru/daniil/shifts/service/MobileAuthServiceTest.java 'refreshRotatesBothTokensInPlaceAndInvalidatesTheOldPair'
contains src/test/java/ru/daniil/shifts/web/MobileAuthLifecycleControllerTest.java 'logoutByBearerWorksWithAnEmptyBodyBecauseLogoutRouteIsPublic'
contains src/test/java/ru/daniil/shifts/service/MobileSyncServiceTest.java 'malformedDateIsAPerItemRejectionAndNoLongerAbortsTheBatch'
contains src/test/java/ru/daniil/shifts/service/MobileSyncServiceTest.java 'clearCreatesAVersionedTombstoneSoStaleOfflineCreatesCannotOverwriteIt'
contains src/test/java/ru/daniil/shifts/web/MobileSyncControllerTest.java 'legacyClearDeletesEmptyRowWhileV1ClearKeepsVersionedTombstone'

# v27.2.19 PostgreSQL migration and CI version hotfix
contains CHANGES.md "v27.2.19 — PostgreSQL migration and CI version hotfix"
contains README.md "v27.2.19 — PostgreSQL migration and CI version hotfix"
contains src/main/resources/db/migration/postgresql/V7__notification_settings.sql 'references users(id) on delete cascade'
not_contains src/main/resources/db/migration/postgresql/V7__notification_settings.sql 'references app_users(id)'
contains src/test/java/ru/daniil/shifts/db/PostgreSqlMigrationContractTest.java 'everyForeignKeyTargetsATableCreatedByTheSameOrAnEarlierMigration'
contains .github/workflows/ci.yml 'release_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)'
contains .github/workflows/ci.yml 'DUTYLOG_BUILD_VERSION="${{ steps.version.outputs.release_version }}-ci.${GITHUB_RUN_NUMBER}"'
contains .github/workflows/deploy-staging.yml 'DUTYLOG_RELEASE_VERSION: ${{ needs.validate.outputs.release_version }}'
contains .github/workflows/deploy-production.yml 'DUTYLOG_RELEASE_VERSION: ${{ needs.validate.outputs.release_version }}'
not_contains .github/workflows/ci.yml '27.2.9'
not_contains .github/workflows/deploy-staging.yml '27.2.9'
not_contains .github/workflows/deploy-production.yml '27.2.9'

# v27.2.20 Telegram bot regression and delivery hardening suite
contains CHANGES.md "v27.2.20 — Telegram bot regression and delivery hardening suite"
contains README.md "v27.2.20 — Telegram bot regression and delivery hardening suite"
contains docs/REGRESSION_TEST_BASELINE.md "TelegramBotServiceTest"
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'return root != null && root.path("ok").asBoolean(false);'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'message.replace(token, "***")'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'chatIdNode.isMissingNode() || chatIdNode.isNull()'
contains src/test/java/ru/daniil/shifts/telegram/TelegramCommandServiceTest.java "intervalOvertimeSupportsDateOvernightBreakPlanAndReason"
contains src/test/java/ru/daniil/shifts/telegram/TelegramBotServiceTest.java "sendMessageValidatesInputTruncatesTextAndFailsClosed"
contains src/test/java/ru/daniil/shifts/telegram/TelegramNotificationServiceTest.java "failedTelegramSendIsRetriedLaterInsteadOfMarkedDelivered"
contains src/test/java/ru/daniil/shifts/web/TelegramControllerTest.java "disabledModuleGuardsEveryTelegramEndpoint"

# v27.2.21 Telegram date validation and test harness hotfix
contains CHANGES.md "v27.2.21 — Telegram date validation and test harness hotfix"
contains README.md "v27.2.21 — Telegram date validation and test harness hotfix"
contains src/main/java/ru/daniil/shifts/telegram/TelegramCommandService.java 'catch (DateTimeException | NumberFormatException ignored)'
contains src/test/java/ru/daniil/shifts/telegram/TelegramCommandServiceTest.java '31.02 Невозможная дата'
python3 - <<'PY_TELEGRAM_HOTFIX'
from pathlib import Path
text = Path('src/test/java/ru/daniil/shifts/telegram/TelegramBotServiceTest.java').read_text()
method = text.split('void sendMessageValidatesInputTruncatesTextAndFailsClosed()', 1)[1].split('@Test', 1)[0]
second_expectation = method.find('server.expect', method.find('server.expect') + 1)
first_request = method.find('assertFalse(bot.sendMessage(1L, longText)')
if second_expectation < 0 or first_request < 0 or second_expectation > first_request:
    raise SystemExit('TelegramBotServiceTest must register both HTTP expectations before the first request')
PY_TELEGRAM_HOTFIX
if [[ $? -eq 0 ]]; then
  ok "TelegramBotServiceTest registers all expectations before execution"
else
  fail "TelegramBotServiceTest expectation ordering regression"
fi

# v27.2.22 security infrastructure regression and auth hardening suite
contains CHANGES.md "v27.2.22 — Security infrastructure regression and auth hardening suite"
contains README.md "v27.2.22 — Security infrastructure regression and auth hardening suite"
contains docs/REGRESSION_TEST_BASELINE.md "SecurityInfrastructureContractTest"
contains src/main/java/ru/daniil/shifts/config/BearerTokenAuthenticationFilter.java 'regionMatches(true, start, "Bearer", 0, 6)'
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java 'BearerTokenAuthenticationFilter.hasBearerScheme'
contains src/main/java/ru/daniil/shifts/config/AuthenticationRateLimitFilter.java 'String bucket = registration ? "registration" : "login";'
contains src/test/java/ru/daniil/shifts/config/BearerTokenAuthenticationFilterTest.java 'bearerSchemeIsCaseInsensitiveAndAcceptsRepeatedWhitespace'
contains src/test/java/ru/daniil/shifts/config/AuthenticationRateLimitFilterTest.java 'webLegacyAndV1LoginAliasesShareOneIpBucket'
contains src/test/java/ru/daniil/shifts/config/SecurityEventLoggerTest.java 'controlCharactersAreFlattenedAndEveryValueIsBounded'
contains src/test/java/ru/daniil/shifts/web/ApiErrorInfrastructureTest.java 'unexpectedExceptionsAreHiddenBehindGeneric500Envelope'
contains src/test/java/ru/daniil/shifts/web/SecurityInfrastructureContractTest.java 'mixedCaseBearerSchemeIsRecognizedInsteadOfFallingThroughAsAnonymous'

# v27.2.23 security test contract and secret-safe error logging hotfix
contains CHANGES.md "v27.2.23 — Security test contract and secret-safe error logging hotfix"
contains README.md "v27.2.23 — Security test contract and secret-safe error logging hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.23 security test contract and secret-safe logging hotfix"
contains src/test/java/ru/daniil/shifts/config/BearerTokenAuthenticationFilterTest.java 'MediaType.APPLICATION_JSON.isCompatibleWith'
contains src/test/java/ru/daniil/shifts/web/ApiErrorInfrastructureTest.java 'MediaType.APPLICATION_JSON.isCompatibleWith'
contains src/test/java/ru/daniil/shifts/web/SecurityInfrastructureContractTest.java 'get("/").accept(MediaType.TEXT_HTML)'
contains src/main/java/ru/daniil/shifts/web/ApiExceptionHandler.java 'exceptionType={}'
contains src/test/java/ru/daniil/shifts/web/ApiErrorInfrastructureTest.java 'assertNull(event.getThrowableProxy())'
not_contains src/main/java/ru/daniil/shifts/web/ApiExceptionHandler.java 'request.getRequestURI(), ex);'

# v27.2.24 coverage floor and startup/module regression suite
contains CHANGES.md "v27.2.24 — Coverage floor and startup/module regression suite"
contains README.md "v27.2.24 — Coverage floor and startup/module regression suite"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.24 coverage floor and startup/module extension"
contains pom.xml "<counter>INSTRUCTION</counter>"
contains pom.xml "<minimum>0.88</minimum>"
contains pom.xml "<counter>BRANCH</counter>"
contains pom.xml "<minimum>0.70</minimum>"
contains src/test/java/ru/daniil/shifts/service/AdminBootstrapServiceTest.java "missingBootstrapAccountIsCreatedSeededAndLegacyAdminsAreDemotedOnce"
contains src/test/java/ru/daniil/shifts/module/ModuleRegistryContractTest.java "everyDependencyChainTerminatesWithoutCycles"
contains src/test/java/ru/daniil/shifts/service/ModuleServiceContractTest.java "enablingScenarioActivatesItsWholeDependencyChain"
contains src/test/java/ru/daniil/shifts/service/CurrentUserServiceTest.java "existingPrincipalResolvesToOwnerEntity"
contains src/test/java/ru/daniil/shifts/service/NoteExportServiceTest.java "postReadLimitProtectsAgainstRowsChangingBetweenCountAndSelect"

# v27.2.25 Playwright browser E2E regression baseline
contains CHANGES.md "v27.2.25 — Playwright browser E2E regression baseline"
contains README.md "v27.2.25 — Playwright browser E2E regression baseline"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.25 Playwright browser E2E extension"
contains docs/PLAYWRIGHT_E2E.md "npm run test:e2e"
contains package.json '"@playwright/test": "1.49.1"'
contains playwright.config.js "serviceWorkers: 'block'"
contains playwright.config.js "spring-boot.run.profiles=e2e"
contains src/main/resources/application-e2e.properties 'jdbc:h2:mem:dutylog_e2e'
not_contains src/main/resources/application-e2e.properties 'jdbc:h2:file:'
contains src/main/resources/application-e2e.properties 'server.port=4173'
contains src/main/resources/application-e2e.properties 'dutylog.telegram.enabled=false'
contains src/main/resources/static/js/30-calendar.js 'cell.dataset.date = k'
contains src/main/resources/static/js/50-tasks.js 'row.dataset.taskId = String(task.id)'
contains src/main/resources/static/js/50-tasks.js 'b.dataset.shiftTypeId = String(s.id)'
contains e2e/auth-onboarding.spec.js "registration keeps login language"
contains e2e/calendar-persistence.spec.js "survive month navigation and full reload"
contains e2e/task-modules.spec.js "survives disabling and re-enabling"
contains e2e/mobile-layout.spec.js "phone viewport"
contains e2e/pwa-offline.spec.js "IndexedDB snapshot while offline"
contains e2e/fixtures.js "console.error"
contains e2e/fixtures.js "response.status() >= 400"
contains e2e/fixtures.js "ERR_ABORTED|NS_BINDING_ABORTED|cancelled"
contains e2e/helpers.js 'data-settings-jump="modules"'
contains e2e/helpers.js "expect(toggle).not.toBeChecked()"
contains .github/workflows/ci.yml "Browser E2E regression suite"
contains .github/workflows/ci.yml "npx playwright install --with-deps chromium"
contains .github/workflows/ci.yml "name: playwright-report"
contains .github/dependabot.yml 'package-ecosystem: "npm"'


# v27.2.26 Playwright selector, accordion and line-ending hotfix
contains CHANGES.md "v27.2.26 — Playwright selector, accordion and line-ending hotfix"
contains README.md "v27.2.26 — Playwright selector, accordion and line-ending hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.26 Playwright selector and accordion hotfix"
contains .gitattributes "* text=auto eol=lf"
contains src/main/resources/static/js/50-tasks.js 'b.setAttribute("aria-pressed", on ? "true" : "false")'
contains e2e/helpers.js "async function openDayModule"
contains e2e/calendar-persistence.spec.js 'aria-pressed="true"'
contains e2e/calendar-persistence.spec.js "await openDayModule(page, 'notes')"
contains e2e/pwa-offline.spec.js "await openDayModule(page, 'notes')"
not_contains e2e/calendar-persistence.spec.js '[data-shift-type-id].on'

# v27.2.27 Playwright marker accordion hotfix
contains CHANGES.md "v27.2.27 — Playwright marker accordion hotfix"
contains README.md "v27.2.27 — Playwright marker accordion hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.27 Playwright marker accordion hotfix"
contains e2e/calendar-persistence.spec.js "await openDayModule(page, 'core')"
contains e2e/calendar-persistence.spec.js "await openDayModule(page, 'notes')"


# v27.2.28 staging deployment gate and diagnostics hardening
contains CHANGES.md "v27.2.28 — Staging deployment gate and diagnostics hardening"
contains README.md "v27.2.28 — Staging deployment gate and diagnostics hardening"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.28 staging deployment gate and diagnostics hardening"
contains docs/CICD.md "DUTYLOG_DEPLOY_ENABLED"
contains docs/STAGING.md "## Deployment gate"
contains .github/workflows/deploy-staging.yml "Build, test and enforce coverage"
contains .github/workflows/deploy-staging.yml "Browser E2E regression suite"
contains .github/workflows/deploy-staging.yml "Verify the exact image on clean PostgreSQL"
contains .github/workflows/deploy-staging.yml "Validate or skip remote staging deployment"
contains .github/workflows/deploy-staging.yml "if: steps.preflight.outputs.configured == 'true'"
contains .github/workflows/deploy-production.yml "Validate production deployment configuration"
contains deploy/scripts/check-ci-deploy-config.sh "write_output configured false"
contains deploy/scripts/check-ci-deploy-config.sh "write_output configured true"
contains deploy/scripts/remote-deploy.sh 'missing=()'

# v27.2.29 final security and product audit hardening
contains CHANGES.md "v27.2.29 — Final security and product audit hardening"
contains README.md "v27.2.29 — Final security and product audit hardening"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.29 security baseline"
contains docs/SECURITY_REVIEW.md "Status: v27.2.30."
contains src/main/resources/db/migration/postgresql/V23__web_auth_version.sql "auth_version BIGINT NOT NULL DEFAULT 0"
contains src/main/java/ru/daniil/shifts/config/DutyLogUserPrincipal.java "private final long authVersion"
contains src/main/java/ru/daniil/shifts/config/WebAccountStateFilter.java "current.getAuthVersion() != principal.getAuthVersion()"
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java "FilterRegistrationBean<WebAccountStateFilter>"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java "int minLength = user.isAdmin() ? 12 : 8"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java "user.bumpAuthVersion()"
contains src/main/java/ru/daniil/shifts/service/UserAdminService.java "target.bumpAuthVersion()"
contains src/main/java/ru/daniil/shifts/service/AdminBootstrapService.java "mobileAuthService.revokeAllSessions(user)"
contains src/test/java/ru/daniil/shifts/web/WebSessionInvalidationTest.java "roleDemotionInvalidatesCachedAdminAuthoritiesOnNextRequest"
contains src/main/java/ru/daniil/shifts/config/ClientIpResolver.java "trustProxyHeaders"
contains src/test/java/ru/daniil/shifts/config/AuthenticationRateLimitFilterTest.java "untrustedForwardingHeadersCannotBypassTheRemoteAddressBucket"
contains deploy/scripts/backup-postgres.sh "umask 077"
contains deploy/scripts/backup-postgres.sh 'chmod 0700 "$BACKUP_DIR"'
contains deploy/scripts/backup-postgres.sh 'chmod 0600 "$OUT"'
contains src/main/java/ru/daniil/shifts/service/MobileAuthTokenCleanupService.java "deleteByRefreshExpiresAtBefore"
contains src/test/java/ru/daniil/shifts/service/MobileAuthTokenCleanupServiceTest.java "cleanupDeletesOnlyRowsOlderThanConfiguredRetention"

# v27.2.30 host nginx CI/CD deployment hardening
contains CHANGES.md "v27.2.30 — Host nginx CI/CD deployment hardening"
contains README.md "v27.2.30 — Host nginx CI/CD deployment hardening"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.30 adds host-nginx deployment"
contains docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md "system nginx :80/:443"
contains docs/CICD.md "Production does not rebuild source code."
contains docs/CICD.md "127.0.0.1:18082"
contains docs/CICD.md "127.0.0.1:18083"
contains docs/VPS_CHECKLIST.md "No Caddy container is started by the active deployment."

DEPLOY_ENV_TMP="$(mktemp -d)"
sed \
  -e 's/change_me_staging_db_password/staging-db-password-1234567890/' \
  -e 's/change_me_staging_admin_password/staging-admin-password-1234567890/' \
  deploy/env/.env.staging.example > "$DEPLOY_ENV_TMP/staging.env"
if DUTYLOG_ENV_FILE="$DEPLOY_ENV_TMP/staging.env" bash deploy/scripts/check-deploy-env.sh staging >/dev/null; then
  ok "staging host-nginx environment example passes strict preflight after secrets are replaced"
else
  fail "staging host-nginx environment example failed strict preflight"
fi
sed 's/DUTYLOG_BIND_ADDRESS=127.0.0.1/DUTYLOG_BIND_ADDRESS=0.0.0.0/' \
  "$DEPLOY_ENV_TMP/staging.env" > "$DEPLOY_ENV_TMP/public-bind.env"
if DUTYLOG_ENV_FILE="$DEPLOY_ENV_TMP/public-bind.env" bash deploy/scripts/check-deploy-env.sh staging >/dev/null 2>&1; then
  fail "public application bind unexpectedly passed deployment preflight"
else
  ok "public application bind is rejected"
fi
STAGING_PORT="$(awk -F= '/^DUTYLOG_BIND_PORT=/{print $2}' deploy/env/.env.staging.example)"
PRODUCTION_PORT="$(awk -F= '/^DUTYLOG_BIND_PORT=/{print $2}' deploy/env/.env.production.cicd.example)"
if [[ "$STAGING_PORT" == "18082" && "$PRODUCTION_PORT" == "18083" && "$STAGING_PORT" != "$PRODUCTION_PORT" ]]; then
  ok "staging and production loopback ports are distinct"
else
  fail "unexpected staging/production loopback port mapping: $STAGING_PORT / $PRODUCTION_PORT"
fi
rm -rf "$DEPLOY_ENV_TMP"

CI_GATE_TMP="$(mktemp -d)"
trap 'rm -rf "$CI_GATE_TMP"' EXIT
GITHUB_OUTPUT="$CI_GATE_TMP/disabled.out" \
GITHUB_STEP_SUMMARY="$CI_GATE_TMP/disabled.md" \
DUTYLOG_DEPLOY_ENABLED=false \
  bash deploy/scripts/check-ci-deploy-config.sh >/dev/null
grep -q '^configured=false$' "$CI_GATE_TMP/disabled.out" && ok "disabled staging deploy is an explicit successful skip" || fail "disabled deploy gate did not emit configured=false"

GITHUB_OUTPUT="$CI_GATE_TMP/enabled.out" \
GITHUB_STEP_SUMMARY="$CI_GATE_TMP/enabled.md" \
DUTYLOG_DEPLOY_ENABLED=true \
DUTYLOG_DEPLOY_ENVIRONMENT=staging \
DUTYLOG_DEPLOY_HOST=staging.example.test \
DUTYLOG_DEPLOY_PORT=22 \
DUTYLOG_DEPLOY_USER=dutylog \
DUTYLOG_DEPLOY_PATH=/opt/dutylog/staging \
DUTYLOG_BASE_URL=https://staging.example.test \
DUTYLOG_SSH_PRIVATE_KEY=$'-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----' \
DUTYLOG_SSH_KNOWN_HOSTS='staging.example.test ssh-ed25519 AAAATEST' \
DUTYLOG_GHCR_USERNAME=dutylog-reader \
DUTYLOG_GHCR_TOKEN=token-for-static-check \
  bash deploy/scripts/check-ci-deploy-config.sh >/dev/null
grep -q '^configured=true$' "$CI_GATE_TMP/enabled.out" && ok "complete deploy configuration emits configured=true" || fail "enabled deploy gate did not emit configured=true"

if DUTYLOG_DEPLOY_ENABLED=true DUTYLOG_DEPLOY_ENVIRONMENT=staging bash deploy/scripts/check-ci-deploy-config.sh >/dev/null 2>&1; then
  fail "enabled deployment with missing environment values unexpectedly passed"
else
  ok "enabled deployment fails closed when required values are missing"
fi

if grep -Il $'\r' deploy/scripts/*.sh >/dev/null 2>&1; then
  fail "deployment shell scripts contain CRLF line endings"
else
  ok "deployment shell scripts use LF line endings"
fi

E2E_TESTS=$(grep -R --include='*.spec.js' -h -E '^[[:space:]]*test\(' e2e | wc -l | tr -d ' ')
if [[ "$E2E_TESTS" == "5" ]]; then
  ok "Playwright test baseline: 5"
else
  fail "expected 5 Playwright tests, found $E2E_TESTS"
fi

TEST_METHODS=$(grep -R --include='*.java' -h -E '^[[:space:]]*@Test([[:space:]]|$)' src/test/java | wc -l | tr -d ' ')
TEST_CLASSES=$(find src/test/java -name '*Test.java' -type f | wc -l | tr -d ' ')
if [[ "$TEST_METHODS" == "340" ]]; then
  ok "test method baseline: 340"
else
  fail "expected 340 @Test methods, found $TEST_METHODS"
fi
if [[ "$TEST_CLASSES" == "65" ]]; then
  ok "test class baseline: 65"
else
  fail "expected 65 test classes, found $TEST_CLASSES"
fi

echo

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

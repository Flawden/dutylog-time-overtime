#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-27.2.8}"
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
contains docker-compose.prod.yml 'DUTYLOG_REGISTRATION_DEFAULT_ENABLED: ${DUTYLOG_REGISTRATION_DEFAULT_ENABLED:-false}'
contains docker-compose.prod.yml 'DUTYLOG_SECURITY_RATE_LIMIT_ENABLED: ${DUTYLOG_SECURITY_RATE_LIMIT_ENABLED:-true}'
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
contains docs/SECURITY_REVIEW.md "v27.2.5"
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
contains deploy/compose/docker-compose.deploy.yml 'name: ${DUTYLOG_EDGE_NETWORK:-dutylog_edge}'
not_contains deploy/compose/docker-compose.deploy.yml "container_name:"
contains deploy/scripts/deploy-environment.sh 'must be an immutable image digest reference'
contains deploy/scripts/deploy-environment.sh 'Creating verified pre-deploy backup'
contains deploy/scripts/deploy-environment.sh 'check-deploy-env.sh'
contains deploy/scripts/check-deploy-env.sh 'production requires DUTYLOG_BACKUP_BEFORE_DEPLOY=true'
contains deploy/scripts/check-deploy-env.sh 'production project name must be dutylog-production'
contains deploy/scripts/check-deploy-env.sh 'staging project name must be dutylog-staging'
contains deploy/scripts/bootstrap-cicd-host.sh 'currently publishes linux/amd64 images'
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
contains docs/BACKUP_RESTORE.md "Status: v27.2.5."
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
contains README.md "v27.2.8 — Test compilation hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "Status: retained in v27.2.8 after the test compilation hotfix."
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


echo

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

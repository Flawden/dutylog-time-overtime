#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-27.47.0}"
CURRENT_RELEASE_TITLE="Generic Compensation Components"
ERRORS=0
STATIC_JS=(
  "js/10-core.js"
  "js/12-ui-platform.js"
  "js/20-data.js"
  "js/30-calendar.js"
  "js/35-today.js"
  "js/37-calendar-experience.js"
  "js/38-schedule-layers.js"
  "js/39-vacation-planner.js"
  "js/40-overtime.js"
  "js/50-tasks.js"
  "js/55-calendar-sync.js"
  "js/60-settings.js"
  "js/70-user-boot.js"
)
STATIC_CSS=(
  "app.css"
  "design-system.css"
  "ui/tokens.css"
  "ui/themes/dutylog-default.css"
  "ui/themes/midnight.css"
  "ui/themes/oled.css"
  "ui/themes/forest.css"
  "ui/themes/sunset.css"
  "ui/themes/industrial.css"
  "ui/themes/soft-purple.css"
  "ui/platform.css"
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

RELEASE_CHECK_MATCHER_TMP=""
RELEASE_CHECK_MATCHER_WRITER_PID=""

cleanup_release_matcher() {
  exec 9>&- 2>/dev/null || true
  if [[ -n "$RELEASE_CHECK_MATCHER_WRITER_PID" ]]; then
    kill "$RELEASE_CHECK_MATCHER_WRITER_PID" 2>/dev/null || true
    wait "$RELEASE_CHECK_MATCHER_WRITER_PID" 2>/dev/null || true
  fi
  if [[ -n "$RELEASE_CHECK_MATCHER_TMP" ]]; then
    rm -rf "$RELEASE_CHECK_MATCHER_TMP"
  fi
}
trap cleanup_release_matcher EXIT

start_release_matcher() {
  RELEASE_CHECK_MATCHER_TMP="$(mktemp -d)"
  mkfifo "$RELEASE_CHECK_MATCHER_TMP/requests.pipe"
  cat "$RELEASE_CHECK_MATCHER_TMP/requests.pipe" > "$RELEASE_CHECK_MATCHER_TMP/requests.tsv" &
  RELEASE_CHECK_MATCHER_WRITER_PID="$!"
  exec 9>"$RELEASE_CHECK_MATCHER_TMP/requests.pipe"
}

queue_release_match() {
  local kind="$1"
  local file="$2"
  local text="$3"
  local encoded="$text"
  encoded="${encoded//\\/\\\\}"
  encoded="${encoded//$'\t'/\\t}"
  encoded="${encoded//$'\n'/\\n}"
  encoded="${encoded//$'\r'/\\r}"
  printf '%s\t%s\t%s\n' "$kind" "$file" "$encoded" >&9
}

contains() {
  queue_release_match contains "$1" "$2"
}

not_contains() {
  queue_release_match not_contains "$1" "$2"
}

flush_release_matcher() {
  exec 9>&-
  wait "$RELEASE_CHECK_MATCHER_WRITER_PID"
  python3 - "$RELEASE_CHECK_MATCHER_TMP/requests.tsv" "$RELEASE_CHECK_MATCHER_TMP/errors.txt" <<'PY_MATCHER'
from pathlib import Path
import sys

requests_path = Path(sys.argv[1])
errors_path = Path(sys.argv[2])
cache = {}
errors = 0
passed = 0

def decode(value: str) -> str:
    out = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\" or index + 1 >= len(value):
            out.append(char)
            index += 1
            continue
        escaped = value[index + 1]
        if escaped == "n":
            out.append("\n")
        elif escaped == "t":
            out.append("\t")
        elif escaped == "r":
            out.append("\r")
        else:
            out.append(escaped)
        index += 2
    return "".join(out)

for raw in requests_path.read_text(encoding="utf-8").splitlines():
    try:
        kind, file_name, encoded = raw.split("\t", 2)
    except ValueError:
        print(f"ERROR: invalid release-check matcher request: {raw!r}", file=sys.stderr)
        errors += 1
        continue
    path = Path(file_name)
    text = decode(encoded)
    if not path.is_file():
        print(f"ERROR: {file_name} is missing", file=sys.stderr)
        errors += 1
        continue
    source = cache.get(file_name)
    if source is None:
        source = path.read_text(encoding="utf-8")
        cache[file_name] = source
    present = text in source
    ok = present if kind == "contains" else not present
    if ok:
        passed += 1
        continue
    if kind == "contains":
        print(f"ERROR: {file_name} does not contain expected text: {text}", file=sys.stderr)
    else:
        print(f"ERROR: {file_name} contains forbidden text: {text}", file=sys.stderr)
    errors += 1

print(f"OK:    queued static text contracts: {passed}")
errors_path.write_text(str(errors), encoding="utf-8")
PY_MATCHER
  local match_errors
  match_errors="$(cat "$RELEASE_CHECK_MATCHER_TMP/errors.txt")"
  ERRORS=$((ERRORS + match_errors))
  rm -rf "$RELEASE_CHECK_MATCHER_TMP"
  RELEASE_CHECK_MATCHER_TMP=""
  RELEASE_CHECK_MATCHER_WRITER_PID=""
}

echo "DutyLog release check"
echo "Project: $PROJECT_ROOT"
echo "Version: $VERSION"
echo

need node
need python3
need bash
need sha256sum
need flock
need javac



if (( ERRORS > 0 )); then
  echo "Missing required commands; aborting." >&2
  exit 1
fi

if python3 deploy/scripts/smoke-test-regression.py >/dev/null; then
  ok "authenticated deployment smoke-test regression"
else
  fail "authenticated deployment smoke-test regression failed"
fi

start_release_matcher

echo

echo "1) Version consistency"
contains src/main/resources/static/js/10-core.js "DUTYLOG_VERSION = \"$VERSION\""
contains src/main/resources/static/service-worker.js "dutylog-shell-v$VERSION"
for asset in "${STATIC_CSS[@]}"; do
  contains src/main/resources/static/index.html "$asset?v=$VERSION"
done
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
contains src/main/resources/static/js/35-today.js '$("todayQuickMore")?.addEventListener("click", () => openQuickActions());'
not_contains src/main/resources/static/js/35-today.js '$("todayQuickMore")?.addEventListener("click", openQuickActions);'

  # v27.16.2 Next Route & Time Settings E2E Hotfix
contains CHANGES.md "v27.16.2 — Next Route & Time Settings E2E Hotfix"
contains README.md "v27.16.2 — Next Route & Time Settings E2E Hotfix"
contains docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md "Next Route & Time Settings E2E Hotfix"
contains e2e/helpers.js "async function openView(page, view)"
contains e2e/helpers.js "await openView(page, 'calendar');"
contains e2e/auth-onboarding.spec.js "#view-today"
contains e2e/important-timezone.spec.js "await openView(page, 'important');"
contains src/main/resources/static/js/60-settings.js "function cancelTimeSettingsAutoApply()"
contains src/main/resources/static/js/60-settings.js "if (!silent) cancelTimeSettingsAutoApply();"
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "function cancelTimeSettingsAutoApply()"

  # v27.16.3 Time Settings Transaction Hotfix
contains CHANGES.md "v27.16.3 — Time Settings Transaction Hotfix"
contains README.md "v27.16.3 — Time Settings Transaction Hotfix"
contains docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md "Time Settings Transaction Hotfix"
contains docs/API.md "# DutyLog API v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
contains src/main/resources/static/js/60-settings.js "let timeSettingsApplyQueue = Promise.resolve();"
contains src/main/resources/static/js/60-settings.js "const pending = timeSettingsApplyQueue.then(operation, operation);"
contains src/main/resources/static/js/60-settings.js "function readShiftDefaultsDraft()"
contains src/main/resources/static/js/60-settings.js "preserveShiftDefaults = timeSettingsDefaultsDirty()"
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "let timeSettingsApplyQueue = Promise.resolve();"

  # v27.17.0 Calendar Mobile Experience
contains CHANGES.md "v27.17.0 — Calendar Mobile Experience"
contains README.md "v27.17.0 — Calendar Mobile Experience"
contains docs/CALENDAR_MOBILE_EXPERIENCE_V27.17.0.md "Calendar Mobile Experience"
contains src/main/resources/static/index.html 'data-calendar-mode="month"'
contains src/main/resources/static/index.html 'data-calendar-mode="week"'
contains src/main/resources/static/index.html 'data-calendar-mode="day"'
contains src/main/resources/static/index.html "js/37-calendar-experience.js?v=$VERSION"
contains src/main/resources/static/js/37-calendar-experience.js 'dutylog.calendar.mode.v1'
contains src/main/resources/static/js/37-calendar-experience.js 'calendarExperienceOpenLegacyDetails'
contains src/main/resources/static/js/35-today.js 'calendarExperienceOpen(date, mode)'
contains e2e/calendar-mobile-experience.spec.js 'calendar switches month week and hourly day'
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java 'class CalendarMobileExperienceFrontendContractTest'

  # v27.17.1 Calendar & Notes Quality Hotfix
contains CHANGES.md "v27.17.1 — Calendar & Notes Quality Hotfix"
contains README.md "v27.17.1 — Calendar & Notes Quality Hotfix"
contains docs/CALENDAR_NOTES_LAYOUT_QUALITY_V27.17.1.md "Calendar & Notes Quality Hotfix"
contains src/main/resources/static/app.css 'container-name:day-notes'
contains src/main/resources/static/app.css '@container day-notes (max-width:720px)'
contains src/main/resources/static/design-system.css '.calendarAllDayHead'
contains src/main/resources/static/design-system.css '.calendarAllDayItems'
contains src/main/resources/static/js/37-calendar-experience.js 'function calendarExperienceReminderDate'
contains src/main/resources/static/js/37-calendar-experience.js 'function calendarExperienceRemindersForDate'
contains src/main/resources/static/js/37-calendar-experience.js 'toUpperCase() === "IMPORTANT_DAY"'
contains src/main/resources/static/js/30-calendar.js 'if ($("impDate")) $("impDate").value = k;'
contains src/main/resources/static/index.html 'id="taskEditDueTime" type="time" step="60"'
contains e2e/multiple-daily-notes.spec.js 'editorTop'
contains e2e/calendar-mobile-experience.spec.js 'calendarAllDayItem.important'
contains e2e/editor-modals.spec.js "fill('17:41')"
contains src/test/java/ru/daniil/shifts/web/TaskAndShiftEditorsFrontendContractTest.java 'step=\"60\"'

  # v27.17.2 Calendar Timeline Readability Hotfix
contains CHANGES.md "v27.17.2 — Calendar Timeline Readability Hotfix"
contains README.md "v27.17.2 — Calendar Timeline Readability Hotfix"
contains docs/CALENDAR_TIMELINE_READABILITY_V27.17.2.md "Calendar Timeline Readability Hotfix"
contains src/main/resources/static/js/37-calendar-experience.js 'function calendarExperienceVisualEnd(event)'
contains src/main/resources/static/js/37-calendar-experience.js 'laneEnds[lane] = calendarExperienceVisualEnd(event);'
contains src/main/resources/static/js/37-calendar-experience.js '[range, event.meta].filter(Boolean).join(" · ")'
contains src/main/resources/static/design-system.css '.calendarTimelineEvent.isCompact'
contains src/main/resources/static/design-system.css 'min-height: 48px'
contains src/main/resources/static/design-system.css 'height: max(48px, calc(var(--duration) * 1%))'
contains e2e/editor-modals.spec.js "expect(eventLayout.height).toBeGreaterThanOrEqual(47);"
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java 'function calendarExperienceVisualEnd(event)'

  # v27.17.3 Java Contract Build Gate Hotfix
contains CHANGES.md "v27.17.3 — Java Contract Build Gate Hotfix"
contains README.md "v27.17.3 — Java Contract Build Gate Hotfix"
contains docs/JAVA_CONTRACT_BUILD_GATE_HOTFIX_V27.17.3.md "Java Contract Build Gate Hotfix"
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java 'assertTrue(js.contains("[range, event.meta].filter(Boolean).join(\" · \")"));'
contains deploy/scripts/release-check.sh 'Static frontend contract Java sources compile'

  # v27.17.4 UI Core & Workspace Foundation
contains CHANGES.md "v27.17.4 — UI Core & Workspace Foundation"
contains README.md "v27.17.4 — UI Core & Workspace Foundation"
contains docs/UI_CORE_WORKSPACE_FOUNDATION_V27.17.4.md "UI Core & Workspace Foundation"

  # v27.17.5 UI Core E2E Accordion Hotfix
contains CHANGES.md "v27.17.5 — UI Core E2E Accordion Hotfix"
contains README.md "v27.17.5 — UI Core E2E Accordion Hotfix"
contains docs/UI_CORE_E2E_ACCORDION_HOTFIX_V27.17.5.md "UI Core E2E Accordion Hotfix"
contains e2e/design-system-shell.spec.js "await expect(appearanceCard).toHaveClass(/is-open/);"
contains e2e/design-system-shell.spec.js "await expect(page.locator('#singleShellNotice')).toBeVisible();"
contains e2e/design-system-shell.spec.js "localStorage.getItem('dutylog.settings.openSection')"

  # v27.17.6 Classic Sunset
contains CHANGES.md "v27.17.6 — Classic Sunset"
contains README.md "v27.17.6 — Classic Sunset"
contains docs/CLASSIC_SUNSET_V27.17.6.md "Classic Sunset"
contains src/main/resources/static/index.html 'id="singleShellNotice"'
not_contains src/main/resources/static/index.html 'data-shell-choice'
not_contains src/main/resources/static/js/10-core.js 'shellMode'
contains src/main/resources/static/js/10-core.js 'root.dataset.shell = "next"'
contains src/main/resources/static/js/shell-bootstrap.js 'root.dataset.shell = "next"'
not_contains src/main/resources/static/js/12-ui-platform.js 'classicNavigation'
not_contains src/main/resources/static/js/12-ui-platform.js 'cfg.shellMode'
not_contains src/main/resources/static/design-system.css 'data-shell="classic"'
not_contains src/main/resources/static/design-system.css '.shellChoice'
not_contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'input.get("shellMode")'
contains e2e/design-system-shell.spec.js "shellMode: 'classic'"
contains e2e/design-system-shell.spec.js "toHaveCount(0)"
contains docs/ROADMAP.md '`v27.18.0` — Overtime Next'
contains docs/ROADMAP.md '`v27.17.6` — Classic Sunset'
contains src/main/resources/static/js/12-ui-platform.js 'const workspaces = Object.freeze'
contains src/main/resources/static/js/12-ui-platform.js 'const layouts = Object.freeze'
contains src/main/resources/static/js/12-ui-platform.js 'const themes = Object.freeze'
contains src/main/resources/static/js/12-ui-platform.js 'const palettes = Object.freeze'
contains src/main/resources/static/js/12-ui-platform.js 'window.DutyLogUI = api'
not_contains src/main/resources/static/js/12-ui-platform.js 'fetch('
not_contains src/main/resources/static/js/12-ui-platform.js 'jfetch('
contains src/main/resources/static/js/shell-bootstrap.js 'root.dataset.uiWorkspace'
contains src/main/resources/static/js/shell-bootstrap.js 'root.dataset.uiLayout'
contains src/main/resources/static/js/70-user-boot.js 'scheduleAppearanceAutoSave'
contains src/main/resources/static/js/70-user-boot.js 'appearanceSaveQueue = Promise.resolve()'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("workspaceId"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("layoutId"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("paletteId"'
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'class UiCoreWorkspaceFrontendContractTest'
contains e2e/design-system-shell.spec.js 'UI Core workspace persists in the single DutyLog shell after Classic sunset'


  # v27.18.0 Overtime Next
contains CHANGES.md "v27.18.0 — Overtime Next"
contains README.md "v27.18.0 — Overtime Next"
contains docs/OVERTIME_NEXT_V27.18.0.md "Overtime Next"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/main/resources/static/index.html 'id="overtimeWorkspaceTitle"'
contains src/main/resources/static/index.html 'id="ledgerThisYear"'
contains src/main/resources/static/index.html 'id="ledgerChart"'
contains src/main/resources/static/index.html 'id="ledgerFifoQueue"'
contains src/main/resources/static/index.html 'id="ledgerCards"'
contains src/main/resources/static/js/40-overtime.js 'function renderOvertimeOverview()'
contains src/main/resources/static/js/40-overtime.js 'function renderLedgerChart(credits, usages)'
contains src/main/resources/static/js/40-overtime.js 'function renderFifoQueue(rows)'
contains src/main/resources/static/js/40-overtime.js 'function ledgerFilteredUsages()'
contains src/main/resources/static/js/40-overtime.js 'function renderLedgerCards(credits, options = {})'
contains src/main/resources/static/js/40-overtime.js 'function setLedgerThisYear()'
contains src/main/resources/static/design-system.css '/* v27.18.0 — Overtime Next */'
contains src/main/resources/static/design-system.css '.overtimeMobileList { display: grid;'
contains src/test/java/ru/daniil/shifts/web/OvertimeNextFrontendContractTest.java 'class OvertimeNextFrontendContractTest'
contains e2e/overtime-next.spec.js 'Overtime Next keeps the professional desktop ledger'
contains e2e/overtime-next.spec.js 'data-series-key'

  # v27.18.1 Overtime Next E2E Contract Hotfix
contains CHANGES.md "v27.18.1 — Overtime Next E2E Contract Hotfix"
contains README.md "v27.18.1 — Overtime Next E2E Contract Hotfix"
contains docs/OVERTIME_NEXT_E2E_CONTRACT_HOTFIX_V27.18.1.md "Overtime Next E2E Contract Hotfix"
contains docs/ROADMAP.md '`v27.18.3` — UI Settings & Button Variants Quality Hotfix'
contains e2e/overtime-editor-modals.spec.js '#ledgerUsageList .timeBankUsageCard'
contains e2e/overtime-editor-modals.spec.js 'await expect(page.locator(`[data-edit-usage="${firstUsageId}"]`)).toHaveCount(0);'
contains e2e/overtime-next.spec.js 'const monthKey = usageDate.slice(0, 7);'
contains e2e/overtime-next.spec.js "page.locator('#ledgerThisMonth').click()"

  # v27.18.2 Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix
contains CHANGES.md "v27.18.2 — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix"
contains README.md "v27.18.2 — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix"
contains docs/OVERTIME_SNAPSHOT_SYNC_TIMEZONE_E2E_HOTFIX_V27.18.2.md "Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix"
contains docs/API.md 'full canonical `usages`'
contains src/main/java/ru/daniil/shifts/dto/Dtos.java 'List<OvertimeUsageDto> usages'
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java 'account.usages()'
contains src/main/resources/static/js/40-overtime.js 'usages: Array.isArray(res?.usages) ? res.usages : []'
contains src/test/java/ru/daniil/shifts/service/OvertimeAccountQueryServiceTest.java 'assertEquals(2, all.usages().get(0).allocations().size());'
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java 'jsonPath("$.usages[0].hours").value(4.0)'
contains e2e/overtime-next.spec.js "(await jfetch('/api/overtime/account')).usages?.[0]?.hours || 0"
contains e2e/helpers.js "element.classList.contains('sel')"
contains e2e/helpers.js 'await expect(cell).toHaveClass(/sel/);'
contains e2e/important-timezone.spec.js "const shiftDate = await firstDay.getAttribute('data-date');"
contains e2e/important-timezone.spec.js 'await selectDate(page, shiftDate);'

  # v27.18.3 UI Settings & Button Variants Quality Hotfix
contains CHANGES.md "v27.18.3 — UI Settings & Button Variants Quality Hotfix"
contains README.md "v27.18.3 — UI Settings & Button Variants Quality Hotfix"
contains docs/UI_SETTINGS_BUTTON_VARIANTS_QUALITY_HOTFIX_V27.18.3.md "UI Settings & Button Variants Quality Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/main/resources/static/index.html 'id="uiPaletteState"'
contains src/main/resources/static/index.html 'id="paletteThemeReset"'
contains src/main/resources/static/index.html 'id="buttonVariantPreview"'
contains src/main/resources/static/js/10-core.js 'function resolveThemePalette('
contains src/main/resources/static/js/10-core.js 'function restoreThemePalette()'
contains src/main/resources/static/js/70-user-boot.js "\$('paletteThemeReset')?.addEventListener('click'"
contains src/main/resources/static/ui/tokens.css '--button-outline-border'
contains src/main/resources/static/ui/tokens.css '--button-ghost-border: transparent'
contains src/main/resources/static/ui/platform.css '[data-button-style="outline"]'
contains src/main/resources/static/ui/platform.css '[data-button-style="ghost"]'
contains e2e/appearance-quality.spec.js 'theme palette can be restored explicitly and Ghost stays distinct from Outline'
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'id=\"paletteThemeReset\"'

  # v27.19.0 Tasks & Inbox Next
contains CHANGES.md "v27.19.0 — Tasks & Inbox Next"
contains README.md "v27.19.0 — Tasks & Inbox Next"
contains docs/TASKS_INBOX_NEXT_V27.19.0.md "Tasks & Inbox Next"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/main/resources/db/migration/postgresql/V37__task_planning_intervals_and_projects.sql "scheduled_start_instant"

  # v27.20.0 Notes & Important Events Next
contains CHANGES.md "v27.20.0 — Notes & Important Events Next"
contains README.md "v27.20.0 — Notes & Important Events Next"
contains docs/NOTES_IMPORTANT_EVENTS_NEXT_V27.20.0.md "Notes & Important Events Next"
contains src/main/resources/db/migration/postgresql/V38__important_events_next.sql "event_type"
contains src/main/resources/db/migration/postgresql/V38__important_events_next.sql "start_instant"
contains src/main/java/ru/daniil/shifts/model/ImportantEventType.java "IMPORTANT_DATE"
contains src/main/java/ru/daniil/shifts/service/ImportantDayService.java "ImportantEventType.PERIOD"
contains src/test/java/ru/daniil/shifts/service/NotificationServiceTest.java "timedImportantEventUsesCanonicalInstantAndPerEventOffsets"
contains src/main/java/ru/daniil/shifts/repo/DayNoteRepository.java "List<DayNote> search"
contains src/main/resources/static/index.html 'id="importantEditModal"'
contains src/main/resources/static/index.html 'id="importantDetailsModal"'
not_contains src/main/resources/static/index.html 'id="importantBoardTitle"'
contains src/main/resources/static/index.html 'id="noteSearch"'
contains src/main/resources/static/js/20-data.js 'item.type === "updateNote"'
contains src/main/java/ru/daniil/shifts/module/DutyLogModules.java 'List.of("day.note", "note.update")'
contains src/main/resources/static/js/50-tasks.js 'function openImportantDetails(id)'
contains src/main/resources/static/js/50-tasks.js 'function runNoteSearch()'
contains src/main/resources/static/js/37-calendar-experience.js 'type:"important"'
contains e2e/notes-important-events-next.spec.js 'Notes and Important Events Next combine searchable notes'
contains e2e/important-timezone.spec.js "page.locator('#importantEditName')"

  # v27.20.1 Important Event Modal & Offline Notes E2E Hotfix
contains CHANGES.md "v27.20.1 — Important Event Modal & Offline Notes E2E Hotfix"
contains README.md "v27.20.1 — Important Event Modal & Offline Notes E2E Hotfix"
contains docs/IMPORTANT_EVENT_MODAL_OFFLINE_NOTES_E2E_HOTFIX_V27.20.1.md "Important Event Modal & Offline Notes E2E Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/main/resources/static/js/50-tasks.js "function closeImportantEventModals()"
contains src/main/resources/static/js/50-tasks.js 'button,a,input,select,textarea,[role=button]'
contains src/main/resources/static/js/50-tasks.js "e.stopPropagation();"
contains src/main/resources/static/js/50-tasks.js "closeImportantEventModals();"
contains e2e/important-timezone.spec.js "await expect(page.locator('#importantDetailsModal')).toBeHidden();"
contains e2e/helpers.js "Day and week modes intentionally hide the month grid"
contains e2e/helpers.js "if (alreadySelected && panelVisible) return cell.first();"
contains e2e/helpers.js "if (originalMode && originalMode !== 'month')"
contains e2e/pwa-offline.spec.js "preserves and synchronizes an existing note edited offline"
contains e2e/pwa-offline.spec.js "queuedNoteUpdates"
contains e2e/pwa-offline.spec.js "toBeEditable()"
contains e2e/pwa-offline.spec.js "toBeDisabled()"
not_contains e2e/pwa-offline.spec.js "toHaveAttribute('readonly', '')"
contains src/main/java/ru/daniil/shifts/model/DayTask.java "scheduledSourceTimezone"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "scheduledDurationMinutes"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "private void applySchedule("
contains src/main/resources/static/index.html 'id="taskEditAllDay"'
contains src/main/resources/static/index.html 'id="taskBoardProject"'
contains src/main/resources/static/index.html 'id="inboxSearch"'
contains src/main/resources/static/js/50-tasks.js "function validateTaskPlanning()"
contains src/main/resources/static/js/50-tasks.js "function renderTaskBoardProjectFilter()"
contains src/main/resources/static/js/37-calendar-experience.js "function calendarExperienceTaskSegment(task, key)"
contains src/test/java/ru/daniil/shifts/web/TasksInboxNextFrontendContractTest.java "class TasksInboxNextFrontendContractTest"

  # v27.20.2 Calendar Day Details E2E Flow Hotfix
contains CHANGES.md "v27.20.2 — Calendar Day Details E2E Flow Hotfix"
contains README.md "v27.20.2 — Calendar Day Details E2E Flow Hotfix"
contains docs/CALENDAR_DAY_DETAILS_E2E_FLOW_HOTFIX_V27.20.2.md "Calendar Day Details E2E Flow Hotfix"
contains e2e/helpers.js "async function openSelectedDayDetails(page)"
contains e2e/helpers.js "page.locator('#calendarDayOpenDetails')"
contains e2e/helpers.js "await expect(monthButton).toHaveAttribute('aria-pressed', 'true');"
contains e2e/notes-important-events-next.spec.js "await openSelectedDayDetails(page);"
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java "openSelectedDayDetails"

  # v27.21.0 Schedule Templates & Calendar Layers
contains CHANGES.md "v27.21.0 — Schedule Templates & Calendar Layers"
contains README.md "v27.21.0 — Schedule Templates & Calendar Layers"
contains docs/SCHEDULE_TEMPLATES_CALENDAR_LAYERS_V27.21.0.md "Schedule Templates & Calendar Layers"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/ARCHITECTURE.md "V39 Schedule Templates & Calendar Layers"
contains src/main/resources/db/migration/postgresql/V39__schedule_templates_and_calendar_layers.sql "CREATE TABLE schedule_templates"
contains src/main/resources/db/migration/postgresql/V39__schedule_templates_and_calendar_layers.sql "CREATE TABLE calendar_layers"
contains src/main/java/ru/daniil/shifts/service/ScheduleTemplateService.java "SKIP_CONFLICT"
contains src/main/java/ru/daniil/shifts/service/CalendarLayerService.java "sourceStart.toInstant().atZone(displayZone)"
contains src/main/java/ru/daniil/shifts/web/ScheduleTemplateController.java '"/api/v1/schedule-templates"'
contains src/main/java/ru/daniil/shifts/web/CalendarLayerController.java '"/api/v1/calendar-layers"'
contains src/main/resources/static/index.html "js/38-schedule-layers.js?v=$VERSION"
contains src/main/resources/static/index.html 'id="calendarLayerBar"'
contains src/main/resources/static/index.html 'id="scheduleTemplateList"'
contains src/main/resources/static/js/30-calendar.js 'overwriteExistingShift:!!$("tplOverwrite").checked'
contains src/main/resources/static/js/38-schedule-layers.js "calendarLayerEntriesByDate"
contains src/test/java/ru/daniil/shifts/service/ScheduleTemplatesAndLayersServiceTest.java "class ScheduleTemplatesAndLayersServiceTest"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplatesAndLayersControllerTest.java "class ScheduleTemplatesAndLayersControllerTest"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplatesCalendarLayersFrontendContractTest.java "class ScheduleTemplatesCalendarLayersFrontendContractTest"
contains e2e/schedule-templates-calendar-layers.spec.js "schedule templates preview safely"
contains e2e/helpers.js "async function openDayModuleById(page, id)"
contains e2e/helpers.js "await expect(section).toHaveCount(1)"
contains e2e/helpers.js "openDayModuleById,"
contains e2e/schedule-templates-calendar-layers.spec.js "openDayModuleById"
contains e2e/schedule-templates-calendar-layers.spec.js "await openDayModuleById(page, 'accSched');"
not_contains e2e/schedule-templates-calendar-layers.spec.js "await openDayModule(page, 'shifts');"

  # v27.21.2 Schedule Accordion E2E Selector Hotfix
contains CHANGES.md "v27.21.2 — Schedule Accordion E2E Selector Hotfix"
contains README.md "v27.21.2 — Schedule Accordion E2E Selector Hotfix"
contains docs/SCHEDULE_ACCORDION_E2E_SELECTOR_HOTFIX_V27.21.2.md "Schedule Accordion E2E Selector Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java "dataLayer.loadCalendar(requestedYear, requestedMonth"
contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java "api.month(y, m, { fresh })"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplateFrontendContractTest.java "authoritativeTemplatePreviewAndApplyKeepAlignmentOnTheServer"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplateFrontendContractTest.java "api.applyScheduleTemplate(template.id, payload)"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplatesCalendarLayersFrontendContractTest.java "async scheduleTemplates()"
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplatesCalendarLayersFrontendContractTest.java 'id=\"tplPreview\"'

  # v27.22.0 Vacation Planner
contains CHANGES.md "v27.22.0 — Vacation Planner"
contains README.md "v27.22.0 — Vacation Planner"
contains docs/VACATION_PLANNER_V27.22.0.md "Vacation Planner"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/ARCHITECTURE.md "V40 Vacation Planner"
contains src/main/resources/db/migration/postgresql/V40__vacation_planner.sql "CREATE TABLE vacation_settings"
contains src/main/resources/db/migration/postgresql/V40__vacation_planner.sql "INSERT INTO vacation_settings(user_id)"
contains src/main/resources/db/migration/postgresql/V40__vacation_planner.sql "CREATE TABLE absence_types"
contains src/main/resources/db/migration/postgresql/V40__vacation_planner.sql "CREATE TABLE absence_periods"
not_contains src/main/resources/db/migration/postgresql/V40__vacation_planner.sql "ALTER TABLE day_entries"
contains src/main/java/ru/daniil/shifts/module/ModuleKeys.java 'VACATION = "vacation"'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "DURATION_PRESETS = List.of(14, 28, 35)"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java '"VACATION_LIMIT_EXCEEDED"'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java '"ABSENCE_OVERLAP"'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java '"ABSENCE_TYPE_IN_USE"'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "mostConstrainedProjection"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "validateAllStoredWorkYears"
contains src/main/java/ru/daniil/shifts/repo/VacationSettingsRepository.java "PESSIMISTIC_WRITE"
contains src/main/java/ru/daniil/shifts/web/VacationPlannerController.java '"/api/v1/vacation-planner"'
contains src/main/java/ru/daniil/shifts/service/CalendarService.java "vacationPlannerService.occurrences"
contains src/main/resources/static/index.html "js/39-vacation-planner.js?v=$VERSION"
contains src/main/resources/static/index.html 'id="view-vacation"'
contains src/main/resources/static/index.html 'id="accVacation"'
contains src/main/resources/static/js/39-vacation-planner.js "function loadVacationPlanner(force = false)"
contains src/main/resources/static/js/39-vacation-planner.js "function renderVacationPreview(preview)"
contains src/main/resources/static/js/30-calendar.js "vacationMark"
contains src/main/resources/static/js/37-calendar-experience.js "facts.absences"
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java "class VacationPlannerServiceTest"
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java "crossWorkYearPreviewReportsTheMostConstrainedBalance"
contains src/test/java/ru/daniil/shifts/web/VacationPlannerControllerTest.java "class VacationPlannerControllerTest"
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java "class VacationPlannerFrontendContractTest"
contains e2e/vacation-planner.spec.js "vacation planner previews allowance"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/vacation-planner:"

  # v27.24.1 Calendar Comfort E2E Panel Contract Hotfix
contains CHANGES.md "v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix"
contains README.md "v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix"
contains docs/CALENDAR_COMFORT_E2E_PANEL_CONTRACT_HOTFIX_V27.24.1.md "selected-day panel remains intentionally modal"
contains e2e/calendar-comfort.spec.js "await expect(page.locator('#panel')).toBeVisible();"
contains e2e/calendar-comfort.spec.js "await page.locator('#pClose').click();"
contains e2e/calendar-comfort.spec.js "await expect(page.locator('#panel')).toBeHidden();"
contains e2e/calendar-comfort.spec.js "await expect(page.locator('#layout')).not.toHaveClass(/with-panel/);"
not_contains e2e/calendar-comfort.spec.js "force: true"
contains src/main/resources/static/js/50-tasks.js '$("pClose").addEventListener("click", () => selectDay(null));'
contains src/main/resources/static/app.css '.layout.with-panel::before'
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"

  # v27.24.0 Calendar Comfort & Correctness
contains CHANGES.md "v27.24.0 — Calendar Comfort & Correctness"
contains README.md "v27.24.0 — Calendar Comfort & Correctness"
contains docs/CALENDAR_COMFORT_CORRECTNESS_V27.24.0.md "Contextual return to today"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/resources/static/index.html 'id="todayBtn" type="button" hidden'
contains src/main/resources/static/index.html 'id="todayShiftDateRange" hidden'
contains src/main/resources/static/index.html 'id="calendarLoadStatus" role="status" aria-live="polite" hidden'
contains src/main/resources/static/js/37-calendar-experience.js "function calendarExperienceIsAtToday()"
contains src/main/resources/static/js/37-calendar-experience.js 'route === "calendar" && !calendarExperienceIsAtToday()'
contains src/main/resources/static/js/37-calendar-experience.js "button.hidden = !visible"
contains src/main/resources/static/js/30-calendar.js "const refreshing = loading && !!state.ui?.calendarHasRendered"
contains src/main/resources/static/js/30-calendar.js "if (refreshing)"
contains src/main/resources/static/js/30-calendar.js "syncImportantSelectedDate(k)"
contains src/main/resources/static/js/35-today.js "function todayDashboardDateRange(occurrence)"
contains src/main/resources/static/js/35-today.js 'return `${startTime}–${endTime}`'
contains src/main/resources/static/js/50-tasks.js "function syncImportantSelectedDate(key = state.selected)"
contains src/main/resources/static/js/70-user-boot.js "function recordCalendarLoadMetric"
contains src/main/resources/static/js/70-user-boot.js "dutylog:calendar-load"
contains src/main/resources/static/js/38-schedule-layers.js 'bar.dataset.label = t("Слои")'
contains src/main/resources/static/design-system.css '.nav #todayBtn:not([hidden])'
contains src/main/resources/static/design-system.css '.importantReminderSet input[type="checkbox"]'
contains src/main/resources/static/design-system.css 'width: 18px !important'
contains src/main/resources/static/design-system.css '.calendarLayerToggle'
contains src/test/java/ru/daniil/shifts/web/CalendarComfortFrontendContractTest.java "class CalendarComfortFrontendContractTest"
contains e2e/calendar-comfort.spec.js "calendar offers a contextual return to today"

  # v27.23.2 Calendar Sync Runtime Boot Hotfix
contains CHANGES.md "v27.23.2 — Calendar Sync Runtime Boot Hotfix"
contains README.md "v27.23.2 — Calendar Sync Runtime Boot Hotfix"
contains docs/CALENDAR_SYNC_RUNTIME_BOOT_HOTFIX_V27.23.2.md "ReferenceError: localDateKey is not defined"
contains src/main/resources/static/js/55-calendar-sync.js "from:keyOf(start.getFullYear(), start.getMonth(), start.getDate())"
contains src/main/resources/static/js/55-calendar-sync.js "to:keyOf(end.getFullYear(), end.getMonth(), end.getDate())"
not_contains src/main/resources/static/js/55-calendar-sync.js "localDateKey("
contains src/test/java/ru/daniil/shifts/web/CalendarSyncFrontendContractTest.java "rangeDefaultsUseTheCanonicalCalendarDateKeyHelper"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncFrontendContractTest.java "!js.contains(\"localDateKey(\")"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"

  # v27.23.1 Calendar Sync JSON UTF-8 Contract Hotfix
contains CHANGES.md "v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix"
contains README.md "v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix"
contains docs/CALENDAR_SYNC_JSON_UTF8_CONTRACT_HOTFIX_V27.23.1.md "getContentAsString(StandardCharsets.UTF_8)"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "getContentAsString(StandardCharsets.UTF_8)"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "contains(\"\\u2026\")"
not_contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java ".getContentAsString();"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"

  # v27.23.0 External Calendar Sync
contains CHANGES.md "v27.23.0 — External Calendar Sync"
contains README.md "v27.23.0 — External Calendar Sync"
contains docs/EXTERNAL_CALENDAR_SYNC_V27.23.0.md "SHA-256-only persistent storage"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/ARCHITECTURE.md "V41 External Calendar Sync"
contains docs/MODULE_CONTRACTS.md '`calendar_sync`'
contains docs/SECURITY_REVIEW.md "External calendar subscription"
contains src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql "CREATE TABLE calendar_feed_subscriptions"
contains src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql "token_hash VARCHAR(64)"
contains src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql "UNIQUE (token_hash)"
not_contains src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql "raw_token"
contains src/main/java/ru/daniil/shifts/module/ModuleKeys.java 'CALENDAR_SYNC = "calendar_sync"'
contains src/main/java/ru/daniil/shifts/service/CalendarSubscriptionService.java "new byte[32]"
contains src/main/java/ru/daniil/shifts/service/CalendarSubscriptionService.java "findByTokenHash(hash(normalized))"
contains src/main/java/ru/daniil/shifts/service/CalendarIcsService.java "BEGIN:VCALENDAR"
contains src/main/java/ru/daniil/shifts/service/CalendarIcsService.java 'replace(",", "\\,")'
contains src/main/java/ru/daniil/shifts/web/CalendarFeedController.java '"/calendar-feed.ics"'
contains src/main/resources/static/index.html "js/55-calendar-sync.js?v=$VERSION"
contains src/main/resources/static/index.html 'id="calendarSyncCard"'
contains src/main/resources/static/js/55-calendar-sync.js "state.calendarSyncIssuedUrl = issued.subscriptionUrl"
contains src/main/resources/static/js/55-calendar-sync.js "state.calendarSyncIssuedUrl = null"
contains src/main/resources/static/js/20-data.js "else { state.vacationPlanner = null; state.absenceOccurrences = []; state.absencesByDate = {}; }"
contains src/main/resources/static/js/20-data.js "else { state.calendarSync = null; state.calendarSyncIssuedUrl = null; }"
contains src/test/java/ru/daniil/shifts/service/CalendarIcsServiceTest.java "rangeExportContainsShiftsTasksImportantEventsAndAbsences"
contains src/test/java/ru/daniil/shifts/service/CalendarSubscriptionServiceTest.java "issuePersistsOnlySha256AndReturnsRawTokenOnce"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "issueRotateFeedAndRevokeFormOnePrivateTokenLifecycle"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncFrontendContractTest.java "OneTimePrivateSubscriptionUrl"
contains e2e/external-calendar-sync.spec.js "private calendar feed exports .ics"
contains e2e/external-calendar-sync.spec.js "rotated.subscriptionUrl"
contains e2e/external-calendar-sync.spec.js "request.get(issued.subscriptionUrl)).status()).toBe(404)"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/calendar-sync/subscription:"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/calendar-feed.ics:"
contains deploy/nginx/dutylog-staging.conf.example 'location = /calendar-feed.ics {'
contains deploy/nginx/dutylog-staging.conf.example 'access_log off;'
contains deploy/nginx/dutylog-production.conf.example 'location = /calendar-feed.ics {'
contains deploy/nginx/dutylog-production.conf.example 'access_log off;'
contains deploy/nginx/dutylog.conf.example 'location = /calendar-feed.ics {'
contains docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md 'every Certbot-managed HTTP and HTTPS `server` block'
contains deploy/scripts/check-production-env.sh 'production nginx suppresses access logs for calendar bearer URLs'

  # v27.22.2 Workspace Route E2E Navigation Hotfix
  contains CHANGES.md "v27.22.2 — Workspace Route E2E Navigation Hotfix"
  contains README.md "v27.22.2 — Workspace Route E2E Navigation Hotfix"
  contains docs/WORKSPACE_ROUTE_E2E_NAVIGATION_HOTFIX_V27.22.2.md "Workspace Route E2E Navigation Hotfix"
  contains e2e/mobile-layout.spec.js "await openView(page, 'tasks');"
  contains e2e/mobile-layout.spec.js "await expect(page.locator('#view-tasks')).toBeVisible();"
  contains e2e/task-details.spec.js "await openView(page, 'tasks');"
  contains e2e/task-modules.spec.js "await openView(page, 'tasks');"
  contains e2e/helpers.js "tasks: '[data-vue-domain-route=\"tasks\"]',"
  contains e2e/task-modules.spec.js "await openView(page, 'tasks');"
  contains e2e/task-modules.spec.js "page.locator('[data-vue-domain-route=\"tasks\"]')).toBeVisible()"
  not_contains e2e/mobile-layout.spec.js "#tabbar a[data-view=\"tasks\"]').click"
  not_contains e2e/task-details.spec.js "#tabbar a[data-view=\"tasks\"]').click"
  not_contains e2e/task-modules.spec.js "#tabbar a[data-view=\"tasks\"]').click"

  # v27.22.1 Vacation Planner Frontend Contract Hotfix
contains CHANGES.md "v27.22.1 — Vacation Planner Frontend Contract Hotfix"
contains README.md "v27.22.1 — Vacation Planner Frontend Contract Hotfix"
contains docs/VACATION_PLANNER_FRONTEND_CONTRACT_HOTFIX_V27.22.1.md "Vacation Planner Frontend Contract Hotfix"
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'navigation:[\"today\",\"calendar\",\"vacation\",\"overtime\",\"settings\"]'
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'todayWidgets:[\"shift\",\"overtime\",\"tasks\",\"important\"]'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.absences)'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'type:\"vacation\"'
not_contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'experience.contains("vacation-day")'
contains src/test/java/ru/daniil/shifts/service/ModuleServiceContractTest.java 'DutyLogModules.ALL.stream()'
not_contains src/test/java/ru/daniil/shifts/service/ModuleServiceContractTest.java 'assertEquals(7, settings.findByOwner(regular).size()'

  # v27.19.1 Task Board Date Range Compatibility Hotfix
contains CHANGES.md "v27.19.1 — Task Board Date Range Compatibility Hotfix"
contains README.md "v27.19.1 — Task Board Date Range Compatibility Hotfix"
contains docs/TASK_BOARD_DATE_RANGE_COMPATIBILITY_HOTFIX_V27.19.1.md "Task Board Date Range Compatibility Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/main/java/ru/daniil/shifts/web/TaskController.java '@RequestParam(name = "scheduledFrom"'
contains src/main/java/ru/daniil/shifts/web/TaskController.java '@RequestParam(name = "scheduledTo"'
contains src/main/java/ru/daniil/shifts/service/TaskService.java "withinTaskBoardDeadlineRange"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "scheduledFromDate"
contains src/main/resources/static/js/50-tasks.js 'scheduledFrom:filters.from || ""'
contains src/main/resources/static/js/50-tasks.js 'scheduledTo:filters.to || ""'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java '"2026-08-11", "2026-08-11", 0, 50'
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java '.param("scheduledFrom", today.toString())'
contains src/test/java/ru/daniil/shifts/web/TasksInboxNextFrontendContractTest.java 'scheduledFrom:filters.from'
contains e2e/tasks-inbox-next.spec.js "Tasks & Inbox Next keeps planning"

  # v27.19.2 Frontend Asset Contract Stability Hotfix
contains CHANGES.md "v27.19.2 — Frontend Asset Contract Stability Hotfix"
contains README.md "v27.19.2 — Frontend Asset Contract Stability Hotfix"
contains docs/FRONTEND_ASSET_CONTRACT_STABILITY_HOTFIX_V27.19.2.md "Frontend Asset Contract Stability Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'js/35-today.js?v='
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'js/12-ui-platform.js?v='
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java 'js/37-calendar-experience.js?v='
contains src/test/java/ru/daniil/shifts/web/DesignSystemMobileShellFrontendContractTest.java 'design-system.css?v='

  # v27.19.3 Task Deadline Validation E2E Contract Hotfix
contains CHANGES.md "v27.19.3 — Task Deadline Validation E2E Contract Hotfix"
contains README.md "v27.19.3 — Task Deadline Validation E2E Contract Hotfix"
contains docs/TASK_DEADLINE_VALIDATION_E2E_CONTRACT_HOTFIX_V27.19.3.md "Task Deadline Validation E2E Contract Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains e2e/task-modules.spec.js "Дедлайн не может быть раньше окончания запланированного интервала."

  # v27.19.4 Ghost Button Transition E2E Stabilization Hotfix
contains CHANGES.md "v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix"
contains README.md "v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix"
contains docs/GHOST_BUTTON_TRANSITION_E2E_STABILIZATION_HOTFIX_V27.19.4.md "Ghost Button Transition E2E Stabilization Hotfix"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains e2e/appearance-quality.spec.js "const borderAlpha = context.getImageData(0, 0, 1, 1).data[3];"
contains e2e/appearance-quality.spec.js "await expect.poll(async () => (await previewStyle(page)).borderAlpha).toBe(0);"
not_contains e2e/appearance-quality.spec.js "expect(ghost.borderColor).toBe('rgba(0, 0, 0, 0)');"

VERSION_HARDCODE_REPORT="${TMPDIR:-/tmp}/dutylog-version-hardcode.txt"
if grep -R "result.put(\"version\", \"" -n src/main/java >"$VERSION_HARDCODE_REPORT"; then
  cat "$VERSION_HARDCODE_REPORT" >&2
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

node - <<'NODE'
const fs = require('fs');
const vm = require('vm');
const source = fs.readFileSync('src/main/resources/static/js/35-today.js', 'utf8');
const handlers = [];
const context = {
  I18N_EN: {},
  I18N_RU: {},
  $: () => ({ addEventListener: (type, handler) => handlers.push({ type, handler }) }),
  setInterval: () => 0,
  clearInterval: () => {}
};
vm.createContext(context);
vm.runInContext(source, context, { filename: '35-today.js' });
if (!handlers.some(item => item.type === 'click' && typeof item.handler === 'function')) {
  throw new Error('Today bundle did not register its click handlers');
}
console.log('OK:    Today bundle evaluates safely before later feature bundles');
NODE

echo
echo "3) Static frontend contract Java syntax"
JAVA_CONTRACT_TMP="$(mktemp -d)"
cleanup_java_contract_tmp() {
  rm -rf "$JAVA_CONTRACT_TMP"
}
trap cleanup_java_contract_tmp EXIT
mkdir -p "$JAVA_CONTRACT_TMP/stubs/org/junit/jupiter/api" "$JAVA_CONTRACT_TMP/classes"
cat > "$JAVA_CONTRACT_TMP/stubs/org/junit/jupiter/api/Test.java" <<'JAVA'
package org.junit.jupiter.api;
public @interface Test {}
JAVA
cat > "$JAVA_CONTRACT_TMP/stubs/org/junit/jupiter/api/Assertions.java" <<'JAVA'
package org.junit.jupiter.api;
public final class Assertions {
  private Assertions() {}
  public static void assertTrue(boolean condition) {}
  public static void assertTrue(boolean condition, String message) {}
  public static void assertFalse(boolean condition) {}
  public static void assertFalse(boolean condition, String message) {}
}
JAVA
javac -encoding UTF-8 -d "$JAVA_CONTRACT_TMP/classes" \
  "$JAVA_CONTRACT_TMP/stubs/org/junit/jupiter/api/Test.java" \
  "$JAVA_CONTRACT_TMP/stubs/org/junit/jupiter/api/Assertions.java"
mapfile -d '' FRONTEND_CONTRACT_TESTS < <(
  find src/test/java/ru/daniil/shifts/web -maxdepth 1 \
    \( -name '*FrontendContractTest.java' -o -name '*HotfixTest.java' \) \
    -print0 | sort -z
)
if (( ${#FRONTEND_CONTRACT_TESTS[@]} == 0 )); then
  fail "no static frontend contract or hotfix Java sources found"
else
  javac -encoding UTF-8 -proc:none -cp "$JAVA_CONTRACT_TMP/classes" \
    -d "$JAVA_CONTRACT_TMP/classes" "${FRONTEND_CONTRACT_TESTS[@]}"
  ok "Static frontend contract and hotfix Java sources compile"
fi
cleanup_java_contract_tmp
trap - EXIT

if python3 - <<'PY_FRONTEND_ASSET_CONTRACTS'
from pathlib import Path
import re

pattern = re.compile(r'\?v=\d+\.\d+\.\d+')
violations = []
contract_paths = set(Path('src/test/java/ru/daniil/shifts/web').glob('*FrontendContractTest.java'))
contract_paths.update(Path('src/test/java/ru/daniil/shifts/web').glob('*HotfixTest.java'))
for path in sorted(contract_paths):
    for line_no, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if pattern.search(line):
            violations.append(f'{path}:{line_no}:{line.strip()}')
if violations:
    print('Hardcoded frontend asset release versions found:', flush=True)
    print('\n'.join(violations), flush=True)
    raise SystemExit(1)
PY_FRONTEND_ASSET_CONTRACTS
then
  ok "frontend asset contracts contain no hardcoded semantic cache-busting versions"
else
  fail "frontend asset contracts must assert stable asset paths with ?v=, not a concrete release number"
fi

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
python3 -m json.tool package-lock.json >/dev/null
ok "package-lock.json is valid JSON"
not_contains package-lock.json "applied-caas"
not_contains package-lock.json "internal.api.openai.org"
not_contains package-lock.json "10.192."
contains package-lock.json "https://registry.npmjs.org/"
contains .npmrc "registry=https://registry.npmjs.org/"
contains .github/workflows/ci.yml "bash ./deploy/scripts/npm-ci-with-retry.sh"
contains .github/workflows/deploy-staging.yml "bash ./deploy/scripts/npm-ci-with-retry.sh"

python3 - "$VERSION" <<'PY'
from pathlib import Path
import re, sys
version = sys.argv[1]
html = Path('src/main/resources/static/index.html').read_text(encoding='utf-8')
expected = [
    'js/10-core.js',
    'js/12-ui-platform.js',
    'js/20-data.js',
    'js/30-calendar.js',
    'js/35-today.js',
    'js/37-calendar-experience.js',
    'js/38-schedule-layers.js',
    'js/39-vacation-planner.js',
    'js/40-overtime.js',
    'js/50-tasks.js',
    'js/55-calendar-sync.js',
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

themes = {
    'midnight':'src/main/resources/static/ui/themes/midnight.css',
    'oled':'src/main/resources/static/ui/themes/oled.css',
    'forest':'src/main/resources/static/ui/themes/forest.css',
    'sunset':'src/main/resources/static/ui/themes/sunset.css',
    'industrial':'src/main/resources/static/ui/themes/industrial.css',
    'softPurple':'src/main/resources/static/ui/themes/soft-purple.css',
}
required = [
    '--color-background', '--color-surface', '--color-surface-elevated',
    '--color-text-primary', '--color-text-secondary', '--color-border',
]
for theme_id, path in themes.items():
    css = Path(path).read_text(encoding='utf-8')
    selector = f'html[data-ui-theme="{theme_id}"]'
    if selector not in css:
        raise SystemExit(f'{path}: missing isolated selector {selector}')
    for token in required:
        if token not in css:
            raise SystemExit(f'{path}: missing required token {token}')
    for other in themes:
        if other != theme_id and f'data-ui-theme="{other}"' in css:
            raise SystemExit(f'{path}: leaks into theme {other}')
    if '@layer' in css:
        raise SystemExit(f'{path}: theme package must remain later-cascade scoped, not hidden in a legacy-external layer')
platform = Path('src/main/resources/static/ui/platform.css').read_text(encoding='utf-8')
for expected in ['--workspace-nav-count', 'data-ui-layout="compact"', 'data-ui-layout="focus"']:
    if expected not in platform:
        raise SystemExit(f'ui/platform.css missing {expected}')
print('OK:    UI Core theme packages satisfy isolated contract v1')
PY

node - <<'NODE'
const fs = require('fs');
const vm = require('vm');
const source = fs.readFileSync('src/main/resources/static/js/40-overtime.js', 'utf8');
const match = source.match(/function allocationRangeLabels\(allocation\)\{[\s\S]*?\n\}/);
if (!match) throw new Error('allocationRangeLabels not found');
const context = {
  displayDateTimeRange: (start, end) => `${start}|${end}`,
  formatDateHuman: key => key.split('-').reverse().join('.')
};
vm.createContext(context);
vm.runInContext(`${match[0]}; this.result = allocationRangeLabels({ exact:true, displayStart:'2026-04-30T17:00:00', displayEnd:'2026-05-01T01:00:00' });`, context);
const expected = ['30.04.2026 17:00–24:00', '01.05.2026 00:00–01:00'];
if (JSON.stringify(context.result) !== JSON.stringify(expected)) {
  throw new Error(`unexpected allocation labels: ${JSON.stringify(context.result)}`);
}
console.log('OK:    overtime cross-midnight allocation runtime smoke');
NODE

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
settings = Path('src/main/resources/static/js/60-settings.js').read_text(encoding='utf-8')
admin = Path('frontend/src/features/admin/components/AdminWorkspace.vue').read_text(encoding='utf-8')
for forbidden in ['function initDiagnosticsEvents', 'function refreshRegistrationAdmin', 'function refreshAdminUsers', 'function refreshAdminPanel']:
    if forbidden in settings:
        raise SystemExit(f'legacy Admin runtime owner still present in settings bundle: {forbidden}')
for required in ['id="diagnosticsRefresh"', 'id="registrationRefresh"', 'id="adminUsersRefresh"']:
    if required not in admin:
        raise SystemExit(f'Vue Admin workspace missing migrated refresh control: {required}')
print('OK:    Admin refresh/events are Vue-owned; generic legacy settings init has no Admin auto-fetch owner')
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
not_contains src/main/resources/static/js/60-settings.js "function refreshAdminPanel()"
not_contains src/main/resources/static/js/70-user-boot.js "if (state.profile?.admin) refreshAdminPanel();"

python3 - <<'PY'
from pathlib import Path
import re
html = '\n'.join(
    Path(path).read_text(encoding='utf-8')
    for path in ['src/main/resources/static/index.html', 'src/main/resources/static/login.html']
)
js = '\n'.join(p.read_text(encoding='utf-8') for p in sorted(Path('src/main/resources/static/js').glob('*.js')))
ids = re.findall(r'\bid=["\']([^"\']+)["\']', html)
vue = '\n'.join(p.read_text(encoding='utf-8') for p in sorted(Path('frontend/src').glob('**/*.vue')))
vue_ids = set(re.findall(r'\bid=["\']([^"\']+)["\']', vue))
idset = set(ids) | vue_ids | set(re.findall(r'id=\\?["\']([\w-]+)\\?["\']', js))
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

def java_code_only(source: str, path: Path) -> str:
    out = []
    i = 0
    n = len(source)
    state = "code"

    while i < n:
        if state == "code":
            if source.startswith('"""', i):
                out.extend("   ")
                i += 3
                state = "textblock"
            elif source.startswith("//", i):
                out.extend("  ")
                i += 2
                state = "line_comment"
            elif source.startswith("/*", i):
                out.extend("  ")
                i += 2
                state = "block_comment"
            elif source[i] == '"':
                out.append(" ")
                i += 1
                state = "string"
            elif source[i] == "'":
                out.append(" ")
                i += 1
                state = "char"
            else:
                out.append(source[i])
                i += 1

        elif state == "line_comment":
            if source[i] == "\n":
                out.append("\n")
                i += 1
                state = "code"
            else:
                out.append(" ")
                i += 1

        elif state == "block_comment":
            if source.startswith("*/", i):
                out.extend("  ")
                i += 2
                state = "code"
            else:
                out.append("\n" if source[i] == "\n" else " ")
                i += 1

        elif state == "string":
            if source[i] == "\\" and i + 1 < n:
                out.extend("  ")
                i += 2
            elif source[i] == '"':
                out.append(" ")
                i += 1
                state = "code"
            else:
                out.append("\n" if source[i] == "\n" else " ")
                i += 1

        elif state == "char":
            if source[i] == "\\" and i + 1 < n:
                out.extend("  ")
                i += 2
            elif source[i] == "'":
                out.append(" ")
                i += 1
                state = "code"
            else:
                out.append("\n" if source[i] == "\n" else " ")
                i += 1

        elif state == "textblock":
            if source.startswith('"""', i):
                out.extend("   ")
                i += 3
                state = "code"
            else:
                out.append("\n" if source[i] == "\n" else " ")
                i += 1

    if state not in {"code", "line_comment"}:
        raise SystemExit(
            f"unterminated Java lexical construct in {path}: {state}"
        )

    return "".join(out)

bad = []

for path in sorted(Path("src").rglob("*.java")):
    source = path.read_text(encoding="utf-8")
    code = java_code_only(source, path)

    depth = 0
    minimum = 0

    for char in code:
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            minimum = min(minimum, depth)

    if depth != 0 or minimum < 0:
        bad.append(
            (
                str(path),
                code.count("{"),
                code.count("}"),
                minimum,
            )
        )

if bad:
    raise SystemExit(
        f"java code brace mismatch: {bad[:10]}"
    )

print("OK:    Java code brace balance")
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
contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
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
contains src/main/resources/static/js/60-settings.js 'const label = state.language === "en" ? "Current time" : "Текущее время";'
contains src/main/resources/static/js/60-settings.js 'displayTimezone: val("workTimezone") || browserTimeZone()'
contains src/main/resources/static/js/60-settings.js 'esc(t("шт"))'
contains docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md "v27.2.5"
contains src/main/resources/static/app.css "v27.2.5: notifications header alignment"
contains src/main/resources/static/app.css "#notifyCard > .notifyHead"
contains src/main/resources/static/app.css ".adminShell.settingsShell"
contains frontend/src/features/admin/components/AdminWorkspace.vue 'data-admin-jump="users"'
contains frontend/src/features/admin/components/AdminWorkspace.vue 'data-admin-jump="registration"'
contains frontend/src/features/admin/components/AdminWorkspace.vue 'data-admin-jump="diagnostics"'
not_contains src/main/resources/static/js/60-settings.js "function initAdminNavigation"
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
contains src/main/java/ru/daniil/shifts/service/NoteExportService.java "countByOwner"
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
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/check-backup-freshness.sh'
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/install-backup-timer.sh'
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/restore-drill.sh'
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
not_contains src/main/resources/static/js/login.js 'serviceWorker.register'
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
contains docs/RELEASE_CHECKLIST.md "git tag -a v27.37.1"

# v27.2.5 calendar persistence regression guards
contains src/main/resources/static/js/70-user-boot.js "dataLayer.loadCalendar(requestedYear, requestedMonth"
contains src/main/resources/static/js/70-user-boot.js "{ fresh:!!opts.fresh }"
contains src/main/resources/static/js/20-data.js "api.month(y, m, { fresh })"
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
contains docs/REGRESSION_TEST_BASELINE.md "Status: v27.2.31."
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
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplateFrontendContractTest.java "authoritativeTemplatePreviewAndApplyKeepAlignmentOnTheServer"


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
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java "canonicalAbsenceMigrationPreservesLegacyFifoAndRetiresDirectUsageWrites"
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
contains src/test/java/ru/daniil/shifts/service/UserAdminServiceTest.java 'service = new UserAdminService(users, encoder, mobileAuthService, rememberMeTokenService, securityEvents, "bootstrap-root")'
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
contains e2e/pwa-offline.spec.js "preserves and synchronizes an existing note edited offline"
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
contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
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

# v27.2.31 authenticated deployment smoke-test hotfix
contains CHANGES.md "v27.2.31 — Authenticated deployment smoke-test hotfix"
contains README.md "v27.2.31 — Authenticated deployment smoke-test hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "v27.2.31 adds an authenticated, CSRF-aware deployment smoke-test regression"
contains docs/AUTHENTICATED_SMOKE_TEST_HOTFIX_V27.2.31.md 'CSRF-protected `/perform_login`'
contains deploy/scripts/smoke-test.sh "DUTYLOG_SMOKE_REQUIRE_AUTH"
contains deploy/scripts/smoke-test.sh '--data-urlencode "password@$PASSWORD_FILE"'
contains deploy/scripts/smoke-test.sh "-H 'Accept: text/html'"
contains deploy/scripts/local-smoke-test.sh "DUTYLOG_SMOKE_REQUIRE_AUTH=true"
contains deploy/scripts/deploy-environment.sh "DUTYLOG_SMOKE_REQUIRE_AUTH=true bash deploy/scripts/smoke-test.sh"
contains deploy/scripts/smoke-test-regression.py "Authenticated smoke-test regression passed."

# v27.2.32 pipefail-safe authenticated smoke-test hotfix
contains CHANGES.md "v27.2.32 — Pipefail-safe authenticated smoke-test hotfix"
contains docs/PIPEFAIL_SAFE_SMOKE_TEST_HOTFIX_V27.2.32.md "SIGPIPE"
contains deploy/scripts/smoke-test.sh "contains_literal"
not_contains deploy/scripts/smoke-test.sh '| grep -q'
not_contains deploy/scripts/smoke-test.sh '| grep -qi'
contains deploy/scripts/smoke-test-regression.py "deployment-smoke-padding"

# v27.2.33 persistent login, per-day write ordering and compact mobile UX
contains CHANGES.md "v27.2.33 — Persistent login, shift reassign and compact mobile UX"
contains docs/PERSISTENT_LOGIN_AND_MOBILE_UX_V27.2.33.md "DUTYLOG_REMEMBER_ME"
contains src/main/resources/db/migration/postgresql/V24__persistent_web_login.sql "CREATE TABLE persistent_logins"
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java '.rememberMe(remember -> remember'
contains src/main/resources/static/login.html 'name="remember-me"'
contains src/main/resources/static/js/50-tasks.js "const daySaveChains = new Map();"
contains src/main/resources/static/index.html 'id="taskBoardFiltersToggle"'
contains src/main/resources/static/app.css 'body.panel-open .tabbar'
contains e2e/calendar-persistence.spec.js "a shift can be deleted and assigned again while a note save is pending"
contains src/test/java/ru/daniil/shifts/web/RememberMeAuthenticationTest.java "rememberedLoginSurvivesWithoutTheOriginalHttpSession"

# v27.3.0 important dates, user timezone and precise overtime editing
contains CHANGES.md "v27.3.0 — Important dates, user timezone and precise overtime editing"
contains docs/IMPORTANT_DATES_TIMEZONE_OVERTIME_V27.3.0.md "work_timezone"
contains src/main/resources/db/migration/postgresql/V25__user_work_timezone.sql "ADD COLUMN IF NOT EXISTS work_timezone"
contains src/main/java/ru/daniil/shifts/model/AppUser.java "private String workTimezone"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("workTimezone"'
contains src/main/java/ru/daniil/shifts/service/UserTimeService.java "LocalDate today"
contains src/main/resources/static/index.html 'id="view-important"'
contains src/main/resources/static/js/50-tasks.js "renderImportantBoard"
contains src/main/resources/static/js/40-overtime.js "startEditOvertimeCredit"
contains src/main/resources/static/js/40-overtime.js "ledgerEditingRow"
contains src/main/resources/static/app.css ".ledgerEditingRow"

# v27.3.1 stable browser session and editor modals
contains CHANGES.md "v27.3.1 — Stable browser session and editor modals"
contains docs/PERSISTENT_SESSION_AND_EDITOR_MODALS_V27.3.1.md "StablePersistentRememberMeServices"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/java/ru/daniil/shifts/config/StablePersistentRememberMeServices.java "processAutoLoginCookie"
contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java "rememberMeServices(rememberMeServices)"
contains src/test/java/ru/daniil/shifts/web/RememberMeAuthenticationTest.java "theSameRememberCookieCanBootstrapParallelPwaRequests"
contains src/main/resources/static/index.html 'id="taskEditModal"'
contains src/main/resources/static/index.html 'id="shiftTypeModal"'
not_contains src/main/resources/static/index.html 'id="shiftSettingsCard"'
not_contains src/main/resources/static/js/50-tasks.js 'prompt("Текст задачи"'
not_contains src/main/resources/static/js/60-settings.js 'prompt(t("Название смены")'
contains e2e/editor-modals.spec.js "task and shift type editors use complete modal forms"

# v27.4.0 unified overtime editors
contains CHANGES.md "v27.4.0 — Unified overtime editors"
contains README.md "v27.4.0 — Unified overtime editors"
contains docs/UNIFIED_OVERTIME_EDITORS_V27.4.0.md "shared credit editor"
contains src/main/resources/static/index.html 'id="dayAddCredit"'
contains src/main/resources/static/index.html 'id="dayAddUsage"'
contains src/main/resources/static/index.html 'id="ledgerAddCredit"'
contains src/main/resources/static/index.html 'id="ledgerAddUsage"'
contains src/main/resources/static/index.html 'id="overtimeCreditModal"'
not_contains src/main/resources/static/index.html 'id="overtimeUsageModal"'
contains src/main/resources/static/index.html 'id="creditScenarioSelect"'
not_contains src/main/resources/static/index.html 'class="quickScenarioPanel"'
not_contains src/main/resources/static/js/40-overtime.js "openOvertimeEditorForDate"
contains src/main/resources/static/js/40-overtime.js "openOvertimeCreditModal"
contains src/main/resources/static/js/40-overtime.js "openOvertimeUsageModal"
contains src/main/resources/static/js/40-overtime.js "openLegacyUsageMigration"
contains src/main/resources/static/app.css "body.app-modal-open .tabbar"
contains src/test/java/ru/daniil/shifts/web/UnifiedOvertimeEditorsFrontendContractTest.java "calendarAndLedgerRouteNewUsageThroughTheUnifiedAbsenceComposer"
contains e2e/overtime-editor-modals.spec.js "overtime credit and usage editors work from calendar and ledger"


# v27.4.1 scenario manager inside the shared overtime editor
contains CHANGES.md "v27.4.1 — Overtime scenario manager"
contains README.md "v27.4.1 — Overtime scenario manager"
contains docs/OVERTIME_SCENARIO_MANAGER_V27.4.1.md "single-window scenario manager"
not_contains src/main/resources/static/index.html 'data-settings-jump="scenarios"'
not_contains src/main/resources/static/index.html 'id="quickScenarioSettingsCard"'
not_contains src/main/resources/static/index.html 'id="settings-scenarios"'
contains src/main/resources/static/index.html 'id="creditScenarioManage"'
contains src/main/resources/static/index.html 'id="creditScenarioSaveCurrent"'
contains src/main/resources/static/index.html 'id="scenarioManagerView"'
contains src/main/resources/static/index.html 'id="scenarioManagerForm"'
contains src/main/resources/static/js/40-overtime.js "function openScenarioManager"
contains src/main/resources/static/js/40-overtime.js "function scenarioDraftFromCreditForm"
contains src/main/resources/static/js/40-overtime.js "api.updateQuickScenario(editingId, payload)"
contains src/test/java/ru/daniil/shifts/web/OvertimeScenarioManagerFrontendContractTest.java "scenariosNoLongerOccupyASettingsCard"
contains e2e/overtime-scenario-manager.spec.js "overtime scenarios are created and edited inside the shared credit modal"

# v27.4.2 timezone simplification and critical regression pack
contains CHANGES.md "v27.4.2 — Timezone simplification and critical regression pack"
contains README.md "v27.4.2 — Timezone simplification and critical regression pack"
contains docs/TIMEZONE_AND_CRITICAL_REGRESSION_V27.4.2.md "Persistent login is restored"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/resources/static/index.html 'id="workTimezone"'
contains src/main/resources/static/index.html 'id="timeSaveTimezone"'
contains src/main/resources/static/index.html 'id="timeDetectBrowser"'
not_contains src/main/resources/static/index.html 'id="workRegionName"'
not_contains src/main/resources/static/index.html 'id="workOffsetMoscow"'
contains src/main/resources/static/js/60-settings.js "function populateTimeZoneSelect"
contains src/main/resources/static/js/60-settings.js "function timezoneOffsetLabel"
contains e2e/remember-me.spec.js "remember-me restores a fresh browser session"
contains e2e/remember-me.spec.js "DUTYLOG_REMEMBER_ME"
contains e2e/editor-modals.spec.js "taskEditDueTime"
contains e2e/editor-modals.spec.js "shiftUpdated"
contains deploy/scripts/smoke-test.sh "Authenticated read-only API contract"
contains deploy/scripts/production-smoke-test.sh "DUTYLOG_SMOKE_REQUIRE_AUTH=true"
contains deploy/scripts/production-smoke-test.sh "https://"
contains deploy/scripts/check-production-env.sh "deploy/scripts/production-smoke-test.sh"
contains deploy/scripts/remote-deploy.sh "deploy/scripts/production-smoke-test.sh"

# v27.4.3 reminder timezone and sync UX bugfix
contains CHANGES.md "v27.4.3 — Reminder timezone and sync UX bugfix"
contains README.md "v27.4.3 — Reminder timezone and sync UX bugfix"
contains docs/REMINDER_TIMEZONE_SYNC_UX_V27.4.3.md "remindAtInstant"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "String remindAtInstant"
contains src/main/java/ru/daniil/shifts/service/NotificationService.java "instant.toString()"
contains src/main/resources/static/js/60-settings.js "browserReminderInstantValue"
contains src/main/resources/static/js/60-settings.js "reminder?.remindAtInstant || reminder?.remindAt"
not_contains src/main/resources/static/js/60-settings.js 'new Date(reminder.remindAt || "").getTime()'
contains src/main/resources/static/index.html 'id="taskEditReminderBefore" max="10080" min="0" step="1"'
not_contains src/main/resources/static/index.html 'id="creditTimeRange"'
not_contains src/main/resources/static/index.html "Короткий интервал"
not_contains src/main/resources/static/js/40-overtime.js "parseManualTimeRange"
contains src/main/resources/static/index.html 'id="offlineSyncFeedback" role="status" aria-live="polite"'
contains src/main/resources/static/js/20-data.js "setOfflineSyncButtonBusy(true)"
contains e2e/editor-modals.spec.js "taskEditReminderBefore"
contains e2e/overtime-editor-modals.spec.js "creditStart"
contains e2e/offline-sync-feedback.spec.js "manual synchronization shows progress and a final result"
contains src/test/java/ru/daniil/shifts/service/NotificationServiceTest.java "browserInstantUsesTheUsersSavedIanaTimezone"
contains src/test/java/ru/daniil/shifts/web/NotificationOvertimeSyncUxFrontendContractTest.java "manualSyncHasAnAccessibleProgressAndResultSurface"

# v27.5.1 Telegram command and mobile sync status bugfix
contains CHANGES.md "v27.5.1 — Telegram commands and mobile sync status bugfix"
contains README.md "v27.5.1 — Telegram commands and mobile sync status bugfix"
contains src/main/java/ru/daniil/shifts/telegram/TelegramCommandService.java 'Часть данных не загрузилась'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'Не удалось выполнить команду'
contains src/main/resources/static/js/20-data.js 'compactStatus ? "синхр…" : "синхронизация…"'
contains src/main/resources/static/app.css 'overflow-wrap:anywhere'

# v27.5.2 Telegram command menu and quick actions
contains CHANGES.md "v27.5.2 — Telegram command menu and quick actions"
contains README.md "v27.5.2 — Telegram command menu and quick actions"
contains docs/TELEGRAM_COMMAND_MENU_V27.5.2.md 'setMyCommands'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'setMyCommands'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'reply_markup'
contains src/main/java/ru/daniil/shifts/telegram/TelegramBotService.java 'is_persistent'
contains src/main/java/ru/daniil/shifts/telegram/TelegramCommandService.java 'case "сегодня" -> "/today"'
contains src/test/java/ru/daniil/shifts/telegram/TelegramBotServiceTest.java 'commandMenuRegistrationIsDiscoverableAndRetrySafe'
contains src/test/java/ru/daniil/shifts/telegram/TelegramCommandServiceTest.java 'quickActionKeyboardLabelsDispatchWithoutSlashCommands'
contains deploy/compose/docker-compose.deploy.yml 'DUTYLOG_TELEGRAM_COMMAND_MENU_ENABLED'
contains src/main/resources/application-prod.properties 'dutylog.telegram.command-menu-enabled'

# v27.6.0 mobile tasks and Inbox UX
contains CHANGES.md "v27.6.0 — Mobile Tasks & Inbox UX"
contains README.md "v27.6.0 — Mobile Tasks & Inbox UX"
contains docs/MOBILE_TASKS_INBOX_V27.6.0.md "thought → one tap → text → saved"
contains src/main/resources/db/migration/postgresql/V26__task_tags_and_inbox.sql "CREATE TABLE inbox_items"
contains src/main/resources/db/migration/postgresql/V26__task_tags_and_inbox.sql "CREATE TABLE day_task_tags"
contains src/main/resources/db/migration/postgresql/V26__task_tags_and_inbox.sql "tag_order INTEGER NOT NULL"
contains src/main/java/ru/daniil/shifts/model/InboxItem.java 'class InboxItem'
contains src/main/java/ru/daniil/shifts/service/InboxService.java 'convertToTask'
contains src/main/java/ru/daniil/shifts/web/InboxController.java '@RequestMapping({"/api/inbox", "/api/v1/inbox"})'
contains src/main/java/ru/daniil/shifts/module/DutyLogModules.java '"inbox.capture"'
contains src/main/resources/static/index.html 'id="taskInboxCard"'
contains src/main/resources/static/index.html 'id="globalQuickAdd"'
contains src/main/resources/static/index.html 'id="taskEditAdvanced"'
contains src/main/resources/static/index.html 'id="taskEditTags"'
contains src/main/resources/static/index.html 'id="quickActionUsage"'
not_contains src/main/resources/static/index.html 'id="taskText"'
not_contains src/main/resources/static/index.html 'id="taskAdd"'
contains src/main/resources/static/js/20-data.js 'async captureInbox(text)'
contains src/main/resources/static/js/20-data.js 'item.type === "captureInbox"'
contains src/main/resources/static/js/50-tasks.js 'openTaskCreate({ text:item.text, inboxId:item.id'
contains src/main/resources/static/js/50-tasks.js '"Быстрое действие":"Quick action"'
contains e2e/editor-modals.spec.js "#taskCreateForDay"
contains e2e/editor-modals.spec.js ".taskItemBody"
not_contains e2e/editor-modals.spec.js "#taskText"
not_contains e2e/editor-modals.spec.js "#taskAdd"
contains src/main/resources/static/app.css '#taskEditModal .appModalPanel'
contains src/main/resources/static/app.css 'height:100dvh'
contains src/test/java/ru/daniil/shifts/service/InboxServiceTest.java 'clientOperationIdMakesRetriesIdempotent'
contains src/test/java/ru/daniil/shifts/web/InboxControllerTest.java 'createListArchiveConvertAndDeleteWorkThroughVersionedApi'
contains src/test/java/ru/daniil/shifts/web/MobileTasksInboxFrontendContractTest.java 'fastCaptureInboxAndOfflineQueueAreConnectedEndToEnd'
contains e2e/task-modules.spec.js 'quick capture survives the fast Inbox flow and converts into a task'

# v27.6.1 quick capture polish
contains CHANGES.md "v27.6.1 — Quick Capture Polish"
contains README.md "v27.6.1 — Quick Capture Polish"
contains docs/QUICK_CAPTURE_POLISH_V27.6.1.md "Inbox is a temporary capture layer"
contains src/main/resources/static/index.html 'class="taskInboxTray" id="taskInboxCard"'
contains src/main/resources/static/index.html 'id="quickActionText"'
contains src/main/resources/static/index.html 'id="quickActionInbox"'
contains src/main/resources/static/index.html 'id="quickActionNote"'
contains src/main/resources/static/index.html 'id="quickActionImportant"'
not_contains src/main/resources/static/index.html 'id="quickCaptureModal"'
contains src/main/resources/static/js/20-data.js 'moduleEnabled("notes") || moduleEnabled("important_dates")'
contains src/main/resources/static/js/50-tasks.js 'async function saveQuickActionInbox()'
contains src/main/resources/static/js/50-tasks.js 'async function quickActionNote()'
contains src/main/resources/static/js/50-tasks.js 'function quickActionImportant()'
contains src/main/resources/static/app.css '.taskInboxTray'
contains src/main/resources/static/app.css '.quickActionCapture'
contains src/test/java/ru/daniil/shifts/web/MobileTasksInboxFrontendContractTest.java 'assertFalse(html.contains("id=\"quickCaptureModal\""))'
contains e2e/task-modules.spec.js "#taskInboxCard > summary"

# v27.7.0 Time Foundation
contains CHANGES.md "v27.7.0 — Time Foundation"
contains README.md "v27.7.0 — Time Foundation"
contains docs/TIME_FOUNDATION_V27.7.0.md "gap / nonexistent time"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"

# v27.7.1 Task and ledger layout hotfix
contains CHANGES.md "v27.7.1 — Task & Ledger Layout Hotfix"
contains README.md "v27.7.1 — Task & Ledger Layout Hotfix"
contains docs/TASK_LEDGER_LAYOUT_HOTFIX_V27.7.1.md "three columns"
contains src/main/resources/static/app.css "v27.7.1: stable task-card grid"
contains src/main/resources/static/index.html "ledgerActionsHead"
contains src/main/resources/static/js/40-overtime.js "ledgerUsageActions"

# v27.8.0 Zoned Work Intervals
contains CHANGES.md "v27.8.0 — Zoned Work Intervals"
contains README.md "v27.8.0 — Zoned Work Intervals"
contains docs/ZONED_WORK_INTERVALS_V27.8.0.md "08:30–17:00 Asia/Yekaterinburg"
contains src/main/resources/db/migration/postgresql/V30__zoned_work_intervals.sql "start_at_instant TIMESTAMPTZ"
contains src/main/resources/db/migration/postgresql/V30__zoned_work_intervals.sql "source_timezone VARCHAR(80)"
not_contains src/main/resources/db/migration/postgresql/V30__zoned_work_intervals.sql "UPDATE overtime_credits"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "record ShiftIntervalDto"
contains src/main/java/ru/daniil/shifts/model/OvertimeCredit.java "private Instant startAtInstant"
contains src/main/java/ru/daniil/shifts/service/WorkIntervalService.java "record ShiftProjection"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "sameCalculatedDefinition"
contains src/main/resources/static/js/30-calendar.js "shiftIntervalRange"
contains src/main/resources/static/js/50-tasks.js "renderShiftProjection"
contains src/main/resources/static/js/40-overtime.js "overtimeCreditDisplayRange"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "savingUnchangedCalculatedCreditDoesNotMoveItsInstantAfterWorkTimezoneChange"
contains src/test/java/ru/daniil/shifts/web/ZonedWorkIntervalsFrontendContractTest.java "selectedDayShowsWorkAndDisplayShiftProjection"
contains e2e/important-timezone.spec.js "existing dated shift keeps its source zone and reprojects after canonical timezone change"
contains src/main/resources/db/migration/postgresql/V29__time_foundation.sql "ADD COLUMN IF NOT EXISTS display_timezone"
contains src/main/resources/db/migration/postgresql/V29__time_foundation.sql "remind_at_instant TIMESTAMPTZ"
contains src/main/resources/db/migration/postgresql/V29__time_foundation.sql "uq_tg_notification_once_instant"
contains src/main/java/ru/daniil/shifts/model/AppUser.java "private String displayTimezone"
contains src/main/java/ru/daniil/shifts/service/UserTimeService.java "resolveLocalDateTime"
contains src/main/java/ru/daniil/shifts/service/UserTimeService.java "offsets.get(0)"
contains src/main/java/ru/daniil/shifts/service/WorkIntervalService.java "record ResolvedWorkInterval"
contains src/main/java/ru/daniil/shifts/web/TimeContextController.java '"/api/v1/time/context"'
contains src/main/java/ru/daniil/shifts/service/NotificationService.java "remindAtInstant"
contains src/main/java/ru/daniil/shifts/telegram/TelegramNotificationService.java "existsByOwnerAndReminderIdAndRemindAtInstant"
contains src/main/java/ru/daniil/shifts/telegram/TelegramNotificationService.java "existsByOwnerAndReminderIdAndRemindAtAndRemindAtInstantIsNull"
contains src/main/resources/static/index.html 'id="displayTimezone"'
contains src/main/resources/static/js/10-core.js "function formatAbsoluteInstant"
contains src/main/resources/static/js/10-core.js "timeZone:displayTimeZone()"
contains src/main/resources/static/js/60-settings.js "displayTimezone:next.workTimezone"
contains src/test/java/ru/daniil/shifts/service/UserTimeServiceTest.java "dstGapMovesForwardAndOverlapUsesEarlierOffset"
contains src/test/java/ru/daniil/shifts/service/WorkIntervalServiceTest.java "daylightSavingChangesActualElapsedDuration"
contains src/test/java/ru/daniil/shifts/web/TimeContextControllerTest.java "legacyAndV1ExposeOneInstantWithCanonicalProjection"
contains src/test/java/ru/daniil/shifts/db/PostgreSqlMigrationContractTest.java "timeFoundationMigrationPreservesUnzonedLegacyDeliveriesWithoutGuessing"
contains e2e/important-timezone.spec.js "canonical timezone survives reload"

# v27.9.4 Overtime Split Projection Contract Hotfix
contains CHANGES.md "v27.9.4 — Overtime Split Projection Contract Hotfix"
contains README.md "v27.9.4 — Overtime Split Projection Contract Hotfix"
contains docs/OVERTIME_SPLIT_PROJECTION_CONTRACT_HOTFIX_V27.9.4.md "Ledger usage references carry stable split-part metadata"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "allocationPartIndex"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "allocationPartCount"
contains src/main/resources/static/js/40-overtime.js "responsePartCount"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "firstPartRef.allocationPartIndex"
contains e2e/overtime-editor-modals.spec.js "selected calendar day owns seven"

# v27.9.3 Overtime Preflight Integrity Hotfix
contains CHANGES.md "v27.9.3 — Overtime Preflight Integrity Hotfix"
contains README.md "v27.9.3 — Overtime Preflight Integrity Hotfix"
contains docs/OVERTIME_PREFLIGHT_INTEGRITY_HOTFIX_V27.9.3.md "Reject over-capacity commands before any managed entity state changes"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "validateUsageCapacity"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "неуспешноеРедактированиеОтгулаНеМеняетСтаруюЗапись"
contains src/test/java/ru/daniil/shifts/web/TaskAndShiftEditorsFrontendContractTest.java "Удалить весь отгул"
contains e2e/overtime-editor-modals.spec.js "await page.locator('#creditBreak').fill('0')"

# v27.9.2 Overtime Ledger Integrity Hotfix
contains CHANGES.md "v27.9.2 — Overtime Ledger Integrity Hotfix"
contains README.md "v27.9.2 — Overtime Ledger Integrity Hotfix"
contains docs/OVERTIME_LEDGER_INTEGRITY_HOTFIX_V27.9.2.md "Build the complete FIFO replacement plan in memory"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "buildAllocationPlan"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "verifyLedgerIntegrity"
contains src/main/resources/static/js/40-overtime.js "safeAllocationRangeLabels"
contains src/main/resources/static/js/40-overtime.js "tbody.replaceChildren(fragment)"
contains src/main/resources/static/js/40-overtime.js "Удалить весь отгул"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "deletingOneSplitUsageKeepsBothCreditsAndTheOtherUsage"
contains src/test/java/ru/daniil/shifts/web/OvertimeLedgerIntegrityFrontendContractTest.java "ledgerRowsAreCommittedAtomicallyAfterTheWholeFragmentIsBuilt"
contains e2e/overtime-editor-modals.spec.js "deleting one canonical split time-off keeps every credit and the other absence usage"

# v27.9.1 Overtime Allocation Rendering Hotfix
contains CHANGES.md "v27.9.1 — Overtime Allocation Rendering Hotfix"
contains docs/OVERTIME_ALLOCATION_RENDERING_HOTFIX_V27.9.1.md "ReferenceError: formatDate is not defined"
contains src/main/resources/static/js/40-overtime.js "formatDateHuman(startDate)"
contains src/main/resources/static/js/40-overtime.js "formatDateHuman(endDate)"
not_contains src/main/resources/static/js/40-overtime.js "formatDate(startDate)"
not_contains src/main/resources/static/js/40-overtime.js "formatDate(endDate)"
contains src/test/java/ru/daniil/shifts/web/OvertimeIntervalEngineFrontendContractTest.java "exactAllocationRangesUseTheExistingHumanDateFormatter"
contains e2e/overtime-editor-modals.spec.js "00:00–01:00"

# v27.9.0 Overtime Interval Engine
contains CHANGES.md "v27.9.0 — Overtime Interval Engine"
contains README.md "v27.9.0 — Overtime Interval Engine"
contains docs/OVERTIME_INTERVAL_ENGINE_V27.9.0.md "Exact FIFO provenance"
contains src/main/resources/db/migration/postgresql/V31__overtime_interval_engine.sql "credited_start_at_instant"
contains src/main/resources/db/migration/postgresql/V31__overtime_interval_engine.sql "allocated_minutes"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "rebuildAllAllocations"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "previewLegacyCredits"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "migrateLegacyCredits"
contains src/main/resources/static/index.html 'id="legacyOvertimeModal"'
contains src/main/resources/static/js/40-overtime.js "allocationRangeLabels"
contains src/main/resources/static/js/40-overtime.js "24:00"
contains src/main/resources/static/js/50-tasks.js "Рабочее время смены"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "exactFifoShowsWhichSourceMinutesWereUsedAndReprojectsAfterTimezoneMove"
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java "legacyMigrationPreviewAndV1MigrateExposeExactReconstructedIntervals"
contains src/test/java/ru/daniil/shifts/web/OvertimeIntervalEngineFrontendContractTest.java "legacyMigrationWizardHasPreviewSelectionAndApplyFlow"

# v27.8.1 Timezone Projection Refresh Hotfix
contains CHANGES.md "v27.8.1 — Timezone Projection Refresh Hotfix"
contains README.md "v27.8.1 — Timezone Projection Refresh Hotfix"
contains docs/TIMEZONE_PROJECTION_REFRESH_V27.8.1.md "authoritative calendar reload"
contains src/main/resources/static/js/20-data.js "const snap = fresh ? null : await this.readSnapshot()"
contains src/main/resources/static/js/60-settings.js "await loadMonth({ fresh:true })"
contains src/main/resources/static/js/70-user-boot.js "await loadProfile();"
contains src/test/java/ru/daniil/shifts/web/TimezoneProjectionRefreshFrontendContractTest.java "profileLoadsBeforeInitialCalendarProjection"
contains e2e/important-timezone.spec.js "existing dated shift keeps its source zone and reprojects after canonical timezone change"

# v27.6.3 task polish and consistency
contains CHANGES.md "v27.6.3 — Polish & Consistency"
contains README.md "v27.6.3 — Polish & Consistency"
contains docs/TASK_POLISH_CONSISTENCY_V27.6.3.md "Validation runs after the complete create/update payload"
contains src/main/resources/db/migration/postgresql/V28__task_subtask_due_date.sql "ADD COLUMN due_date DATE"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "validateBusinessRules"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "TASK_DISPLAY_ORDER"
contains src/main/java/ru/daniil/shifts/model/TaskSubtask.java "LocalDate dueDate"
contains src/main/resources/static/js/50-tasks.js "validateTaskEditorDeadlines"
contains src/main/resources/static/js/50-tasks.js "sortedTasksOpenFirst"
contains src/main/resources/static/js/50-tasks.js 'setAttribute("role", "progressbar")'
contains src/main/resources/static/app.css ".taskCompletionDivider"
contains src/main/resources/static/app.css ".taskSubtaskProgressTrack"
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java "deadlinesValidateTheFinalTaskStateAndAllowTheSameDay"
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java "deadlineRulesAreEnforcedAcrossLegacyAndV1Endpoints"
contains e2e/task-modules.spec.js "task polish validates deadlines, persists subtask dates and keeps completed tasks below open tasks"

# v27.6.2 tasks and subtasks
contains CHANGES.md "v27.6.2 — Tasks & Subtasks"
contains README.md "v27.6.2 — Tasks & Subtasks"
contains docs/TASK_SUBTASKS_V27.6.2.md "Subtasks are intentionally not recursive in v27.6.2"
contains src/main/resources/db/migration/postgresql/V27__task_subtasks.sql "CREATE TABLE task_subtasks"
contains src/main/resources/db/migration/postgresql/V27__task_subtasks.sql "ON DELETE CASCADE"
contains src/main/java/ru/daniil/shifts/model/TaskSubtask.java "class TaskSubtask"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "List<SubtaskDto> subtasks"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "updateSubtask"
contains src/main/java/ru/daniil/shifts/web/TaskController.java 'subtasks/{subtaskId}'
contains src/main/resources/static/index.html 'id="taskEditSubtaskList"'
contains src/main/resources/static/index.html 'id="taskEditSubtaskAdd"'
contains src/main/resources/static/js/20-data.js 'async updateSubtask(taskId, subtaskId, b)'
contains src/main/resources/static/js/50-tasks.js 'function taskSubtaskProgress'
contains src/main/resources/static/js/50-tasks.js 'async function toggleSubtask'
contains src/main/resources/static/app.css '.taskSubtaskProgress'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java 'subtasksPersistInUserOrderCanBeReconciledAndParticipateInSearch'
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java 'subtasksAreReturnedInOrderAndHaveAnOwnerScopedToggleEndpoint'
contains src/test/java/ru/daniil/shifts/web/TaskAndShiftEditorsFrontendContractTest.java 'subtasksStayInsideTaskEditorAndUseCompactInlineProgress'
contains e2e/task-modules.spec.js 'task subtasks keep order, update progress and require explicit parent completion'

# v27.10.0 Task Details
contains CHANGES.md "v27.10.0 — Task Details"
contains README.md "v27.10.0 — Task Details"
contains docs/TASK_DETAILS_V27.10.0.md "read-first"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/resources/db/migration/postgresql/V32__task_details.sql "ADD COLUMN description"
contains src/main/java/ru/daniil/shifts/model/DayTask.java "private String description"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "public TaskDto get(AppUser user, Long id)"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "cleanDescription"
contains src/main/java/ru/daniil/shifts/web/TaskController.java '@GetMapping("/{id}")'
contains src/main/resources/static/index.html 'id="taskDetailsModal"'
contains src/main/resources/static/index.html 'id="taskEditDescription"'
contains src/main/resources/static/js/20-data.js 'async task(id)'
contains src/main/resources/static/js/50-tasks.js 'async function openTaskDetails'
contains src/main/resources/static/js/50-tasks.js 'function renderTaskDetails'
contains src/main/resources/static/app.css '.taskDetailsPanel'
contains src/test/java/ru/daniil/shifts/service/TaskServiceTest.java 'taskDetailsPersistSearchAndClearAnOptionalDescription'
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java 'detailsEndpointPersistsDescriptionAcrossLegacyAndV1Aliases'
contains src/test/java/ru/daniil/shifts/web/TaskDetailsFrontendContractTest.java 'readFirstDetailsModalIsSeparateFromTheEditor'
contains e2e/task-details.spec.js 'task details separate reading from editing and persist a long description'

# v27.11.1 Shift Occurrences & Calendar Projection
contains CHANGES.md "v27.11.0 — Shift Occurrences & Calendar Projection"
contains README.md "v27.11.0 — Shift Occurrences & Calendar Projection"
contains docs/SHIFT_OCCURRENCES_CALENDAR_PROJECTION_V27.11.0.md "immutable absolute occurrence"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/resources/db/migration/postgresql/V33__shift_occurrences.sql "shift_start_instant"
contains src/main/resources/db/migration/postgresql/V33__shift_occurrences.sql "shift_source_timezone"
contains src/main/java/ru/daniil/shifts/model/DayEntry.java "captureShiftOccurrence"
contains src/main/java/ru/daniil/shifts/service/ShiftOccurrenceService.java "captureLegacyBeforeTimezoneChange"
contains src/main/java/ru/daniil/shifts/service/ShiftOccurrenceService.java "listForDisplayRange"
contains src/main/java/ru/daniil/shifts/web/ShiftOccurrenceController.java '/api/shifts/legacy-migration'
contains src/main/resources/static/js/30-calendar.js "function occurrenceSegments"
contains src/main/resources/static/js/30-calendar.js "shiftSegmentsByDate"
contains src/main/resources/static/js/60-settings.js "refreshLegacyShiftIndicator"
contains src/main/resources/static/service-worker.js '.then(() => self.clients.claim())'
contains src/main/resources/static/service-worker.js 'SKIP_WAITING'
contains src/test/java/ru/daniil/shifts/service/ShiftOccurrenceServiceTest.java "datedShiftKeepsItsAbsoluteIdentityAndReprojectsAfterTimezoneMove"
contains src/test/java/ru/daniil/shifts/web/ShiftOccurrenceFrontendContractTest.java "calendarUsesProjectedOccurrenceDatesInsteadOfReinterpretingTheTemplate"
contains e2e/important-timezone.spec.js "a timezone projection can move a late shift to the next calendar date"

# v27.5.0 backup and recovery hardening
contains CHANGES.md "v27.5.0 — Backup and recovery hardening"
contains README.md "v27.5.0 — Backup and recovery hardening"
contains docs/BACKUP_RESTORE_OPERATIONS_V27.5.0.md "RESTORE DRILL PASSED"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains deploy/scripts/backup-postgres.sh 'DUTYLOG_COMPOSE_FILE:-deploy/compose/docker-compose.deploy.yml'
not_contains deploy/scripts/backup-postgres.sh 'DUTYLOG_COMPOSE_FILE:-docker-compose.prod.yml'
contains deploy/scripts/backup-postgres.sh 'flock -n 9'
contains deploy/scripts/check-backup-freshness.sh 'BACKUP_HEALTHY'
contains deploy/scripts/check-backup-freshness.sh 'BACKUP_MAX_AGE_HOURS'
contains deploy/scripts/restore-postgres.sh 'DUTYLOG_RESTORE_REQUIRE_CHECKSUM'
contains deploy/scripts/restore-postgres.sh 'restart_app_on_exit'
contains deploy/scripts/restore-postgres.sh 'compose ps --status running -q'
contains deploy/scripts/restore-drill.sh '--network none'
contains deploy/scripts/restore-drill.sh "CASE WHEN success THEN 'success' ELSE 'failed' END"
contains deploy/scripts/restore-drill.sh '[[ "$FLYWAY_STATE" == *"|success" ]]'
not_contains deploy/scripts/restore-drill.sh '[[ "$FLYWAY_STATE" == *"|t" ]]'
contains deploy/scripts/restore-drill.sh 'RESTORE DRILL PASSED'
contains deploy/scripts/restore-drill.sh 'docker volume rm "$DRILL_VOLUME"'
contains deploy/scripts/install-backup-timer.sh 'ExecStartPost=/usr/bin/bash $CHECK_SCRIPT'
contains deploy/systemd/dutylog-backup.timer.example 'Persistent=true'
contains deploy/env/.env.staging.example 'BACKUP_MAX_AGE_HOURS=30'
contains deploy/env/.env.production.cicd.example 'DUTYLOG_RESTORE_REQUIRE_CHECKSUM=true'
if bash deploy/scripts/backup-tooling-self-test.sh >/dev/null; then
  ok "backup tooling self-test"
else
  fail "backup tooling self-test failed"
fi

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

if GITHUB_OUTPUT="$CI_GATE_TMP/missing.out" \
GITHUB_STEP_SUMMARY="$CI_GATE_TMP/missing.md" \
DUTYLOG_DEPLOY_ENABLED=true \
DUTYLOG_DEPLOY_ENVIRONMENT=staging \
  bash deploy/scripts/check-ci-deploy-config.sh >/dev/null 2>&1; then
  fail "enabled deployment with missing environment values unexpectedly passed"
else
  if grep -q 'Deployment configuration is incomplete' "$CI_GATE_TMP/missing.md"; then
    ok "enabled deployment fails closed without polluting the real GitHub job summary"
  else
    fail "missing deploy configuration did not write its isolated diagnostic summary"
  fi
fi

if grep -Il $'\r' deploy/scripts/*.sh >/dev/null 2>&1; then
  fail "deployment shell scripts contain CRLF line endings"
else
  ok "deployment shell scripts use LF line endings"
fi

# v27.25.2 Absence Experience Frontend Contract Hotfix
contains CHANGES.md "v27.25.2 — Absence Experience Frontend Contract Hotfix"
contains README.md "v27.25.2 — Absence Experience Frontend Contract Hotfix"
contains docs/ABSENCE_EXPERIENCE_FRONTEND_CONTRACT_HOTFIX_V27.25.2.md "stale string contract"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.absences.slice(0, 3))'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.partialAbsences)'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.absences.filter(item => item.coverage !== \"PARTIAL\"))'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'absenceExperienceKeepsWeekAgendaBoundedAndDayModesSeparated'
not_contains src/main/resources/static/js/37-calendar-experience.js 'for (const absence of facts.absences)'

# v27.25.1 Absence Preview Lambda Compile Hotfix
contains CHANGES.md "v27.25.1 — Absence Preview Lambda Compile Hotfix"
contains README.md "v27.25.1 — Absence Preview Lambda Compile Hotfix"
contains docs/ABSENCE_PREVIEW_LAMBDA_COMPILE_HOTFIX_V27.25.1.md "effectively-final"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "LocalDate previewDate = date;"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "filter(period -> covers(period, previewDate))"
not_contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "filter(period -> covers(period, date))"
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java "previewLoopSnapshotsItsMutableDateBeforeTheOverlapLambda"
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java "assertFalse(service.contains(\"filter(period -> covers(period, date))\"))"

# v27.25.0 Absence & Time-Off Overhaul
contains CHANGES.md "v27.25.0 — Absence & Time-Off Overhaul"
contains README.md "v27.25.0 — Absence & Time-Off Overhaul"
contains docs/ABSENCE_TIME_OFF_OVERHAUL_V27.25.0.md "planned shift from the work schedule"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql "time_off_balance_minutes"
contains src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql "TIME_OFF_HOURS"
contains src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql "coverage = 'PARTIAL'"
not_contains src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql "ALTER TABLE day_entries"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java 'TIME_OFF_HOURS = "TIME_OFF_HOURS"'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java 'OVERTIME_BALANCE_EXCEEDED'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java 'addDefaultType(user, "TIME_OFF"'
contains src/main/resources/static/js/30-calendar.js "factualAbsence"
contains src/main/resources/static/js/30-calendar.js 'actual.className = "absenceFact"'
contains src/main/resources/static/js/30-calendar.js 'partial.className = "partialAbsenceBar"'
contains src/main/resources/static/js/30-calendar.js 'planned.className = "plannedShiftGhost"'
contains src/main/resources/static/js/39-vacation-planner.js 'TIME_OFF_LIMIT_EXCEEDED'
contains src/main/resources/static/js/39-vacation-planner.js 'coverage:draft.coverage'
not_contains src/main/resources/static/js/39-vacation-planner.js 'shiftTypeId:'
contains src/main/resources/static/openapi/dutylog-v1.yaml 'TIME_OFF_HOURS'
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java 'class AbsenceTimeOffOverhaulContractTest'
contains e2e/absence-time-off-overhaul.spec.js 'partial time off keeps the planned shift'


# v27.26.0 Unified Time & Compensation Ledger
contains CHANGES.md "v27.26.0 — Unified Time & Compensation Ledger"
contains README.md "v27.26.0 — Unified Time & Compensation Ledger"
contains docs/UNIFIED_TIME_COMPENSATION_LEDGER_V27.26.0.md "planned shift / day off"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/API.md "# DutyLog API v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "compensation_policy"
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "source_absence_id"
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "Начальный баланс отгулов — перенос в единый банк V43"
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "UPDATE vacation_settings SET time_off_balance_minutes = 0"
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "ON DELETE CASCADE"
not_contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "ALTER TABLE day_entries"
contains src/main/java/ru/daniil/shifts/model/OvertimeUsage.java 'private String sourceKind = "MANUAL"'
contains src/main/java/ru/daniil/shifts/model/OvertimeUsage.java "private Long sourceAbsenceId"
contains src/main/java/ru/daniil/shifts/model/AbsencePeriod.java "private String compensationPolicy"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "upsertLinkedAbsenceUsage"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "deleteLinkedAbsenceUsage"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "LINKED_USAGE_MANAGED_BY_ABSENCE"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java 'usage.setSourceKind("ABSENCE")'
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "private void ensureAllocationConsistency"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "OVERTIME_BALANCE_EXCEEDED"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "syncLinkedOvertimeUsage"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "the canonical compensatory balance lives in the overtime ledger"
not_contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "settings.setTimeOffBalanceMinutes(hoursToMinutes(req.timeOffBalanceHours()))"
contains src/main/java/ru/daniil/shifts/service/TimeCompensationService.java "class TimeCompensationService"
contains src/main/java/ru/daniil/shifts/web/TimeCompensationController.java '@RequestMapping({"/api/time-compensation", "/api/v1/time-compensation"})'
contains src/main/java/ru/daniil/shifts/web/TimeCompensationController.java "CacheControl.noStore()"
contains src/main/resources/static/index.html 'id="vacationCompensation"'
contains src/main/resources/static/index.html 'id="timeCompensationCard"'
contains src/main/resources/static/js/20-data.js "/api/time-compensation"
contains src/main/resources/static/js/39-vacation-planner.js "compensationPolicy:"
contains src/main/resources/static/js/39-vacation-planner.js "syncVacationCompensation({ preserve:true });"
not_contains src/main/resources/static/js/39-vacation-planner.js "timeOffBalanceHours:Number"
contains src/main/resources/static/js/40-overtime.js "usageManagedByAbsence"
contains src/main/resources/static/js/40-overtime.js 'selectDay(button.dataset.timeCompDate)'
not_contains src/main/resources/static/js/40-overtime.js "renderSelected();"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/time-compensation:"
contains src/main/resources/static/openapi/dutylog-v1.yaml "TimeCompensationSummary:"
contains src/test/java/ru/daniil/shifts/web/UnifiedTimeCompensationLedgerContractTest.java "class UnifiedTimeCompensationLedgerContractTest"
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java "overtimeCoveredAbsenceCreatesLockedUsageAndDeleteRestoresFifoBalance"
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java "unifiedMonthlyReadModelJoinsPlanFactOvertimeAndUnpaidTime"
contains src/test/java/ru/daniil/shifts/web/VacationPlannerControllerTest.java "unifiedTimeCompensationEndpointIsNoStoreAndAvailableUnderV1"
contains e2e/unified-time-compensation-ledger.spec.js "absence compensation is linked to FIFO overtime and monthly plan-fact summary"

# v27.26.2 Canonical Lineage Recovery
contains CHANGES.md "v27.26.2 — Canonical Lineage Recovery"
contains README.md "v27.26.2 — Canonical Lineage Recovery"
contains docs/CANONICAL_LINEAGE_RECOVERY_V27.26.2.md "forward-only recovery release"
contains docs/ROADMAP.md "v27.26.2 — Canonical Lineage Recovery — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.26.2 Canonical Lineage Recovery extension"
contains src/test/java/ru/daniil/shifts/web/UnifiedTimeCompensationLedgerContractTest.java "canonicalLineageRecoveryKeepsV41ThroughV43AndWorkspaceRoute"
contains src/main/resources/db/migration/postgresql/V41__calendar_feed_subscriptions.sql "CREATE TABLE calendar_feed_subscriptions"
contains src/main/resources/db/migration/postgresql/V42__absence_time_off_overhaul.sql "balance_policy"
contains src/main/resources/db/migration/postgresql/V43__unified_time_compensation_ledger.sql "source_absence_id"
contains src/main/resources/static/js/55-calendar-sync.js "from:keyOf(start.getFullYear(), start.getMonth(), start.getDate())"
not_contains src/main/resources/static/js/55-calendar-sync.js "localDateKey("
contains e2e/calendar-comfort.spec.js "await page.locator('#pClose').click();"
not_contains e2e/calendar-comfort.spec.js "force: true"
contains e2e/helpers.js "tasks: '[data-vue-domain-route=\"tasks\"]',"
contains e2e/task-modules.spec.js "page.locator('[data-vue-domain-route=\"tasks\"]')).toBeVisible()"
contains src/main/java/ru/daniil/shifts/service/TimeCompensationService.java "class TimeCompensationService"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "LINKED_USAGE_MANAGED_BY_ABSENCE"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "syncLinkedOvertimeUsage"

for migration in \
  V41__calendar_feed_subscriptions.sql \
  V42__absence_time_off_overhaul.sql \
  V43__unified_time_compensation_ledger.sql; do
  count=$(find src/main/resources/db/migration/postgresql -maxdepth 1 -type f -name "$migration" | wc -l | tr -d ' ')
  if [[ "$count" == "1" ]]; then
    ok "canonical lineage has exactly one $migration"
  else
    fail "expected exactly one $migration, found $count"
  fi
done

# v27.27.0 Ledger Integrity & Approval Workflow
contains CHANGES.md "v27.27.0 — Ledger Integrity & Approval Workflow"
contains README.md "v27.27.0 — Ledger Integrity & Approval Workflow"
contains docs/LEDGER_INTEGRITY_APPROVAL_WORKFLOW_V27.27.0.md "append-only"
contains docs/ROADMAP.md "v27.27.0 — Ledger Integrity & Approval Workflow — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.27.0 Ledger Integrity & Approval Workflow extension"
contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "CREATE TABLE time_ledger_entries"
contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "CREATE TABLE time_accounting_periods"
contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "CREATE TABLE actual_work_intervals"
contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "reversal_of_id"
not_contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "ALTER TABLE day_entries"
not_contains src/main/resources/db/migration/postgresql/V44__ledger_integrity_approval_workflow.sql "DROP TABLE"
contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java "RESERVED_STATUSES"
contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java "POSTED_STATUSES"
contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java "ABSENCE_REVERSAL"
contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java "LEDGER_INTEGRITY_FAILED"
contains src/main/java/ru/daniil/shifts/service/ActualWorkService.java "ACTUAL_WORK_OVERLAP"
contains src/main/java/ru/daniil/shifts/service/ActualWorkService.java "assertRangeOpen"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "assertPeriodOpen(user"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "PERIOD_CLOSED"
contains src/main/java/ru/daniil/shifts/service/AccountingPeriodLockService.java "class AccountingPeriodLockService"
contains src/main/java/ru/daniil/shifts/service/AccountingPeriodLockService.java "PERIOD_CLOSED"
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java "periodLocks.assertOpen(user, d)"
contains src/main/java/ru/daniil/shifts/service/ScheduleTemplateService.java "periodLocks.assertOpen(user, date)"
contains src/main/java/ru/daniil/shifts/service/ShiftTypeService.java "assignedEntries.forEach(entry -> periodLocks.assertOpen"
contains src/main/java/ru/daniil/shifts/web/LedgerIntegrityController.java '/api/v1/ledger-integrity'
contains src/main/java/ru/daniil/shifts/web/ActualWorkController.java '/api/v1/actual-work'
contains src/main/resources/static/index.html 'id="ledgerIntegrityCard"'
contains src/main/resources/static/index.html 'value="SUBMITTED"'
contains src/main/resources/static/js/39-vacation-planner.js "absencePostingLabel"
contains src/main/resources/static/js/40-overtime.js "loadLedgerIntegrity"
contains src/main/resources/static/js/40-overtime.js "saveActualWork"
not_contains src/main/resources/static/js/40-overtime.js "force: true"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/ledger-integrity:"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/actual-work:"
contains src/test/java/ru/daniil/shifts/service/LedgerIntegrityApprovalWorkflowServiceTest.java "draftReservePostAndCancelKeepOneReversibleOvertimeSource"
contains src/test/java/ru/daniil/shifts/web/LedgerIntegrityApprovalWorkflowContractTest.java "v44AddsWorkflowAuditPeriodsAndActualWorkWithoutTouchingDayEntries"
contains e2e/ledger-integrity-approval-workflow.spec.js "approval workflow reserves posts reverses and locks a closed accounting period"

for migration in V44__ledger_integrity_approval_workflow.sql; do
  count=$(find src/main/resources/db/migration/postgresql -maxdepth 1 -type f -name "$migration" | wc -l | tr -d ' ')
  if [[ "$count" == "1" ]]; then
    ok "approval workflow has exactly one $migration"
  else
    fail "expected exactly one $migration, found $count"
  fi
done

# v27.27.1 Ledger Workflow Browser Contract Hotfix
contains CHANGES.md "v27.27.1 — Ledger Workflow Browser Contract Hotfix"
contains README.md "v27.27.1 — Ledger Workflow Browser Contract Hotfix"
contains docs/LEDGER_WORKFLOW_BROWSER_CONTRACT_HOTFIX_V27.27.1.md "serialized"
contains docs/ROADMAP.md "v27.27.1 — Ledger Workflow Browser Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.27.1 Ledger Workflow Browser Contract Hotfix extension"
contains src/main/resources/static/js/40-overtime.js "function refreshLedgerReadModels()"
contains src/main/resources/static/js/40-overtime.js "let ledgerReadModelRefreshChain = Promise.resolve()"
contains src/main/resources/static/js/40-overtime.js "await loadLedgerIntegrity();"
not_contains src/main/resources/static/js/40-overtime.js "Promise.all([loadTimeCompensation(), loadLedgerIntegrity(), loadActualWork()])"
not_contains src/main/resources/static/js/40-overtime.js "Promise.all([loadLedgerIntegrity(), loadActualWork(), loadTimeCompensation()])"
contains src/main/resources/static/js/70-user-boot.js "window.__dutylogLedgerRouteReady = Promise.resolve(loadLedgerPage(true))"
contains e2e/helpers.js "async function waitForAppIdle(page)"
contains e2e/helpers.js "async function waitForLedgerReady(page)"
contains e2e/helpers.js "if (view === 'overtime') await waitForLedgerReady(page)"
contains e2e/fixtures.js "x-dutylog-e2e-expected-status"
contains e2e/ledger-integrity-approval-workflow.spec.js "X-DutyLog-E2E-Expected-Status"
contains e2e/ledger-integrity-approval-workflow.spec.js "409);"
not_contains e2e/important-timezone.spec.js "2026-07-03"
contains e2e/important-timezone.spec.js 'sourceDisplay:`03.${month}`'
contains e2e/absence-time-off-overhaul.spec.js "#vacationStatus').selectOption('APPROVED')"
contains e2e/unified-time-compensation-ledger.spec.js "#vacationStatus').selectOption('APPROVED')"
contains e2e/design-system-shell.spec.js "waitForAppIdle(page)"
contains e2e/overtime-next.spec.js "waitForLedgerReady(page)"
contains src/test/java/ru/daniil/shifts/web/LedgerWorkflowBrowserContractHotfixTest.java "browserContractsRefreshSerializeAndMarkExpectedFailures"

# v27.27.2 Ledger Browser State & Visibility Hotfix
contains CHANGES.md "v27.27.2 — Ledger Browser State & Visibility Hotfix"
contains README.md "v27.27.2 — Ledger Browser State & Visibility Hotfix"
contains docs/LEDGER_BROWSER_STATE_VISIBILITY_HOTFIX_V27.27.2.md "one-use"
contains docs/ROADMAP.md "v27.27.2 — Ledger Browser State & Visibility Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.27.2 Ledger Browser State & Visibility Hotfix extension"
contains src/main/resources/static/js/39-vacation-planner.js "window.__dutylogVacationReady = Promise.resolve()"
contains src/main/resources/static/js/39-vacation-planner.js "function openVacationPlannerView(force = false)"
contains src/main/resources/static/js/70-user-boot.js "openVacationPlannerView(true)"
contains e2e/helpers.js "async function waitForVacationReady(page)"
contains e2e/helpers.js "if (view === 'vacation') await waitForVacationReady(page)"
contains src/main/resources/static/js/60-settings.js "state.ui.savingTimeSettings = true"
contains src/main/resources/static/js/60-settings.js "window.__dutylogTimeSettingsSaveReady = Promise.resolve()"
contains src/main/resources/static/js/60-settings.js 'addEventListener("click", runTimeSettingsSave)'
contains e2e/helpers.js "!ui.savingTimeSettings"
contains e2e/important-timezone.spec.js "window.__dutylogTimeSettingsSaveReady"
contains e2e/fixtures.js "expectedStatusConsoleBudget"
contains e2e/fixtures.js "consumeExpectedStatusConsole"
not_contains e2e/overtime-next.spec.js "plusDays(today, -2)"
contains e2e/overtime-next.spec.js 'usageDate:`${prefix}-03`'
contains e2e/unified-time-compensation-ledger.spec.js "#ledgerUsageList .timeBankUsageCard"
contains src/test/java/ru/daniil/shifts/web/LedgerBrowserStateVisibilityHotfixTest.java "browserRoutesExposeFreshReadinessAndResponsiveSelectors"

# v27.28.0 Payroll Foundation
contains CHANGES.md "v27.28.0 — Payroll Foundation"
contains README.md "v27.28.0 — Payroll Foundation"
contains docs/PAYROLL_FOUNDATION_V27.28.0.md "immutable versioned payroll snapshots"
contains docs/ROADMAP.md "v27.28.0 — Payroll Foundation — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.28.0 Payroll Foundation extension"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "CREATE TABLE payroll_settings"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "CREATE TABLE payroll_adjustments"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "CREATE TABLE payroll_snapshots"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "paid_absence_minutes"
not_contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "DROP TABLE"
contains src/main/java/ru/daniil/shifts/service/TimeCompensationService.java "Canonical posted-only source for money calculation"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "requireClosedPeriod(user, month, true)"
contains src/main/java/ru/daniil/shifts/service/CompensationCalculationService.java "RoundingMode.HALF_UP"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "previous.supersedeWith(created)"
contains src/main/java/ru/daniil/shifts/web/PayrollController.java '"/api/v1/payroll"'
contains src/main/java/ru/daniil/shifts/web/PayrollController.java "CacheControl.noStore()"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'id="view-payroll"'
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'id="payrollCalculate"'
contains frontend/src/features/payroll/api/payrollApi.ts 'client.request("payrollPeriod"'
not_contains src/main/resources/static/index.html 'js/45-payroll.js'
not_contains src/main/resources/static/js/70-user-boot.js 'openPayrollView(true)'
contains e2e/helpers.js "async function waitForPayrollReady(page)"
contains e2e/helpers.js "if (view === 'payroll') await waitForPayrollReady(page)"
contains e2e/payroll-foundation.spec.js "Payroll Foundation calculates a versioned closed-month snapshot"
contains src/test/java/ru/daniil/shifts/service/PayrollFoundationServiceTest.java "closedHealthyPeriodCreatesImmutableVersionedMoneySnapshots"
contains src/test/java/ru/daniil/shifts/web/PayrollFoundationContractTest.java "payrollFoundationKeepsClosedPeriodMoneyAndUiContracts"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/payroll/periods/{month}:"
contains docs/API.md "## Payroll Foundation"
contains docs/MODULE_CONTRACTS.md "## Payroll module"
contains docs/SECURITY_REVIEW.md "## Payroll Foundation boundaries"

if [[ "$(find src/main/resources/db/migration/postgresql -maxdepth 1 -type f -name 'V45__payroll_foundation.sql' | wc -l | tr -d ' ')" == "1" ]]; then
  ok "V45 payroll migration appears exactly once"
else
  fail "V45 payroll migration must appear exactly once"
fi

# v27.26.1 Absence Request Constructor Compile Hotfix
contains CHANGES.md "v27.26.1 — Absence Request Constructor Compile Hotfix"
contains README.md "v27.26.1 — Absence Request Constructor Compile Hotfix"
contains docs/ABSENCE_REQUEST_CONSTRUCTOR_COMPILE_HOTFIX_V27.26.1.md "nine-argument"
contains src/test/java/ru/daniil/shifts/web/UnifiedTimeCompensationLedgerContractTest.java "serviceFixturesUseTheCompensationAwareAbsenceCreateConstructor"
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java '"PARTIAL", "09:00", "13:00", "OVERTIME_BANK"'
contains src/test/java/ru/daniil/shifts/service/VacationPlannerServiceTest.java '"FULL_DAY", null, null, "OVERTIME_BANK"'
contains docs/ROADMAP.md "v27.27.0 — Ledger Integrity & Approval Workflow"
contains README.md "v27.27.0 — Ledger Integrity & Approval Workflow"

if python3 - <<'PY_CONSTRUCTOR_GUARD'
from pathlib import Path

needle = "new AbsencePeriodCreateRequest("
bad = []
for path in Path("src/test/java").rglob("*.java"):
    source = path.read_text(encoding="utf-8")
    index = 0
    while True:
        start = source.find(needle, index)
        if start < 0:
            break
        open_paren = source.find("(", start)
        depth = 0
        in_string = False
        escaped = False
        commas = 0
        pos = open_paren
        while pos < len(source):
            char = source[pos]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
            else:
                if char == '"':
                    in_string = True
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                    if depth == 0:
                        break
                elif char == "," and depth == 1:
                    commas += 1
            pos += 1
        arguments = commas + 1
        if arguments == 9:
            bad.append(f"{path}:{source.count(chr(10), 0, start) + 1}")
        index = pos + 1
if bad:
    raise SystemExit("nine-argument AbsencePeriodCreateRequest calls: " + ", ".join(bad))
PY_CONSTRUCTOR_GUARD
then
  ok "absence request constructor arity guard"
else
  fail "stale nine-argument AbsencePeriodCreateRequest fixture"
fi

LOCKFILE_MANIFEST_SHA=$(awk -F= '/^committedLockfileSha256=/{print $2}' frontend/generated-lockfile-manifest.txt)
LOCKFILE_ACTUAL_SHA=$(sha256sum frontend/package-lock.json | awk '{print $1}')
if [[ "$LOCKFILE_MANIFEST_SHA" == "$LOCKFILE_ACTUAL_SHA" ]]; then
  ok "frontend lockfile manifest SHA-256 matches committed lockfile"
else
  fail "frontend lockfile manifest SHA-256 mismatch: manifest=$LOCKFILE_MANIFEST_SHA actual=$LOCKFILE_ACTUAL_SHA"
fi

E2E_TESTS=$(grep -R --include='*.spec.js' -h -E '^[[:space:]]*test\(' e2e | wc -l | tr -d ' ')
if [[ "$E2E_TESTS" == "52" ]]; then
  # v27.11.1 CI & Contract Hotfix
contains CHANGES.md "v27.11.1 — CI & Contract Hotfix"
contains README.md "v27.11.1 — CI & Contract Hotfix"
contains docs/SHIFT_OCCURRENCES_CI_CONTRACT_HOTFIX_V27.11.1.md "CI & Contract Hotfix"
contains src/test/java/ru/daniil/shifts/web/TaskControllerTest.java "objectMapper.writeValueAsString"
contains src/test/java/ru/daniil/shifts/service/ShiftOccurrenceServiceTest.java "assertNotNull(firstId)"

  # v27.11.2 E2E Stability Hotfix
contains CHANGES.md "v27.11.2 — E2E Stability Hotfix"
contains README.md "v27.11.2 — E2E Stability Hotfix"
contains docs/SHIFT_OCCURRENCES_E2E_STABILITY_HOTFIX_V27.11.2.md "E2E Stability Hotfix"
contains e2e/editor-modals.spec.js "const calendarReloaded = waitForApi(page, 'GET', '/api/v1/calendar')"
contains e2e/important-timezone.spec.js 'sourceDisplay:`03.${month}`'
contains e2e/important-timezone.spec.js "dates.source"

  # v27.11.3 Shift Template & Reminder Timezone Hotfix
contains CHANGES.md "v27.11.3 — Shift Template & Reminder Timezone Hotfix"
contains README.md "v27.11.3 — Shift Template & Reminder Timezone Hotfix"
contains docs/SHIFT_TEMPLATE_REMINDER_TIMEZONE_HOTFIX_V27.11.3.md "Shift Template & Reminder Timezone Hotfix"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java "workTimezoneChangeService.upsertAndReconcile"
contains src/main/java/ru/daniil/shifts/service/WorkTimezoneChangeService.java "shiftTypeService.rebaseForTimezoneChange"
contains src/main/java/ru/daniil/shifts/service/NotificationService.java "d.hasShiftOccurrenceSnapshot()"
contains src/main/resources/static/js/60-settings.js "function syncTimeSettingsFromBuiltins"
contains e2e/important-timezone.spec.js "Начало 06:30 Europe/Kyiv"

  # v27.11.4 Task Deadline & Reminder Timezone Hotfix
contains CHANGES.md "v27.11.4 — Task Deadline & Reminder Timezone Hotfix"
contains README.md "v27.11.4 — Task Deadline & Reminder Timezone Hotfix"
contains docs/TASK_DEADLINE_REMINDER_TIMEZONE_HOTFIX_V27.11.4.md "Task Deadline & Reminder Timezone Hotfix"
contains src/main/resources/db/migration/postgresql/V34__task_deadline_instants.sql "due_instant"
contains src/main/java/ru/daniil/shifts/model/DayTask.java "private Instant dueInstant"
contains src/main/java/ru/daniil/shifts/service/TaskService.java "rebaseForTimezoneChange"
contains src/main/java/ru/daniil/shifts/service/NotificationService.java "task.getDueInstant().minusSeconds"
contains src/main/java/ru/daniil/shifts/telegram/TelegramNotificationService.java "parseInstant(reminder.remindAtInstant())"
contains src/main/resources/static/js/60-settings.js "openLegacyTaskDeadlineMigration"
contains src/main/resources/static/js/50-tasks.js "Исходный срок"
contains e2e/task-details.spec.js "deadlineAbsolute"
contains src/test/java/ru/daniil/shifts/web/TaskDeadlineTimezoneFrontendContractTest.java "class TaskDeadlineTimezoneFrontendContractTest"

  # v27.12.0 Zoned Daily Projection Engine
contains CHANGES.md "v27.12.0 — Zoned Daily Projection Engine"
contains README.md "v27.12.0 — Zoned Daily Projection Engine"
contains docs/ZONED_DAILY_PROJECTION_ENGINE_V27.12.0.md "Zoned Daily Projection Engine"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "record OvertimeDailyProjectionDto"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "splitByLocalDay"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "sourceUsedMinutes"
contains src/main/resources/static/js/40-overtime.js "function overtimeDaySummaryHtml(credit)"
contains src/main/resources/static/js/40-overtime.js "projection.sourceUsedHours <= 0.0001"
contains src/main/resources/static/openapi/dutylog-v1.yaml "OvertimeDailyProjection:"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "dailyProjectionRedistributesExactMinutesWithoutMovingFifo"
contains src/test/java/ru/daniil/shifts/web/OvertimeDailyProjectionFrontendContractTest.java "class OvertimeDailyProjectionFrontendContractTest"
contains e2e/overtime-daily-projection.spec.js "overtime and FIFO are redistributed by current timezone day"

  # v27.12.1 Midnight Projection Contract Hotfix
contains CHANGES.md "v27.12.1 — Midnight Projection Contract Hotfix"
contains README.md "v27.12.1 — Midnight Projection Contract Hotfix"
contains docs/MIDNIGHT_PROJECTION_CONTRACT_HOTFIX_V27.12.1.md "Midnight Projection Contract Hotfix"
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java "ровныеСуткиХранятсяПополамНоПроецируютсяПоКалендарнымДням"

  # v27.13.0 Temporal Consistency & Legacy Cleanup
contains CHANGES.md "v27.13.0 — Temporal Consistency & Legacy Cleanup"
contains README.md "v27.13.0 — Temporal Consistency & Legacy Cleanup"
contains docs/TEMPORAL_CONSISTENCY_LEGACY_CLEANUP_V27.13.0.md "Temporal Consistency & Legacy Cleanup"
contains src/main/resources/db/migration/postgresql/V35__quick_scenario_day_offsets.sql "end_day_offset"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "previewCredit"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "projectedRowsInRange"
contains src/main/java/ru/daniil/shifts/service/QuickScenarioService.java "rebaseForTimezoneChange"
contains src/main/resources/static/js/10-core.js "function overtimeRangeTotals(from, to)"
contains src/main/resources/static/js/30-calendar.js "overtimeRangeTotals(monthStart, monthEnd)"
not_contains src/main/resources/static/js/30-calendar.js "legacyBal"
contains src/main/resources/static/js/40-overtime.js "runCanonicalOvertimePreview"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/overtime/preview:"
contains src/test/java/ru/daniil/shifts/web/TemporalConsistencyFrontendContractTest.java "class TemporalConsistencyFrontendContractTest"
contains src/test/java/ru/daniil/shifts/service/QuickScenarioServiceTest.java "fixedTimeScenarioRebasesAcrossExtremeZonesAndRoundTrips"
contains src/test/java/ru/daniil/shifts/web/ProfileControllerTest.java "projectedScenario.endFixedTime"
contains src/main/resources/static/index.html 'value="-2"'

  # v27.14.0 Multiple Daily Notes
contains CHANGES.md "v27.14.0 — Multiple Daily Notes"
contains README.md "v27.14.0 — Multiple Daily Notes"
contains docs/MULTIPLE_DAILY_NOTES_V27.14.0.md "Multiple Daily Notes"
contains src/main/resources/db/migration/postgresql/V36__multiple_daily_notes.sql "CREATE TABLE day_notes"
contains src/main/java/ru/daniil/shifts/model/DayNote.java "class DayNote"
contains src/main/java/ru/daniil/shifts/service/DayNoteService.java "syncPrimaryFromLegacy"
contains src/main/java/ru/daniil/shifts/web/DayNoteController.java '@RequestMapping({"/api/notes", "/api/v1/notes"})'
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "List<DayNoteDto> notes"
contains src/main/resources/static/index.html 'id="noteList"'
contains src/main/resources/static/js/50-tasks.js "function renderDayNotes()"
contains src/main/resources/static/js/50-tasks.js "sameNote ? pendingNoteSave.patch"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/notes:"
contains src/test/java/ru/daniil/shifts/service/DayNoteServiceTest.java "class DayNoteServiceTest"
contains src/test/java/ru/daniil/shifts/web/DayNoteControllerTest.java "class DayNoteControllerTest"
contains src/test/java/ru/daniil/shifts/web/MultipleDailyNotesFrontendContractTest.java "class MultipleDailyNotesFrontendContractTest"
contains e2e/multiple-daily-notes.spec.js "multiple notes on one day remain independent"

  # v27.14.1 Mobile Notes Tombstone Hotfix
contains CHANGES.md "v27.14.1 — Mobile Notes Tombstone Hotfix"
contains README.md "v27.14.1 — Mobile Notes Tombstone Hotfix"
contains docs/MOBILE_NOTES_TOMBSTONE_HOTFIX_V27.14.1.md "Mobile Notes Tombstone Hotfix"
contains src/main/java/ru/daniil/shifts/service/DayEntryService.java "preserveEmptyVersionRow"
contains src/main/java/ru/daniil/shifts/service/DayNoteService.java "preserveEmptyDayEntry"
contains src/test/java/ru/daniil/shifts/service/MobileSyncServiceTest.java "clearCreatesAVersionedTombstoneSoStaleOfflineCreatesCannotOverwriteIt"
contains src/test/java/ru/daniil/shifts/service/MobileSyncServiceTest.java "explicitClearFlagsWinOverValuesInTheSamePatch"
contains src/test/java/ru/daniil/shifts/web/MobileSyncControllerTest.java "legacyClearDeletesEmptyRowWhileV1ClearKeepsVersionedTombstone"

  # v27.14.2 Calendar Notes Persistence E2E Hotfix
contains CHANGES.md "v27.14.2 — Calendar Notes Persistence E2E Hotfix"
contains README.md "v27.14.2 — Calendar Notes Persistence E2E Hotfix"
contains docs/CALENDAR_NOTES_PERSISTENCE_E2E_HOTFIX_V27.14.2.md "Calendar Notes Persistence E2E Hotfix"
contains e2e/calendar-persistence.spec.js "const noteCreated = waitForApi(page, 'POST', '/api/v1/notes', 201)"
contains e2e/calendar-persistence.spec.js "^\/api\/notes\/\d+$"
not_contains e2e/calendar-persistence.spec.js "const noteSaved = waitForApi(page, 'PUT'"

  # v27.15.0 Design System & Mobile Shell Foundation
contains CHANGES.md "v27.15.0 — Design System & Mobile Shell Foundation"
contains README.md "v27.15.0 — Design System & Mobile Shell Foundation"
contains docs/DESIGN_SYSTEM_MOBILE_SHELL_FOUNDATION_V27.15.0.md "Design System & Mobile Shell Foundation"
contains src/main/resources/static/index.html "design-system.css?v=$VERSION"
contains src/main/resources/static/index.html 'id="nextTopbar"'
not_contains src/main/resources/static/index.html 'data-shell-choice="classic"'
contains src/main/resources/static/design-system.css 'html[data-shell="next"] .tabbar'
contains src/main/resources/static/design-system.css 'env(safe-area-inset-bottom'
contains src/main/resources/static/js/10-core.js 'root.dataset.shell = "next"'
not_contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'input.get("shellMode")'
contains src/test/java/ru/daniil/shifts/web/DesignSystemMobileShellFrontendContractTest.java 'class DesignSystemMobileShellFrontendContractTest'
contains e2e/design-system-shell.spec.js 'single DutyLog shell after Classic sunset'

  # v27.16.0 Today Dashboard
contains CHANGES.md "v27.16.0 — Today Dashboard"
contains README.md "v27.16.0 — Today Dashboard"
contains docs/TODAY_DASHBOARD_V27.16.0.md "Today Dashboard"
contains src/main/resources/static/index.html 'id="view-today"'
contains src/main/resources/static/index.html 'data-view="today" href="#today"'
contains src/main/resources/static/index.html "js/35-today.js?v=$VERSION"
contains src/main/resources/static/js/35-today.js 'function todayDashboardShiftModel'
contains src/main/resources/static/js/35-today.js 'state.shiftOccurrences'
contains src/main/resources/static/js/35-today.js 'state.overtimeAccount'
not_contains src/main/resources/static/js/35-today.js '/api/today'
contains src/main/resources/static/js/70-user-boot.js 'const defaultRoute = "#today"'
contains src/main/resources/static/design-system.css '.todayDashboardGrid'
contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'class TodayDashboardFrontendContractTest'
contains e2e/today-dashboard.spec.js 'Today Dashboard composes the day and opens existing feature flows'

  # v27.16.1 Today Runtime & Repository Truth Hotfix
contains CHANGES.md "v27.16.1 — Today Runtime & Repository Truth Hotfix"
contains README.md "v27.16.1 — Today Runtime & Repository Truth Hotfix"
contains docs/TODAY_RUNTIME_HOTFIX_V27.16.1.md "Today Runtime & Repository Truth Hotfix"
contains docs/ROADMAP.md '`v27.17.6` — Classic Sunset'
contains docs/ARCHITECTURE.md "V39 Schedule Templates & Calendar Layers"
contains src/main/resources/static/js/35-today.js '$("todayQuickMore")?.addEventListener("click", () => openQuickActions());'
not_contains src/main/resources/static/js/35-today.js '$("todayQuickMore")?.addEventListener("click", openQuickActions);'

  # v27.16.2 Next Route & Time Settings E2E Hotfix
contains CHANGES.md "v27.16.2 — Next Route & Time Settings E2E Hotfix"
contains README.md "v27.16.2 — Next Route & Time Settings E2E Hotfix"
contains docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md "Next Route & Time Settings E2E Hotfix"
contains e2e/helpers.js "async function openView(page, view)"
contains e2e/helpers.js "await openView(page, 'calendar');"
contains e2e/auth-onboarding.spec.js "#view-today"
contains e2e/important-timezone.spec.js "await openView(page, 'important');"
contains src/main/resources/static/js/60-settings.js "function cancelTimeSettingsAutoApply()"
contains src/main/resources/static/js/60-settings.js "if (!silent) cancelTimeSettingsAutoApply();"
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "function cancelTimeSettingsAutoApply()"

  # v27.16.3 Time Settings Transaction Hotfix
contains CHANGES.md "v27.16.3 — Time Settings Transaction Hotfix"
contains README.md "v27.16.3 — Time Settings Transaction Hotfix"
contains docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md "Time Settings Transaction Hotfix"
contains docs/API.md "# DutyLog API v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
contains src/main/resources/static/js/60-settings.js "let timeSettingsApplyQueue = Promise.resolve();"
contains src/main/resources/static/js/60-settings.js "const pending = timeSettingsApplyQueue.then(operation, operation);"
contains src/main/resources/static/js/60-settings.js "function readShiftDefaultsDraft()"
contains src/main/resources/static/js/60-settings.js "preserveShiftDefaults = timeSettingsDefaultsDirty()"
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "let timeSettingsApplyQueue = Promise.resolve();"



  # v27.32.1 Time Bank Absence Navigation Hotfix
contains CHANGES.md "v27.32.1 — Time Bank Absence Navigation Hotfix"
contains README.md "v27.32.1 — Time Bank Absence Navigation Hotfix"
contains docs/TIME_BANK_ABSENCE_NAVIGATION_HOTFIX_V27.32.1.md "single cross-workspace editor boundary"
contains docs/ROADMAP.md "v27.32.1 — Time Bank Absence Navigation Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.32.1 Time Bank Absence Navigation Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Cross-workspace absence editor ownership (v27.32.1)"
contains docs/SECURITY_REVIEW.md "v27.32.1 time-bank navigation review"
contains docs/RELEASE_CHECKLIST.md "v27.32.1 Time Bank Absence Navigation Hotfix acceptance"

  # v27.33.0 Vue Frontend Foundation & CI/CD — historical foundation
contains CHANGES.md "v27.33.0 — Vue Frontend Foundation & CI/CD"
contains README.md "v27.33.0 — Vue Frontend Foundation & CI/CD"
contains docs/VUE_FRONTEND_FOUNDATION_V27.33.0.md "one Spring Boot image"
contains docs/ROADMAP.md "v27.33.0 — Vue Frontend Foundation & CI/CD — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.33.0 Vue Frontend Foundation & CI/CD extension"
contains docs/MODULE_CONTRACTS.md "Frontend migration ownership (v27.33.0)"
contains docs/SECURITY_REVIEW.md "v27.33.0 Vue foundation security review"
contains docs/RELEASE_CHECKLIST.md "v27.33.0 Vue Frontend Foundation & CI/CD acceptance"
contains frontend/package.json '"vue": "3.5.13"'
contains frontend/package.json '"typescript": "5.7.3"'
contains frontend/package.json '"vite": "5.4.14"'
contains frontend/package.json '"pinia": "2.3.0"'
contains frontend/package.json '"vue-router": "4.5.0"'
contains frontend/package.json '"vitest": "2.1.8"'
contains frontend/tsconfig.json '"strict": true'
contains frontend/src/platform/router/index.ts 'createMemoryHistory'
not_contains frontend/src/platform/router/index.ts 'createWebHashHistory'
contains frontend/src/platform/bridge/legacyBridge.ts 'target.DutyLogLegacyPlatform'
contains src/main/resources/static/js/10-core.js 'window.DutyLogLegacyPlatform = Object.freeze'
contains src/main/resources/static/js/shell-bootstrap.js 'window.__dutylogVueReady = new Promise'
contains src/main/resources/static/index.html 'id="dutylog-vue-root"'
contains pom.xml '<directory>frontend/dist</directory>'
contains pom.xml '<targetPath>static/vue</targetPath>'
contains Dockerfile 'FROM node:20.18.1-alpine3.20 AS frontend-build'
contains Dockerfile 'COPY --from=frontend-build /frontend/dist ./frontend/dist'
contains .dockerignore '/package.json'
not_contains .dockerignore $'\npackage.json\n'
contains deploy/scripts/frontend-gate.sh 'npm --prefix "$FRONTEND_DIR" run typecheck'
contains .github/workflows/ci.yml 'bash ./deploy/scripts/frontend-gate.sh'
contains .github/workflows/deploy-staging.yml 'bash ./deploy/scripts/frontend-gate.sh'
contains .github/workflows/deploy-production.yml 'bash ./deploy/scripts/frontend-gate.sh'
contains .github/dependabot.yml 'directory: "/frontend"'
contains src/test/java/ru/daniil/shifts/web/VueFrontendFoundationContractTest.java 'class VueFrontendFoundationContractTest'
contains e2e/vue-frontend-foundation.spec.js 'window.__dutylogVueReady'

  # v27.34.0 Vue App Shell & Design System
contains CHANGES.md "v27.34.0 — Vue App Shell & Design System"
contains README.md "v27.34.0 — Vue App Shell & Design System"
contains docs/VUE_APP_SHELL_DESIGN_SYSTEM_V27.34.0.md "Vue now owns the application brand"
contains docs/FRONTEND_ARCHITECTURE.md "Vue owns the application shell"
contains docs/FRONTEND_ARCHITECTURE.md "Vue owns all user-facing screens"
contains docs/ROADMAP.md "v27.34.0 — Vue App Shell & Design System — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.34.0 Vue App Shell & Design System extension"
contains docs/MODULE_CONTRACTS.md "Vue app-shell ownership (v27.34.0)"
contains docs/SECURITY_REVIEW.md "v27.34.0 Vue app shell security review"
contains docs/RELEASE_CHECKLIST.md "v27.34.0 Vue App Shell & Design System acceptance"
contains frontend/vite.config.ts 'vue-shell-v1'
contains frontend/vite.config.ts 'dutylog-vue-app-shell.js'
contains frontend/vite.config.ts 'dutylog-vue-app-shell.css'
contains frontend/src/app/AppShell.vue 'data-vue-app-shell'
contains frontend/src/app/AppNavigation.vue 'data-vue-shell-navigation'
contains frontend/src/app/AppNavigation.vue 'aria-current'
contains frontend/src/app/shellStore.ts 'synchronize(snapshot'
contains frontend/src/shared/overlays/UiModal.vue '<Teleport to="body">'
contains frontend/src/shared/overlays/ToastHost.vue 'ui-toast-region'
not_contains frontend/src/styles/design-system.css 'html[data-vue-shell="ready"] .nextTopbar'
not_contains frontend/src/styles/design-system.css 'html[data-vue-shell="ready"] #tabbar'
contains frontend/src/styles/design-system.css '@media (max-width: 840px)'
contains frontend/src/styles/design-system.css '@media (prefers-reduced-motion: reduce)'
contains frontend/src/platform/bridge/legacyBridge.ts 'dutylog:legacy-state'
contains frontend/src/platform/bridge/legacyBridge.ts 'subscribe(listener'
contains src/main/resources/static/js/10-core.js 'function legacyPlatformSnapshot()'
contains src/main/resources/static/js/10-core.js 'function publishLegacyPlatformState()'
contains src/main/resources/static/index.html "/vue/dutylog-vue-app-shell.css?v=${VERSION}"
contains src/main/resources/static/index.html "/vue/dutylog-vue-app-shell.js?v=${VERSION}"
contains Dockerfile 'dist/dutylog-vue-app-shell.js'
contains deploy/scripts/frontend-gate.sh 'dist/dutylog-vue-app-shell.css'
contains src/test/java/ru/daniil/shifts/web/VueAppShellDesignSystemContractTest.java 'class VueAppShellDesignSystemContractTest'
contains e2e/vue-app-shell.spec.js 'Vue app shell owns navigation chrome'
if grep -R -F 'document.querySelector(' frontend/src >/dev/null; then
  fail "frontend/src contains forbidden direct DOM query"
else
  ok "frontend/src contains no direct DOM query"
fi
if grep -R -F 'window.state' frontend/src >/dev/null; then
  fail "frontend/src contains forbidden mutable legacy state access"
else
  ok "frontend/src contains no mutable legacy state access"
fi

  # v27.34.1 Vue Strict Type Contract Hotfix
contains CHANGES.md "v27.34.1 — Vue Strict Type Contract Hotfix"
contains README.md "v27.34.1 — Vue Strict Type Contract Hotfix"
contains docs/VUE_STRICT_TYPE_CONTRACT_HOTFIX_V27.34.1.md "keeps the strict compiler settings"
contains docs/ROADMAP.md "v27.34.1 — Vue Strict Type Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.34.1 Vue Strict Type Contract Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Strict Vue compiler compatibility (v27.34.1)"
contains docs/SECURITY_REVIEW.md "v27.34.1 strict Vue type contract security review"
contains docs/RELEASE_CHECKLIST.md "v27.34.1 Vue Strict Type Contract Hotfix acceptance"
contains frontend/tsconfig.json '"exactOptionalPropertyTypes": true'
contains frontend/src/app/AppNavigation.vue ":aria-current=\"activeRoute === item.route ? 'page' : false\""
not_contains frontend/src/app/AppNavigation.vue "? 'page' : undefined"
contains frontend/src/shared/ui/UiButton.vue ":type=\"type ?? 'button'\""
contains frontend/src/shared/ui/UiButton.vue ':disabled="disabled ?? false"'
contains frontend/src/shared/ui/UiTabs.vue ':disabled="option.disabled ?? false"'
not_contains frontend/vite.config.ts 'cssFileName:'
contains frontend/vite.config.ts 'assetInfo.name === "style.css"'
contains src/test/java/ru/daniil/shifts/web/VueStrictOptionalTemplateContractHotfixTest.java 'class VueStrictOptionalTemplateContractHotfixTest'

  # v27.34.2 Vue Browser Runtime Bundle Hotfix
contains CHANGES.md "v27.34.2 — Vue Browser Runtime Bundle Hotfix"
contains README.md "v27.34.2 — Vue Browser Runtime Bundle Hotfix"
contains docs/VUE_BROWSER_RUNTIME_BUNDLE_HOTFIX_V27.34.2.md 'generated Vue bundle still referenced `process.env.NODE_ENV`'
contains docs/ROADMAP.md "v27.34.2 — Vue Browser Runtime Bundle Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.34.2 Vue Browser Runtime Bundle Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Vue browser-runtime bundle safety (v27.34.2)"
contains docs/SECURITY_REVIEW.md "v27.34.2 Vue browser-runtime bundle security review"
contains docs/RELEASE_CHECKLIST.md "v27.34.2 Vue Browser Runtime Bundle Hotfix acceptance"
contains frontend/vite.config.ts '"process.env.NODE_ENV": JSON.stringify("production")'
contains frontend/vite.config.ts 'import packageMetadata from "./package.json";'
contains frontend/vite.config.ts 'const releaseVersion = packageMetadata.version;'
not_contains frontend/vite.config.ts 'const releaseVersion = "27.'
contains frontend/src/platform/diagnostics/frontendDiagnostics.spec.ts 'import packageMetadata from "../../../package.json";'
contains frontend/src/platform/diagnostics/frontendDiagnostics.spec.ts 'expect(failure.releaseVersion).toBe(packageMetadata.version);'
contains frontend/package.json '"audit:bundle": "node ./scripts/audit-browser-bundle.mjs"'
contains frontend/package.json 'vite build && npm run audit:bundle'
contains frontend/scripts/audit-browser-bundle.mjs 'unreplaced process.env'
contains frontend/scripts/audit-browser-bundle.mjs 'CommonJS require'
contains frontend/scripts/audit-browser-bundle.mjs 'CommonJS module.exports'
contains frontend/scripts/audit-browser-bundle.mjs 'Node __dirname'
contains frontend/scripts/audit-browser-bundle.mjs 'Node __filename'
contains frontend/scripts/audit-browser-bundle.mjs 'dutylog-vue-app-shell.js'
contains e2e/fixtures.js "page.on('pageerror'"
contains src/test/java/ru/daniil/shifts/web/VueBrowserRuntimeBundleHotfixTest.java 'class VueBrowserRuntimeBundleHotfixTest'
not_contains frontend/vite.config.ts 'process: {}'
not_contains frontend/vite.config.ts 'global: {}'


  # v27.34.3 Vue Shell E2E Navigation Compatibility Hotfix
contains CHANGES.md "v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix"
contains README.md "v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix"
contains docs/VUE_SHELL_E2E_NAVIGATION_COMPATIBILITY_HOTFIX_V27.34.3.md 'public `DutyLogVuePlatform.navigateLegacy(...)` capability'
contains docs/ROADMAP.md "v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.34.3 Vue Shell E2E Navigation Compatibility Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Vue shell E2E ownership compatibility (v27.34.3)"
contains docs/SECURITY_REVIEW.md "v27.34.3 Vue shell E2E navigation compatibility review"
contains docs/RELEASE_CHECKLIST.md "v27.34.3 Vue Shell E2E Navigation Compatibility Hotfix acceptance"
contains e2e/helpers.js "window.DutyLogVuePlatform"
contains e2e/helpers.js "platform.navigate(target)"
contains e2e/helpers.js "window.DutyLogLegacyPlatform?.navigate(target)"
contains e2e/helpers.js "async function waitForVueShell(page)"
not_contains e2e/helpers.js '#tabbar a[data-view='
contains frontend/src/app/AppShell.vue 'data-vue-shell-brand'
contains frontend/src/app/AppShell.vue 'data-vue-shell-profile'
contains frontend/src/app/AppShell.vue 'data-vue-shell-logout'
contains frontend/src/app/AppNavigation.vue 'data-vue-shell-more'
contains e2e/external-calendar-sync.spec.js 'PRODID:-//DutyLog//Time and Overtime ${releaseVersion}//RU'
contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java 'class VueShellE2eNavigationCompatibilityHotfixTest'

  # v27.35.0 Vue Delivery, Contracts & Diagnostics Foundation
contains CHANGES.md "v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation"
contains README.md "v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation"
contains docs/VUE_DELIVERY_CONTRACTS_DIAGNOSTICS_FOUNDATION_V27.35.0.md 'No product domain moves to Vue'
contains docs/ROADMAP.md "v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation — stabilization baseline"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.0 Vue Delivery, Contracts & Diagnostics Foundation extension"
contains docs/MODULE_CONTRACTS.md "Vue delivery, generated API and authentic lockfile bootstrap (v27.35.2)"
contains docs/SECURITY_REVIEW.md "v27.35.2 authentic-lockfile bootstrap review"
contains docs/RELEASE_CHECKLIST.md "v27.35.0 Vue Delivery, Contracts & Diagnostics Foundation acceptance"
contains frontend/package.json '"packageManager": "npm@10.8.2"'
contains frontend/package.json '"node": "20.18.1"'
contains frontend/.npmrc 'package-lock=true'
contains frontend/.npmrc 'engine-strict=true'
contains deploy/scripts/frontend-gate.sh 'npm --prefix "$FRONTEND_DIR" ci'
contains Dockerfile 'FROM node:20.18.1-alpine3.20 AS frontend-build'
contains Dockerfile 'npm ci --no-audit --no-fund'
contains frontend/scripts/generate-openapi-contract.mjs '--check'
contains frontend/src/generated/dutylog-api.ts 'export const dutyLogOperations'
contains frontend/src/platform/api/generatedClient.ts 'DutyLogOperationId'
contains frontend/src/platform/api/httpClient.ts 'X-Request-Id'
contains frontend/src/platform/diagnostics/frontendDiagnostics.ts 'unhandledrejection'
contains frontend/src/platform/diagnostics/frontendDiagnostics.ts 'do not call preventDefault'
contains frontend/src/shared/errors/AppErrorBoundary.vue 'data-vue-recovery-ui'
contains docs/migration/_template.md 'Parity matrix'
contains docs/architecture/adr/INDEX.md 'ADR-005'
contains docs/ENGINEERING_QUALITY_REGISTER.md 'Q-01–Q-05 реализованы'
contains src/test/java/ru/daniil/shifts/web/VueDeliveryContractsDiagnosticsFoundationTest.java 'class VueDeliveryContractsDiagnosticsFoundationTest'
not_contains deploy/scripts/frontend-gate.sh 'package-lock=false'
contains Dockerfile 'COPY frontend/package.json frontend/package-lock.json'
not_contains Dockerfile 'npx '

  # v27.35.2 Authentic npm Lockfile Bootstrap Hotfix
not_contains .gitignore 'frontend/package-lock.json'
contains deploy/scripts/bootstrap-frontend-lockfile.sh 'rm -f "$FRONTEND_DIR/package-lock.json"'
contains CHANGES.md "v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix"
contains README.md "v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix"
contains docs/AUTHENTIC_NPM_LOCKFILE_BOOTSTRAP_HOTFIX_V27.35.2.md 'Gate A remains blocked'
contains docs/ROADMAP.md "v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.2 Authentic npm Lockfile Bootstrap extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.2 Authentic npm Lockfile Bootstrap Hotfix acceptance"
contains frontend/package-lock.json 'https://registry.npmjs.org/'
contains frontend/package-lock.json 'sha512-'
contains frontend/generated-lockfile-manifest.txt 'sourceArtifactSha256=3f6c590948a62c506c2191c9b279f15712f5182148c5f3131e96d5a56bd54060'
contains frontend/scripts/verify-authentic-lockfile.mjs 'integrityEntries.length < 70'
contains .github/workflows/ci.yml 'cache-dependency-path: frontend/package-lock.json'
not_contains .github/workflows/ci.yml 'Upload generated authentic frontend lockfile'
contains frontend/scripts/verify-delivery-foundation.mjs 'verify-authentic-lockfile.mjs'
contains deploy/scripts/frontend-gate.sh 'for command in vue-tsc vitest vite'
contains deploy/scripts/frontend-gate.sh 'npm --prefix "$FRONTEND_DIR" ls --all'
not_contains deploy/scripts/frontend-gate.sh 'bootstrap-frontend-lockfile.sh'
contains Dockerfile 'COPY frontend/package.json frontend/package-lock.json'
not_contains Dockerfile 'npm install --package-lock-only'
contains src/test/java/ru/daniil/shifts/web/AuthenticLockfileCommitGeneratedClientFixtureHotfixTest.java 'class AuthenticLockfileCommitGeneratedClientFixtureHotfixTest'
contains frontend/src/platform/api/generatedClient.spec.ts 'mockImplementation(async () =>'
not_contains frontend/src/platform/api/generatedClient.spec.ts 'mockResolvedValue(jsonResponse'
not_contains deploy/scripts/frontend-gate.sh 'npx '
not_contains Dockerfile 'npx '

  # v27.35.4 Frontend Gate Static Contract Java Escaping Hotfix
contains CHANGES.md "v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix"
contains README.md "v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix"
contains docs/FRONTEND_GATE_STATIC_CONTRACT_JAVA_ESCAPING_HOTFIX_V27.35.4.md 'valid Java string'
contains docs/ROADMAP.md "v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.4 Frontend gate static-contract Java escaping extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.4 Frontend Gate Static Contract Java Escaping Hotfix acceptance"

  # v27.35.5 Gate A Quality Register Lambda Capture Compile Hotfix
contains CHANGES.md "v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix"
contains README.md "v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix"
contains docs/GATE_A_QUALITY_REGISTER_LAMBDA_CAPTURE_COMPILE_HOTFIX_V27.35.5.md 'String rowPrefix = "| Q-0" + number + " ";'
contains docs/ROADMAP.md "v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix — stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.5 Gate A quality-register lambda-capture compile extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.5 Gate A Quality Register Lambda Capture Compile Hotfix acceptance"
contains src/test/java/ru/daniil/shifts/web/VueDeliveryContractsDiagnosticsFoundationTest.java 'String rowPrefix = "| Q-0" + number + " ";'
contains src/test/java/ru/daniil/shifts/web/VueDeliveryContractsDiagnosticsFoundationTest.java '.filter(line -> line.startsWith(rowPrefix))'
not_contains src/test/java/ru/daniil/shifts/web/VueDeliveryContractsDiagnosticsFoundationTest.java '.filter(line -> line.startsWith("| Q-0" + number + " "))'
contains src/test/java/ru/daniil/shifts/web/AuthenticLockfileCommitGeneratedClientFixtureHotfixTest.java 'assertTrue(gate.contains("npm --prefix \"$FRONTEND_DIR\" ci"));'
not_contains src/test/java/ru/daniil/shifts/web/AuthenticLockfileCommitGeneratedClientFixtureHotfixTest.java 'gate.contains("npm --prefix "$FRONTEND_DIR" ci")'

  # v27.37.1 Vue Calendar & Timeline Strict Typecheck Hotfix
contains CHANGES.md "v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix"
contains README.md "v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix"
contains docs/VUE_CALENDAR_TIMELINE_STRICT_TYPECHECK_HOTFIX_V27.37.1.md "strict TypeScript"
contains docs/migration/calendar-timeline-vue-migration-manifest.md "## v27.37.1 strict typecheck follow-up"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "v27.37.1 Vue Calendar & Timeline Strict Typecheck Hotfix acceptance"
contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'openDate: async (date: string, mode?: CalendarMode)'
contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'async openDate(date: string, mode?: CalendarMode): Promise<void>'
contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'const resolvedMode = mode ?? this.mode'
contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'async goToday(mode?: CalendarMode): Promise<void>'
not_contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'mode: CalendarMode = this.mode'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineStrictTypecheckHotfixTest.java "workspaceBridgeCallbackCarriesExplicitGeneratedDomainTypes"


  # v27.39.2 Vue Settings Maven Contract Alignment Hotfix
  contains CHANGES.md "v27.39.2 — Vue Settings Maven Contract Alignment Hotfix"
  contains README.md "DutyLog v27.39.2 — Vue Settings Maven Contract Alignment Hotfix"
  contains docs/VUE_SETTINGS_WORKSPACE_INTEGRATIONS_V27.39.0.md "118 operations / 120 schemas"
  contains docs/VUE_SETTINGS_MAVEN_CONTRACT_ALIGNMENT_HOTFIX_V27.39.2.md "758 tests"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/architecture/adr/ADR-008-production-source-maps-and-frontend-diagnostics.md "Production frontend source maps are disabled by default"
  contains frontend/vite.config.ts 'DUTYLOG_FRONTEND_SOURCEMAPS'
  contains frontend/vite.config.ts '? "hidden" : false'
  contains frontend/src/app/AppShell.vue "SettingsWorkspace"
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'retireDomainOwners("settings-workspace")'
  not_contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'settingsLegacyHost'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'TimeSettingsCard'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'ScheduleSettingsCard'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'NotificationSettingsCard'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'attachSettingsLegacy'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openSettingsLegacySection'
  not_contains src/main/resources/static/js/10-core.js 'settingsLegacyParking'
  contains src/main/resources/static/js/60-settings.js 'document.documentElement.dataset.vueSettingsWorkspace === "ready"'
  contains src/main/resources/static/js/38-schedule-layers.js 'document.documentElement.dataset.vueSettingsWorkspace === "ready"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("updateModules"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("updateProfile"'
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'bridge.previewModuleEnabled(key, false)'
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'bridge.commitModuleList'
  contains src/main/resources/static/js/10-core.js 'document.documentElement.setAttribute("data-vue-settings-workspace", "ready")'
  contains src/main/resources/static/js/10-core.js 'await loadMonth({ fresh:true })'
  contains src/main/java/ru/daniil/shifts/web/TelegramController.java '@RequestMapping({"/api/telegram", "/api/v1/telegram"})'
  contains frontend/src/generated/dutylog-api.ts '"updateModules": { method: "PATCH", path: "/api/v1/modules" }'
  contains frontend/src/generated/dutylog-api.ts '"getTelegramStatus": { method: "GET", path: "/api/v1/telegram/status" }'
  contains e2e/vue-settings-workspace.spec.js 'data-vue-settings-workspace'
  contains e2e/helpers.js "waitForApi(page, 'PATCH', '/api/v1/modules')"
  contains e2e/external-calendar-sync.spec.js "url.pathname === '/api/v1/calendar-sync/subscription'"
  contains src/test/java/ru/daniil/shifts/web/VueSettingsWorkspaceMigrationFrontendContractTest.java 'integrationSecretsStayVolatileAndProductionSourceMapsAreOptInHidden'
  not_contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts "localStorage"
  contains frontend/src/features/settings-workspace/components/AppearanceSettingsCard.vue 'screenEntry(id).required === true'
  contains frontend/src/features/settings-workspace/components/AppearanceSettingsCard.vue 'widgetEntry(id).required === true'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue ':key="session.id ?? sessionIndex"'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue ':checked="telegram?.notificationsEnabled === true"'
  contains frontend/src/features/settings-workspace/types/model.ts 'if (fromValue === undefined || toValue === undefined) return current;'
  contains src/test/java/ru/daniil/shifts/web/VueSettingsWorkspaceMigrationFrontendContractTest.java 'strictTemplateBindingsDoNotLeakUndefinedIntoVueDomAttributesOrCatalogEntries'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'dutylog-shell-v27.38.15-synthetic-previous'
contains src/test/java/ru/daniil/shifts/web/VueSettingsWorkspaceMigrationFrontendContractTest.java 'document.documentElement.setAttribute(\"data-vue-settings-workspace\", \"ready\")'

  # v27.40.0 Vue Legacy Retirement & Parity: Settings Island Cutover
  contains CHANGES.md "v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover"
  contains README.md "DutyLog v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover"
  contains docs/VUE_LEGACY_RETIREMENT_SETTINGS_CUTOVER_V27.40.0.md "# v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover"
  contains docs/VUE_LEGACY_RETIREMENT_SETTINGS_CUTOVER_V27.40.0.md "Remaining v27.40.x retirement blockers"
  contains docs/VUE_SETTINGS_MODULE_RUNTIME_SYNC_PWA_RECONNECT_HOTFIX_V27.39.6.md "44 passed and 4 failed"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/RELEASE_CHECKLIST.md "v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover acceptance"
  contains e2e/helpers.js "const alreadyEnabled = await toggle.isChecked()"
  contains e2e/helpers.js "if (alreadyEnabled === enabled)"
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts "synchronizeModuleEnabledMap(enabled: Readonly<Record<string, boolean>>"
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue "modules: shellModules"
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue "settings.synchronizeModuleEnabledMap(shellModules.value)"
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue "watch(shellModules"
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'TimeSettingsCard'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'ScheduleSettingsCard'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'NotificationSettingsCard'
  not_contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'settingsLegacyHost'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'attachSettingsLegacy'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openSettingsLegacySection'
  not_contains src/main/resources/static/js/10-core.js 'settingsLegacyParking'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("getTimeContext"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("getNotificationSettings"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("listScheduleTemplates"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("listCalendarLayers"'
  contains src/main/resources/static/js/60-settings.js 'document.documentElement.dataset.vueSettingsWorkspace === "ready"'
  contains src/main/resources/static/js/38-schedule-layers.js 'document.documentElement.dataset.vueSettingsWorkspace === "ready"'
  contains frontend/src/platform/bridge/legacyBridge.ts 'OFFLINE_SYNC_COMPLETE_EVENT = "dutylog:offline-sync-complete"'
  contains src/main/resources/static/js/20-data.js 'new CustomEvent("dutylog:offline-sync-complete"'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue "window.addEventListener(OFFLINE_SYNC_COMPLETE_EVENT, handleOfflineSyncComplete)"
  not_contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'window.addEventListener("online", reconnect)'
  not_contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'store.flushOfflineQueue()'
  not_contains src/main/resources/static/js/10-core.js 'localStorage.setItem("dutylog.settings.openSection", wanted)'
  contains frontend/src/features/settings-workspace/types/model.ts "const orderedVisibleWidgets = source.filter"
  contains e2e/absence-time-off-overhaul.spec.js "response.request().postDataJSON()"
  contains e2e/absence-time-off-overhaul.spec.js "expect(preview.durationMinutes).toBe(240)"
  contains src/main/resources/static/service-worker.js "dutylog-shell-v${VERSION}-"

  # v27.40.12 Legacy Command Surface Retirement — historical accepted cut
  contains CHANGES.md "v27.40.12 — Legacy Command Surface Retirement"
  contains README.md "DutyLog v27.40.12 — Legacy Command Surface Retirement"
  contains docs/VUE_LEGACY_COMMAND_SURFACE_RETIREMENT_V27.40.12.md "open-modal"
  contains docs/RELEASE_CHECKLIST.md "v27.40.12 — Legacy Command Surface Retirement acceptance — accepted predecessor"
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'open-modal'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openModal('
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openTaskCreate('
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openTaskDetails('
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openImportantDetails('
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'openTaskDetails: async (id: number)'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'openImportantDetails: async (id: number)'
  contains frontend/src/features/settings-workspace/types/domain.ts 'openShiftTypeManager(editId?: number | null): void;'
  contains src/main/resources/static/js/10-core.js 'async offlineUpdateNote(id, patch, date)'
  contains src/main/resources/static/js/10-core.js 'async offlineSync()'

  # v27.40.14 Vue Route Guard Authority Cutover predecessor cut
  contains CHANGES.md "v27.40.14 — Vue Route Guard Authority Cutover"
  contains README.md "DutyLog v27.40.14 — Vue Route Guard Authority Cutover"
  contains docs/VUE_ROUTE_GUARD_AUTHORITY_CUTOVER_V27.40.14.md "guardHashRoute"
  contains docs/RELEASE_CHECKLIST.md "v27.40.14 — Vue Route Guard Authority Cutover acceptance"

  # v27.41.2 Overtime Chart Source Contract Alignment
  contains CHANGES.md "v27.41.2 — Overtime Chart Source Contract Alignment"
  contains README.md "DutyLog v27.41.2 — Overtime Chart Source Contract Alignment"
  contains docs/OVERTIME_CHART_SOURCE_CONTRACT_ALIGNMENT_V27.41.2.md "The Java source contract now asserts the intended pixel model"
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'page.contains("const CHART_PLOT_HEIGHT_PX = 126;")'
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'return \"0px\"'
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'Math.round(ratio * CHART_PLOT_HEIGHT_PX)'
  not_contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'return \"0%\"'

  # v27.41.6 Accounting Integrity Semantics & Actionability Hotfix
  contains CHANGES.md "v27.41.6 — Accounting Integrity Semantics & Actionability Hotfix"
  contains README.md "DutyLog v27.41.6 — Accounting Integrity Semantics & Actionability Hotfix"
  contains docs/ACCOUNTING_INTEGRITY_SEMANTICS_ACTIONABILITY_V27.41.6.md 'zero-minute `POSTED` audit fact'
  contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java 'if (latestAudit != null && !activeWorkflow)'
  contains src/main/java/ru/daniil/shifts/service/LedgerIntegrityService.java 'int expectedAuditMinutes = overtimeBacked'
  contains src/test/java/ru/daniil/shifts/service/LedgerIntegrityApprovalWorkflowServiceTest.java 'completedUnpaidAbsenceKeepsPostedZeroMinuteAuditWithoutIntegrityFalsePositive'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'Что делать:'
  contains frontend/src/features/absence-time-bank/absence-time-bank.css '.integrity-issue-group > .integrity-issue-records'

  # v27.41.7 Frontend Bundle Segmentation predecessor
  contains CHANGES.md "v27.41.7 — Frontend Bundle Segmentation"
  contains README.md "DutyLog v27.41.7 — Frontend Bundle Segmentation"
  contains docs/FRONTEND_BUNDLE_SEGMENTATION_V27.41.7.md "Vite emits content-hashed"
  contains frontend/vite.config.ts 'chunkFileNames: "chunks/[name]-[hash].js"'
  not_contains frontend/vite.config.ts 'inlineDynamicImports: true'
  contains frontend/src/app/AppShell.vue 'defineAsyncComponent'
  contains frontend/src/app/AppShell.vue 'loader: () => import("@/features/payroll/components/PayrollWorkspace.vue")'
  contains frontend/src/app/AppShell.vue '<PayrollWorkspace v-if='
  contains frontend/src/app/AppShell.vue "activeRoute === 'payroll'"
  contains frontend/src/app/AppShell.vue 'loader: () => import("@/features/admin/components/AdminWorkspace.vue")'
  contains frontend/src/app/AppShell.vue '<AdminWorkspace v-if='
  contains frontend/src/app/AppShell.vue "activeRoute === 'admin'"
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'import("./TodayPage.vue")'
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'import("./CalendarPage.vue")'
  contains frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue 'import("./AbsencePage.vue")'
  contains frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue '<AbsenceComposer v-if="absenceModalOpen" />'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'import("./TasksPage.vue")'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'import("./ImportantPage.vue")'
  contains frontend/scripts/audit-browser-bundle.mjs 'collectJavaScriptBundles'
  contains frontend/scripts/audit-browser-bundle.mjs 'maxEntryBytes'
  contains frontend/scripts/audit-browser-bundle.mjs 'maxChunkBytes'
  contains frontend/scripts/audit-browser-bundle.mjs 'maxTotalBytes'
  contains frontend/browser-bundle-budget.json "\"release\": \"${VERSION}\""
  contains frontend/browser-bundle-budget.json '"maxEntryBytes": 750000'
  contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'
  contains deploy/scripts/frontend-gate.sh 'dist/chunks'
  contains e2e/preflight.mjs 'frontend/dist/chunks'
  contains src/main/resources/static/service-worker.js 'content-hashed Vue chunks'

  # v27.41.5 Browser Bundle Budget Rebaseline Hotfix
  contains CHANGES.md "v27.41.5 — Browser Bundle Budget Rebaseline Hotfix"
  contains README.md "DutyLog v27.41.5 — Browser Bundle Budget Rebaseline Hotfix"
  contains docs/BROWSER_BUNDLE_BUDGET_REBASELINE_V27.41.5.md "800000 → 810000 B"

  # v27.41.4 Desktop UX & Workflow Clarity
  contains CHANGES.md "v27.41.4 — Desktop UX & Workflow Clarity"
  contains README.md "DutyLog v27.41.4 — Desktop UX & Workflow Clarity"
  contains docs/DESKTOP_UX_WORKFLOW_CLARITY_V27.41.4.md "Absence → overtime bank → FIFO"
  contains frontend/src/features/absence-time-bank/components/AbsencePage.vue 'sortedAbsences'
  contains frontend/src/features/absence-time-bank/components/AbsencePage.vue 'Сначала актуальные'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'openIntegritySource'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'integrity-issue-record'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'timeBankAllocationRow__range'
  contains frontend/src/features/productivity/components/SelectedDayNotes.vue 'id="noteFocusMode"'
  contains frontend/src/features/productivity/components/SelectedDayNotes.vue '<Teleport to="body" :disabled="!focusMode">'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'Оформить отгул'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'id="dayAddSettlement"'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue "Оба варианта расходуют один банк по FIFO"
  contains frontend/src/features/calendar-timeline/calendar-timeline.css 'width:min(100%,1680px)'
  contains frontend/src/features/settings-workspace/settings-workspace.css 'max-width:var(--vue-shell-max)'
  contains src/main/resources/static/app.css '.payrollWorkspace{'
  contains e2e/vue-absence-time-bank-migration.spec.js "getByLabel('Сортировка отсутствий')"
  contains e2e/notes-important-events-next.spec.js "locator('#noteFocusMode').click()"
  contains e2e/overtime-editor-modals.spec.js "toHaveText('Оформить отгул')"
  contains e2e/overtime-editor-modals.spec.js 'timeBankAllocationRow__range'

  # v27.41.3 Today Visual Parity & Overtime Summary Recovery
  contains CHANGES.md "v27.41.3 — Today Visual Parity & Overtime Summary Recovery"
  contains README.md "DutyLog v27.41.3 — Today Visual Parity & Overtime Summary Recovery"
  contains docs/TODAY_VISUAL_PARITY_OVERTIME_SUMMARY_RECOVERY_V27.41.3.md "same canonical day visual semantics"
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function calendarDayVisualStyle'
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function calendarFactualAbsence'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue 'class="todayDatePalm"'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue ':style="nearbyStyle(date)"'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue ':data-balance-percent="overtimeBalancePercent.toFixed(1)"'
  contains frontend/src/features/calendar-timeline/calendar-timeline.css '.vue-today-view .todayDateChip.hasShift:not(.hasAbsenceFact)'
  contains e2e/today-dashboard.spec.js "getBoundingClientRect().width"
  contains e2e/today-dashboard.spec.js '.todayDateChip.isScheduleFree'
  contains e2e/today-dashboard.spec.js '.todayDateImportantGlyph'
  contains e2e/today-dashboard.spec.js '.todayDateTaskCount'

  # v27.41.1 Overtime Chart Rendering Recovery
  contains CHANGES.md "v27.41.1 — Overtime Chart Rendering Recovery"
  contains README.md "DutyLog v27.41.1 — Overtime Chart Rendering Recovery"
  contains docs/OVERTIME_CHART_RENDERING_RECOVERY_V27.41.1.md "explicit 126 px"
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'const CHART_PLOT_HEIGHT_PX = 126;'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'return "0px";'
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue ':data-earned-hours="column.earnedHours"'
  contains frontend/src/features/absence-time-bank/absence-time-bank.css '.overtimeChartColumn__bars {'
  contains frontend/src/features/absence-time-bank/absence-time-bank.css 'height: 126px;'
  contains e2e/overtime-next.spec.js "getBoundingClientRect().height"
  contains e2e/overtime-next.spec.js "toHaveAttribute('data-earned-hours', '5')"
  contains e2e/overtime-next.spec.js 'height:\s*0px'
  not_contains e2e/overtime-next.spec.js 'height:\s*0%'

  # v27.41.0 Calendar Visual Language Foundation
  contains CHANGES.md "v27.41.0 — Calendar Visual Language Foundation"
  contains README.md "DutyLog v27.41.0 — Calendar Visual Language Foundation"
  contains docs/CALENDAR_VISUAL_LANGUAGE_FOUNDATION_V27.41.0.md "stable visual grammar"
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'class="calendarDayOffWatermark"'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'calendarCellImportantZone'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'calendarCellMarkerZone'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'calendarCellTaskZone'
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function calendarScheduleFree'
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function calendarImportantGlyph'
  contains e2e/calendar-mobile-experience.spec.js '.cell.isScheduleFree:not(.outside)'
  contains e2e/calendar-mobile-experience.spec.js '.calendarWeekPalm'

  # v27.40.37 Calendar E2E Source Contract Alignment Hotfix
  contains CHANGES.md "v27.40.37 — Calendar E2E Source Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.37 — Calendar E2E Source Contract Alignment Hotfix"
  contains docs/CALENDAR_E2E_SOURCE_CONTRACT_ALIGNMENT_HOTFIX_V27.40.37.md "guaranteed false"
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'const shiftSaved = waitForApi(page'
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'const shiftCell = page.locator(`#grid .cell[data-date=\"${today}\"]`);'
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'await expect(shiftCell).toHaveClass(/hasShift/);'
  not_contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'browser.contains(".cell.hasShift")'

  # v27.40.36 Calendar Fixture & Period Label E2E Hotfix
  contains CHANGES.md "v27.40.36 — Calendar Fixture & Period Label E2E Hotfix"
  contains README.md "DutyLog v27.40.36 — Calendar Fixture & Period Label E2E Hotfix"
  contains docs/CALENDAR_FIXTURE_PERIOD_LABEL_E2E_HOTFIX_V27.40.36.md "46 passed / 2 failed"
  contains e2e/calendar-mobile-experience.spec.js "const shiftSaved = waitForApi(page, 'PUT'"
  contains e2e/calendar-mobile-experience.spec.js 'const shiftCell = page.locator(`#grid .cell[data-date="${today}"]`);'
  contains e2e/calendar-mobile-experience.spec.js 'await expect(shiftCell).toHaveClass(/hasShift/);'
  contains e2e/overtime-next.spec.js "toContainText(/год|year/i)"
  not_contains e2e/overtime-next.spec.js "toContainText(/Год|Year/)"
  contains docs/RELEASE_CHECKLIST.md "Start from canonical v27.40.35 commit"
  contains docs/RELEASE_CHECKLIST.md "de877d89baa8f639809737324b09f22065c4558d"
  contains docs/RELEASE_CHECKLIST.md "93e5c05211553a541d74a6bd020b9bf60cf74171"

  # v27.40.35 Functional Parity Source Contract Scope Hotfix
  contains CHANGES.md "v27.40.35 — Functional Parity Source Contract Scope Hotfix"
  contains README.md "DutyLog v27.40.35 — Functional Parity Source Contract Scope Hotfix"
  contains docs/FUNCTIONAL_PARITY_SOURCE_CONTRACT_SCOPE_HOTFIX_V27.40.35.md "stale source-contract scope assumptions"
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'const shiftColor = facts.shift?.color;'
  contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'styles[\"--shift-color\"] = shiftColor'
  contains src/test/java/ru/daniil/shifts/web/TimeBankUsageDateChartParityHotfixTest.java 'String dayTotalsBlock = functionBlock(model, "export function dayCreditTotals", "export function ledgerChartColumns");'
  contains src/test/java/ru/daniil/shifts/web/TimeBankUsageDateChartParityHotfixTest.java 'assertTrue(dayTotalsBlock.contains("serverProjection?.dayEarnedHours"));'
  not_contains src/test/java/ru/daniil/shifts/web/FunctionalParitySweepIIMobileUsabilityContractTest.java 'styles[\"--shift-color\"] = facts.shift.color'

  # v27.40.34 Vue Asset Version Contract Hotfix
contains CHANGES.md "v27.40.34 — Vue Asset Version Contract Hotfix"
contains README.md "DutyLog v27.40.34 — Vue Asset Version Contract Hotfix"
contains docs/VUE_ASSET_VERSION_CONTRACT_HOTFIX_V27.40.34.md "canonical application release version"
contains src/test/java/ru/daniil/shifts/web/VueAppShellDesignSystemContractTest.java 'releaseVersion()'
contains src/test/java/ru/daniil/shifts/web/VueFrontendFoundationContractTest.java 'releaseVersion()'
not_contains src/test/java/ru/daniil/shifts/web/VueAppShellDesignSystemContractTest.java 'v=27.40.32'
not_contains src/test/java/ru/daniil/shifts/web/VueFrontendFoundationContractTest.java 'v=27.40.32'

# v27.40.33 Overtime Chart Projection Fixture Contract Hotfix
  contains CHANGES.md "v27.40.33 — Overtime Chart Projection Fixture Contract Hotfix"
  contains README.md "DutyLog v27.40.33 — Overtime Chart Projection Fixture Contract Hotfix"
  contains docs/OVERTIME_CHART_PROJECTION_FIXTURE_CONTRACT_HOTFIX_V27.40.33.md "contradictory overtime chart test fixtures"
  contains frontend/src/features/absence-time-bank/types/model.spec.ts 'dayEarnedHours: 3, dayUsedHours: 3, dayRemainingHours: 0'
  contains frontend/src/features/absence-time-bank/types/model.spec.ts 'sourceWorkedDate: "2026-08-02", dayEarnedHours: 2'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"

  # v27.40.32 Functional Parity Sweep II & Mobile Usability Closure
  contains CHANGES.md "v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure"
  contains README.md "DutyLog v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure"
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function todayShiftProjection'
  contains frontend/src/features/calendar-timeline/types/model.ts 'occurrence.startInstant'
  contains frontend/src/features/calendar-timeline/types/model.ts 'occurrence.endInstant'
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function durationCountdown'
  contains frontend/src/features/calendar-timeline/types/model.ts 'export function importantRelativeLabel'
  contains frontend/src/features/calendar-timeline/types/model.ts 'russianDayWord'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue ':aria-valuenow="shiftProgress"'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue 'setInterval(() => { nowMs.value = Date.now(); }, 30_000)'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue '<strong>{{ relativeImportant(item.date) }}</strong>'
  contains frontend/src/features/calendar-timeline/types/model.spec.ts 'restores Today live shift countdown and progress from immutable occurrence instants'
  contains frontend/src/features/calendar-timeline/types/model.spec.ts 'restores relative Important Days copy on Today'
  contains e2e/today-dashboard.spec.js "upcomingTomorrow.locator('strong')).toHaveText('завтра')"
  contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'vueTodayRestoresImmutableInstantProgressAndCountdownParity'
  contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'vueTodayRestoresRelativeImportantDayCopy'
  contains src/main/resources/static/index.html 'data-bounded-legacy-owner="first-run-onboarding"'
  contains docs/ROADMAP.md 'v27.40.32 — Functional Parity Sweep II'
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
  contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
  contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
  not_contains frontend/src/features/calendar-timeline/components/TodayPage.vue 'todayShiftCountdown">{{ facts.shift ?'

  # v27.40.30 Legacy Retirement Closure & First-Run Onboarding Boundary
  contains CHANGES.md "v27.40.30 — Legacy Retirement Closure & First-Run Onboarding Boundary"
  contains README.md "DutyLog v27.40.30 — Legacy Retirement Closure & First-Run Onboarding Boundary"
  contains docs/FIRST_RUN_ONBOARDING_BOUNDARY_AUDIT_V27.40.30.md "single bounded post-ready legacy presentation exception"
  contains docs/FIRST_RUN_ONBOARDING_BOUNDARY_AUDIT_V27.40.30.md "module update → module publication/refresh → profile onboarding completion → overlay release → service-worker registration"
  contains src/main/resources/static/index.html 'data-bounded-legacy-owner="first-run-onboarding"'
  contains src/main/resources/static/index.html 'id="firstRunOnboarding"'
  contains src/main/resources/static/js/20-data.js 'async function finishOnboarding'
  contains src/main/resources/static/js/20-data.js 'void window.DutyLogPwaRuntime?.register?.();'
  contains frontend/src/app/shellStore.ts 'this.onboardingCompleted = snapshot.profile?.onboardingCompleted === true'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'modulesLoaded.value && (onboardingCompleted.value || !online.value)'
  not_contains frontend/src/app/AppShell.vue 'firstRunOnboarding'
  contains docs/FRONTEND_ARCHITECTURE.md 'no second bounded legacy presentation owner is allowed'
  contains docs/MODULE_CONTRACTS.md 'First-run onboarding bounded presentation contract (v27.40.30)'
  contains docs/ROADMAP.md 'v27.40.32 — Functional Parity Sweep I'
  contains src/test/java/ru/daniil/shifts/web/FirstRunOnboardingBoundedLegacyExceptionFrontendContractTest.java 'class FirstRunOnboardingBoundedLegacyExceptionFrontendContractTest'
  contains src/test/java/ru/daniil/shifts/web/FirstRunOnboardingBoundedLegacyExceptionFrontendContractTest.java 'firstRunOnboardingIsTheOnlyExplicitBoundedLegacyPresentationOwner'
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
  contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
  contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"

  # v27.41.8 Shared Runtime Bundle Split
  contains CHANGES.md "v27.41.8 — Shared Runtime Bundle Split"
  contains README.md "DutyLog v27.41.8 — Shared Runtime Bundle Split"
  contains docs/SHARED_RUNTIME_BUNDLE_SPLIT_V27.41.8.md '565411 B raw / 141782 B gzip'
  contains frontend/vite.config.ts 'manualChunks: manualChunkName'
  contains frontend/vite.config.ts 'return "vendor"'
  contains frontend/vite.config.ts 'return "api-contract"'
  contains frontend/vite.config.ts 'return "platform"'
  contains frontend/vite.config.ts 'return "settings-workspace"'
  contains frontend/browser-bundle-budget.json '"maxChunkBytes": 300000'
  contains frontend/browser-bundle-budget.json '"maxChunkGzipBytes": 100000'
  contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

  # v27.41.9 Browser Bundle Source Contract Alignment Hotfix
  contains CHANGES.md "v27.41.9 — Browser Bundle Source Contract Alignment Hotfix"
  contains README.md "DutyLog v27.41.9 — Browser Bundle Source Contract Alignment Hotfix"
  contains docs/BROWSER_BUNDLE_SOURCE_CONTRACT_ALIGNMENT_HOTFIX_V27.41.9.md 'all 791 tests with one failure'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'audit.contains("budget.maxEntryBytes")'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'audit.contains("budget.maxChunkGzipBytes")'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'audit.contains("budget.maxTotalGzipBytes")'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'assertFalse(audit.contains("budget.maxBytes"))'

  # v27.42.0 People Profiles
  contains CHANGES.md "v27.42.0 — People Profiles"
  contains README.md "DutyLog v27.42.0 — People Profiles"
  contains docs/PEOPLE_PROFILES_V27.42.0.md 'one active person context'
# v27.42.1 People Profiles Source Contract Alignment Hotfix
contains CHANGES.md "v27.42.1 — People Profiles Source Contract Alignment Hotfix"
contains README.md "DutyLog v27.42.1 — People Profiles Source Contract Alignment Hotfix"
contains docs/PEOPLE_PROFILES_SOURCE_CONTRACT_ALIGNMENT_HOTFIX_V27.42.1.md "dayPanelOpen && viewingSelf"

# v27.42.2 Concurrent Schedule Seed Hotfix
contains CHANGES.md "v27.42.2 — Concurrent Schedule Seed Hotfix"
contains README.md "DutyLog v27.42.2 — Concurrent Schedule Seed Hotfix"
contains docs/CONCURRENT_SCHEDULE_SEED_HOTFIX_V27.42.2.md "PESSIMISTIC_WRITE"
contains src/test/java/ru/daniil/shifts/service/ScheduleTemplateConcurrentSeedTest.java "concurrentFirstListsSeedFivePresetsExactlyOnce"

# v27.42.3 Concurrent Schedule Seed Test Isolation Hotfix
contains CHANGES.md "v27.42.3 — Concurrent Schedule Seed Test Isolation Hotfix"
contains README.md "DutyLog v27.42.3 — Concurrent Schedule Seed Test Isolation Hotfix"
contains docs/CONCURRENT_SCHEDULE_SEED_TEST_ISOLATION_HOTFIX_V27.42.3.md "DirtiesContext"

# v27.42.4 Release Check Contract Alignment Hotfix
contains CHANGES.md "v27.42.4 — Release Check Contract Alignment Hotfix"
contains README.md "DutyLog v27.42.4 — Release Check Contract Alignment Hotfix"
contains docs/RELEASE_CHECK_CONTRACT_ALIGNMENT_HOTFIX_V27.42.4.md '792 `@Test` methods'

# v27.42.5 Release Check Shell Contract Hotfix
contains CHANGES.md "v27.42.5 — Release Check Shell Contract Hotfix"
contains README.md "DutyLog v27.42.5 — Release Check Shell Contract Hotfix"
contains docs/RELEASE_CHECK_SHELL_CONTRACT_HOTFIX_V27.42.5.md "concurrentFirstListsSeedFivePresetsExactlyOnce"
contains docs/RELEASE_CHECK_SHELL_CONTRACT_HOTFIX_V27.42.5.md '792 `@Test` methods'

# v27.42.6 Release Check Current-Version Contract Hotfix
contains CHANGES.md "v27.42.6 — Release Check Current-Version Contract Hotfix"
contains README.md "DutyLog v27.42.6 — Release Check Current-Version Contract Hotfix"
contains docs/RELEASE_CHECK_CURRENT_VERSION_CONTRACT_HOTFIX_V27.42.6.md 'current release artifact assertions use `${VERSION}`'
contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"
contains frontend/browser-bundle-budget.json "\"release\": \"${VERSION}\""
contains src/main/resources/static/service-worker.js "dutylog-shell-v${VERSION}-"
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java '<SelectedDayPanel v-if=\"dayPanelOpen && viewingSelf\"'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'dutylog.calendar.profile.v1'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'selectProfile(profileId: string)'
  contains frontend/src/features/calendar-timeline/types/model.ts 'dayFactsForProfile'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'id="calendarProfileBar"'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'dayPanelOpen && viewingSelf'
  not_contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'class="calendarLayerChip"'
  contains frontend/src/features/settings-workspace/components/ScheduleSettingsCard.vue 'Профили людей'
  contains e2e/schedule-templates-calendar-layers.spec.js 'calendarProfileToggle'

  # v27.40.29 Vue Logout Ownership & Offline Status Contract Hotfix
  contains CHANGES.md "v27.40.29 — Vue Logout Ownership & Offline Status Contract Hotfix"
  contains README.md "DutyLog v27.40.29 — Vue Logout Ownership & Offline Status Contract Hotfix"
  contains docs/VUE_LOGOUT_OWNERSHIP_OFFLINE_STATUS_CONTRACT_HOTFIX_V27.40.29.md "The v27.40.28 staging Playwright report executed all 48 scenarios: 46 passed and 2 failed"
  contains docs/VUE_LOGOUT_OWNERSHIP_OFFLINE_STATUS_CONTRACT_HOTFIX_V27.40.29.md 'contains no `/logout` request after the click'
  contains src/main/resources/static/js/10-core.js 'new CustomEvent("dutylog:logout-request")'
  not_contains src/main/resources/static/js/10-core.js 'document.getElementById("logout")?.click()'
  contains src/main/resources/static/js/70-user-boot.js 'async function performDutyLogLogout()'
  contains src/main/resources/static/js/70-user-boot.js 'window.addEventListener("dutylog:logout-request"'
  contains src/main/resources/static/js/70-user-boot.js 'await fetch("/logout"'
  contains src/main/resources/static/js/70-user-boot.js '$("logout")?.addEventListener("click"'
  contains frontend/src/app/AppShell.vue ":data-network-state=\"offline.online ? 'online' : 'offline'\""
  contains e2e/pwa-offline.spec.js "toHaveAttribute('data-network-state', 'offline')"
  not_contains e2e/pwa-offline.spec.js 'toContainText(/оффлайн|offline/i)'
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'offlineBrowserContractUsesSemanticNetworkStateInsteadOfTranslatedCopy'
  contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java 'vueLogoutNoLongerDependsOnTheRetiredLegacyHeaderButton'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
  contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"

  # v27.40.28 E2E Vue Shell Identity Contract Alignment Hotfix
  contains CHANGES.md "v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix"
  contains e2e/helpers.js "page.locator('[data-vue-shell-profile] > b')"
  not_contains e2e/helpers.js "page.locator('#whoami')"
  not_contains e2e/auth-onboarding.spec.js "page.locator('#whoami')"
  not_contains e2e/important-timezone.spec.js "page.locator('#whoami')"
  not_contains e2e/remember-me.spec.js "page.locator('#whoami')"
  contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java "page.locator('[data-vue-shell-profile] > b')"
  contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java "page.locator('#whoami')"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"

  # v27.40.27 Legacy Header Async Boot Ownership Hotfix
  contains CHANGES.md "v27.40.27 — Legacy Header Async Boot Ownership Hotfix"
  contains README.md "DutyLog v27.40.27 — Legacy Header Async Boot Ownership Hotfix"
  contains docs/LEGACY_HEADER_ASYNC_BOOT_OWNERSHIP_HOTFIX_V27.40.27.md 'Cannot set properties of null'
  contains docs/LEGACY_HEADER_ASYNC_BOOT_OWNERSHIP_HOTFIX_V27.40.27.md '#whoami'
  contains src/main/resources/static/js/70-user-boot.js 'const legacyWhoami = $("whoami");'
  contains src/main/resources/static/js/70-user-boot.js 'if (legacyWhoami) legacyWhoami.textContent = me.username;'
  not_contains src/main/resources/static/js/70-user-boot.js '$("whoami").textContent = me.username;'
  contains src/main/resources/static/js/70-user-boot.js 'const who = $("whoami");'
  contains src/main/resources/static/js/70-user-boot.js 'if (who) {'
  contains src/main/resources/static/js/70-user-boot.js 'who.parentNode?.insertBefore(av, who);'
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'authenticatedBootDoesNotRequireTheRetiredLegacyIdentityNode'
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'profilePublicationSurvivesLegacyHeaderRetirement'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
  contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"

  # v27.40.26 Legacy Header Retirement Selector Hotfix
  contains CHANGES.md "v27.40.26 — Legacy Header Retirement Selector Hotfix"
  contains README.md "DutyLog v27.40.26 — Legacy Header Retirement Selector Hotfix"
  contains docs/LEGACY_HEADER_RETIREMENT_SELECTOR_HOTFIX_V27.40.26.md 'body > .head'
  contains docs/LEGACY_HEADER_RETIREMENT_SELECTOR_HOTFIX_V27.40.26.md '#legacyGlobalHeader'
  contains src/main/resources/static/index.html 'id="legacyGlobalHeader"'
  contains src/main/resources/static/js/shell-bootstrap.js 'document.getElementById("legacyGlobalHeader")?.remove();'
  not_contains src/main/resources/static/js/shell-bootstrap.js 'document.querySelector("body > .head")?.remove();'
  contains e2e/vue-app-shell.spec.js "page.locator('#legacyGlobalHeader')).toHaveCount(0)"
  contains e2e/mobile-layout.spec.js "document.querySelector('.vue-shell-header').getBoundingClientRect().height"
  not_contains e2e/mobile-layout.spec.js "document.querySelector('.head').getBoundingClientRect().height"
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'document.getElementById(\"legacyGlobalHeader\")?.remove();'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
  contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
  contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
  contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"

  # v27.40.25 Vue Offline Sync Surface & Legacy Header Retirement
  contains CHANGES.md "v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement"
  contains README.md "DutyLog v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement"
  contains docs/VUE_OFFLINE_SYNC_SURFACE_LEGACY_HEADER_RETIREMENT_V27.40.25.md '`dataLayer.syncQueue()` remains the single offline queue executor'
  contains docs/FRONTEND_ARCHITECTURE.md "First-run onboarding is the only intentionally live post-ready legacy presentation exception in v27.40.29"
  contains docs/FRONTEND_ARCHITECTURE.md 'OfflineSyncModal.vue'
  contains docs/FRONTEND_ARCHITECTURE.md 'dataLayer remains the single offline mutation/sync owner'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
  contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
  contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
  contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"
  contains frontend/src/app/AppShell.vue 'id="offlineStatus"'
  contains frontend/src/app/AppShell.vue '<OfflineSyncModal'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineSyncDialog"'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineSyncNow"'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineFailedRetryAll"'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineExport"'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineFailedClear"'
  contains frontend/src/app/OfflineSyncModal.vue 'id="offlineDiagnosticsCopy"'
  not_contains frontend/src/app/OfflineSyncModal.vue 'dataLayer.syncQueue()'
  not_contains frontend/src/app/OfflineSyncModal.vue 'indexedDB.open('
  contains frontend/src/platform/bridge/legacyBridge.ts 'SAVE_FEEDBACK_EVENT = "dutylog:save-feedback"'
  contains frontend/src/platform/bridge/legacyBridge.ts 'offlineSyncDetails(): Promise<DutyLogOfflineSyncDetailsSnapshot | null>'
  contains frontend/src/platform/bridge/legacyBridge.ts 'async offlineRetryAllFailed()'
  contains frontend/src/types/window.d.ts 'interface DutyLogOfflineSyncStatusSnapshot'
  contains frontend/src/types/window.d.ts 'interface DutyLogOfflineSyncDetailsSnapshot'
  contains frontend/src/App.vue 'window.addEventListener(SAVE_FEEDBACK_EVENT, synchronizeSaveFeedback)'
  contains src/main/resources/static/js/10-core.js 'function legacyOfflineStatusSnapshot()'
  contains src/main/resources/static/js/10-core.js 'async offlineSyncDetails()'
  contains src/main/resources/static/js/10-core.js 'await dataLayer.syncQueue()'
  contains src/main/resources/static/js/20-data.js 'async syncQueue()'
  contains src/main/resources/static/js/20-data.js 'dataset.vueOfflineSync === "ready"'
  contains src/main/resources/static/js/20-data.js 'new CustomEvent("dutylog:save-feedback"'
  contains src/main/resources/static/js/shell-bootstrap.js 'document.getElementById("legacyGlobalHeader")?.remove();'
  contains src/main/resources/static/js/shell-bootstrap.js 'document.getElementById("offlineSyncDialog")?.remove();'
  contains src/main/resources/static/js/shell-bootstrap.js 'document.documentElement.dataset.vueOfflineSync = "ready"'
  contains src/main/resources/static/index.html 'id="legacyGlobalHeader"'
  contains src/main/resources/static/index.html 'id="firstRunOnboarding"'
  contains src/main/resources/static/index.html 'id="offlineSyncDialog"'
  contains e2e/vue-app-shell.spec.js "page.locator('#legacyGlobalHeader')).toHaveCount(0)"
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'class VueOfflineSyncSurfaceFrontendContractTest'
  contains src/test/java/ru/daniil/shifts/web/VueOfflineSyncSurfaceFrontendContractTest.java 'dataLayerRemainsTheSingleOfflineQueueExecutorAndSaveFeedbackMovesToVue'

  # v27.40.24 Final Legacy Ownership Audit & Dead UI Surface Retirement
  contains CHANGES.md "v27.40.24 — Final Legacy Ownership Audit & Dead UI Surface Retirement"
  contains README.md "DutyLog v27.40.24 — Final Legacy Ownership Audit & Dead UI Surface Retirement"
  contains docs/FINAL_LEGACY_OWNERSHIP_AUDIT_DEAD_UI_SURFACE_RETIREMENT_V27.40.24.md "zero legacy-owned user screens"
  contains docs/FRONTEND_ARCHITECTURE.md "Vue owns all user-facing screens"
  contains docs/FRONTEND_ARCHITECTURE.md "Known live legacy presentation is limited to first-run onboarding and offline/sync UX"
  contains docs/FRONTEND_ARCHITECTURE.md 'dataLayer remains the single offline mutation/sync owner'
  contains src/main/resources/static/js/shell-bootstrap.js 'for (const id of ["nextTopbar", "tabbar"]) document.getElementById(id)?.remove();'
  not_contains frontend/src/styles/design-system.css 'html[data-vue-shell="ready"] .nextTopbar'
  not_contains frontend/src/styles/design-system.css 'html[data-vue-shell="ready"] #tabbar'
  contains src/main/resources/static/js/10-core.js '"legacyOvertimeModal", "legacyUsageMigrationModal"'
  contains src/main/resources/static/js/10-core.js 'document.getElementById("legacyShiftModal")?.remove();'
  contains src/main/resources/static/js/10-core.js 'document.getElementById("legacyTaskDeadlineModal")?.remove();'
  contains src/main/resources/static/index.html 'id="firstRunOnboarding"'
  contains src/main/resources/static/index.html 'id="offlineStatus"'
  contains src/main/resources/static/index.html 'id="offlineSyncDialog"'
  contains src/main/resources/static/js/20-data.js 'const dataLayer = {'
  contains src/main/resources/static/js/20-data.js 'async syncQueue()'
  not_contains src/main/resources/static/js/70-user-boot.js 'applyRemainingLegacyRouteEffects'
  not_contains src/main/resources/static/js/70-user-boot.js 'dutylog:vue-route-committed'
  not_contains src/main/resources/static/index.html 'id="view-admin"'
  not_contains src/main/resources/static/index.html 'id="view-payroll"'
  contains e2e/vue-app-shell.spec.js "page.locator('#tabbar')).toHaveCount(0)"
  contains e2e/vue-app-shell.spec.js "page.locator('#nextTopbar')).toHaveCount(0)"
  contains e2e/design-system-shell.spec.js "page.locator('#nextTopbar')).toHaveCount(0)"
  contains src/test/java/ru/daniil/shifts/web/FinalLegacyOwnershipAuditFrontendContractTest.java 'remainingLegacyPresentationAndInfrastructureExceptionsAreExplicitAndBounded'
  contains docs/API.md "# DutyLog API v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
  contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
  contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."
  contains frontend/generated-lockfile-manifest.txt "committedLockfileSha256=${LOCKFILE_ACTUAL_SHA}"

  # v27.40.23 Pre-Vue Admin Fallback Contract Alignment Hotfix
  contains CHANGES.md "v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix"
  contains docs/PRE_VUE_ADMIN_FALLBACK_CONTRACT_ALIGNMENT_HOTFIX_V27.40.23.md "285 passed / 1 failed"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'name === \"admin\" ? \"settings\"'
  contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'VIEWS[name] ? name : \"today\"'
  not_contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'let active = VIEWS[name] ? name : \"today\"'
  contains src/main/resources/static/js/70-user-boot.js 'name === "admin" ? "settings"'
  not_contains src/main/resources/static/js/70-user-boot.js 'applyRemainingLegacyRouteEffects'
  not_contains src/main/resources/static/js/70-user-boot.js 'dutylog:vue-route-committed'

  # v27.40.22 Vue Admin Workspace & Final Live Legacy UI Retirement
  contains CHANGES.md "v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement"
  contains README.md "DutyLog v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement"
  contains docs/VUE_ADMIN_WORKSPACE_FINAL_LIVE_LEGACY_UI_RETIREMENT_V27.40.22.md "After Vue readiness, all user-facing routes are Vue-owned"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement acceptance"
  contains docs/API.md "132 operations / 138 schemas"
  contains src/main/java/ru/daniil/shifts/web/SystemController.java '@RequestMapping({"/api/admin", "/api/v1/admin"})'
  contains src/main/java/ru/daniil/shifts/config/SecurityConfig.java '"/api/admin/**", "/api/v1/admin/**"'
  contains frontend/src/app/AppShell.vue 'loader: () => import("@/features/admin/components/AdminWorkspace.vue")'
  contains frontend/src/app/AppShell.vue '<AdminWorkspace v-if='
  contains frontend/src/features/admin/components/AdminWorkspace.vue 'data-vue-domain-route="admin"'
  contains frontend/src/features/admin/components/AdminWorkspace.vue 'id="adminUsersList"'
  contains frontend/src/features/admin/components/AdminWorkspace.vue 'id="registrationEnabledToggle"'
  contains frontend/src/features/admin/components/AdminWorkspace.vue 'id="diagnosticsList"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("adminSystemStatus"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("listAdminUsers"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("updateAdminUserRole"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("resetAdminUserPassword"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("getAdminRegistrationSettings"'
  contains frontend/src/features/admin/api/adminApi.ts 'client.request("updateAdminRegistrationSettings"'
  contains frontend/src/app/shellStore.ts 'snapshot.modulesLoaded && snapshot.modules?.admin === false'
  contains frontend/src/app/shellStore.ts 'availableNavigation.filter(route => route !== "admin")'
  contains src/test/java/ru/daniil/shifts/web/AdminAccessTest.java '/api/v1/admin/status'
  contains src/test/java/ru/daniil/shifts/web/VueAdminWorkspaceRetirementFrontendContractTest.java 'noLiveLegacyAdminDomStateApiOrPostVueRouteAdapterRemains'
  not_contains src/main/resources/static/index.html '<section class="view adminView"'
  not_contains src/main/resources/static/js/10-core.js 'adminUsersPage:'
  not_contains src/main/resources/static/js/20-data.js 'async adminUsers('
  not_contains src/main/resources/static/js/60-settings.js 'function refreshAdminPanel'
  not_contains src/main/resources/static/js/60-settings.js 'function initAdminNavigation'
  not_contains src/main/resources/static/js/60-settings.js 'function initDiagnosticsEvents'
  not_contains src/main/resources/static/js/70-user-boot.js 'applyRemainingLegacyRouteEffects'
  not_contains src/main/resources/static/js/70-user-boot.js 'dutylog:vue-route-committed'
  not_contains frontend/src/platform/router/hashRoute.ts 'publishCommittedHashRoute'
  not_contains frontend/src/App.vue 'publishCommittedHashRoute'
  contains src/main/resources/static/js/70-user-boot.js 'name === "admin" ? "settings"'

  # v27.40.21 Vue Payroll Workspace Retirement
  contains CHANGES.md "v27.40.21 — Vue Payroll Workspace Retirement"
  contains README.md "DutyLog v27.40.21 — Vue Payroll Workspace Retirement"
  contains docs/VUE_PAYROLL_WORKSPACE_RETIREMENT_V27.40.21.md "Post-Vue legacy route effects are now Admin-only"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.21 — Vue Payroll Workspace Retirement acceptance"
  contains frontend/src/app/AppShell.vue 'loader: () => import("@/features/payroll/components/PayrollWorkspace.vue")'
  contains frontend/src/app/AppShell.vue '<PayrollWorkspace v-if='
  contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'data-vue-domain-owner="payroll"'
  contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'window.DutyLogVueDomains?.payroll'
  contains frontend/src/features/payroll/api/payrollApi.ts 'client.request("payrollPeriod"'
  contains frontend/src/features/payroll/api/payrollApi.ts 'client.request("calculatePayrollRevision"'
  contains frontend/src/types/window.d.ts 'readonly payroll?: DutyLogPayrollDomain'
  contains e2e/helpers.js 'window.DutyLogVueDomains?.payroll?.ready()'
  contains src/test/java/ru/daniil/shifts/web/VuePayrollWorkspaceRetirementFrontendContractTest.java 'legacyPayrollDomScriptAndRouteSideEffectsAreRetired'
  not_contains src/main/resources/static/index.html 'id="view-payroll"'
  not_contains src/main/resources/static/index.html 'js/45-payroll.js'
  not_contains src/main/resources/static/js/70-user-boot.js 'openPayrollView'
  not_contains src/main/resources/static/js/10-core.js 'payrollLoading'
  not_contains src/main/resources/static/js/20-data.js 'payrollPeriod(month)'

  # v27.40.20 E2E Release Version Contract Alignment Hotfix
  contains CHANGES.md "v27.40.20 — E2E Release Version Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.20 — E2E Release Version Contract Alignment Hotfix"
  contains docs/E2E_RELEASE_VERSION_CONTRACT_ALIGNMENT_HOTFIX_V27.40.20.md "two deterministic failures"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.20 — E2E Release Version Contract Alignment Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java "dutylog-shell-v\${releaseVersion}-"
  contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java 'Time and Overtime ${releaseVersion}//RU'
  not_contains src/test/java/ru/daniil/shifts/web/VueShellE2eNavigationCompatibilityHotfixTest.java "Time and Overtime 27.40.20//RU"

  # v27.40.19 E2E Release Version Authority Hotfix
  contains CHANGES.md "v27.40.19 — E2E Release Version Authority Hotfix"
  contains README.md "DutyLog v27.40.19 — E2E Release Version Authority Hotfix"
  contains docs/E2E_RELEASE_VERSION_AUTHORITY_HOTFIX_V27.40.19.md "e2e/release-version.js"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.19 — E2E Release Version Authority Hotfix acceptance"
  contains e2e/release-version.js "require('../package.json')"
  contains e2e/vue-frontend-foundation.spec.js "const { releaseVersion } = require('./release-version');"
  contains e2e/vue-app-shell.spec.js "const { releaseVersion } = require('./release-version');"
  contains e2e/external-calendar-sync.spec.js "const { releaseVersion } = require('./release-version');"
  contains e2e/pwa-upgrade.spec.js "const { releaseVersion } = require('./release-version');"
  not_contains e2e/vue-frontend-foundation.spec.js "27.40.16"
  not_contains e2e/vue-app-shell.spec.js "27.40.16"
  not_contains e2e/external-calendar-sync.spec.js "27.40.19"
  not_contains e2e/pwa-upgrade.spec.js "dutylog-shell-v27.40.28-"
  contains e2e/pwa-upgrade.spec.js "27.38.15-synthetic-previous"
  if grep -Eq '27\.[0-9]+\.[0-9]+' e2e/vue-frontend-foundation.spec.js e2e/vue-app-shell.spec.js e2e/external-calendar-sync.spec.js; then
    fail "current E2E release assertions must not hardcode semantic release versions"
  else
    ok "current E2E release assertions use canonical package version"
  fi
  if grep -E '27\.[0-9]+\.[0-9]+' e2e/pwa-upgrade.spec.js | grep -v '27.38.15-synthetic-previous' >/dev/null; then
    fail "PWA current-release assertion must use canonical package version"
  else
    ok "PWA keeps only the intentional synthetic previous-release literal"
  fi

  # v27.40.18 Calendar ICS Release Version Contract Hotfix
  contains CHANGES.md "v27.40.18 — Calendar ICS Release Version Contract Hotfix"
  contains README.md "DutyLog v27.40.18 — Calendar ICS Release Version Contract Hotfix"
  contains docs/CALENDAR_ICS_RELEASE_VERSION_CONTRACT_HOTFIX_V27.40.18.md 'Calendar ICS `PRODID`'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.18 — Calendar ICS Release Version Contract Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/CalendarIcsReleaseVersionContractTest.java 'calendarIcsProdIdTracksTheProjectReleaseVersion'
  contains src/test/java/ru/daniil/shifts/web/CalendarIcsReleaseVersionContractTest.java 'calendarIcsProdIdTracksTheProjectReleaseVersion'

  # v27.40.17 Vue Route Commit & Legacy Hash Listener Retirement
  contains CHANGES.md "v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement"
  contains README.md "DutyLog v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement"
  contains docs/VUE_ROUTE_COMMIT_LEGACY_HASH_LISTENER_RETIREMENT_V27.40.17.md "dutylog:vue-route-committed"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement acceptance"
  not_contains frontend/src/platform/router/hashRoute.ts 'VUE_ROUTE_COMMITTED_EVENT = "dutylog:vue-route-committed"'
  not_contains frontend/src/platform/router/hashRoute.ts 'export function publishCommittedHashRoute'
  not_contains frontend/src/App.vue 'publishCommittedHashRoute(route);'
  not_contains frontend/src/App.vue 'const committedKey = `${route.rawRoute}|${route.activeRoute}`'
  not_contains src/main/resources/static/js/70-user-boot.js 'function applyRemainingLegacyRouteEffects(active)'
  not_contains src/main/resources/static/js/70-user-boot.js 'window.addEventListener("dutylog:vue-route-committed", handleVueRouteCommitted)'
  contains src/main/resources/static/js/70-user-boot.js 'window.removeEventListener("hashchange", applyRoute);'
  contains src/test/java/ru/daniil/shifts/web/VueRouteCommitLegacyHashListenerRetirementTest.java 'legacyStopsListeningToHashAfterVueReadyAndHasNoPostVueRouteEffects'
  contains src/test/java/ru/daniil/shifts/web/VueRouteCommitLegacyHashListenerRetirementTest.java 'class VueRouteCommitLegacyHashListenerRetirementTest'
  not_contains frontend/src/App.vue 'props.bridge.navigate'
  not_contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'window.addEventListener("online", reconnect)'

  # v27.40.16 Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix
  contains CHANGES.md "v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix"
  contains README.md "DutyLog v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix"
  contains docs/VUE_ROUTE_ENTRY_FRESHNESS_TODAY_WORKSPACE_NOTE_RYW_HOTFIX_V27.40.16.md "read-your-write"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix acceptance"
  contains frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue "await store.refresh();"
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue "await store.refresh(true);"
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue "workspaceDefinition(appearance.value.themeConfig)"
  contains frontend/src/features/productivity/stores/productivityStore.ts "this.selectedNotes = sortDayNotes(["
  contains frontend/src/features/productivity/stores/productivityStore.ts "The create response is already the authoritative DTO."
  contains src/test/java/ru/daniil/shifts/web/VueRouteEntryFreshnessAndNoteReadYourWriteHotfixTest.java "class VueRouteEntryFreshnessAndNoteReadYourWriteHotfixTest"
  # v27.40.15 Route Guard Profile Publication Contract Alignment Hotfix
  contains CHANGES.md "v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix"
  contains docs/VUE_ROUTE_GUARD_PROFILE_PUBLICATION_CONTRACT_ALIGNMENT_HOTFIX_V27.40.15.md "publishLegacyPlatformState"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/VueRouteGuardAuthorityCutoverTest.java 'String loadProfile = loadProfileSurface(boot);'
  contains src/test/java/ru/daniil/shifts/web/VueRouteGuardAuthorityCutoverTest.java 'assertTrue(loadProfile.contains("publishLegacyPlatformState();"));'
  contains src/test/java/ru/daniil/shifts/web/VueRouteGuardAuthorityCutoverTest.java 'loadProfile.indexOf("publishLegacyPlatformState();") > loadProfile.indexOf("applyRoute();")'
  not_contains src/test/java/ru/daniil/shifts/web/VueRouteGuardAuthorityCutoverTest.java 'boot.contains("Profile load still must publish authoritative access state")'
  contains frontend/src/platform/router/hashRoute.ts 'export function guardHashRoute'
  contains frontend/src/platform/router/hashRoute.ts 'activeRoute === "admin" && access.profileLoaded && !access.admin'
  contains frontend/src/platform/router/hashRoute.ts 'access.modulesLoaded && access.modules[moduleKey] === false'
  contains frontend/src/App.vue 'const route = guardHashRoute(requested'
  contains frontend/src/App.vue 'document.body.dataset.view = route.activeRoute'
  contains frontend/src/App.vue 'if (route.rawRoute !== requested.rawRoute) navigateHashRoute(route.rawRoute)'
  contains frontend/src/app/shellStore.ts 'profileLoaded: false'
  contains frontend/src/app/shellStore.ts 'this.profileLoaded = snapshot.profile != null'
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'route !== "calendar" && store.dayPanelOpen'
  contains src/main/resources/static/js/70-user-boot.js 'document.documentElement.dataset.vueShell === "ready"'
  not_contains src/main/resources/static/js/70-user-boot.js 'const payrollView = document.getElementById(VIEWS.payroll)'
  not_contains src/main/resources/static/js/70-user-boot.js 'const adminView = document.getElementById(VIEWS.admin)'
  contains src/main/resources/static/js/70-user-boot.js 'Pre-Vue recovery is intentionally limited to legacy-compatible screens.'
  contains e2e/vue-app-shell.spec.js "window.location.hash = '#tasks'"
  contains e2e/vue-app-shell.spec.js "window.location.hash = '#admin'"
  contains src/test/java/ru/daniil/shifts/web/VueRouteGuardAuthorityCutoverTest.java 'postVueLegacyRouterHasNoLiveUiSideEffects'
  not_contains frontend/src/App.vue 'selectDay(null)'

  # v27.40.13 Vue Route State Authority Cutover
  contains CHANGES.md "v27.40.13 — Vue Route State Authority Cutover"
  contains README.md "DutyLog v27.40.13 — Vue Route State Authority Cutover"
  contains docs/VUE_ROUTE_STATE_AUTHORITY_CUTOVER_V27.40.13.md "Vue now owns reading, writing and subscribing to the route state directly"
  contains docs/ROADMAP.md "## v27.40.13 — Vue Route State Authority Cutover — accepted predecessor"
  contains docs/REGRESSION_TEST_BASELINE.md "Historical v27.40.13 extension:"
  contains docs/RELEASE_CHECKLIST.md "v27.40.13 — Vue Route State Authority Cutover acceptance — accepted predecessor"
  contains frontend/src/platform/router/hashRoute.ts 'export function readHashRoute'
  contains frontend/src/platform/router/hashRoute.ts 'export function navigateHashRoute'
  contains frontend/src/platform/router/hashRoute.ts 'target.addEventListener("hashchange", handler)'
  contains frontend/src/App.vue 'subscribeHashRoute(() => synchronizeRoute())'
  contains frontend/src/app/shellStore.ts 'synchronizeRoute(rawRoute: string)'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'navigate(view:'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'type: "navigate"'
  contains frontend/src/types/window.d.ts 'navigate(view: string): void;'
  not_contains frontend/src/types/window.d.ts 'navigateLegacy(view: string): void;'
  not_contains frontend/src/App.vue 'snapshot.route'
  not_contains frontend/src/app/shellStore.ts 'snapshot.route'
  not_contains src/main/resources/static/js/10-core.js 'route: String(window.location.hash'
  contains e2e/helpers.js 'platform.navigate(target)'
  contains e2e/helpers.js 'window.DutyLogLegacyPlatform?.navigate(target)'
  contains src/main/resources/static/js/70-user-boot.js 'window.addEventListener("hashchange", applyRoute)'
  contains src/main/resources/static/js/10-core.js '  navigate(view){'

  # v27.40.11 Vue Shift Type Manager Modal Retirement
  contains CHANGES.md "v27.40.11 — Vue Shift Type Manager Modal Retirement"
  contains README.md "DutyLog v27.40.11 — Vue Shift Type Manager Modal Retirement"
  contains docs/VUE_SHIFT_TYPE_MANAGER_MODAL_RETIREMENT_V27.40.11.md "ShiftTypeManagerModal.vue"
  contains docs/ROADMAP.md "## v27.40.11 — Vue Shift Type Manager Modal Retirement — accepted predecessor"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.11 — Vue Shift Type Manager Modal Retirement acceptance — accepted predecessor"
  contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue 'id="shiftTypeModal"'
  contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue 'id="shiftTypeForm"'
  contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue 'id="customList"'
  contains frontend/src/features/settings-workspace/types/domain.ts 'openShiftTypeManager(editId?: number | null): void;'
  contains frontend/src/features/settings-workspace/components/SettingsWorkspace.vue 'settingsWorkspace: domain'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'DutyLogVueDomains?.settingsWorkspace?.openShiftTypeManager()'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("createShiftType"'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'client.request("deleteShiftType"'
  contains src/main/resources/static/js/10-core.js 'document.getElementById("shiftTypeModal")?.remove()'
  contains src/main/resources/static/js/60-settings.js 'vueManager.openShiftTypeManager'
  contains e2e/editor-modals.spec.js "waitForApi(page, 'POST', '/api/v1/shift-types', 201)"
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openShiftTypeManager'
  not_contains frontend/src/types/window.d.ts 'openShiftTypeManager?()'
  not_contains src/main/resources/static/js/10-core.js 'openShiftTypeManager(){'

  # v27.40.10 Vue Quick Actions Modal Retirement — accepted predecessor
  contains CHANGES.md "v27.40.10 — Vue Quick Actions Modal Retirement"
  contains README.md "DutyLog v27.40.10 — Vue Quick Actions Modal Retirement"
  contains docs/VUE_QUICK_ACTIONS_MODAL_RETIREMENT_V27.40.10.md "QuickActionsModal.vue"
  contains docs/ROADMAP.md "## v27.40.10 — Vue Quick Actions Modal Retirement — accepted predecessor"
  contains docs/RELEASE_CHECKLIST.md "v27.40.10 — Vue Quick Actions Modal Retirement acceptance — accepted predecessor"
  contains frontend/src/features/productivity/components/QuickActionsModal.vue 'id="quickActionsModal"'
  contains frontend/src/features/productivity/components/QuickActionsModal.vue 'id="quickActionInbox"'
  contains frontend/src/features/productivity/components/QuickActionsModal.vue 'id="quickActionUsage"'
  contains frontend/src/features/productivity/components/QuickActionsModal.vue 'openAbsenceComposer({ date, source: "quick-add" })'
  contains frontend/src/features/productivity/types/domain.ts 'openQuickActions(date?: string): void;'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'openQuickActions: (date?: string)'
  contains frontend/src/features/calendar-timeline/components/TodayPage.vue 'DutyLogVueDomains?.productivity?.openQuickActions(workDate.value)'
  contains src/main/resources/static/js/50-tasks.js 'DutyLogVueDomains?.productivity?.openQuickActions?.(state.selected || todayKey())'
  contains src/main/resources/static/js/10-core.js '"quickActionsModal", "noteFullscreen"'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openQuickActions(date: string)'
  not_contains frontend/src/types/window.d.ts 'openQuickActions?(date: string)'
  not_contains src/main/resources/static/js/10-core.js 'openQuickActions(date){'
  not_contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'window.addEventListener("online"'

  # v27.40.9 Vue Shell Navigation Model Retirement — accepted predecessor
  contains CHANGES.md "v27.40.9 — Vue Shell Navigation Model Retirement"
  contains README.md "DutyLog v27.40.9 — Vue Shell Navigation Model Retirement"
  contains docs/VUE_SHELL_NAVIGATION_MODEL_RETIREMENT_V27.40.9.md "#tabbar"
  contains docs/ROADMAP.md "## v27.40.9 — Vue Shell Navigation Model Retirement — accepted predecessor"
  contains src/main/resources/static/js/10-core.js 'window.DutyLogUI?.workspaceDefinition?.(config)'
  contains src/main/resources/static/js/10-core.js 'LEGACY_ROUTE_MODULES'
  not_contains src/main/resources/static/js/10-core.js 'document.querySelectorAll("#tabbar a[data-view]")'
  contains frontend/src/app/navigation.ts 'ru: "Настройки", en: "Settings"'
  contains frontend/src/app/AppNavigation.vue "language === 'en' ? 'More' : 'Ещё'"
  contains e2e/vue-app-shell.spec.js "await expect(settings).toContainText('Настройки')"
  contains e2e/vue-app-shell.spec.js "await expect(more).toContainText('Ещё')"

  # v27.40.8 Offline Reconnect Source Contract Alignment Hotfix
  contains CHANGES.md "v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix"
  contains docs/OFFLINE_RECONNECT_SOURCE_CONTRACT_ALIGNMENT_HOTFIX_V27.40.8.md "758"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'int unmountStart = notes.indexOf("onBeforeUnmount(() => {")'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'String unmount = notes.substring(unmountStart, unmountEnd)'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'unmount.contains("if (timer != null && currentNote.value)")'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'unmount.contains("globalThis.clearTimeout(timer)")'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'unmount.contains("void store.updateNote(currentNote.value.id")'


  # v27.40.7 Selected-Day Parity & Offline Reconnect Ownership Hotfix
  contains CHANGES.md "v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix"
  contains README.md "DutyLog v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix"
  contains docs/VUE_CALENDAR_SELECTED_DAY_PARITY_RECONNECT_HOTFIX_V27.40.7.md "43 clean passes, 1 retry-only flaky scenario, and 4 final failures"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix acceptance"
  contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue 'truth.value.scheduledStartTime && truth.value.scheduledEndTime'
  contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue 'scheduledBreakMinutes'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'allocationRangeLabels(allocation)'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue '24:00'
  contains frontend/src/features/productivity/components/SelectedDayNotes.vue 'if (timer != null && currentNote.value)'
  contains e2e/multiple-daily-notes.spec.js 'editorLeft'
  contains e2e/multiple-daily-notes.spec.js 'listRight'
  not_contains e2e/multiple-daily-notes.spec.js 'expect(noteLayout.editorTop).toBeGreaterThanOrEqual(noteLayout.listBottom - 1);'

  # v27.40.6 Selected-Day Schedule Preview Key Strict Type Hotfix
  contains CHANGES.md "v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix"
  contains README.md "DutyLog v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix"
  contains docs/VUE_CALENDAR_SELECTED_DAY_PREVIEW_KEY_STRICT_TYPE_HOTFIX_V27.40.6.md "TS2379"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix acceptance"
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'v-for="(item, index) in schedulePreview?.items?.slice(0, 14) ?? []"'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue ':key="item.date ?? `preview-${index}`"'
  not_contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue ':key="item.date" class="vue-schedule-preview-row"'

  # v27.40.5 Selected-Day Strict Type Contract Alignment Hotfix
  contains CHANGES.md "v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix"
  contains README.md "DutyLog v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix"
  contains docs/VUE_CALENDAR_SELECTED_DAY_STRICT_TYPE_CONTRACT_ALIGNMENT_HOTFIX_V27.40.5.md "stale static/source contract"
  contains docs/ROADMAP.md "## v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix — predecessor hotfix"
  contains docs/RELEASE_CHECKLIST.md "v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix acceptance"
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineStrictTypecheckHotfixTest.java 'import type { CalendarDaySection, CalendarMode, DutyLogCalendarTimelineDomain }'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineStrictTypecheckHotfixTest.java 'openDay: async (date: string, section?: CalendarDaySection | null)'
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'import type { CalendarDaySection, CalendarMode, DutyLogCalendarTimelineDomain }'
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'openDay: async (date: string, section?: CalendarDaySection | null)'

  # v27.40.4 Vue Calendar Selected-Day Panel Retirement
  contains CHANGES.md "v27.40.4 — Vue Calendar Selected-Day Panel Retirement"
  contains README.md "DutyLog v27.40.4 — Vue Calendar Selected-Day Panel Retirement"
  contains docs/VUE_CALENDAR_SELECTED_DAY_PANEL_RETIREMENT_V27.40.4.md "SelectedDayPanel.vue"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.40.4 — Vue Calendar Selected-Day Panel Retirement acceptance"
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'SelectedDayPanel'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'data-vue-selected-day-panel'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'props.bridge.writeCalendarDay'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'shiftSourceDate'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'calendarTimelineApi.previewScheduleTemplate'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'vueSelectedDayTasksMount'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'vueSelectedDayNotesMount'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'vueSelectedDayImportantMount'
  contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue 'document.body.classList.add("panel-open")'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "'with-panel': dayPanelOpen"
  contains frontend/src/features/calendar-timeline/calendar-timeline.css '.layout.with-panel .vue-selected-day-panel'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue '<Teleport defer'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'dayPanelOpen: false'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'openDayPanel(date: string, section: CalendarDaySection | null = null)'
  contains frontend/src/platform/bridge/legacyBridge.ts 'writeCalendarDay(date: string, patch: Record<string, unknown>)'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'attachCalendarEditor'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'parkCalendarEditor'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openCalendarDay'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'openCalendarSection'
  not_contains frontend/src/platform/bridge/legacyBridge.ts 'closeCalendarDay'
  contains src/main/resources/static/js/10-core.js 'data-vue-calendar-selected-day'
  contains src/main/resources/static/js/10-core.js 'async writeCalendarDay(date, patch)'
  contains src/main/resources/static/js/10-core.js 'dataLayer.putDay(key, next)'
  not_contains src/main/resources/static/js/10-core.js 'document.body.appendChild(panel)'
  contains src/main/resources/static/js/30-calendar.js 'dataset.vueCalendarSelectedDay === "ready"'
  contains src/main/resources/static/js/50-tasks.js 'dataset.vueCalendarSelectedDay === "ready"'
  contains src/main/resources/static/js/39-vacation-planner.js 'dataset.vueCalendarSelectedDay === "ready"'
  contains src/main/resources/static/js/40-overtime.js 'dataset.vueCalendarSelectedDay === "ready"'
  contains e2e/vue-calendar-timeline-migration.spec.js '#panel[data-vue-selected-day-panel]'
  contains e2e/vue-calendar-timeline-migration.spec.js "page.locator('#calendarLegacyPanelHost')).toHaveCount(0)"
  contains e2e/schedule-templates-calendar-layers.spec.js '/\/api\/v1\/schedule-templates\/\d+\/preview$/'
  contains e2e/schedule-templates-calendar-layers.spec.js '/\/api\/v1\/schedule-templates\/\d+\/apply$/'

  # v27.40.3 Notification Settings First-Read Serialization & Timezone Parity Hotfix
  contains CHANGES.md "v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix"
  contains README.md "DutyLog v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix"
  contains docs/VUE_NOTIFICATION_SETTINGS_FIRST_READ_TIMEZONE_PARITY_HOTFIX_V27.40.3.md "32 clean passes, 10 retry-only flaky scenarios and 6 final failures"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/RELEASE_CHECKLIST.md "v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix acceptance"
  contains src/main/java/ru/daniil/shifts/repo/UserRepository.java "LockModeType.PESSIMISTIC_WRITE"
  contains src/main/java/ru/daniil/shifts/repo/UserRepository.java "findForUpdateById"
  contains src/main/java/ru/daniil/shifts/service/NotificationService.java "userRepository.findForUpdateById(user.getId())"
  contains src/main/java/ru/daniil/shifts/service/NotificationService.java "settingsRepo.findByOwner(lockedOwner)"
  contains src/main/java/ru/daniil/shifts/service/NotificationService.java "settingsRepo.saveAndFlush(new NotificationSettings(lockedOwner))"
  contains frontend/src/features/settings-workspace/components/TimeSettingsCard.vue "TIMEZONE_FALLBACKS"
  contains frontend/src/features/settings-workspace/components/TimeSettingsCard.vue '"Europe/Kyiv"'
  contains e2e/important-timezone.spec.js "toHaveText('Часовой пояс сохранён')"
  contains e2e/task-details.spec.js "toHaveText('Часовой пояс сохранён')"
  not_contains e2e/important-timezone.spec.js "toContainText(/сохранено|saved/i)"
  not_contains e2e/task-details.spec.js "toContainText(/сохранено|saved/i)"

  # v27.40.2 Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix
  contains CHANGES.md "v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix"
  contains README.md "DutyLog v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix"
  contains docs/VUE_SETTINGS_BOOTSTRAP_SERIALIZATION_MIGRATION_PREVIEW_QUERY_HOTFIX_V27.40.2.md "Alternating which endpoint fails"
  contains docs/ROADMAP.md "## v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix — predecessor"
  contains docs/RELEASE_CHECKLIST.md "v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix acceptance"
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'previewLegacyShiftMigration", { query: { sourceTimezone } }'
  contains frontend/src/features/settings-workspace/api/settingsWorkspaceApi.ts 'previewLegacyTaskDeadlineMigration", { query: { sourceTimezone } }'
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'const scheduleTemplates = await api.scheduleTemplates();'
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'const calendarLayers = await api.calendarLayers();'
  contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'const sourceTimezone = this.profile?.workTimezone || timeContext?.workTimezone || "UTC";'
  not_contains frontend/src/features/settings-workspace/stores/settingsWorkspaceStore.ts 'api.timeContext(), api.shiftTypes(), api.scheduleTemplates(), api.calendarLayers()'

  # v27.39.3 Frontend Diagnostics Release Version Source Hotfix
  contains CHANGES.md "v27.39.3 — Frontend Diagnostics Release Version Source Hotfix"
  contains README.md "DutyLog v27.39.3 — Frontend Diagnostics Release Version Source Hotfix"
  contains docs/VUE_SETTINGS_FRONTEND_DIAGNOSTICS_VERSION_HOTFIX_V27.39.3.md "frontend/package.json"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/RELEASE_CHECKLIST.md "v27.39.3 — Frontend Diagnostics Release Version Source Hotfix acceptance"
  contains frontend/vite.config.ts 'import packageMetadata from "./package.json";'
  contains frontend/vite.config.ts 'const releaseVersion = packageMetadata.version;'
  not_contains frontend/vite.config.ts 'const releaseVersion = "27.'
  contains frontend/src/platform/diagnostics/frontendDiagnostics.spec.ts 'import packageMetadata from "../../../package.json";'
  contains frontend/src/platform/diagnostics/frontendDiagnostics.spec.ts 'expect(failure.releaseVersion).toBe(packageMetadata.version);'

  # v27.38.15 Module Cache Authority Browser Parity Hotfix
  contains CHANGES.md "v27.38.15 — Module Cache Authority Browser Parity Hotfix"
  contains README.md "v27.38.15 — Module Cache Authority Browser Parity Hotfix"
  contains docs/VUE_MODULE_CACHE_AUTHORITY_BROWSER_PARITY_HOTFIX_V27.38.15.md "46 passed / 1 failed"
  contains src/main/resources/static/js/20-data.js 'const cachedBundle = { ...snap.bundle };'
  contains src/main/resources/static/js/20-data.js 'if (state.modulesLoaded) cachedBundle.modules = (state.modulesList || []).map(item => ({ ...item }));'
  contains src/main/resources/static/js/20-data.js 'applyBundle(cachedBundle, true);'
  not_contains src/main/resources/static/js/20-data.js 'applyBundle(snap.bundle, true);'
  contains src/main/resources/static/js/20-data.js 'await loadMonth({ fresh:true });'
  contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'month cache не должен откатывать уже загруженную глобальную карту модулей'
  contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'после authoritative module PATCH календарь должен обходить pre-mutation IndexedDB snapshot'
  contains src/main/resources/static/service-worker.js "dutylog-shell-v${VERSION}-"

  # v27.38.14 Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix
  contains CHANGES.md "v27.38.14 — Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix"
  contains README.md "v27.38.14 — Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix"
  contains docs/VUE_MODULE_RUNTIME_GATE_PAGE_LIFECYCLE_BROWSER_PARITY_HOTFIX_V27.38.14.md "46/47"
  contains docs/ROADMAP.md "v27.41.0 — Vacation Entitlement & Accrual Engine"
  contains docs/ROADMAP.md "v27.42.0 — Payroll Calculation Engine"
  contains frontend/src/features/productivity/stores/productivityStore.ts 'function runtimeModuleEnabled(key: string): boolean'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'const snapshot = bridge?.snapshot()'
  contains frontend/src/features/productivity/stores/productivityStore.ts '!runtimeModuleEnabled("tasks")'
  contains frontend/src/features/productivity/stores/productivityStore.ts '!runtimeModuleEnabled("important_dates")'
  contains frontend/src/features/productivity/stores/productivityStore.ts '!runtimeModuleEnabled("notes")'
  contains src/main/resources/static/js/20-data.js 'const optimisticList = previousList.map'
  contains src/main/resources/static/js/20-data.js 'if (!enabled) setModuleList(optimisticList);'
  contains src/main/resources/static/js/20-data.js 'setModuleList(previousList);'
  contains src/main/resources/static/js/70-user-boot.js 'window.addEventListener("pagehide"'
  contains src/main/resources/static/js/70-user-boot.js 'function expectedPageLifecycleFetchAbort(error)'
  contains src/main/resources/static/js/70-user-boot.js 'if (expectedPageLifecycleFetchAbort(err)) return;'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'optimisticModuleGate'
  contains src/test/java/ru/daniil/shifts/web/CalendarMonthReloadContractTest.java 'expectedPageLifecycleFetchAbort'
  contains src/main/resources/static/service-worker.js "dutylog-shell-v${VERSION}-"

  # v27.38.13 Vue Productivity Legacy Renderer Retirement Barrier Hotfix
  contains CHANGES.md "v27.38.13 — Vue Productivity Legacy Renderer Retirement Barrier Hotfix"
  contains README.md "v27.38.13 — Vue Productivity Legacy Renderer Retirement Barrier Hotfix"
  contains docs/VUE_PRODUCTIVITY_LEGACY_RENDERER_RETIREMENT_BARRIER_HOTFIX_V27.38.13.md "44/47"
  contains docs/VUE_PRODUCTIVITY_LEGACY_RENDERER_RETIREMENT_BARRIER_HOTFIX_V27.38.13.md 'value="all"'
  contains src/main/resources/static/js/50-tasks.js 'function vueOwnsProductivityUi()'
  contains src/main/resources/static/js/50-tasks.js 'function renderTaskMetadataSuggestions(){'
  contains src/main/resources/static/js/50-tasks.js 'const metadata = await api.taskMetadata();'
  contains src/main/resources/static/js/50-tasks.js 'function renderInbox(){'
  contains src/main/resources/static/js/50-tasks.js 'function renderTaskBoardCategoryFilter(){'
  contains src/main/resources/static/js/50-tasks.js 'function renderTaskBoardProjectFilter(){'
  contains src/main/resources/static/js/50-tasks.js 'function syncTaskBoardFiltersToInputs(){'
  contains src/main/resources/static/js/50-tasks.js 'function renderTaskBoard(){'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'count(tasks, "if (vueOwnsProductivityUi()) return;") >= 14'

  # v27.38.12 Vue Productivity Summary Ownership & PWA E2E Parity Hotfix
  contains CHANGES.md "v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix"
  contains README.md "v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix"
  contains docs/VUE_PRODUCTIVITY_SUMMARY_OWNERSHIP_PWA_E2E_PARITY_HOTFIX_V27.38.12.md "42 passed / 5 failed"
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'data-vue-productivity-summary="tasks"'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'data-vue-productivity-summary="notes"'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'data-vue-productivity-summary="important"'
  contains src/main/resources/static/js/30-calendar.js 'const vueOwnsProductivitySummaries = document.documentElement.dataset.vueProductivity === "ready";'
  contains src/main/resources/static/js/30-calendar.js 'if ($("sumTasks") && !vueOwnsProductivitySummaries)'
  contains src/main/resources/static/js/30-calendar.js 'if ($("sumImp") && !vueOwnsProductivitySummaries)'
  contains src/main/resources/static/js/30-calendar.js 'if (!vueOwnsProductivitySummaries)'
  contains e2e/pwa-upgrade.spec.js "preset: 'basic'"
  not_contains e2e/pwa-upgrade.spec.js "preset: 'minimum'"

  # v27.38.11 Vue Read-Your-Write & PWA Activation Browser Parity Hotfix
  contains CHANGES.md "v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix"
  contains README.md "v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix"
  contains docs/VUE_READ_YOUR_WRITE_PWA_ACTIVATION_BROWSER_PARITY_HOTFIX_V27.38.11.md "42 passed / 5 failed"
  contains e2e/multiple-daily-notes.spec.js "'/api/v1/calendar'"
  not_contains e2e/multiple-daily-notes.spec.js "pathname === '/api/calendar'"
  contains frontend/src/features/productivity/stores/productivityStore.ts 'const taskReadYourWrite = new Map<number, Task>()'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'taskReadYourWrite.set(saved.id, saved)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'for (const saved of taskReadYourWrite.values())'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'taskReadYourWrite.delete(saved.id)'
  contains src/main/resources/static/js/70-user-boot.js 'navigator.serviceWorker.getRegistration("/")'
  contains src/main/resources/static/js/70-user-boot.js 'if (existingRegistration) await registration.update()'

  # v27.38.10 Vue Offline, Task Publication & PWA Browser Parity Hotfix
  contains CHANGES.md "v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix"
  contains README.md "v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix"
  contains docs/VUE_OFFLINE_TASK_PWA_BROWSER_PARITY_HOTFIX_V27.38.10.md "40 passed / 6 failed / 1 flaky"
  contains docs/ROADMAP.md "v27.42.0 — Payroll Calculation Engine"
  contains e2e/multiple-daily-notes.spec.js '/^\/api\/notes\/\d+$/'
  contains e2e/multiple-daily-notes.spec.js '/^\/api\/v1\/notes\/\d+$/'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'const offline = typeof navigator !== "undefined" && !navigator.onLine && bridge !== null'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'tasksEnabled && !offline ? this.loadBoard() : Promise.resolve(true)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'importantEnabled && !offline ? this.loadImportantDays() : Promise.resolve(true)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'tasksEnabled && !offline ? this.loadInbox() : Promise.resolve(true)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'publishSavedTask(saved: Task)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'items[boardIndex] = saved'
  not_contains frontend/src/features/productivity/stores/productivityStore.ts 'this.board = { ...this.board, items: sortDayTasks(items)'
  contains src/main/resources/static/js/70-user-boot.js 'window.DutyLogPwaRuntime = Object.freeze'
  contains src/main/resources/static/js/70-user-boot.js 'state.profile?.onboardingCompleted === true'
  contains src/main/resources/static/js/20-data.js 'window.DutyLogPwaRuntime?.register?.()'
  not_contains src/main/resources/static/js/login.js 'serviceWorker.register'

  # v27.38.9 Vue Read-Model & Offline Browser Parity Hotfix
  contains CHANGES.md "v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix"
  contains README.md "v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix"
  contains docs/VUE_READ_MODEL_OFFLINE_BROWSER_PARITY_HOTFIX_V27.38.9.md "37 passed and 10 failed"
  not_contains e2e/calendar-persistence.spec.js "page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/calendar'"
  contains e2e/important-timezone.spec.js '/^\/api\/v1\/important-days\/\d+$/'
  contains e2e/schedule-templates-calendar-layers.spec.js '#calendarProfileBar'
  contains e2e/multiple-daily-notes.spec.js '/^\/api\/notes\/\d+$/'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue '(onboardingCompleted.value || !online.value)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'defaultBoardAccepts(saved, this)'
  not_contains src/main/resources/static/js/login.js 'serviceWorker.register'

  # v27.38.8 Vue Shared Browser Parity Hotfix
  contains CHANGES.md "v27.38.8 — Vue Shared Browser Parity Hotfix"
  contains README.md "v27.38.8 — Vue Shared Browser Parity Hotfix"
  contains docs/VUE_SHARED_BROWSER_PARITY_HOTFIX_V27.38.8.md "four shared browser-parity root causes"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.38.8 — Vue Shared Browser Parity Hotfix acceptance"
  contains e2e/helpers.js "Clicking an already-focused"
  not_contains e2e/helpers.js 'not.toHaveClass(/sel/)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'taskDraftSnapshot(this.taskDraft)'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'importantDraftSnapshot(this.importantDraft)'
  not_contains frontend/src/features/productivity/stores/productivityStore.ts 'structuredClone(this.taskDraft)'
  not_contains frontend/src/features/productivity/stores/productivityStore.ts 'structuredClone(this.importantDraft)'
  contains frontend/src/features/productivity/components/TasksPage.vue 'deadlineLabel(task)'
  contains src/main/resources/static/js/70-user-boot.js 'let hasController = Boolean(navigator.serviceWorker.controller);'
  contains src/main/resources/static/js/70-user-boot.js 'if (!hasController) { hasController = true; return; }'

  # v27.38.7 Vue Productivity Module Readiness Browser Canary Hotfix
  contains CHANGES.md "v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix"
  contains README.md "v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix"
  contains docs/WINDOWS_FRONTEND_GATE_CALENDAR_CONTRACT_ALIGNMENT_HOTFIX_V27.38.5.md "Full 47/47 Chromium remains required before acceptance"
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix acceptance"
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'calendar.openDayPanel(targetDate, "notes")'
  contains frontend/src/features/productivity/stores/productivityStore.ts 'addMinutesToDateTime'
  contains frontend/src/features/calendar-timeline/types/model.ts 'function dateSpanContains'
  contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'class="partialAbsenceBar"'
  contains src/main/resources/static/js/50-tasks.js 'if (document.documentElement.dataset.vueProductivity === "ready") return;'
  contains src/main/resources/static/js/37-calendar-experience.js 'document.documentElement.dataset.vueCalendarTimeline === "ready"'
  contains src/main/resources/static/js/38-schedule-layers.js 'document.documentElement.dataset.vueCalendarTimeline === "ready"'
  contains src/main/resources/static/js/10-core.js 'async writeCalendarDay(date, patch)'
  contains e2e/preflight.mjs 'DutyLog E2E preflight failed: the Vue bundle has not been built.'
  contains package.json '"pretest:e2e": "node ./e2e/preflight.mjs"'
  contains package.json '"pretest:e2e:canary": "node ./e2e/preflight.mjs"'
  contains package.json '"test:e2e:canary": "playwright test e2e/auth-onboarding.spec.js"'
  contains .github/workflows/ci.yml 'name: Browser E2E boot canary'
  contains .github/workflows/deploy-staging.yml 'name: Browser E2E boot canary'
  contains deploy/scripts/frontend-gate.ps1 'Node $actualNode is running; $expectedNode is required.'
  contains deploy/scripts/frontend-gate.ps1 'Get-Command npm.cmd -ErrorAction Stop'
  contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'scheduledEndDate: \"2026-08-05\"'
  contains e2e/important-timezone.spec.js '#grid .cell:not(.outside)'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'export function installCalendarTimelineOfflineSource'
  contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts 'canUseOfflineFallback(error) ? await loadOfflineCalendar(fallbackFocus) : null'
  contains frontend/src/features/calendar-timeline/components/CalendarTimelineWorkspace.vue 'async (focusDate: string) => props.bridge.offlineCalendarSnapshot(focusDate)'
  contains docs/VUE_CALENDAR_OFFLINE_SOURCE_TYPECHECK_HOTFIX_V27.38.6.md 'No second offline database or queue is introduced'
  contains docs/VUE_PRODUCTIVITY_MODULE_READINESS_BROWSER_CANARY_HOTFIX_V27.38.7.md 'modulesLoaded && onboardingCompleted'
  contains frontend/src/app/shellStore.ts 'function booleanMapEquals'
  contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'const productivityReadable = computed(() => modulesLoaded.value && (onboardingCompleted.value || !online.value))'

  # v27.38.3 Vue Productivity Strict Typecheck Hotfix
  contains CHANGES.md "v27.38.3 — Vue Productivity Strict Typecheck Hotfix"
  contains README.md "v27.38.3 — Vue Productivity Strict Typecheck Hotfix"
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'import java.util.Locale;'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'manifest.toLowerCase(Locale.ROOT).contains("offline/reconnect")'
  contains docs/migration/tasks-notes-important-vue-migration-manifest.md '## Offline/reconnect boundary'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.38.3 — Vue Productivity Strict Typecheck Hotfix acceptance"

  # v27.38.1 Vue Productivity Static Contract Alignment Hotfix
  contains CHANGES.md "v27.38.1 — Vue Productivity Static Contract Alignment Hotfix"
  contains README.md "v27.38.1 — Vue Productivity Static Contract Alignment Hotfix"
  contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java 'generated.contains("GENERATED FILE — DO NOT EDIT.")'
  contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java 'generated.contains("DUTYLOG_OPENAPI_SOURCE_SHA256")'
  not_contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java 'generated.contains("98 operations")'
  not_contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java 'generated.contains("103 schemas")'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'v-for=\"minutes in [15,30,45,60,90,120]\"'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java ':data-task-duration=\"minutes\"'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'dataset.vueProductivity === \"ready\"'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'productivity?.openTaskCreate'
  contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'productivity?.openImportantCreate'
  contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
  contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
  contains docs/RELEASE_CHECKLIST.md "v27.38.1 — Vue Productivity Static Contract Alignment Hotfix acceptance"

  # v27.38.0 Vue Tasks, Notes & Important Days
contains CHANGES.md "v27.38.0 — Vue Tasks, Notes & Important Days"
contains README.md "v27.38.0 — Vue Tasks, Notes & Important Days"
contains docs/VUE_TASKS_NOTES_IMPORTANT_V27.38.0.md "No second IndexedDB/localStorage queue is introduced"
contains docs/migration/tasks-notes-important-vue-migration-manifest.md 'target_release: "v27.38.0"'
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "v27.38.0 — Vue Tasks, Notes & Important Days acceptance"
contains docs/API.md "132 operations / 138 schemas"
contains docs/ENGINEERING_QUALITY_REGISTER.md '| Q-10 | Offline queue и reconnect correctness | `v27.38.0`'
contains frontend/src/app/AppShell.vue 'ProductivityWorkspace'
contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'retireDomainOwners("productivity")'
not_contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'window.addEventListener("online", reconnect)'
contains frontend/src/features/productivity/components/ProductivityWorkspace.vue 'OFFLINE_SYNC_COMPLETE_EVENT'
contains src/main/resources/static/js/20-data.js 'dutylog:offline-sync-complete'
contains frontend/src/features/productivity/stores/productivityStore.ts 'if (this.mutationPending) return'
contains frontend/src/features/productivity/stores/productivityStore.ts 'error.status === 409'
contains frontend/src/features/productivity/stores/productivityStore.ts 'bridge.offlineSelectedDay'
contains frontend/src/features/productivity/stores/productivityStore.ts 'bridge.offlineUpdateNote'
contains frontend/src/features/productivity/stores/productivityStore.ts 'bridge.offlineSetTaskDone'
contains frontend/src/features/productivity/stores/productivityStore.ts 'bridge.offlineCaptureInbox'
contains frontend/src/features/productivity/stores/productivityStore.ts 'bridge.offlineSync()'
contains src/main/resources/static/js/10-core.js 'data-vue-productivity'
contains src/main/resources/static/js/10-core.js 'dataLayer.updateDayNote'
contains src/main/resources/static/js/10-core.js 'dataLayer.setTaskDone'
contains src/main/resources/static/js/10-core.js 'dataLayer.captureInbox'
contains src/main/resources/static/js/10-core.js 'dataLayer.syncQueue'
contains src/main/resources/static/js/50-tasks.js 'document.documentElement.dataset.vueProductivity === "ready"'
contains src/test/java/ru/daniil/shifts/web/VueTasksNotesImportantMigrationFrontendContractTest.java 'class VueTasksNotesImportantMigrationFrontendContractTest'
contains frontend/src/features/productivity/types/model.spec.ts 'normalizes persistent task tags case-insensitively without duplicates'
not_contains frontend/src/features/productivity/api/productivityApi.ts 'fetch('
not_contains frontend/src/features/productivity/api/productivityApi.ts 'jfetch('

  # v27.37.5 Vue Calendar Selected-Day Island Lifecycle Hotfix
contains CHANGES.md "v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix"
contains README.md "v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix"
contains docs/VUE_CALENDAR_SELECTED_DAY_ISLAND_LIFECYCLE_HOTFIX_V27.37.5.md "parkCalendarEditor()"
contains docs/migration/calendar-timeline-vue-migration-manifest.md 'follow_up_release: "v27.37.5"'
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "v27.37.5 Vue Calendar Selected-Day Island Lifecycle Hotfix acceptance"
contains docs/VUE_CALENDAR_SELECTED_DAY_PANEL_RETIREMENT_V27.40.4.md 'The old `#calendarLegacyPanelHost` attach/park lifecycle is removed'
not_contains frontend/src/platform/bridge/legacyBridge.ts 'parkCalendarEditor'
not_contains frontend/src/platform/bridge/legacyBridge.ts 'attachCalendarEditor'
not_contains src/main/resources/static/js/10-core.js 'parkCalendarEditor(){'
not_contains src/main/resources/static/js/10-core.js 'document.body.appendChild(panel)'
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'SelectedDayPanel'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'vueOwnsSelectedDayPanelWhileLegacyDataLayerRemainsTheSingleOfflineWriter'
  # v27.37.4 PWA Bundle Budget Release Contract Alignment Hotfix
contains CHANGES.md "v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix"
contains README.md "v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix"
contains docs/PWA_BUNDLE_BUDGET_RELEASE_CONTRACT_ALIGNMENT_HOTFIX_V27.37.4.md 'derives `releaseVersion` from the DutyLog project version in `pom.xml`'
contains docs/ROADMAP.md "v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix — predecessor"
contains docs/RELEASE_CHECKLIST.md "v27.37.4 PWA Bundle Budget Release Contract Alignment Hotfix acceptance"
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'String releaseVersion = projectVersion();'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'pwa.contains("dutylog-shell-v${releaseVersion}-")'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'budget.contains("\"release\": \"" + releaseVersion + "\"")'
not_contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'budget.contains("\"release\": \"27.37.1\"")'
  # v27.37.3 Vue Calendar Selected-Day Island Routing Hotfix
contains CHANGES.md "v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix"
contains README.md "v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix"
contains docs/VUE_CALENDAR_SELECTED_DAY_ISLAND_ROUTING_HOTFIX_V27.37.3.md '$("layout")?.classList.toggle'
contains docs/ROADMAP.md "v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix — predecessor"
contains docs/RELEASE_CHECKLIST.md "v27.37.3 Vue Calendar Selected-Day Island Routing Hotfix acceptance"
contains docs/VUE_CALENDAR_SELECTED_DAY_ISLAND_ROUTING_HOTFIX_V27.37.3.md '$("layout")?.classList.toggle'
contains src/main/resources/static/js/30-calendar.js 'dataset.vueCalendarSelectedDay === "ready"'
contains src/main/resources/static/js/30-calendar.js 'calendarTimeline?.openDay'
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java 'calendar.contains("calendarTimeline?.openDay")'
  # v27.37.2 Vue Calendar Boot Routing Null-Safety Hotfix
contains CHANGES.md "v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix"
contains README.md "v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix"
contains docs/VUE_CALENDAR_BOOT_ROUTING_NULL_SAFETY_HOTFIX_V27.37.2.md "null.style"
contains docs/RELEASE_CHECKLIST.md "v27.37.2 Vue Calendar Boot Routing Null-Safety Hotfix acceptance"
contains src/main/resources/static/js/70-user-boot.js 'document.querySelectorAll(".nav #prev, .nav #todayBtn, .nav #next")'
not_contains src/main/resources/static/js/70-user-boot.js 'document.querySelector(".nav #prev").style.visibility'
not_contains src/main/resources/static/js/70-user-boot.js 'document.querySelector(".nav #todayBtn").style.visibility'
not_contains src/main/resources/static/js/70-user-boot.js 'document.querySelector(".nav #next").style.visibility'
  # v27.37.0 Vue Calendar & Timeline
contains CHANGES.md "v27.37.0 — Vue Calendar & Timeline"
contains README.md "v27.37.0 — Vue Calendar & Timeline"
contains docs/VUE_CALENDAR_TIMELINE_V27.37.0.md "selected-day mutation editor"
contains docs/migration/calendar-timeline-vue-migration-manifest.md 'target_release: "v27.37.0"'
contains docs/ROADMAP.md "v27.37.0 — Vue Calendar & Timeline — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.37.0 Vue Calendar & Timeline extension"
contains docs/RELEASE_CHECKLIST.md "v27.37.0 Vue Calendar & Timeline acceptance"
contains frontend/src/app/AppShell.vue "CalendarTimelineWorkspace"
contains frontend/src/features/calendar-timeline/api/calendarTimelineApi.ts 'client.request("calendarRange"'
contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts "let readSequence = 0"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'data-vue-domain-owner="calendar-timeline"'
contains frontend/src/features/calendar-timeline/components/TodayPage.vue 'data-vue-domain-owner="calendar-timeline"'
contains src/main/resources/static/js/10-core.js "vueCalendarTimelineRefreshQueued"
contains src/main/resources/static/js/30-calendar.js "requestVueCalendarTimelineRefresh()"
contains src/main/resources/static/js/35-today.js "requestVueCalendarTimelineRefresh()"
contains e2e/vue-calendar-timeline-migration.spec.js "#panel[data-vue-selected-day-panel]"
contains e2e/pwa-upgrade.spec.js "dutylog-shell-v27.38.15-synthetic-previous"
contains e2e/pwa-upgrade.spec.js 'dutylog-shell-v${releaseVersion}-'
contains src/main/resources/static/service-worker.js 'k.startsWith("dutylog-shell-")'
contains frontend/browser-bundle-budget.json "\"release\": \"${VERSION}\""
contains frontend/scripts/audit-browser-bundle.mjs "gzipSync"
contains docs/architecture/adr/ADR-006-pwa-asset-version-upgrade-strategy.md "Status: accepted"
contains docs/architecture/adr/INDEX.md "ADR-006-pwa-asset-version-upgrade-strategy.md"
contains src/test/java/ru/daniil/shifts/web/VueCalendarTimelineMigrationFrontendContractTest.java "class VueCalendarTimelineMigrationFrontendContractTest"
not_contains frontend/src/features/calendar-timeline/stores/calendarTimelineStore.ts "window.state"
not_contains frontend/src/features/calendar-timeline/api/calendarTimelineApi.ts "jfetch("

  # v27.36.8 Vue Read Sequencing Static Contract Alignment Hotfix
contains CHANGES.md "v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix"
contains README.md "v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix"
contains docs/VUE_READ_SEQUENCING_STATIC_CONTRACT_ALIGNMENT_HOTFIX_V27.36.8.md "three historical static assertions"
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Read-sequencing static contract alignment follow-up — v27.36.8"
contains docs/ROADMAP.md "v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix — completed and green"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.8 Vue Read Sequencing Static Contract Alignment extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.8 Vue Read Sequencing Static Contract Alignment Hotfix acceptance"
contains src/test/java/ru/daniil/shifts/web/VueReadSequencingStaticContractAlignmentHotfixTest.java "productionStoreUsesOneSharedReadSequenceForFullAndPeriodLoads"
contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java "let readSequence = 0"
contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankBrowserParityHotfixTest.java "if (sequence !== readSequence) return;"
contains src/test/java/ru/daniil/shifts/web/SinglePassCiFinalVueBrowserParityHotfixTest.java "without replacing the canonical account snapshot"
not_contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java "let refreshSequence = 0"
not_contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankBrowserParityHotfixTest.java "sequence !== refreshSequence"
not_contains src/test/java/ru/daniil/shifts/web/SinglePassCiFinalVueBrowserParityHotfixTest.java "while the refreshed model is loading"

  # v27.36.7 Time Bank Period Toggle Snapshot Stability Hotfix
contains CHANGES.md "v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix"
contains README.md "v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix"
contains docs/TIME_BANK_PERIOD_TOGGLE_SNAPSHOT_STABILITY_HOTFIX_V27.36.7.md "period-only loader"
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Period-toggle snapshot stability follow-up — v27.36.7"
contains docs/ROADMAP.md "v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix — completed implementation predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.7 Time Bank Period Toggle Snapshot Stability extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.7 Time Bank Period Toggle Snapshot Stability Hotfix acceptance"
contains frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts "async function loadPeriod"
contains frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts "const periodRequest = loadPeriod(rangeMode);"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "const result = await api.loadPeriod(mode);"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "let readSequence = 0;"
not_contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "refreshSequence"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts "without replacing the canonical account snapshot"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts "keeps the newest period response when month and year toggles race"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts "lets a later full refresh supersede an in-flight period-only request"
contains src/test/java/ru/daniil/shifts/web/TimeBankPeriodToggleSnapshotStabilityHotfixTest.java "class TimeBankPeriodToggleSnapshotStabilityHotfixTest"

  # v27.36.6 Time Bank Usage-Date Chart Parity Hotfix
contains CHANGES.md "v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix"
contains README.md "v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix"
contains docs/TIME_BANK_USAGE_DATE_CHART_PARITY_HOTFIX_V27.36.6.md '`usage.usageDate`'
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Usage-date chart parity follow-up — v27.36.6"
contains docs/ROADMAP.md "v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix — completed predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.6 Time Bank Usage-Date Chart Parity extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.6 Time Bank Usage-Date Chart Parity Hotfix acceptance"
contains frontend/src/features/absence-time-bank/types/model.ts "for (const [date, totals] of dayCreditTotals(account.credits))"
contains frontend/src/features/absence-time-bank/types/model.ts "rowFor(usage.usageDate).usedHours += Number(usage.hours ?? 0);"
not_contains frontend/src/features/absence-time-bank/types/model.ts "current.usedHours += Number(credit.usedHours ?? 0);"
contains frontend/src/features/absence-time-bank/types/model.spec.ts "plots time-bank usage on the actual usage date without double-counting credit usedHours"
contains frontend/src/features/absence-time-bank/types/model.spec.ts "folds earned work dates and actual usage dates into the same yearly month bucket"
contains src/test/java/ru/daniil/shifts/web/TimeBankUsageDateChartParityHotfixTest.java "class TimeBankUsageDateChartParityHotfixTest"

  # v27.36.5 Single-Pass CI & Final Vue Browser Parity Hotfix
contains CHANGES.md "v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix"
contains README.md "v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix"
contains docs/SINGLE_PASS_CI_FINAL_VUE_BROWSER_PARITY_HOTFIX_V27.36.5.md "Single-Pass"
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Final Chromium parity follow-up — v27.36.5"
contains docs/ROADMAP.md "v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix — completed predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.5 Single-Pass CI & Final Vue Browser Parity extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.5 Single-Pass CI & Final Vue Browser Parity Hotfix acceptance"
contains .github/workflows/ci.yml "branches-ignore: [test]"
contains .github/workflows/ci.yml "tags: ['**']"
contains .github/workflows/deploy-staging.yml "branches: [test]"
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerThisMonth"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "rangeMode === 'month' ? 'true' : 'false'"
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerThisYear"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "rangeMode === 'year' ? 'true' : 'false'"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "this.rangeMode = mode;"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "await this.refresh(date, this.rangeMode);"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts "refreshes a previously loaded account before opening the absence composer"
contains src/test/java/ru/daniil/shifts/web/SinglePassCiFinalVueBrowserParityHotfixTest.java "class SinglePassCiFinalVueBrowserParityHotfixTest"

  # v27.36.4 Vue Absence & Time Bank Browser Parity Hotfix
contains CHANGES.md "v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix"
contains README.md "v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix"
contains docs/VUE_ABSENCE_TIME_BANK_BROWSER_PARITY_HOTFIX_V27.36.4.md "projection synchronization"
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Browser parity follow-up — v27.36.4"
contains docs/ROADMAP.md "v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix — completed browser-parity predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.4 Vue Absence & Time Bank Browser Parity extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.4 Vue Absence & Time Bank Browser Parity Hotfix acceptance"
contains frontend/src/platform/bridge/legacyBridge.ts 'ABSENCE_TIME_BANK_PROJECTION_EVENT = "dutylog:absence-time-bank-projection"'
contains frontend/src/platform/bridge/legacyBridge.ts "publishAbsenceTimeBankProjection"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "if (sequence !== readSequence) return;"
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts "publishAbsenceTimeBankProjection(window"
contains src/main/resources/static/js/10-core.js '"timeBankGuideModal", "timeBankGuideBackdrop"'
contains src/main/resources/static/js/10-core.js "synchronizeLegacyAbsenceTimeBankProjection"
contains src/main/resources/static/js/10-core.js 'renderVacationDay()'
contains src/main/resources/static/js/10-core.js 'renderOvertimeDayDetails()'
contains src/main/resources/static/js/10-core.js 'renderCalendar()'
not_contains src/main/resources/static/js/10-core.js 'synchronizeLegacyAbsenceTimeBankProjection(snapshot){ renderVacationPlanner()'
contains frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue 'if (options?.source === "vacation") navigateHashRoute("vacation");'
contains frontend/src/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue 'if (options?.source === "time-bank") navigateHashRoute("overtime");'
contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue ':data-delete-absence="absenceDraft.id"'
contains frontend/src/features/absence-time-bank/components/AbsencePage.vue ':data-delete-absence-row="period.id"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'data-time-bank-insights'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerUsageRatio"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerOldestCredit"'
contains src/main/resources/static/js/39-vacation-planner.js 'openAbsenceEditor(Number(button.dataset.dayAbsence), { source:"calendar" })'
contains frontend/src/platform/bridge/legacyBridge.spec.ts 'publishes immutable Absence and Time Bank projections'
contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankBrowserParityHotfixTest.java "class VueAbsenceTimeBankBrowserParityHotfixTest"

  # v27.36.3 CI Artifact Quota Resilience Hotfix
contains CHANGES.md "v27.36.3 — CI Artifact Quota Resilience Hotfix"
contains README.md "v27.36.3 — CI Artifact Quota Resilience Hotfix"
contains docs/CI_ARTIFACT_QUOTA_RESILIENCE_HOTFIX_V27.36.3.md "artifact storage"
contains docs/ROADMAP.md "v27.36.3 — CI Artifact Quota Resilience Hotfix — completed stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.3 CI Artifact Quota Resilience extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.3 CI Artifact Quota Resilience Hotfix acceptance"
contains .github/workflows/ci.yml "target/site/jacoco/jacoco.xml"
contains .github/workflows/ci.yml "target/site/jacoco/jacoco.csv"
contains .github/workflows/ci.yml "continue-on-error: true"
contains .github/workflows/ci.yml "retention-days: 3"
contains .github/workflows/deploy-staging.yml "if: failure()"
contains src/test/java/ru/daniil/shifts/web/CiArtifactQuotaResilienceHotfixTest.java "class CiArtifactQuotaResilienceHotfixTest"
contains src/test/java/ru/daniil/shifts/web/CiArtifactQuotaResilienceHotfixTest.java "github.run_attempt"

  # v27.36.2 Vue Timer Static Contract Compile Coverage Hotfix
contains CHANGES.md "v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix"
contains README.md "v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix"
contains docs/VUE_TIMER_STATIC_CONTRACT_COMPILE_COVERAGE_HOTFIX_V27.36.2.md "compile coverage"
contains docs/ROADMAP.md "v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix — completed stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.2 Vue Timer Static Contract Compile Coverage extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.2 Vue Timer Static Contract Compile Coverage Hotfix acceptance"
contains deploy/scripts/release-check.sh "-name '*HotfixTest.java'"
contains src/test/java/ru/daniil/shifts/web/VueBrowserTimerHandleTypeFrontendContractTest.java "class VueBrowserTimerHandleTypeFrontendContractTest"
contains src/test/java/ru/daniil/shifts/web/VueBrowserTimerHandleTypeFrontendContractTest.java 'return value.replaceAll("\\s+", " ").trim();'
contains src/test/java/ru/daniil/shifts/web/VueBrowserTimerHandleTypeFrontendContractTest.java 'String cleanup = "onBeforeUnmount(() => { "'
not_contains src/test/java/ru/daniil/shifts/web/VueBrowserTimerHandleTypeFrontendContractTest.java 'assertTrue(absence.contains("onBeforeUnmount(() => {'
if [[ -e src/test/java/ru/daniil/shifts/web/VueBrowserTimerHandleTypeHotfixTest.java ]]; then
  fail "obsolete timer hotfix test filename must be retired"
else
  ok "timer contract uses compile-gated FrontendContractTest naming"
fi

  # v27.36.1 Vue Browser Timer Handle Type Hotfix
contains CHANGES.md "v27.36.1 — Vue Browser Timer Handle Type Hotfix"
contains README.md "v27.36.1 — Vue Browser Timer Handle Type Hotfix"
contains docs/VUE_BROWSER_TIMER_HANDLE_TYPE_HOTFIX_V27.36.1.md "browser timer overload"
contains docs/ROADMAP.md "v27.36.1 — Vue Browser Timer Handle Type Hotfix — completed stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.1 Vue Browser Timer Handle Type extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.1 Vue Browser Timer Handle Type Hotfix acceptance"
contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue "let previewTimer: number | null = null;"
contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue "window.setTimeout(() => { void store.previewAbsence(); }, 260)"
contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue "window.clearTimeout(previewTimer)"
not_contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue "globalThis.setTimeout"
contains frontend/src/features/absence-time-bank/components/CreditEditor.vue "let previewTimer: number | null = null;"
contains frontend/src/features/absence-time-bank/components/CreditEditor.vue "window.setTimeout(() => { void store.previewCredit(); }, 280)"
contains frontend/src/features/absence-time-bank/components/CreditEditor.vue "window.clearTimeout(previewTimer)"
not_contains frontend/src/features/absence-time-bank/components/CreditEditor.vue "globalThis.setTimeout"

  # v27.36.0 Vue Absence & Time Bank
contains CHANGES.md "v27.36.0 — Vue Absence & Time Bank"
contains README.md "v27.36.0 — Vue Absence & Time Bank"
contains docs/VUE_ABSENCE_TIME_BANK_V27.36.0.md "first product-domain Vue migration after Gate A"
contains docs/migration/absence-time-bank-vue-migration-manifest.md 'target_release: "v27.36.0"'
contains docs/migration/absence-time-bank-vue-migration-manifest.md "Final UI owner after this release: Vue"
contains docs/ROADMAP.md "v27.36.0 — Vue Absence & Time Bank — completed domain predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.36.0 Vue Absence & Time Bank extension"
contains docs/RELEASE_CHECKLIST.md "v27.36.0 Vue Absence & Time Bank acceptance"
contains docs/ENGINEERING_QUALITY_REGISTER.md '| Q-06 | Optimistic concurrency / stale-write / double-submit policy'
contains docs/ENGINEERING_QUALITY_REGISTER.md '| DONE |'
contains docs/MODULE_CONTRACTS.md "Vue Absence & Time Bank ownership (v27.36.0)"
contains frontend/src/app/AppShell.vue "AbsenceTimeBankWorkspace"
contains frontend/src/main.ts 'bridge.retireDomainOwners("absence-time-bank")'
contains frontend/src/platform/bridge/legacyBridge.ts 'retireDomainOwners(domain'
contains src/main/resources/static/js/10-core.js 'view-vacation'
contains src/main/resources/static/js/10-core.js 'view-overtime'
contains src/main/resources/static/js/10-core.js 'data-vue-absence-time-bank'
contains frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts 'createGeneratedDutyLogApiClient'
not_contains frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts 'fetch('
not_contains frontend/src/features/absence-time-bank/api/absenceTimeBankApi.ts 'jfetch('
not_contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts 'window.state'
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts 'let readSequence = 0;'
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts 'if (sequence !== readSequence) return'
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts 'if (this.mutationPending) return'
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.ts 'error.status === 409'
contains frontend/src/features/absence-time-bank/components/AbsencePage.vue 'id="vacationPeriodList"'
contains frontend/src/features/absence-time-bank/components/AbsenceComposer.vue 'id="absenceFifoForecast"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerChart"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerCards"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="ledgerIntegrityCard"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'id="fifoForecastForm"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'role="tablist"'
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue 'aria-live="polite"'
contains frontend/src/features/absence-time-bank/components/CreditEditor.vue 'id="scenarioManagerView"'
contains src/main/resources/static/js/39-vacation-planner.js 'vueDomain.openAbsenceComposer'
contains src/main/resources/static/js/40-overtime.js 'vueDomain.openCreditEditor'
contains src/main/resources/static/openapi/dutylog-v1.yaml 'operationId: createQuickScenario'
contains src/main/resources/static/openapi/dutylog-v1.yaml 'operationId: updateQuickScenario'
contains src/main/resources/static/openapi/dutylog-v1.yaml 'operationId: deleteQuickScenario'
contains frontend/src/generated/dutylog-api.ts 'credits: Array<DutyLogApiSchemas.OvertimeCredit>'
contains frontend/src/generated/dutylog-api.ts 'allocations: Array<DutyLogApiSchemas.OvertimeAllocation>'
contains frontend/src/generated/dutylog-api.ts 'items: Array<DutyLogApiSchemas.AbsencePreviewItem>'
contains frontend/src/features/absence-time-bank/types/model.spec.ts 'forecasts FIFO'
not_contains frontend/src/features/absence-time-bank/types/model.ts $'allocations,\n    allocations,'
contains frontend/src/features/absence-time-bank/stores/absenceTimeBankStore.spec.ts 'blocks a second submit'
contains e2e/vue-absence-time-bank-migration.spec.js 'button.click();'
contains e2e/vue-absence-time-bank-migration.spec.js '#view-vacation'
contains src/test/java/ru/daniil/shifts/web/VueAbsenceTimeBankMigrationTest.java 'class VueAbsenceTimeBankMigrationTest'

  # v27.35.7 Docker Frontend OpenAPI Build Context Hotfix
contains CHANGES.md "v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix"
contains README.md "v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix"
contains docs/DOCKER_FRONTEND_OPENAPI_BUILD_CONTEXT_HOTFIX_V27.35.7.md 'canonical backend OpenAPI YAML'
contains docs/ROADMAP.md "v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.7 Docker frontend OpenAPI build-context extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.7 Docker Frontend OpenAPI Build Context Hotfix acceptance"
contains Dockerfile 'COPY src/main/resources/static/openapi/dutylog-v1.yaml'
contains Dockerfile '/src/main/resources/static/openapi/dutylog-v1.yaml'
contains frontend/scripts/generate-openapi-contract.mjs 'src/main/resources/static/openapi/dutylog-v1.yaml'
contains src/test/java/ru/daniil/shifts/web/DockerFrontendOpenApiBuildContextHotfixTest.java 'class DockerFrontendOpenApiBuildContextHotfixTest'
not_contains Dockerfile 'COPY frontend/dutylog-v1.yaml'

  # v27.35.6 Gate A Historical Static Contract Alignment Hotfix
contains CHANGES.md "v27.35.6 — Gate A Historical Static Contract Alignment Hotfix"
contains README.md "v27.35.6 — Gate A Historical Static Contract Alignment Hotfix"
contains docs/GATE_A_HISTORICAL_STATIC_CONTRACT_ALIGNMENT_HOTFIX_V27.35.6.md 'four stale test-only expectations'
contains docs/ROADMAP.md "v27.35.6 — Gate A Historical Static Contract Alignment Hotfix — stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.6 Gate A historical static-contract alignment extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.6 Gate A Historical Static Contract Alignment Hotfix acceptance"
contains src/test/java/ru/daniil/shifts/web/AuthenticNpmLockfileBootstrapHotfixTest.java 'release.contains("passes full CI and staging")'
contains src/test/java/ru/daniil/shifts/web/AuthenticNpmLockfileBootstrapHotfixTest.java 'release.contains("only after full green")'
not_contains src/test/java/ru/daniil/shifts/web/AuthenticNpmLockfileBootstrapHotfixTest.java 'release.contains("full green CI and staging")'
contains src/test/java/ru/daniil/shifts/web/FrontendLockfileExecutableResolutionHotfixTest.java 'assertFalse(ignore.contains("frontend/package-lock.json"));'
not_contains src/test/java/ru/daniil/shifts/web/FrontendLockfileExecutableResolutionHotfixTest.java 'assertTrue(ignore.contains("frontend/package-lock.json"));'
contains src/test/java/ru/daniil/shifts/web/VueDeliveryContractsDiagnosticsFoundationTest.java 'V47__absence_hours_only_legacy_shape.sql'
contains src/test/java/ru/daniil/shifts/web/VueFrontendFoundationContractTest.java 'FROM node:20.18.1-alpine3.20 AS frontend-build'
not_contains src/test/java/ru/daniil/shifts/web/VueFrontendFoundationContractTest.java 'FROM node:20-alpine AS frontend-build'

  # v27.35.3 Authentic Lockfile Commit & Generated Client Fixture Hotfix
contains CHANGES.md "v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix"
contains README.md "v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix"
contains docs/AUTHENTIC_LOCKFILE_COMMIT_GENERATED_CLIENT_FIXTURE_HOTFIX_V27.35.3.md 'Source artifact'
contains docs/ROADMAP.md "v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix — stabilization predecessor"
contains docs/REGRESSION_TEST_BASELINE.md "v27.35.3 Authentic lockfile commit and generated-client fixture extension"
contains docs/RELEASE_CHECKLIST.md "v27.35.3 Authentic Lockfile Commit & Generated Client Fixture Hotfix acceptance"
contains docs/ENGINEERING_QUALITY_REGISTER.md 'exact CI-generated lockfile committed'
contains frontend/generated-lockfile-manifest.txt 'source=github-actions-run-30906521813'
contains .github/workflows/deploy-staging.yml 'cache-dependency-path: frontend/package-lock.json'
not_contains .gitignore 'frontend/package-lock.json'

  # v27.34.4 Vue Secondary Navigation & Overtime Preview Contract Hotfix
contains CHANGES.md "v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix"
contains README.md "v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix"
contains docs/VUE_SECONDARY_NAVIGATION_OVERTIME_PREVIEW_HOTFIX_V27.34.4.md 'zero or negative credited minutes'
contains docs/ROADMAP.md "v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.34.4 Vue Secondary Navigation & Overtime Preview Contract Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Vue secondary navigation and overtime draft preview (v27.34.4)"
contains docs/SECURITY_REVIEW.md "v27.34.4 secondary navigation and draft-preview review"
contains docs/RELEASE_CHECKLIST.md "v27.34.4 Vue Secondary Navigation & Overtime Preview Contract Hotfix acceptance"
contains frontend/src/app/AppNavigation.vue 'secondaryNavigation.value.includes(activeRoute.value)'
contains frontend/src/app/AppNavigation.vue "secondaryActive ? 'page' : false"
contains frontend/src/app/AppShell.vue "activeRoute === item.route ? 'page' : false"
contains e2e/vue-app-shell.spec.js "page.locator('[data-vue-shell-more]')"
contains e2e/vue-app-shell.spec.js '.vue-shell-more-grid [data-route="tasks"]'
not_contains e2e/vue-app-shell.spec.js '[data-vue-shell-navigation] [data-route="tasks"]'
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java 'calculateCredit(user, req, null, false)'
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java 'creditedMinutes <= 0 && requirePositiveCalculatedHours'
contains e2e/overtime-scenario-manager.spec.js "waitForApi(page, 'POST', '/api/v1/overtime/preview', 200)"
contains src/test/java/ru/daniil/shifts/web/VueSecondaryNavigationOvertimePreviewHotfixTest.java 'class VueSecondaryNavigationOvertimePreviewHotfixTest'
contains src/test/java/ru/daniil/shifts/service/OvertimeServiceTest.java 'canonicalPreviewAllowsZeroDraftButPersistenceStillRejectsIt'
contains src/test/java/ru/daniil/shifts/web/OvertimeControllerTest.java 'previewReturnsOkForAZeroCalculatedDraftThroughLegacyAndV1Aliases'

  # v27.32.0 Absence & Time Bank Experience
contains CHANGES.md "v27.32.0 — Absence & Time Bank Experience"
contains README.md "v27.32.0 — Absence & Time Bank Experience"
contains docs/ABSENCE_TIME_BANK_EXPERIENCE_V27.32.0.md "one canonical absence event"
contains docs/ROADMAP.md "v27.32.0 — Absence & Time Bank Experience — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.32.0 Absence & Time Bank Experience extension"
contains docs/MODULE_CONTRACTS.md "Absence and time-bank presentation ownership (v27.32.0)"
contains docs/SECURITY_REVIEW.md "v27.32.0 absence/time-bank experience review"
contains docs/RELEASE_CHECKLIST.md "v27.32.0 Absence & Time Bank Experience acceptance"
contains src/main/resources/static/index.html 'id="timeBankTabOverview"'
contains src/main/resources/static/index.html 'id="timeBankTabCredits"'
contains src/main/resources/static/index.html 'id="timeBankTabUsage"'
contains src/main/resources/static/index.html 'id="timeBankTabFifo"'
contains src/main/resources/static/index.html 'id="ledgerReserved"'
contains src/main/resources/static/index.html 'id="ledgerUsagePanel"'
contains src/main/resources/static/index.html 'id="ledgerFifoForecast"'
contains src/main/resources/static/index.html 'id="absenceFifoForecast"'
contains src/main/resources/static/index.html 'id="timeBankGuideModal"'
contains src/main/resources/static/js/40-overtime.js 'function timeBankUsageBuckets()'
contains src/main/resources/static/js/40-overtime.js 'function timeBankForecast(requestedMinutes,'
contains src/main/resources/static/js/40-overtime.js 'function renderTimeBankUsageList()'
contains src/main/resources/static/js/40-overtime.js 'function setTimeBankView(view = "overview"'
contains src/main/resources/static/js/39-vacation-planner.js 'function openTimeBankUsageForAbsence(absenceId)'
contains src/main/resources/static/js/39-vacation-planner.js 'function renderAbsenceFifoForecast'
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeBankExperienceContractTest.java 'timeBankSeparatesOverviewCreditsUsageAndFifoDetail'
contains e2e/absence-time-bank-experience.spec.js 'absence remains the event owner while the time bank explains reservations and FIFO'

  # v27.31.2 Canonical Absence Browser Contract Alignment Hotfix
contains CHANGES.md "v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix"
contains README.md "v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix"
contains docs/CANONICAL_ABSENCE_BROWSER_CONTRACT_ALIGNMENT_HOTFIX_V27.31.2.md "changes no production behavior"
contains docs/ROADMAP.md "v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.31.2 Canonical Absence Browser Contract Alignment Hotfix extension"
contains docs/MODULE_CONTRACTS.md "Browser ownership alignment (v27.31.2)"
contains docs/SECURITY_REVIEW.md "v27.31.2 browser contract alignment review"
contains docs/RELEASE_CHECKLIST.md "v27.31.2 browser contract alignment acceptance"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceBrowserContractAlignmentHotfixTest.java "expectedRetiredUsageProbeRunsOutsideTheBrowserRuntimeFailureMonitor"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceBrowserContractAlignmentHotfixTest.java "linkedUsageBrowserFlowsEditAndDeleteTheOwningAbsenceInsteadOfExpectingLegacyButtons"
contains e2e/canonical-absence-ledger.spec.js "page.context().request.post('/api/overtime/usages'"
contains e2e/canonical-absence-ledger.spec.js 'expect(retired.body.code).toBe('"'"'DIRECT_USAGE_RETIRED'"'"')'
not_contains e2e/canonical-absence-ledger.spec.js "await fetch('/api/overtime/usages'"
contains e2e/canonical-absence-ledger.spec.js "#ledgerUsageList .timeBankUsageCard', { hasText:'Canonical time off'"
contains e2e/canonical-absence-ledger.spec.js '[data-edit-absence="${absence.id}"]'
contains e2e/canonical-absence-ledger.spec.js '[data-edit-usage="${account.usages[0].id}"]`)).toHaveCount(0)'
contains e2e/overtime-editor-modals.spec.js '[data-edit-usage="${secondUsageId}"]`)).toHaveCount(0)'
contains e2e/overtime-editor-modals.spec.js "hasText:'Surviving time-off'"
contains e2e/overtime-editor-modals.spec.js "await expect(surviving).toContainText(/Управляется отсутствием|Managed by absence/i)"
not_contains e2e/overtime-editor-modals.spec.js '[data-edit-usage="${secondUsageId}"]`)).toHaveCount(1)'

  # v27.31.1 Canonical Absence Static Contract Alignment Hotfix
contains CHANGES.md "v27.31.1 — Canonical Absence Static Contract Alignment Hotfix"
contains README.md "v27.31.1 — Canonical Absence Static Contract Alignment Hotfix"
contains docs/CANONICAL_ABSENCE_STATIC_CONTRACT_ALIGNMENT_HOTFIX_V27.31.1.md "changes no production behavior"
contains docs/ROADMAP.md "v27.31.1 — Canonical Absence Static Contract Alignment Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.31.1 Canonical Absence Static Contract Alignment Hotfix extension"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceStaticContractAlignmentHotfixTest.java "historicalFrontendContractsFollowCanonicalAbsenceOwnershipWithoutRestoringLegacyWrites"
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java 'const coverage = $(\"vacationCoverage\")?.value || \"FULL_DAY\";'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'coverage === \"HOURS_ONLY\"'
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "async function openLegacyUsageMigration(focusId = null)"
contains src/test/java/ru/daniil/shifts/web/UnifiedOvertimeEditorsFrontendContractTest.java "function renderLegacyUsageMigrationPreview(preview)"
contains src/test/java/ru/daniil/shifts/web/TaskAndShiftEditorsFrontendContractTest.java "Управляется отсутствием"
contains src/test/java/ru/daniil/shifts/web/TaskAndShiftEditorsFrontendContractTest.java "usageManagedByAbsence(fullUsage)"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceStaticContractAlignmentHotfixTest.java 'assertFalse(overtime.contains("openLegacyUsageMigrationModal"))'

  # v27.31.0 Canonical Absence Ledger & Legacy Retirement
contains CHANGES.md "v27.31.0 — Canonical Absence Ledger & Legacy Retirement"
contains README.md "v27.31.0 — Canonical Absence Ledger & Legacy Retirement"
contains docs/CANONICAL_ABSENCE_LEDGER_LEGACY_RETIREMENT_V27.31.0.md "one canonical write model"
contains docs/ROADMAP.md "v27.31.0 — Canonical Absence Ledger & Legacy Retirement — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.31.0 Canonical Absence Ledger & Legacy Retirement extension"
contains docs/API.md "/api/v1/overtime/legacy-usages/preview"
contains docs/MODULE_CONTRACTS.md "Canonical absence ownership (v27.31.0)"
contains docs/SECURITY_REVIEW.md "v27.31.0 canonical ownership review"
contains docs/RELEASE_CHECKLIST.md "v27.31.0 canonical absence ledger acceptance"
contains src/main/java/ru/daniil/shifts/web/OvertimeController.java "DIRECT_USAGE_RETIRED"
contains src/main/java/ru/daniil/shifts/web/OvertimeController.java "LEGACY_USAGE_MUST_BE_MIGRATED"
contains src/main/java/ru/daniil/shifts/web/OvertimeController.java '"/legacy-usages/preview"'
contains src/main/java/ru/daniil/shifts/web/OvertimeController.java '"/legacy-usages/migrate"'
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "attachManualUsageToAbsence"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "isAbsenceLinkedUsage"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java 'usage.setSourceKind("ABSENCE")'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java 'public static final String HOURS_ONLY = "HOURS_ONLY";'
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "previewLegacyUsageMigration"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "migrateLegacyUsages"
contains src/main/java/ru/daniil/shifts/telegram/TelegramCommandService.java "createTimeOff"
contains src/main/resources/static/index.html 'id="legacyUsageMigrationModal"'
contains src/main/resources/static/index.html 'value="HOURS_ONLY"'
not_contains src/main/resources/static/index.html 'id="overtimeUsageModal"'
not_contains src/main/resources/static/index.html 'id="overtimeUsageForm"'
contains src/main/resources/static/js/20-data.js "previewLegacyOvertimeUsages"
contains src/main/resources/static/js/20-data.js "migrateLegacyOvertimeUsages"
not_contains src/main/resources/static/js/20-data.js "createOvertimeUsage"
not_contains src/main/resources/static/js/20-data.js "updateOvertimeUsage"
contains src/main/resources/static/js/39-vacation-planner.js "Интервал не указан"
contains src/main/resources/static/js/40-overtime.js "openLegacyUsageMigration"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceLedgerLegacyRetirementContractTest.java "browserCreatesTimeOffOnlyThroughTheUnifiedAbsenceComposer"
contains e2e/canonical-absence-ledger.spec.js "canonical absence ledger owns new time-off while overtime keeps FIFO statistics"
contains src/main/resources/static/openapi/dutylog-v1.yaml "DIRECT_USAGE_RETIRED"
contains src/main/resources/db/migration/postgresql/V47__absence_hours_only_legacy_shape.sql "coverage IN ('FULL_DAY', 'PARTIAL', 'HOURS_ONLY')"
contains src/main/resources/db/migration/postgresql/V47__absence_hours_only_legacy_shape.sql "coverage = 'HOURS_ONLY' AND start_date = end_date"
contains src/main/resources/db/migration/postgresql/V47__absence_hours_only_legacy_shape.sql "start_time IS NULL AND end_time IS NULL"
not_contains src/main/resources/db/migration/postgresql/V47__absence_hours_only_legacy_shape.sql "UPDATE absence_periods"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceLedgerLegacyRetirementContractTest.java "2bd6b413899be74f5d070ecde18915bd5152072d2fc05bcb87e40c0ea931a76c"
contains src/test/java/ru/daniil/shifts/web/CanonicalAbsenceLedgerLegacyRetirementContractTest.java "v47AllowsTruthfulHoursOnlyRowsWithoutRewritingV42"

if [[ "$(find src/main/resources/db/migration/postgresql -maxdepth 1 -type f -name 'V47__absence_hours_only_legacy_shape.sql' | wc -l | tr -d ' ')" == "1" ]]; then
  ok "V47 absence HOURS_ONLY constraint migration appears exactly once"
else
  fail "V47 absence HOURS_ONLY constraint migration must appear exactly once"
fi

  # v27.30.2 Today Overtime Journal Contract Hotfix
contains CHANGES.md "v27.30.2 — Today Overtime Journal Contract Hotfix"
contains README.md "v27.30.2 — Today Overtime Journal Contract Hotfix"
contains docs/TODAY_OVERTIME_JOURNAL_CONTRACT_HOTFIX_V27.30.2.md "Today Overtime Journal Contract Hotfix"
contains docs/ROADMAP.md "v27.30.2 — Today Overtime Journal Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.30.2 Today Overtime Journal Contract Hotfix extension"
contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'location.hash = \"#overtime\"'
not_contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'assertTrue(js.contains("openOvertimeCreditModal"))'
contains src/test/java/ru/daniil/shifts/web/TodayOvertimeJournalContractHotfixTest.java "todayOvertimeCardOpensTheJournalWhileCreditCreationRemainsInOvertime"
contains src/main/resources/static/index.html 'id="todayOpenOvertime" type="button">Журнал</button>'
contains src/main/resources/static/js/35-today.js '$("todayOpenOvertime")?.addEventListener("click", () => { location.hash = "#overtime"; });'
not_contains src/main/resources/static/js/35-today.js 'openOvertimeCreditModal'
contains src/main/resources/static/js/40-overtime.js 'function openOvertimeCreditModal(date = null)'

  # v27.30.1 Unified Absence Quick Access Integration
contains CHANGES.md "v27.30.1 — Unified Absence Quick Access Integration"
contains README.md "v27.30.1 — Unified Absence Quick Access Integration"
contains docs/UNIFIED_ABSENCE_QUICK_ACCESS_INTEGRATION_V27.30.1.md "Functional entry points"
contains docs/ROADMAP.md "v27.30.1 — Unified Absence Quick Access Integration — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.30.1 Unified Absence Quick Access Integration extension"
contains src/main/resources/static/index.html 'id="todayQuickAbsence"'
contains src/main/resources/static/index.html '<b>Оформить отсутствие</b><small>отпуск, отгул, больничный</small>'
contains src/main/resources/static/js/35-today.js 'openAbsenceComposer({ date:todayKey(), source:"today" })'
contains src/main/resources/static/js/35-today.js '$("todayQuickAbsence").hidden = !moduleEnabled("vacation")'
not_contains src/main/resources/static/js/35-today.js 'todayQuickCredit'
contains src/main/resources/static/js/50-tasks.js 'if (moduleEnabled("vacation")) return "quickActionUsage";'
contains src/main/resources/static/js/50-tasks.js 'source:"quick-add"'
contains src/main/resources/static/js/40-overtime.js 'systemCode:"TIME_OFF"'
contains src/test/java/ru/daniil/shifts/web/UnifiedAbsenceQuickAccessFrontendContractTest.java "todayHasADirectAbsenceActionForTheCurrentDate"
contains e2e/today-dashboard.spec.js "Today opens the neutral absence composer directly"


  # v27.30.0 Unified Absence Composer & Calendar Projection
contains CHANGES.md "v27.30.0 — Unified Absence Composer & Calendar Projection"
contains README.md "v27.30.0 — Unified Absence Composer & Calendar Projection"
contains docs/UNIFIED_ABSENCE_COMPOSER_CALENDAR_PROJECTION_V27.30.0.md "one absence flow"
contains docs/ROADMAP.md "v27.30.0 — Unified Absence Composer & Calendar Projection — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.30.0 Unified Absence Composer & Calendar Projection extension"
contains src/main/resources/static/index.html 'id="absenceComposerModal"'
contains src/main/resources/static/index.html 'id="absenceComposerModalMount"'
contains src/main/resources/static/index.html 'id="vacationTitle" maxlength="120"'
contains src/main/resources/static/index.html 'Оформить отсутствие'
contains src/main/resources/static/js/39-vacation-planner.js "function openAbsenceComposer("
contains src/main/resources/static/js/39-vacation-planner.js "function renderAbsenceComposerContext("
contains src/main/resources/static/js/39-vacation-planner.js "function absenceGlyph(value)"
contains src/main/resources/static/js/40-overtime.js 'systemCode:"TIME_OFF"'
contains src/main/resources/static/js/20-data.js 'toggle($("quickActionUsage"), moduleEnabled("vacation"))'
contains src/main/resources/static/js/50-tasks.js 'source:"quick-add"'
not_contains src/main/resources/static/js/50-tasks.js 'openOvertimeUsageModal(state.selected || todayKey())'
contains src/main/resources/static/js/30-calendar.js "dataset.absenceStatus"
contains src/main/resources/static/app.css ".absenceComposerContext"
contains src/test/java/ru/daniil/shifts/web/UnifiedAbsenceComposerFrontendContractTest.java "oneComposerOwnsVacationTimeOffSickAndUnpaidEntryPoints"
contains e2e/unified-absence-composer.spec.js "one absence composer routes balances"
contains src/main/resources/static/js/39-vacation-planner.js "await loadVacationPlanner(true);"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java 'PRODID:-//DutyLog//Time and Overtime '
not_contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "PRODID:-//DutyLog//Time and Overtime 27.29.3//RU"
contains e2e/unified-absence-composer.spec.js "await page.locator('#pClose').click();"
contains e2e/unified-absence-composer.spec.js "await expect(page.locator('#globalQuickAdd')).toBeVisible();"
contains e2e/vacation-planner.spec.js ".toContainText('E2E отпуск');"
not_contains e2e/vacation-planner.spec.js ".toHaveText('E2E отпуск');"
contains src/test/java/ru/daniil/shifts/telegram/TelegramLinkDetachedOwnerIntegrationTest.java 'jdbc:h2:mem:telegram_link_detached;DB_CLOSE_DELAY=-1'
not_contains src/test/java/ru/daniil/shifts/telegram/TelegramLinkDetachedOwnerIntegrationTest.java '@SpringBootTest\n@DirtiesContext'

  # v27.29.3 Custom Workspace Today Widget Order Persistence Hotfix
contains CHANGES.md "v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix"
contains README.md "v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix"
contains docs/CUSTOM_WORKSPACE_TODAY_WIDGET_ORDER_PERSISTENCE_HOTFIX_V27.29.3.md "This is a bounded Workspace Studio profile-persistence hotfix."
contains docs/ROADMAP.md "v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.29.3 Custom Workspace Today Widget Order Persistence Hotfix extension"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'LinkedHashSet<String> result = new LinkedHashSet<>(selected);'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'if (!result.contains("shift"))'
contains src/test/java/ru/daniil/shifts/web/ProfileControllerTest.java "customTodayWidgetOrderSurvivesServerSanitization"
contains src/test/java/ru/daniil/shifts/web/CustomWorkspaceTodayWidgetOrderPersistenceHotfixTest.java "serverPreservesExplicitWidgetOrderWhileStillRestoringRequiredShift"
contains e2e/workspace-layout-theme-studio.spec.js "tasks.compareDocumentPosition(shift)"

  # v27.29.2 Custom Workspace Today Widget Inheritance Hotfix
contains CHANGES.md "v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix"
contains README.md "v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix"
contains docs/CUSTOM_WORKSPACE_TODAY_WIDGET_INHERITANCE_HOTFIX_V27.29.2.md "This is a bounded Workspace Studio behavior hotfix."
contains docs/ROADMAP.md "v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.29.2 Custom Workspace Today Widget Inheritance Hotfix extension"
contains src/main/resources/static/js/12-ui-platform.js 'todayWidgets:[...workspace.todayWidgets]'
contains e2e/workspace-layout-theme-studio.spec.js "studioRow(page, 'widget', 'overtime').locator('[data-studio-visible]')).toBeChecked()"
contains e2e/workspace-layout-theme-studio.spec.js "studioRow(page, 'widget', 'tasks').locator('[data-studio-visible]')).toBeChecked()"
contains src/test/java/ru/daniil/shifts/web/CustomWorkspaceTodayWidgetInheritanceHotfixTest.java "customWorkspaceCopiesTheActivePresetNavigationAndTodayWidgets"

  # v27.29.1 Theme Package Token Scope Contract Hotfix
contains CHANGES.md "v27.29.1 — Theme Package Token Scope Contract Hotfix"
contains README.md "v27.29.1 — Theme Package Token Scope Contract Hotfix"
contains docs/THEME_PACKAGE_TOKEN_SCOPE_CONTRACT_HOTFIX_V27.29.1.md "This is a Maven source-contract hotfix only."
contains docs/ROADMAP.md "v27.29.1 — Theme Package Token Scope Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.29.1 Theme Package Token Scope Contract Hotfix extension"
contains src/main/resources/static/js/12-ui-platform.js 'tokenScope:`html[data-ui-theme="${id}"]`'
contains src/test/java/ru/daniil/shifts/web/WorkspaceLayoutThemeStudioFrontendContractTest.java 'tokenScope:`html[data-ui-theme=\"${id}\"]`'
not_contains src/test/java/ru/daniil/shifts/web/WorkspaceLayoutThemeStudioFrontendContractTest.java 'tokenScope:`html[data-ui-theme=\\\"${id}\\\"]`'
contains src/test/java/ru/daniil/shifts/web/ThemePackageTokenScopeContractHotfixTest.java "javaContractMatchesTheActualJavascriptTemplateLiteral"
contains src/test/java/ru/daniil/shifts/web/ThemePackageTokenScopeContractHotfixTest.java "threeSourceBackslashesQuote"

  # v27.29.0 Workspace, Layout & Theme Studio
contains CHANGES.md "v27.29.0 — Workspace, Layout & Theme Studio"
contains README.md "v27.29.0 — Workspace, Layout & Theme Studio"
contains docs/WORKSPACE_LAYOUT_THEME_STUDIO_V27.29.0.md "DutyLog UI Core advances to contract v2"
contains docs/ROADMAP.md "v27.29.0 — Workspace, Layout & Theme Studio — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.29.0 Workspace, Layout & Theme Studio extension"
contains src/main/resources/static/js/10-core.js "const UI_CONTRACT_VERSION = 2"
contains src/main/resources/static/js/10-core.js 'const UI_WORKSPACE_IDS = Object.freeze(["shift-worker","planner","minimal","custom"])'
contains src/main/resources/static/js/10-core.js 'const UI_LAYOUT_IDS = Object.freeze(["dashboard","compact","focus","sidebar","mobile-flow"])'
contains src/main/resources/static/js/10-core.js 'const UI_DECORATION_IDS = Object.freeze(["none","grid"])'
contains src/main/resources/static/js/10-core.js "navigationVisible"
contains src/main/resources/static/js/10-core.js "calendarLayerStyle"
contains src/main/resources/static/js/12-ui-platform.js "function workspaceDefinition(cfg)"
contains src/main/resources/static/js/12-ui-platform.js "function renderStudio(cfg)"
contains src/main/resources/static/js/12-ui-platform.js "ordered.length > 5"
contains src/main/resources/static/js/12-ui-platform.js 'selected.add("shift")'
not_contains src/main/resources/static/js/12-ui-platform.js "jfetch("
not_contains src/main/resources/static/js/12-ui-platform.js "fetch("
contains src/main/resources/static/index.html 'id="workspaceStudio"'
contains src/main/resources/static/index.html 'id="uiDecoration"'
contains src/main/resources/static/index.html 'id="uiCalendarDensity"'
contains src/main/resources/static/index.html 'id="uiCalendarLayerStyle"'
contains src/main/resources/static/ui/platform.css 'html[data-ui-layout="sidebar"][data-shell="next"]'
contains src/main/resources/static/ui/platform.css 'html[data-ui-layout="mobile-flow"][data-shell="next"]'
contains src/main/resources/static/ui/platform.css 'data-ui-calendar-layers="dots"'
contains src/main/resources/static/ui/platform.css 'data-ui-decoration="grid"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("navigationOrder"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("navigationVisible"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("calendarDensity"'
contains src/main/java/ru/daniil/shifts/web/ProfileController.java 'out.put("calendarLayerStyle"'
contains src/test/java/ru/daniil/shifts/web/WorkspaceLayoutThemeStudioFrontendContractTest.java "studioEditsDeclarativeConfigurationWithoutFeatureApiCalls"
contains e2e/workspace-layout-theme-studio.spec.js "Workspace Studio persists custom navigation"
contains e2e/design-system-shell.spec.js "UI Core v2"
contains src/main/resources/static/js/shell-bootstrap.js 'root.dataset.uiContract = "2"'
contains src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql "ALTER COLUMN calculation_hash TYPE VARCHAR(64)"

  # v27.28.3 Payroll Snapshot Hash Schema Validation Hotfix
contains CHANGES.md "v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix"
contains README.md "v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix"
contains docs/PAYROLL_SNAPSHOT_HASH_SCHEMA_VALIDATION_HOTFIX_V27.28.3.md "This is a forward-only schema-validation hotfix."
contains docs/ROADMAP.md "v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.28.3 Payroll Snapshot Hash Schema Validation Hotfix extension"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "calculation_hash CHAR(64) NOT NULL"
contains src/main/resources/db/migration/postgresql/V45__payroll_foundation.sql "ck_payroll_snapshot_hash CHECK (calculation_hash ~ '^[0-9a-f]{64}$')"
contains src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql "ALTER COLUMN calculation_hash TYPE VARCHAR(64)"
contains src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql "USING BTRIM(calculation_hash)"
not_contains src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql "DROP CONSTRAINT ck_payroll_snapshot_hash"
not_contains src/main/resources/db/migration/postgresql/V46__payroll_snapshot_hash_schema_alignment.sql "DROP NOT NULL"
contains src/main/java/ru/daniil/shifts/model/PayrollSnapshot.java '@Column(name = "calculation_hash", nullable = false, length = 64)'
contains src/test/java/ru/daniil/shifts/db/PayrollSnapshotHashSchemaValidationHotfixTest.java "6fab27acb0af68a36dfe2dc85c4df09562cc273cf0bb859807ae34f518798709"
contains src/test/java/ru/daniil/shifts/db/PayrollSnapshotHashSchemaValidationHotfixTest.java "v46AlignsSnapshotHashWithJpaWithoutRewritingV45"

if [[ "$(find src/main/resources/db/migration/postgresql -maxdepth 1 -type f -name 'V46__payroll_snapshot_hash_schema_alignment.sql' | wc -l | tr -d ' ')" == "1" ]]; then
  ok "V46 payroll snapshot hash alignment migration appears exactly once"
else
  fail "V46 payroll snapshot hash alignment migration must appear exactly once"
fi

  # v27.28.2 Calendar Persistence Reload Readiness Hotfix
contains CHANGES.md "v27.28.2 — Calendar Persistence Reload Readiness Hotfix"
contains README.md "v27.28.2 — Calendar Persistence Reload Readiness Hotfix"
contains docs/CALENDAR_PERSISTENCE_RELOAD_READINESS_HOTFIX_V27.28.2.md "This is a browser-readiness contract hotfix only."
contains docs/ROADMAP.md "v27.28.2 — Calendar Persistence Reload Readiness Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.28.2 Calendar Persistence Reload Readiness Hotfix extension"
contains src/main/resources/static/js/60-settings.js "window.__dutylogCalendarNavigationReady = Promise.resolve()"
contains src/main/resources/static/js/60-settings.js "function trackCalendarNavigation(operation)"
contains src/main/resources/static/js/60-settings.js "trackCalendarNavigation(goto(state.y, state.m - 1))"
contains src/main/resources/static/js/60-settings.js "trackCalendarNavigation(goto(state.y, state.m + 1))"
contains src/main/resources/static/js/37-calendar-experience.js "trackCalendarNavigation(operation)"
contains e2e/helpers.js "async function waitForCalendarNavigationReady(page)"
contains e2e/helpers.js "window.__dutylogCalendarNavigationReady"
contains e2e/helpers.js "window.__dutylogLedgerReady"
contains e2e/calendar-persistence.spec.js "await waitForCalendarNavigationReady(page)"
contains e2e/calendar-persistence.spec.js "await waitForAppIdle(page);"
contains src/test/java/ru/daniil/shifts/web/CalendarPersistenceReloadReadinessHotfixTest.java "calendarReloadWaitsForNavigationAndLedgerReadModelsWithoutSuppressingFailures"
not_contains e2e/fixtures.js "Failed to load actual work TypeError: Failed to fetch"
not_contains e2e/fixtures.js "Failed to load time compensation summary TypeError: Failed to fetch"

  # v27.28.1 Payroll Module Registry Contract Hotfix
contains CHANGES.md "v27.28.1 — Payroll Module Registry Contract Hotfix"
contains README.md "v27.28.1 — Payroll Module Registry Contract Hotfix"
contains docs/PAYROLL_MODULE_REGISTRY_CONTRACT_HOTFIX_V27.28.1.md "This is a build-contract hotfix only."
contains docs/ROADMAP.md "v27.28.1 — Payroll Module Registry Contract Hotfix — completed"
contains docs/REGRESSION_TEST_BASELINE.md "v27.28.1 Payroll Module Registry Contract Hotfix extension"
contains src/test/java/ru/daniil/shifts/web/PayrollFoundationContractTest.java 'String moduleKeys = source("src/main/java/ru/daniil/shifts/module/ModuleKeys.java");'
contains src/test/java/ru/daniil/shifts/web/PayrollFoundationContractTest.java 'public static final String PAYROLL = \"payroll\";'
contains src/test/java/ru/daniil/shifts/web/PayrollFoundationContractTest.java 'PAYROLL,\n                    ModuleCategory.TIME_ACCOUNTING,'
not_contains src/test/java/ru/daniil/shifts/web/PayrollFoundationContractTest.java 'modules.contains("ModuleService.PAYROLL")'
contains src/main/java/ru/daniil/shifts/module/ModuleKeys.java 'public static final String PAYROLL = "payroll";'
contains src/main/java/ru/daniil/shifts/module/DutyLogModules.java '                    PAYROLL,'
contains src/main/java/ru/daniil/shifts/module/DutyLogModules.java '                    ModuleCategory.TIME_ACCOUNTING,'
not_contains src/main/java/ru/daniil/shifts/module/DutyLogModules.java 'ModuleService.PAYROLL'

  # v27.46.2 6H3 Native NIGHT Premium vertical E2E
  contains e2e/payroll-night-premium.spec.js "native NIGHT pricing adds configured premium from factual work into Payroll and snapshot"
  contains e2e/payroll-night-premium.spec.js "NIGHT_E2E_20"
  contains e2e/payroll-night-premium.spec.js "ordinaryPremiumPayMinor).toBe(20000)"
  contains e2e/payroll-night-premium.spec.js "ordinaryPremiumPricingFingerprint).toHaveLength(64)"
  contains e2e/payroll-night-premium.spec.js "totalPayMinor).toBe(120000)"
  # v27.46.2 6H4a HOLIDAY Premium vertical E2E
  contains e2e/payroll-holiday-premium.spec.js "native HOLIDAY pricing adds configured premium from production calendar into Payroll and snapshot"
  contains e2e/payroll-holiday-premium.spec.js "HOLIDAY_E2E_100"
  contains e2e/payroll-holiday-premium.spec.js "payrollEffect:'HOLIDAY'"
  contains e2e/payroll-holiday-premium.spec.js "ordinaryPremiumPayMinor).toBe(100000)"
  contains e2e/payroll-holiday-premium.spec.js "totalPayMinor).toBe(200000)"
  # v27.46.2 6H4b OVERTIME bank-first cash settlement vertical
  contains e2e/payroll-overtime-settlement.spec.js "banked overtime becomes Payroll money only after explicit settlement with configured tiers"
  contains e2e/payroll-overtime-settlement.spec.js "OT_TIER_1"
  contains e2e/payroll-overtime-settlement.spec.js "OT_TIER_2"
  contains e2e/payroll-overtime-settlement.spec.js "settlementBasePayMinor).toBe(300000)"
  contains e2e/payroll-overtime-settlement.spec.js "settlementPremiumPayMinor).toBe(200000)"
  contains e2e/payroll-overtime-settlement.spec.js "settlementPayMinor).toBe(500000)"
  contains e2e/payroll-overtime-settlement.spec.js "totalPayMinor).toBe(1300000)"
  contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "сумма рассчитывается в Payroll по правилам Pricing"
  contains frontend/src/features/absence-time-bank/components/SettlementEditor.vue "Сумма рассчитывается в Payroll по исторической ставке и правилам Pricing исходной переработки."
  not_contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "сумма денег ещё не рассчитывается"
  not_contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "Сумма появится только после слоя Pricing."
  not_contains frontend/src/features/absence-time-bank/components/SettlementEditor.vue "Стоимость пока не рассчитывается."
  not_contains frontend/src/features/absence-time-bank/components/SettlementEditor.vue "Денежная сумма появится позже"
  # v27.47.0 7A6A Generic Compensation Components E2E
  contains e2e/payroll-compensation-components.spec.js "generic compensation components preserve frozen payroll revisions across later configuration changes"
  contains e2e/payroll-compensation-components.spec.js "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE"
  contains e2e/payroll-compensation-components.spec.js "Премия за выживание после ночной смены"
  contains e2e/payroll-compensation-components.spec.js "PERCENT_OF_BASE"
  contains e2e/payroll-compensation-components.spec.js "FIXED_AMOUNT"
  contains e2e/payroll-compensation-components.spec.js "compensationComponentEarningsMinor"
  contains e2e/payroll-compensation-components.spec.js "persisted1Line.rateBps"
  contains e2e/payroll-compensation-components.spec.js "persisted2Line.rateBps"
  contains e2e/payroll-compensation-components.spec.js "persisted1.supersededById"
  contains e2e/payroll-compensation-components.spec.js "#payrollCompensationComponentBreakdown"
  contains e2e/payroll-compensation-components.spec.js "#compensationComponentPreset"
  ok "Playwright test baseline: 52"
else
  fail "expected 52 Playwright tests, found $E2E_TESTS"
fi

VITEST_TESTS=$(grep -R --include='*.spec.ts' --include='*.test.ts' -h -E '^[[:space:]]*(it|test)\(' frontend/src | wc -l | tr -d ' ')
if [[ "$VITEST_TESTS" == "88" ]]; then
  ok "Vitest case baseline: 88"
else
  fail "expected 88 Vitest cases, found $VITEST_TESTS"
fi


# DutyLog deployment image retention
contains deploy/scripts/prune-dutylog-images.sh '--image must be an immutable image digest reference'
contains deploy/scripts/prune-dutylog-images.sh 'REPO="${BASH_REMATCH[1]}"'
contains deploy/scripts/prune-dutylog-images.sh 'docker ps -aq'
contains deploy/scripts/prune-dutylog-images.sh 'RepoDigests'
contains deploy/scripts/prune-dutylog-images.sh 'docker image rm "$ref"'
contains deploy/scripts/prune-dutylog-images.sh 'docker image inspect "$IMAGE_REF" >/dev/null'
not_contains deploy/scripts/prune-dutylog-images.sh 'docker system prune'
not_contains deploy/scripts/prune-dutylog-images.sh 'docker image prune'
not_contains deploy/scripts/prune-dutylog-images.sh 'docker image rm -f'
not_contains deploy/scripts/prune-dutylog-images.sh 'ctr '
not_contains deploy/scripts/prune-dutylog-images.sh 'rm -rf'
contains deploy/scripts/remote-deploy.sh 'deploy/scripts/prune-dutylog-images.sh'
contains deploy/scripts/remote-deploy.sh 'DUTYLOG_IMAGE_RETENTION_KEEP_NEWEST'
contains deploy/scripts/remote-deploy.sh 'Retention runs only after the deployment and its smoke checks have succeeded.'
contains deploy/scripts/remote-deploy.sh 'Application deployment succeeded, but post-deploy DutyLog image retention failed.'

# 8A4F3F1 — explicit effective-dated work-time accounting regime FACT authority.
contains src/main/resources/db/migration/postgresql/V76__work_time_accounting_regime_fact_authority.sql "CREATE TABLE work_time_accounting_terms"
contains src/main/resources/db/migration/postgresql/V76__work_time_accounting_regime_fact_authority.sql "accounting_mode IN ('DAILY', 'SUMMARIZED')"
not_contains src/main/resources/db/migration/postgresql/V76__work_time_accounting_regime_fact_authority.sql "INSERT INTO work_time_accounting_terms"
contains src/main/java/ru/daniil/shifts/service/WorkTimeAccountingHistoryService.java "WORK_TIME_ACCOUNTING_MODE_FACT_MISSING"
contains src/main/java/ru/daniil/shifts/service/WorkTimeAccountingHistoryService.java "resolveRange"
not_contains src/main/java/ru/daniil/shifts/service/WorkTimeAccountingHistoryService.java "getPayMode()"
contains src/test/java/ru/daniil/shifts/service/WorkTimeAccountingHistoryServiceTest.java "missingHistoryRemainsUnknownWithoutCompatibilityBaseline"

# 8A4F3F2 — immutable P15 scheduled-work snapshot FACT authority.
contains src/main/resources/db/migration/postgresql/V77__snapshot_p15_scheduled_work_fact_freeze.sql "CREATE TABLE payroll_snapshot_p15_work_time_manifests"
contains src/main/resources/db/migration/postgresql/V77__snapshot_p15_scheduled_work_fact_freeze.sql "CREATE TABLE payroll_snapshot_p15_scheduled_work_facts"
contains src/main/resources/db/migration/postgresql/V77__snapshot_p15_scheduled_work_fact_freeze.sql "worked_outside_plan_minutes INTEGER NOT NULL"
not_contains src/main/resources/db/migration/postgresql/V77__snapshot_p15_scheduled_work_fact_freeze.sql "INSERT INTO payroll_snapshot_p15"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "PLANNED_AND_WORKED"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "WORKED_OUTSIDE_PLAN"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "paragraph-13"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "freezeP15ScheduledWork("
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15Formula.java "PayrollP15ScheduledWorkFreezeService"
contains src/test/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeServiceTest.java "absencePlusDerivedOvertimeCannotMasqueradeAsFullyWorkedReferenceTime"
contains src/test/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeServiceTest.java "explicitSecondHalfOfOvernightShiftMarksFirstHalfNotWorkedAndCarriesSupportingActualIdentity"
contains src/test/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeServiceTest.java "fullPlanDerivedOvernightShiftCarriesPreviousSourceTruthAcrossMonthBoundary"

# 8A4F3F3 — 12-month P15 reference worked-time FACT resolver.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "P15_REFERENCE_WORK_TIME_DAILY_PARTIAL_DAY_UNRESOLVED"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "P15_REFERENCE_WORK_TIME_MIXED_ACCOUNTING_MODE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "getPlannedAndWorkedMinutes()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "getScheduleMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "WorkTimeAccountingHistoryService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "snapshot.getWorkedMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactService.java "snapshot.getPlannedMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15Formula.java "AverageEarningsBonusP15ReferenceWorkedTimeFactService"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactServiceTest.java "summarizedUsesOnlyScheduledIntersectionAndKeepsOvertimeOutsideCoefficient"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactServiceTest.java "dailyPartialScheduledDayBlocksInsteadOfRoundingToZeroOrOne"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsBonusP15ReferenceWorkedTimeFactServiceTest.java "mixedModesAcrossReferenceMonthsAlsoBlock"

TEST_METHODS=$(grep -R --include='*.java' -h -E '^[[:space:]]*@Test([[:space:]]|$)' src/test/java | wc -l | tr -d ' ')
TEST_CLASSES=$(find src/test/java -name '*Test.java' -type f | wc -l | tr -d ' ')
# 8A4F3H final average-earnings numerator money assembly contracts.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java "RAW_PREMIUM_RECONCILIATION_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java "premium.calculation().includedPremiumAmountMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java "AverageEarningsParagraph5MoneyPolicy.resolve("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph5MoneyPolicy.java "PP_540_P5_ORDINARY_EARNING_TIME_AUTHORITY_MISSING"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph5MoneyPolicy.java "PP_540_P5_ORDINARY_EARNING_PARTIAL_OVERLAP_UNRESOLVED"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph5MoneyPolicy.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph5MoneyPolicy.java ".divide("
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorFactsService.java "AverageEarningsNumeratorCalculationService"

# 8A4F3I primary vacation average-daily exact-money contracts.
contains src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java "VacationAverageDailyEarningsFormula.calculate("
contains src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java "Paragraph 13 average-hourly earnings is intentionally absent"
contains src/main/java/ru/daniil/shifts/service/VacationAverageDailyEarningsFormula.java "ExactMoneyPerDay"
contains src/main/java/ru/daniil/shifts/service/VacationAverageDailyEarningsFormula.java "does not calculate vacation-pay money"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageDailyEarningsFormula.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageDailyEarningsFormula.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java "averageHourly"

# 8A4F3J1 reference-window parameterization: legal event date != selected 12-month window.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsReferenceWindow.java "referenceTo.equals(referenceFrom.plusMonths(11))"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsReferenceWindow.java "eventMonth.minusMonths(12)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15Policy.java "int previousEventYear = eventDate.getYear() - 1"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15Policy.java "AverageEarningsReferenceWindow.of("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java "AverageEarningsReferenceWindow referenceWindow"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15CalculationPipelineService.java "AverageEarningsReferenceWindow referenceWindow"
contains src/main/java/ru/daniil/shifts/service/VacationAverageReferenceCalendarService.java "AverageEarningsReferenceWindow referenceWindow"
contains src/main/java/ru/daniil/shifts/service/VacationAverageCalendarDenominator.java "AverageEarningsReferenceWindow referenceWindow"
contains src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java "AverageEarningsReferenceWindow referenceWindow"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java "minusMonths(12)"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15CalculationPipelineService.java "minusMonths(12)"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageReferenceCalendarService.java "minusMonths(12)"
not_contains src/main/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationService.java "minusMonths(12)"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsBonusP15PolicyTest.java "previousEqualReferenceWindowKeepsAnnualRewardBoundToLegalEventYear"
contains src/test/java/ru/daniil/shifts/service/VacationAveragePrimaryCalculationServiceTest.java "explicitPreviousReferenceWindowFlowsToNumeratorAndCalendarWithoutChangingEventDate"

# 8A4F3J3A paragraph-7 pre-event actually-worked FACT authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "LocalDate periodTo = eventDate.minusDays(1)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "timeCompensation.payrollSource("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "if (day.workedMinutes() > 0)"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "CompensationCalculationService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "HistoricalCompensationRateService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventWorkFactServiceTest.java "paidAbsenceWithoutWorkedMinutesDoesNotBecomeParagraph7WorkedDay"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventWorkAuthorityContractTest.java "eventDateAndFutureDaysCannotEnterParagraph7Facts"
# 8A4F3J3B1 paragraph-7 pre-event BASE_PAY pricing FACT authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "LocalDate compensationBoundary = eventMonth.atDay(1)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "day.hourlyBaseWorkedMinutes()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "Math.min("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "day.plannedMinutes(),"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "day.workedMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "CompensationCalculationService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "amountMinor"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityServiceTest.java "hourlyAuthorityUsesOnlyHourlyBaseWorkedMinutes"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayAuthorityServiceTest.java "salaryAuthorityCapsEachWorkedDayAtPlannedMinutes"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBasePayAuthorityContractTest.java "salaryAuthorityCapsQuantityAtScheduledWorkWithoutMoneyFormula"
# 8A4F3J3B2 paragraph-7 pre-event BASE_PAY pure money formula.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormula.java "RoundingMode.HALF_UP"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormula.java "ratioMoney("
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormula.java "@Service"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormula.java "Repository"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormula.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormulaTest.java "hourlyHalfMinorRoundsUp"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBasePayFormulaTest.java "salaryHalfMinorRoundsUp"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBasePayFormulaContractTest.java "J3B2DoesNotSelectFallbackOrInferEligibilityFromMoney"
# 8A4F3J3B3 paragraph-7 pre-event explicit semantic wage FACT authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "combinationFacts.resolveMonth(user, eventMonth)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "regionalFacts.resolveMonth(user, eventMonth)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "bonusFacts.resolveMonth(user, eventMonth)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "SOURCE_PERIOD_CROSSES_EVENT"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "SOURCE_CURRENCY_MISMATCH"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "PayrollService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "PayrollSnapshot"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "PayrollHistoricalSemanticEarningsService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "PayrollOrdinaryPremiumPreviewService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactService.java "AverageEarningsParagraph8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactServiceTest.java "combinationPeriodCrossingEventBlocksInsteadOfProratingMoney"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventSemanticWageFactServiceTest.java "zeroBasePayWithWorkedTimeStillDiscoversObservedSemanticFacts"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventSemanticWageAuthorityContractTest.java "J3B3ReadsOnlyExplicitDatedSemanticSourceAuthorities"
# 8A4F3J3B4 paragraph-7 pre-event range-bound ordinary NIGHT / HOLIDAY premium.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "date.isBefore(cutoffExclusive)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "slicesByRate"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "priced.premiumAmountMinor()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "priceMonth("
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "PayrollOrdinaryPremiumPreviewService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "priced.baseAmountMinor()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "priced.totalAmountMinor()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumServiceTest.java "sameEconomicKeyAcrossDatesIsAggregatedBeforeHalfUpRounding"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventOrdinaryPremiumServiceTest.java "laterAmbiguousCurrencyClearsEarlierAcceptedPricingEvidence"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventOrdinaryPremiumAuthorityContractTest.java "J3B4AddsOnlyPremiumMoneyAndNeverDuplicatesBasePay"
# 8A4F3J3B5 paragraph-7 pre-event range-bound HARMFUL_CONDITIONS compensation.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "PayrollEarningKind.HARMFUL_CONDITIONS"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "CalculationBase.EARNED_BASE_PAY"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "resolver.resolve(user, eventMonth)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "calculator.calculate("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "CONFIGURATION_NOT_RANGE_BOUND"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "PayrollCompensationComponentPreviewService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "PayrollSnapshot"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationServiceTest.java "harmfulPercentOfPreEventEarnedBaseUsesCanonicalCalculator"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationServiceTest.java "fixedAmountHarmfulBlocksInsteadOfProratingMonthlyMoney"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventHarmfulCompensationServiceTest.java "workedTimeWithZeroBasePayProducesZeroWithoutFallbackMeaning"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventHarmfulCompensationAuthorityContractTest.java "J3B5ReusesCanonicalResolverAndComponentCalculator"
# 8A4F3J3B6A paragraph-7 pre-event bonus paragraph-15 FACT authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "averageFacts.resolveForBonusFacts(user, source)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "natureFacts.resolveForAverageFacts(user, orderedAverage)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "SourceAuthority.BONUS_SOURCE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "AVERAGE_FACT_MISSING"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "NATURE_FACT_MISSING"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "AverageEarningsBonusP15Policy"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "AverageEarningsBonusP15Formula"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "PayrollBonusSourceFactRepository"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactServiceTest.java "missingAverageFactBlocksAndDoesNotReadNatureAuthority"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FactServiceTest.java "natureFactIdentityMismatchBlocksWithoutPartialAuthority"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBonusP15FactAuthorityContractTest.java "J3B6ADoesNotRunParagraph15PolicyOrFormula"
# 8A4F3J3B6B1 paragraph-7 pre-event bonus accrual / annual discovery FACT authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "historicalDiscovery.resolve("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "DIRECT_ACCRUAL_AUTHORITY_MISSING"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "PayrollBonusP15Nature.ANNUAL_RESULT"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "PayrollBonusP15Nature.SERVICE_LENGTH"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "AverageEarningsBonusP15Policy.resolve"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "AverageEarningsBonusP15Formula"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "AverageEarningsNumeratorCalculationService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityServiceTest.java "missingDirectAccrualAuthorityBlocksWithoutPartialFacts"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityServiceTest.java "unmatchedHistoricalAnnualRewardIsSurfacedForLaterP15Policy"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusAccrualAuthorityServiceTest.java "unmatchedHistoricalMonthlyBonusIsIgnored"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBonusAccrualAuthorityContractTest.java "J3B6B1HistoricalDiscoveryAddsOnlyAnnualOrServiceRewardsWithoutPreEventSource"
# 8A4F3J3B6B2 paragraph-7 pre-event bonus paragraph-15 POLICY.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "[eventMonthStart,eventDate)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "MONTHLY_PART_FOR_PRE_EVENT_MONTH"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "eventDate.getYear() - 1"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "IncompletePreEventTreatment"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "AverageEarningsBonusP15Policy.resolve"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "AverageEarningsBonusP15Formula"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "ReferenceWorkedTimeFact"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "ProductionCalendarService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "TimeCompensationService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "BigInteger"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Policy.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15PolicyTest.java "duplicateMonthlyIndicatorInEventMonthBlocksInsteadOfChoosingForEmployer"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15PolicyTest.java "workPeriodAccruedInEventMonthSelectsOneMonthlyPartForP7Basis"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15PolicyTest.java "awardInsidePreEventAndUnknownActualAccrualDefersFailClosedDecisionToB6B3"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBonusP15PolicyContractTest.java "J3B6B2DefersIncompleteBasisRatioToB6B3"
# 8A4F3J3B6B3A paragraph-7 pre-event bonus scheduled/worked-time FACT authority.
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "public RangeResult deriveRange"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "RANGE_SOURCE_IDENTITY_INCOMPLETE"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "RANGE_PREVIOUS_SOURCE_WINDOW_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java "relationEngine.compareDay("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "[eventMonthStart,eventDate)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "timeCompensation.payrollSource(user, periodFrom, periodTo)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "scheduledWork.deriveRange(user, source)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "DAILY_PARTIAL_DAY_UNRESOLVED"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "MIXED_ACCOUNTING_MODE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "fact.plannedAndWorkedMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "AverageEarningsBonusP15Formula"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "BigInteger"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeServiceTest.java "rangeAuthorityUsesPlanDerivedRelationWithoutPersistence"
contains src/test/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeServiceTest.java "rangeAuthorityBlocksWhenPreviousDayPayrollWindowIsNotExact"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactServiceTest.java "summarizedMissedSchedulePreservesTrueDenominator"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusWorkTimeFactServiceTest.java "dailyPartialScheduledDayBlocksInsteadOfRounding"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBonusWorkTimeAuthorityContractTest.java "J3B6B3AReusesCanonicalP15PlanActualRelationAuthority"
# 8A4F3J3B6B3B paragraph-7 pre-event BONUS P15 formula/money.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "Pure paragraph-7 paragraph-15 BONUS money formula"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "MONTHLY_PART_FOR_PRE_EVENT_MONTH"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "denominator = denominator.multiply(BigInteger.valueOf(awardMonthCount));"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "workTimeAuthority.scheduleFullyWorked()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "INCLUDED_CURRENCY_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "roundHalfUpToLong(numerator, denominator)"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "Paragraph8"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15Formula.java "PARAGRAPH_8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FormulaTest.java "monthlyPartAndWorkedTimeRatioRoundOnlyOnceAtFinalMinorUnit"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FormulaTest.java "unknownActualAccrualFactBlocksOnlyWhenBasisIsIncomplete"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventBonusP15FormulaTest.java "includedCurrenciesMustMatchBeforeMoneyCanBeSummed"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventBonusP15FormulaContractTest.java "J3B6B3BUsesOneMonthlyPartAndOneFinalHalfUpBoundary"
# 8A4F3J3B6C full paragraph-7 pre-event accrued-wage authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "Final paragraph-7 authority for money actually accrued"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "basePay.basePayAmountMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "ordinary.ordinaryPremiumAmountMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "harmful.harmfulAmountMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "case COMBINATION_EPISODE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "case REGIONAL_SOURCE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "case BONUS_SOURCE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "bonus.includedPremiumAmountMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "semantic.equals(bonusSemantic)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "TOTAL_OVERFLOW"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "workedTimePresent()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "accruedWagePresent()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "AverageEarningsParagraph8"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "PARAGRAPH_8"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "@Service"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthority.java "@Transactional"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthorityTest.java "rawBonusSemanticMoneyIsNeverAddedDirectly"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthorityTest.java "allSixMoneyBucketsAssembleOneExactNumerator"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7PreEventAccruedWageAuthorityTest.java "accruedWagePresenceIsIndependentFromWorkedTimePresence"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph7PreEventAccruedWageAuthorityContractTest.java "J3B6CRawBonusFactsAreProvenanceOnlyAndCannotDoubleCountP15Money"
# 8A4F3J4 paragraph-8 established tariff / official-salary authority.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "RULE_ID = \"PP_540_P8\""
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "LocalDate compensationBoundary = eventMonth.atDay(1);"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "term.getHourlyRateMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "EstablishedBasis.HOURLY_TARIFF_RATE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "term.getMonthlySalaryMinor()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "EstablishedBasis.MONTHLY_OFFICIAL_SALARY"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "Blocked paragraph-8 authority cannot expose partial compensation identity"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "HistoricalCompensationRateService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "CompensationCalculationService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "ProductionCalendarService"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "AverageEarningsParagraph6ReferenceResolver"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "AverageEarningsParagraph7PreEvent"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityService.java "RoundingMode"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph8TariffSalaryAuthorityServiceTest.java "salaryResolvesExactConfiguredMonthlyOfficialSalaryWithoutHourlyDerivation"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsParagraph8TariffSalaryAuthorityContractTest.java "J4NeverUsesDerivedHistoricalHourlyRateAuthority"
# 8A4F3J5 ordered primary -> p6 -> p7 -> p8 fallback resolver.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "PRIMARY -> PARAGRAPH 6 PRECEDING -> PARAGRAPH 7 PRE-EVENT -> PARAGRAPH 8"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "paragraph7Supplier.get()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "paragraph8Supplier.get()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "if (paragraph7WagePresent && paragraph7WorkedTimePresent)"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "NO_PRE_EVENT_ACCRUED_WAGE"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "NO_PRE_EVENT_ACTUALLY_WORKED_TIME"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "Blocked ordered fallback cannot expose partial selection"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "VacationAverageDailyEarningsFormula"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolver.java "CompensationTermRepository"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolverTest.java "primarySelectionStopsBeforeParagraph7AndParagraph8"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsOrderedFallbackResolverTest.java "paragraph7BlockedNeverFallsThroughToParagraph8"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsOrderedFallbackResolverContractTest.java "J5CannotJumpDirectlyFromParagraph6ExhaustionToParagraph8"
# 8A4F3K unified exact vacation average-daily resolver.
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "J5 owns that policy"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "VacationAveragePrimaryCalculationService.Resolution"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "Paragraph7CalendarBasis"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "basis.denominatorDays()"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "PARAGRAPH_8_FORMULA_BASIS_REQUIRED"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "MONTHLY_OFFICIAL_SALARY_DIV_29_3"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3"
contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "authorityCode"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java ".workedDayCount()"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "payableVacationDays"
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolver.java "vacationPayMinor"
contains src/test/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolverTest.java "paragraph7UsesExplicitParagraph10PartialMonthDenominator"
contains src/test/java/ru/daniil/shifts/service/VacationAverageUnifiedDailyResolverTest.java "paragraph8HourlyTariffUsesExplicitAnnualNormFormula"
contains src/test/java/ru/daniil/shifts/web/VacationAverageUnifiedDailyResolverContractTest.java "KDoesNotChooseFallbackBranch"
# 8A4F3L Article-120 payable annual-vacation calendar-day FACT.
contains src/main/java/ru/daniil/shifts/service/AnnualPaidVacationHolidayPolicy.java "RULE_ID = \"TK_RF_ARTICLE_120\""
contains src/main/java/ru/daniil/shifts/service/AnnualPaidVacationHolidayPolicy.java "TK_RF_ARTICLE_112_FEDERAL_HOLIDAY"
contains src/main/java/ru/daniil/shifts/service/AnnualPaidVacationHolidayPolicy.java "productionCalendar.resolvedDay(user, date)"
contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "RULE_ID = \"TK_RF_ARTICLE_120\""
contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "ledgerIntegrity.posts(period.getStatus())"
contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "AnnualPaidVacationHolidayPolicy.classify("
contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "payableDates"
contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "excludedHolidayDates"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "VacationAverageUnifiedDailyResolver"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "VacationSettings"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayableDaysFactService.java "currencyCode"
contains src/main/java/ru/daniil/shifts/service/VacationAverageReferenceCalendarService.java "AnnualPaidVacationHolidayPolicy.classify("
not_contains src/main/java/ru/daniil/shifts/service/VacationAverageReferenceCalendarService.java "FEDERAL_NON_WORKING_HOLIDAYS"
contains src/test/java/ru/daniil/shifts/service/AnnualPaidVacationHolidayPolicyTest.java "transferredDayOffIsNotSilentlyPromotedToHoliday"
contains src/test/java/ru/daniil/shifts/service/VacationPayableDaysFactServiceTest.java "entirelyFederalHolidaySpanIsProvenReadyZero"
contains src/test/java/ru/daniil/shifts/service/VacationPayableDaysFactServiceTest.java "plannedStatusIsNotPayableAuthority"
contains src/test/java/ru/daniil/shifts/web/VacationPayableDaysFactAuthorityContractTest.java "LOwnsArticle120PayableDayFactOnly"
# 8A4F3M PP-540 paragraph-9 final vacation-pay MONEY formula.
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "RULE_ID = \"PP_540_P9_VACATION_PAY_MONEY\""
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "ROUNDING_POLICY = \"FINAL_MINOR_UNIT_HALF_UP\""
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "VacationAverageUnifiedDailyResolver.Resolution dailyAuthority"
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "VacationPayableDaysFactService.Resolution payableDaysAuthority"
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java ".multiply(BigInteger.valueOf(payableCalendarDays))"
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "divideAndRemainder"
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "AMOUNT_OVERFLOW"
contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "CURRENCY_REQUIRED"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "double"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "VacationSettings"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "ProductionCalendarService"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayMoneyFormula.java "AbsencePeriodRepository"
contains src/test/java/ru/daniil/shifts/service/VacationPayMoneyFormulaTest.java "provenZeroPayableDaysProduceReadyZeroMoney"
contains src/test/java/ru/daniil/shifts/service/VacationPayMoneyFormulaTest.java "exactHalfMinorRoundsAwayFromZeroUnderExplicitHalfUpPolicy"
contains src/test/java/ru/daniil/shifts/service/VacationPayMoneyFormulaTest.java "finalAmountOverflowBlocksInsteadOfWrapping"
contains src/test/java/ru/daniil/shifts/web/VacationPayMoneyFormulaContractTest.java "MDoesNotRecomputeKOrLPolicies"
# 8A4F3N annual-vacation pay authority orchestrator.
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "RULE_ID = \"DUTYLOG_VACATION_PAY_ORCHESTRATOR\""
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "@Autowired"
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "VacationAverageUnifiedDailyResolver::resolve"
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "VacationPayMoneyFormula::calculate"
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "if (!payableDaysAuthority.ready())"
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "if (!dailyAuthority.ready())"
contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "if (!moneyAuthority.ready())"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "BigInteger"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "divideAndRemainder"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "ProductionCalendarService"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "AbsencePeriodRepository"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "AnnualPaidVacationHolidayPolicy"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "PRIMARY_REFERENCE_PERIOD"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java "PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY"
contains src/test/java/ru/daniil/shifts/service/VacationPayOrchestratorTest.java "lBlockedWinsBeforeNullOrderedFallbackOrSuppliers"
contains src/test/java/ru/daniil/shifts/service/VacationPayOrchestratorTest.java "blockedJ5MakesKBlockedAndMIsNotCalled"
contains src/test/java/ru/daniil/shifts/service/VacationPayOrchestratorTest.java "provenZeroPayableDaysRemainReadyZeroEndToEnd"
contains src/test/java/ru/daniil/shifts/service/VacationPayOrchestratorTest.java "mOverflowPropagatesWithoutPartialMoney"
contains src/test/java/ru/daniil/shifts/web/VacationPayOrchestratorContractTest.java "NDoesNotChooseJ5FallbackBranch"
contains src/test/java/ru/daniil/shifts/web/VacationPayOrchestratorContractTest.java "NBlockedFacadeNeverExposesPartialMoney"
# 8A4F3O vacation-pay application authority: P6 -> J5 -> N.
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "RULE_ID = \"DUTYLOG_VACATION_PAY_APPLICATION\""
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "@Autowired"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "AverageEarningsOrderedFallbackResolver::resolve"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "orderedFallback.selectedReferenceWindow()"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "vacationResolver.resolve("
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "PayrollService"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "PayrollSnapshot"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "BigInteger"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "BigDecimal"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "RoundingMode"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "ProductionCalendarService"
not_contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "AbsencePeriodRepository"
contains src/test/java/ru/daniil/shifts/service/VacationPayApplicationServiceTest.java "realJ5Paragraph8EvaluatesParagraph7ThenParagraph8ExactlyOnce"
contains src/test/java/ru/daniil/shifts/service/VacationPayApplicationServiceTest.java "selectedReferenceWindowIsPassedExactlyToCalculator"
contains src/test/java/ru/daniil/shifts/web/VacationPayApplicationServiceContractTest.java "ODoesNotMutatePayrollSnapshotOrTotalPay"
# 8A4F3P vacation-pay basis authorities: p7 p10 calendar + p8 formula norm.
contains src/main/java/ru/daniil/shifts/service/AverageEarningsReferenceFactsService.java "public ReferenceFacts resolveRange("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java "RULE_ID = \"PP_540_P7_P10_CALENDAR_BASIS\""
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java "referenceFacts.resolveRange("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java "AverageEarningsLegalPolicy.classifyAbsence("
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java "AnnualPaidVacationHolidayPolicy.classify("
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java "workedDayCount"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "MONTHLY_SALARY_RULE_ID"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "HOURLY_TARIFF_RULE_ID"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "monthNumber <= 12"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "calendar.scheduleCoverageComplete()"
contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "calendar.productionNormMinutes()"
not_contains src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java "baseNormMinutes()"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityServiceTest.java "fullPreEventEmploymentCountsCalendarDaysNotWorkedShifts"
contains src/test/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityServiceTest.java "hourlyTariffSumsTwelveCompleteProductionNormMonths"
contains src/test/java/ru/daniil/shifts/web/AverageEarningsVacationBasisAuthoritiesContractTest.java "basisAuthoritiesContainNoMoneyCalculationOrPayrollMutation"
# 8A4F3Q vacation-pay basis wiring: canonical P7/P8 authority suppliers through O/N/K.
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "AverageEarningsParagraph7CalendarBasisAuthorityService paragraph7CalendarBasis"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "AverageEarningsParagraph8VacationFormulaBasisAuthorityService paragraph8FormulaBasis"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "paragraph7CalendarBasis::resolve"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "paragraph8FormulaBasis::resolve"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "ordered -> () -> canonicalParagraph7CalendarBasis(user, eventDate)"
contains src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java "orderedFallback.paragraph8Authority()"
contains src/test/java/ru/daniil/shifts/service/VacationPayApplicationServiceTest.java "canonicalBasisAuthoritiesStayLazyUntilVacationResolverEvaluatesSuppliers"
contains src/test/java/ru/daniil/shifts/service/VacationPayApplicationServiceTest.java "canonicalParagraph8SupplierUsesExactSelectedJ5Authority"
contains src/test/java/ru/daniil/shifts/service/VacationPayApplicationServiceTest.java "explicitBasisSupplierSeamNeverTouchesCanonicalBasisAuthorities"
contains src/test/java/ru/daniil/shifts/web/VacationPayApplicationServiceContractTest.java "QWiresCanonicalBasisAuthoritiesAndRetainsExplicitSupplierSeam"

if [[ "$TEST_METHODS" == "2289" ]]; then
  ok "test method baseline: 2289"
else
  fail "expected 2289 @Test methods, found $TEST_METHODS"
fi
if [[ "$TEST_CLASSES" == "336" ]]; then
  ok "test class baseline: 336"
else
  fail "expected 336 test classes, found $TEST_CLASSES"
fi

# v27.42.7 People Profiles E2E Locator Alignment Hotfix
contains CHANGES.md "v27.42.7 — People Profiles E2E Locator Alignment Hotfix"
contains README.md "DutyLog v27.42.7 — People Profiles E2E Locator Alignment Hotfix"
contains docs/PEOPLE_PROFILES_E2E_LOCATOR_ALIGNMENT_HOTFIX_V27.42.7.md "People Profiles E2E Locator Alignment Hotfix"
contains e2e/schedule-templates-calendar-layers.spec.js "page.getByText('График только для просмотра', { exact: true })"
not_contains e2e/schedule-templates-calendar-layers.spec.js "page.locator('.calendarProfileReadOnly')).toContainText('только для просмотра')"

# v27.42.8 Tasks Module E2E Route Contract Alignment Hotfix
contains CHANGES.md "v27.42.8 — Tasks Module E2E Route Contract Alignment Hotfix"
contains README.md "DutyLog v27.42.8 — Tasks Module E2E Route Contract Alignment Hotfix"
contains docs/TASKS_MODULE_E2E_ROUTE_CONTRACT_ALIGNMENT_HOTFIX_V27.42.8.md "Tasks Module E2E Route Contract Alignment Hotfix"
contains e2e/helpers.js "tasks: '[data-vue-domain-route=\"tasks\"]',"
contains e2e/task-modules.spec.js "page.locator('[data-vue-domain-route=\"tasks\"]')).toBeVisible()"
not_contains e2e/task-modules.spec.js "page.locator('#view-tasks')).not.toHaveClass(/moduleHidden/)"
not_contains e2e/task-modules.spec.js "page.locator('#view-tasks')).toHaveClass(/moduleHidden/)"

# v27.46.1 Temporal Work Context & Native Pay Pricing
contains CHANGES.md "v27.46.1 — Temporal Work Context & Native Pay Pricing"
contains README.md "DutyLog v27.46.1 — Temporal Work Context & Native Pay Pricing"
contains docs/TEMPORAL_WORK_CONTEXT_NATIVE_PAY_PRICING_V27.46.1.md "Reality → Temporal Work Context → Classification → Time Bank / Settlement → Pricing → Payroll Snapshot"
contains pom.xml "<version>${VERSION}</version>"
contains package.json "\"version\": \"${VERSION}\""
contains frontend/package.json "\"version\": \"${VERSION}\""
contains frontend/generated-lockfile-manifest.txt "release=${VERSION}"
contains frontend/generated-lockfile-manifest.txt "nextAction=roadmap-after-v${VERSION}-green"
contains frontend/browser-bundle-budget.json "\"release\": \"${VERSION}\""
contains frontend/browser-bundle-budget.json '"maxTotalBytes": 980000'
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'
contains src/main/resources/application-prod.properties "info.app.release-version=${VERSION}"
contains src/main/resources/static/service-worker.js "dutylog-shell-v${VERSION}-"
contains src/main/resources/db/migration/postgresql/V53__temporal_work_timezone.sql "CREATE TABLE work_timezone_terms"
contains src/main/resources/db/migration/postgresql/V54__actual_work_historical_identity.sql "source_timezone"
contains src/main/resources/db/migration/postgresql/V55__overtime_credit_provenance_slices.sql "CREATE TABLE overtime_credit_slices"
contains src/main/resources/db/migration/postgresql/V56__overtime_allocation_credit_offsets.sql "credit_offset_start_minutes"
contains src/main/resources/db/migration/postgresql/V57__explicit_overtime_settlement_foundation.sql "CREATE TABLE overtime_settlements"
contains src/main/resources/db/migration/postgresql/V58__effective_dated_pay_pricing_rules.sql "CREATE TABLE pay_pricing_terms"
contains src/main/resources/db/migration/postgresql/V59__payroll_snapshot_settlement_pricing.sql "settlement_pay_minor"
contains src/main/resources/db/migration/postgresql/V60__payroll_snapshot_settlement_pricing_fingerprint.sql "settlement_pricing_fingerprint"
contains src/main/resources/db/migration/postgresql/V61__payroll_snapshot_ordinary_premium_pricing.sql "ordinary_premium_pricing_fingerprint"
contains src/main/java/ru/daniil/shifts/service/PayClassificationService.java "class PayClassificationService"
contains src/main/java/ru/daniil/shifts/service/OvertimeSettlementService.java "class OvertimeSettlementService"
contains src/main/java/ru/daniil/shifts/service/PayPricingEngine.java "class PayPricingEngine"
contains src/main/java/ru/daniil/shifts/service/OrdinaryWorkPremiumPricingService.java "DUTYLOG_ORDINARY_PREMIUM_PRICING_V1"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "ordinaryPremiumPreview.pricingFingerprint()"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'id="payrollOrdinaryPremiumBreakdown"'
contains CHANGES.md 'OpenAPI is **138 operations / 144 schemas** with SHA-256 `1c76051d23596643e6cd2c92a248bfa7126c0e7a33c62587cea3c62a11d38352`.'
contains docs/TEMPORAL_WORK_CONTEXT_NATIVE_PAY_PRICING_V27.46.1.md 'OpenAPI: 138 operations / 144 schemas.'
contains frontend/src/generated/dutylog-api.ts "Contract: 145 operations, 151 schemas"
contains frontend/src/generated/dutylog-api.ts 'DUTYLOG_OPENAPI_SOURCE_SHA256 = "31e70acc2763ee31a8ba91006adcaedcd00309459afda0b728f2c45aa775288f"'
contains docs/API.md "# DutyLog API v${VERSION}"
contains docs/ROADMAP.md "Current release: **v${VERSION} — ${CURRENT_RELEASE_TITLE}**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v${VERSION}"
contains docs/RELEASE_CHECKLIST.md "Status: v${VERSION}."
contains docs/SECURITY_REVIEW.md "Status: v${VERSION}."
contains docs/MODULE_CONTRACTS.md "Status: v${VERSION}."
contains docs/SUPPLY_CHAIN.md "Status: v${VERSION}."

# v27.47.0 Generic Compensation Components release closure
contains CHANGES.md "v27.47.0 — Generic Compensation Components"
contains README.md "DutyLog v27.47.0 — Generic Compensation Components"
contains docs/GENERIC_COMPENSATION_COMPONENTS_V27.47.0.md "arbitrary user-owned"
contains docs/GENERIC_COMPENSATION_COMPONENTS_V27.47.0.md "145 operations / 151 schemas"
contains docs/GENERIC_COMPENSATION_COMPONENTS_V27.47.0.md "56442081179218567fafea52e21e0e7b4fc00e9b96789b1058715809225df78f"
contains docs/GENERIC_COMPENSATION_COMPONENTS_V27.47.0.md "Flyway **V63**"
contains docs/GENERIC_COMPENSATION_COMPONENTS_V27.47.0.md "52nd Chromium journey"
contains docs/ROADMAP.md "## v27.47.0 — Generic Compensation Components"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.47.0 Generic Compensation Components"
contains docs/RELEASE_CHECKLIST.md "## v27.47.0 — Generic Compensation Components acceptance"
contains docs/SECURITY_REVIEW.md "# v27.47.0 Generic Compensation Components review"
contains docs/MODULE_CONTRACTS.md "## Generic Compensation Components contract (v27.47.0)"
contains docs/SUPPLY_CHAIN.md "## v27.47.0 generic compensation components"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "CREATE TABLE compensation_components"
contains src/main/resources/db/migration/postgresql/V63__payroll_snapshot_compensation_components.sql "payroll_snapshot_compensation_component_lines"
contains frontend/browser-bundle-budget.json '"maxTotalBytes": 980000'
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.47.0 7A5 Neutral Generic Compensation Presets
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue 'data-compensation-preset-helper'
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue 'id="compensationComponentPreset"'
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue 'value="earned"'
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue 'value="nominal"'
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue 'value="fixed"'
# Generic compensation presets must remain semantically neutral.
# Explicit machine-owned earning identity now has its own selector, therefore
# semantic labels are allowed elsewhere in CompensationComponentsCard.vue.
python3 - <<'PY_RELEASE_GENERIC_PRESET'
from pathlib import Path

card = Path(
    "frontend/src/features/payroll/components/"
    "CompensationComponentsCard.vue"
).read_text(encoding="utf-8")

marker = "data-compensation-preset-helper"

start = card.find(marker)

if start < 0:
    raise SystemExit(
        "ERROR: generic compensation preset helper is missing"
    )

end = card.find(
    "</div>",
    start,
)

if end < 0:
    raise SystemExit(
        "ERROR: generic compensation preset helper boundary is invalid"
    )

preset = card[start:end]

for forbidden in (
    "Вредность",
    "Районный коэффициент",
    "Совмещение",
    "HARMFUL_CONDITIONS",
    "COMBINATION",
    "MONTHLY_BONUS",
    "ONE_TIME_BONUS",
    "REGIONAL_COEFFICIENT",
):
    if forbidden in preset:
        raise SystemExit(
            "ERROR: generic compensation preset contains semantic identity: "
            + forbidden
        )

print(
    "OK:    generic compensation presets remain semantically neutral"
)
PY_RELEASE_GENERIC_PRESET
not_contains frontend/src/features/payroll/api/payrollApi.ts "compensationPreset"
not_contains frontend/src/features/payroll/stores/payrollStore.ts "compensationPreset"
contains frontend/src/features/payroll/components/CompensationComponentsCard.spec.ts "keeps presets as ephemeral form helpers"
contains frontend/browser-bundle-budget.json '"maxTotalBytes": 980000'
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.47.0 7A4B Generic Compensation Explainability UI
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'id="payrollCompensationComponentsTotal"'
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'id="payrollCompensationComponentBreakdown"'
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "preview.compensationComponentLines"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "compensationComponentCalculationBlockingReason"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "PAYROLL_COMP_COMPONENT_INVALID"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "item.compensationComponentLines"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "item.compensationComponentFingerprint"
contains frontend/src/features/payroll/components/PayrollWorkspace.spec.ts "PayrollWorkspace generic compensation explainability"

# v27.47.0 7A4A Generic Compensation Settings UI
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue "data-generic-compensation-components"
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue "премия за выживание после ночной смены"
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue "Фактически начисленная базовая оплата"
contains frontend/src/features/payroll/api/payrollApi.ts 'client.request("createPayrollCompensationComponent"'
contains frontend/src/features/payroll/api/payrollApi.ts '"upsertPayrollCompensationComponentVersion"'
contains frontend/src/features/payroll/stores/payrollStore.ts "compensationComponentHistory"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "<CompensationComponentsCard"
contains frontend/src/features/payroll/components/CompensationComponentsCard.spec.ts "keeps display names user-owned"
contains frontend/browser-bundle-budget.json '"maxTotalBytes": 980000'
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.47.0 7A3B Generic Compensation Payroll Projection Integration
contains src/main/java/ru/daniil/shifts/service/PayrollCompensationComponentPreviewService.java "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH"
contains src/main/java/ru/daniil/shifts/service/PayrollCompensationComponentPreviewService.java "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "compensationComponentEarningsMinor"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "freezeComponentLines"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "Frozen compensation component line count "
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "Frozen compensation component earnings "
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "PayrollCompensationComponentLineDto"
contains src/main/resources/static/openapi/dutylog-v1.yaml "PayrollCompensationComponentLine:"
contains frontend/src/generated/dutylog-api.ts "Contract: 145 operations, 151 schemas"
contains frontend/src/generated/dutylog-api.ts 'DUTYLOG_OPENAPI_SOURCE_SHA256 = "31e70acc2763ee31a8ba91006adcaedcd00309459afda0b728f2c45aa775288f"'
contains src/test/java/ru/daniil/shifts/service/PayrollCompensationComponentPayrollIntegrationTest.java "Премия за выживание после ночной смены"

# v27.47.0 7A3A Immutable Generic Compensation Component Snapshot Foundation
contains src/main/resources/db/migration/postgresql/V63__payroll_snapshot_compensation_components.sql "compensation_component_count"
contains src/main/resources/db/migration/postgresql/V63__payroll_snapshot_compensation_components.sql "CREATE TABLE payroll_snapshot_compensation_component_lines"
contains src/main/resources/db/migration/postgresql/V63__payroll_snapshot_compensation_components.sql "Deliberately no FK to mutable configuration"
contains src/main/java/ru/daniil/shifts/model/PayrollSnapshot.java "compensationComponentCount"
contains src/main/java/ru/daniil/shifts/model/PayrollSnapshot.java "compensationComponentEarningsMinor"
contains src/main/java/ru/daniil/shifts/model/PayrollSnapshot.java "compensationComponentFingerprint"
contains src/main/java/ru/daniil/shifts/model/PayrollSnapshotComponentLine.java "class PayrollSnapshotComponentLine"
contains src/main/java/ru/daniil/shifts/repo/PayrollSnapshotComponentLineRepository.java "findBySnapshotOrderByLineIndexAsc"
contains src/test/java/ru/daniil/shifts/model/PayrollSnapshotCompensationComponentModelTest.java "zeroMoneyComponentProjectionMayStillFreezeIdentity"
contains src/test/java/ru/daniil/shifts/model/PayrollSnapshotCompensationComponentModelTest.java "Премия за выживание после ночной смены"

# v27.47.0 7A2 Generic Compensation Components API / Resolver development slice
contains src/main/java/ru/daniil/shifts/service/CompensationComponentResolverService.java "class CompensationComponentResolverService"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentResolverService.java "putIfAbsent"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentConfigurationService.java "PAYROLL_COMP_COMPONENT_INVALID"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentConfigurationService.java "findOwnerHistory"
contains src/main/java/ru/daniil/shifts/web/CompensationComponentController.java '/api/v1/payroll/compensation-components'
not_contains src/main/java/ru/daniil/shifts/web/CompensationComponentController.java "@DeleteMapping"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: createPayrollCompensationComponent"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: listPayrollCompensationComponentHistory"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: listEffectivePayrollCompensationComponents"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: upsertPayrollCompensationComponentVersion"
contains frontend/src/generated/dutylog-api.ts "Contract: 145 operations, 151 schemas"
contains frontend/src/generated/dutylog-api.ts 'DUTYLOG_OPENAPI_SOURCE_SHA256 = "31e70acc2763ee31a8ba91006adcaedcd00309459afda0b728f2c45aa775288f"'
contains frontend/src/generated/dutylog-api.ts '"createPayrollCompensationComponent": { method: "POST", path: "/api/v1/payroll/compensation-components" }'
contains frontend/src/generated/dutylog-api.ts '"listPayrollCompensationComponentHistory": { method: "GET", path: "/api/v1/payroll/compensation-components" }'
contains frontend/src/generated/dutylog-api.ts '"listEffectivePayrollCompensationComponents": { method: "GET", path: "/api/v1/payroll/compensation-components/effective/{month}" }'
contains frontend/src/generated/dutylog-api.ts '"upsertPayrollCompensationComponentVersion": { method: "PUT", path: "/api/v1/payroll/compensation-components/{componentId}/versions/{month}" }'
contains src/test/java/ru/daniil/shifts/service/CompensationComponentConfigurationServiceTest.java "Премия за выживание после ночной смены"
contains src/test/java/ru/daniil/shifts/service/CompensationComponentConfigurationServiceTest.java "disabledEffectiveVersionDoesNotEraseStableComponentHistory"
contains src/test/java/ru/daniil/shifts/web/CompensationComponentControllerTest.java "componentWritesRemainCsrfProtected"
contains src/test/java/ru/daniil/shifts/web/CompensationComponentOpenApiContractTest.java "upsertPayrollCompensationComponentVersion"

# v27.47.0 7A1 Generic Compensation Components semantic foundation development slice
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "CREATE TABLE compensation_components"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "CREATE TABLE compensation_component_versions"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "'FIXED_AMOUNT'"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "'PERCENT_OF_BASE'"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "'NOMINAL_SALARY'"
contains src/main/resources/db/migration/postgresql/V62__generic_compensation_components.sql "'EARNED_BASE_PAY'"
contains src/main/java/ru/daniil/shifts/model/CompensationComponentVersion.java "String displayName"
contains src/main/java/ru/daniil/shifts/model/CompensationComponentVersion.java "enum CalculationType"
contains src/main/java/ru/daniil/shifts/model/CompensationComponentVersion.java "enum CalculationBase"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentCalculationService.java "DUTYLOG_COMP_COMPONENT_PROJECTION_V1"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentCalculationService.java "RoundingMode.HALF_UP"
contains src/main/resources/db/migration/postgresql/V69__local_eligible_earnings_component_base.sql "LOCAL_ELIGIBLE_EARNINGS"
contains src/main/java/ru/daniil/shifts/service/CompensationComponentCalculationService.java "LOCAL_ELIGIBLE_EARNINGS is only proven for MONTHLY_BONUS and REGIONAL_COEFFICIENT"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "componentUpstreamSemanticEarningsComplete"
contains src/main/resources/static/openapi/dutylog-v1.yaml "LOCAL_ELIGIBLE_EARNINGS"
contains src/main/resources/static/openapi/dutylog-v1.yaml "PAYROLL_COMP_COMPONENT_LOCAL_BASE_INCOMPLETE"
contains src/main/resources/static/openapi/dutylog-v1.yaml "PAYROLL_COMP_COMPONENT_LOCAL_BASE_UNSUPPORTED"
contains frontend/src/generated/dutylog-api.ts "PAYROLL_COMP_COMPONENT_LOCAL_BASE_INCOMPLETE"
contains frontend/src/generated/dutylog-api.ts "PAYROLL_COMP_COMPONENT_LOCAL_BASE_UNSUPPORTED"
contains frontend/src/features/payroll/components/CompensationComponentsCard.vue "LOCAL_ELIGIBLE_EARNINGS"
contains src/test/java/ru/daniil/shifts/service/CompensationComponentCalculationServiceTest.java "Вредность 4%"
contains src/test/java/ru/daniil/shifts/service/CompensationComponentCalculationServiceTest.java "duplicateStableComponentIdentityFailsClosed"

# v27.48.0 8A4E2B3C2 REGIONAL explicit source-period authority
contains src/main/resources/db/migration/postgresql/V70__regional_coefficient_source_fact_authority.sql "CREATE TABLE payroll_regional_coefficient_source_facts"
contains src/main/resources/db/migration/postgresql/V70__regional_coefficient_source_fact_authority.sql "Missing rows mean no exact source"
contains src/main/java/ru/daniil/shifts/model/PayrollRegionalCoefficientSourceFact.java "never authorize using posting month"
contains src/main/java/ru/daniil/shifts/service/PayrollRegionalCoefficientSourceFactService.java "never allocates a monthly regional amount"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "regionalCoefficientSourceFacts.resolveMonth("
contains src/main/java/ru/daniil/shifts/service/PayrollCompensationComponentSemanticProvenance.java "Explicit REGIONAL source fact requires frozen LOCAL_ELIGIBLE_EARNINGS percentage formula"
contains src/test/java/ru/daniil/shifts/payroll/truth/RealPayrollTruthPackTest.java "Observed REGIONAL remains one source line"

# v27.46.2 Effective-Dated Pricing Settings & Payroll Verticals release closure
contains CHANGES.md "v27.46.2 — Effective-Dated Pricing Settings & Payroll Verticals"
contains README.md "DutyLog v27.46.2 — Effective-Dated Pricing Settings & Payroll Verticals"
contains docs/EFFECTIVE_DATED_PRICING_SETTINGS_PAYROLL_VERTICALS_V27.46.2.md "GET /api/v1/payroll/pricing/terms"
contains docs/EFFECTIVE_DATED_PRICING_SETTINGS_PAYROLL_VERTICALS_V27.46.2.md 'rules: []'
contains docs/EFFECTIVE_DATED_PRICING_SETTINGS_PAYROLL_VERTICALS_V27.46.2.md "141 operations / 147 schemas"
contains docs/EFFECTIVE_DATED_PRICING_SETTINGS_PAYROLL_VERTICALS_V27.46.2.md "19f794b9af9676dc21698f7a92e340c55c8d181dcae817395512c9d4cc063f46"
contains docs/API.md "141 operations / 147 schemas"
contains docs/API.md "19f794b9af9676dc21698f7a92e340c55c8d181dcae817395512c9d4cc063f46"
contains docs/ROADMAP.md "## v27.46.2 — Effective-Dated Pricing Settings & Payroll Verticals"
contains docs/REGRESSION_TEST_BASELINE.md "Historical v27.46.2 extension:"
contains docs/RELEASE_CHECKLIST.md "## v27.46.2 — Effective-Dated Pricing Settings & Payroll Verticals acceptance"
contains docs/SECURITY_REVIEW.md "# v27.46.2 Effective-Dated Pricing Settings & Payroll Verticals review"
contains docs/MODULE_CONTRACTS.md "## Effective-Dated Pricing Settings & Payroll Verticals contract (v27.46.2)"
contains docs/SUPPLY_CHAIN.md "## v27.46.2 effective-dated pricing settings & payroll verticals"
contains frontend/browser-bundle-budget.json '"maxTotalBytes": 980000'
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.46.2 6H1 Native Pricing API Foundation development slice
contains src/main/java/ru/daniil/shifts/service/PayPricingConfigurationService.java "class PayPricingConfigurationService"
contains src/main/java/ru/daniil/shifts/web/PayPricingController.java '/api/v1/payroll/pricing/terms'
contains src/main/resources/static/openapi/dutylog-v1.yaml '/api/v1/payroll/pricing/terms:'
contains src/main/resources/static/openapi/dutylog-v1.yaml '/api/v1/payroll/pricing/terms/{effectiveFrom}:'
contains frontend/src/generated/dutylog-api.ts '"listPayrollPricingTerms": { method: "GET", path: "/api/v1/payroll/pricing/terms" }'
contains frontend/src/generated/dutylog-api.ts '"upsertPayrollPricingTerm": { method: "PUT", path: "/api/v1/payroll/pricing/terms/{effectiveFrom}" }'
contains frontend/src/generated/dutylog-api.ts '"deletePayrollPricingTerm": { method: "DELETE", path: "/api/v1/payroll/pricing/terms/{effectiveFrom}" }'
contains src/test/java/ru/daniil/shifts/service/PayPricingConfigurationServiceTest.java "class PayPricingConfigurationServiceTest"
contains src/test/java/ru/daniil/shifts/web/PayPricingControllerTest.java "class PayPricingControllerTest"
contains src/test/java/ru/daniil/shifts/web/PayPricingConfigurationOpenApiContractTest.java "class PayPricingConfigurationOpenApiContractTest"

# v27.46.0 Compensation Setup & Base Native Payroll
contains CHANGES.md "v27.46.0 — Compensation Setup & Base Native Payroll"
contains README.md "DutyLog v27.46.0 — Compensation Setup & Base Native Payroll"
contains docs/COMPENSATION_SETUP_BASE_NATIVE_PAYROLL_V27.46.0.md "effective-month"
contains src/main/resources/db/migration/postgresql/V52__compensation_terms_base_native_payroll.sql "CREATE TABLE compensation_terms"
contains src/main/resources/db/migration/postgresql/V52__compensation_terms_base_native_payroll.sql "pay_mode"
contains src/main/java/ru/daniil/shifts/service/CompensationCalculationService.java "salaryCoveredMinutes"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "timeCompensation.payrollSource"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "PAYROLL_COMPENSATION_REQUIRED"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "Как тебе платят?"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "Расчётная стоимость часа"
contains src/main/resources/static/openapi/dutylog-v1.yaml "/api/v1/payroll/compensation-terms/{month}:"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "PAYROLL_PRODUCTION_NORM_INCOMPLETE"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "effectiveMonth.value=month.value"
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.45.1 Native Workday / Day Truth Integration
contains CHANGES.md "v27.45.1 — Native Workday / Day Truth Integration"
contains README.md "DutyLog v27.45.1 — Native Workday / Day Truth Integration"
contains docs/NATIVE_WORKDAY_DAY_TRUTH_V27.45.1.md "Пользователь описывает реальность"
contains src/main/java/ru/daniil/shifts/service/WorkdayTruthService.java "class WorkdayTruthService"
contains src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java "requiredMinutes(AppUser user, LocalDate date, DayEntry schedule)"
contains src/main/java/ru/daniil/shifts/service/TimeCompensationService.java "productionCalendar.requiredMinutes(user, date"
contains src/main/java/ru/daniil/shifts/web/WorkdayTruthController.java '@RequestMapping({"/api/workdays", "/api/v1/workdays"})'
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: workdayTruth"
contains frontend/src/generated/dutylog-api.ts '"workdayTruth": { method: "GET", path: "/api/v1/workdays/{date}" }'
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "data-native-workday-truth"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "data-native-special-day-editor"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "data-native-actual-work-editor"
contains src/main/resources/db/migration/postgresql/V50__native_workday_closed_loop.sql "break_minutes"
contains src/main/resources/db/migration/postgresql/V50__native_workday_closed_loop.sql "SYSTEM_ACTUAL_WORK"
contains src/main/resources/db/migration/postgresql/V51__native_actual_work_end_date.sql "end_date"
contains src/main/java/ru/daniil/shifts/service/ActualWorkDayAllocationService.java "netMinutesByDate"
contains src/main/java/ru/daniil/shifts/service/ActualWorkService.java "reconcileRange"
contains src/main/java/ru/daniil/shifts/dto/Dtos.java "String endDate"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "Заканчивается в другой день"
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "Автоматически из фактической работы"
contains frontend/src/features/absence-time-bank/components/TimeBankPage.vue "Открыть день / изменить факт"
contains src/test/java/ru/daniil/shifts/service/NativeWorkdayClosedLoopServiceTest.java "crossMidnightActualSplitsNetMinutesAcrossCalendarDatesAndBankCredits"
contains src/test/java/ru/daniil/shifts/service/NativeWorkdayClosedLoopServiceTest.java "systemActualWorkCreditRowsAreReadOnlyAndExposeProvenance"
contains src/main/java/ru/daniil/shifts/service/ActualWorkService.java "resolveBreakMinutes"
contains src/main/java/ru/daniil/shifts/service/ActualWorkService.java "derivedCompensation.reconcileRange(user"
contains src/main/java/ru/daniil/shifts/service/WorkdayDerivedCompensationService.java "reconcileActualWorkCredit"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "SYSTEM_DERIVED_CREDIT_MANAGED_BY_ACTUAL_WORK"
contains src/main/java/ru/daniil/shifts/service/OvertimeService.java "DERIVED_OVERTIME_ALREADY_USED"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "Неоплачиваемый перерыв, мин"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "Удалить факт"
contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "Сбросить особый день"
not_contains frontend/src/features/calendar-timeline/components/NativeWorkdayCard.vue "проводка переработки пока не создана автоматически"
not_contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue "Текущее отображение"
not_contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue "Исходная смена"
contains frontend/src/features/calendar-timeline/components/SelectedDayPanel.vue "@open-section=\"openNativeWorkdaySection\""
contains frontend/src/features/payroll/components/PayrollWorkspace.vue 'planned:"Обязательная норма"'
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "data-production-calendar-day"
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "data-production-calendar-summary-only"
not_contains frontend/src/features/payroll/components/PayrollWorkspace.vue "productionCalendarForm"
contains src/test/java/ru/daniil/shifts/service/ProductionCalendarFoundationServiceTest.java "shortenedDayBecomesCanonicalRequiredMinutesForTimeCompensation"
contains src/test/java/ru/daniil/shifts/service/ProductionCalendarFoundationServiceTest.java "workdayTruthJoinsBaseNormRequiredNormAndExplicitReality"
contains src/test/java/ru/daniil/shifts/service/NativeWorkdayClosedLoopServiceTest.java "firstActualIntervalInheritsShiftBreakAndPostsOnlyNetOvertime"
contains src/test/java/ru/daniil/shifts/service/NativeWorkdayClosedLoopServiceTest.java "usedDerivedCreditBlocksFactDeletionInsteadOfCorruptingFifo"
contains frontend/browser-bundle-budget.json '"maxTotalGzipBytes": 250000'

# v27.45.0 Production Calendar Foundation
contains CHANGES.md "v27.45.0 — Production Calendar Foundation"
contains README.md "DutyLog v27.45.0 — Production Calendar Foundation"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "scheduleEffect"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "v27.45.0 does not change the existing money formula"
contains src/main/resources/db/migration/postgresql/V49__production_calendar_foundation.sql "CREATE TABLE production_calendar_days"
contains src/main/resources/db/migration/postgresql/V49__production_calendar_foundation.sql "'BASE', 'LOCAL_OVERRIDE'"
contains src/main/resources/db/migration/postgresql/V49__production_calendar_foundation.sql "schedule_effect"
contains src/main/resources/db/migration/postgresql/V49__production_calendar_foundation.sql "payroll_effect"
contains src/main/java/ru/daniil/shifts/model/ProductionCalendarDay.java "class ProductionCalendarDay"
contains src/main/java/ru/daniil/shifts/repo/ProductionCalendarDayRepository.java "findByOwnerAndDateAndLayer"
contains src/main/java/ru/daniil/shifts/service/WorkNormService.java "basePlannedMinutes"
contains src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java "LOCAL_OVERRIDE"
contains src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java '"NORM_OVERRIDE"'
contains src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java '"HOLIDAY"'
contains src/main/java/ru/daniil/shifts/service/ProductionCalendarService.java "periodLocks.assertOpen"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "base schedule norm"
not_contains src/main/java/ru/daniil/shifts/service/TimeCompensationService.java "private int plannedMinutes(DayEntry"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "productionCalendar.month"
contains src/main/java/ru/daniil/shifts/web/ProductionCalendarController.java '@RequestMapping({"/api/production-calendar", "/api/v1/production-calendar"})'
contains src/main/java/ru/daniil/shifts/web/ProductionCalendarController.java "CacheControl.noStore()"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: productionCalendarMonth"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: upsertProductionCalendarDay"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: deleteProductionCalendarDayOverride"
contains frontend/src/generated/dutylog-api.ts '"productionCalendarMonth": { method: "GET", path: "/api/v1/production-calendar/months/{month}" }'
contains frontend/src/generated/dutylog-api.ts '"upsertProductionCalendarDay": { method: "PUT", path: "/api/v1/production-calendar/days/{date}" }'
contains frontend/src/generated/dutylog-api.ts '"deleteProductionCalendarDayOverride": { method: "DELETE", path: "/api/v1/production-calendar/days/{date}" }'
contains frontend/src/features/payroll/components/PayrollWorkspace.vue "data-production-calendar-foundation"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "owner-edited rule that wins for the same date"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "v27.45.0 does not change the existing money formula"
contains src/test/java/ru/daniil/shifts/service/ProductionCalendarFoundationServiceTest.java "holidayNormAndHolidayPayrollClassificationAreIndependent"
contains src/test/java/ru/daniil/shifts/web/ProductionCalendarFoundationContractTest.java "class ProductionCalendarFoundationContractTest"
contains CHANGES.md "measured **869227 B raw**"
contains CHANGES.md "total raw ceiling to **875000 B**"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "Total gzip stays **250000 B**"
contains CHANGES.md "measured **869227 B raw**"
contains CHANGES.md "total raw ceiling to **875000 B**"
contains docs/PRODUCTION_CALENDAR_FOUNDATION_V27.45.0.md "Payroll Core will consume the production norm explicitly in the next phase"

# v27.44.4 Shared Full-Day Free Dates
contains CHANGES.md "v27.44.4 — Shared Full-Day Free Dates"
contains README.md "DutyLog v27.44.4 — Shared Full-Day Free Dates"
contains docs/SHARED_FULL_DAY_FREE_DATES_V27.44.4.md "zero effective work minutes"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "sharedAvailabilityByDate"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "hasSharedFreeAllDay"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "data-shared-free-month-summary"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "Общих выходных"
contains frontend/src/features/calendar-timeline/types/model.spec.ts "classifies shared full-day free dates from effective work rather than absence names"
contains frontend/src/features/calendar-timeline/calendar-timeline.css "v27.44.4 shared full-day free dates"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "Показать совместные смены"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "Скрыть совместные смены"
contains frontend/src/features/calendar-timeline/calendar-timeline.css "v27.44.4 shared-work calendar marker visibility hotfix"
contains frontend/src/features/calendar-timeline/calendar-timeline.css ".vue-calendar-view .cell.hasSharedWorkOverlap::after"
contains frontend/src/features/calendar-timeline/calendar-timeline.css ".calendarWeekDay.hasSharedWorkOverlap::after"
contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue "function inputText(raw: unknown): string"
contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue "String(raw ?? \"\").trim()"
not_contains frontend/src/features/settings-workspace/components/ShiftTypeManagerModal.vue "notificationMinutesBefore.trim()"
contains frontend/src/features/calendar-timeline/components/ManagedProfileDayCard.vue "data-profile-override-error"
contains frontend/src/features/calendar-timeline/components/ManagedProfileDayCard.vue "catch (error)"
contains frontend/src/features/calendar-timeline/components/ManagedProfileDayCard.vue "store.error ||"
contains CHANGES.md "Browser ceilings remain **855000 B raw / 250000 B gzip** pending exact Node 20 v27.44.4 measurement."

# v27.44.3 People Profile Coverage Semantics Hotfix
contains CHANGES.md "v27.44.3 — People Profile Coverage Semantics Hotfix"
contains README.md "DutyLog v27.44.3 — People Profile Coverage Semantics Hotfix"
contains docs/PEOPLE_PROFILE_COVERAGE_SEMANTICS_V27.44.3.md "PROFILE_OUTSIDE_COVERAGE"
contains frontend/src/features/calendar-timeline/types/domain.ts "startDate?: string | null"
contains frontend/src/features/calendar-timeline/types/domain.ts "PROFILE_OUTSIDE_COVERAGE"
contains frontend/src/features/calendar-timeline/types/model.ts "export function profileDateCovered"
contains frontend/src/features/calendar-timeline/types/model.ts 'unknownReason: "PROFILE_OUTSIDE_COVERAGE"'
contains frontend/src/features/calendar-timeline/types/model.spec.ts "does not turn dates outside a companion profile range into days off or shared free time"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "focusProfileCovered"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "Нет данных о графике"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "график на эту дату не задан"
contains frontend/src/features/calendar-timeline/calendar-timeline.css "v27.44.3 companion schedule coverage semantics"
contains CHANGES.md "Browser ceilings remain **855000 B raw / 250000 B gzip**"

# v27.44.2 Exact Availability Timeline & Shared Shift Summary
contains CHANGES.md "v27.44.2 — Exact Availability Timeline & Shared Shift Summary"
contains README.md "DutyLog v27.44.2 — Exact Availability Timeline & Shared Shift Summary"
contains docs/SHARED_AVAILABILITY_EXACT_TIMELINE_SHARED_SHIFT_SUMMARY_V27.44.2.md "Совпало смен: N"
contains docs/SHARED_AVAILABILITY_EXACT_TIMELINE_SHARED_SHIFT_SUMMARY_V27.44.2.md "06:00–15:00"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "timelineBoundaries"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "boundaryTimeLabel"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "sharedAvailabilityBoundaries"
not_contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue '<span>06</span><span>12</span><span>18</span>'
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "sharedWorkMonthSummary"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "data-shared-work-month-summary"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "Совпало смен"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "Вместе на работе"
contains frontend/src/features/calendar-timeline/calendar-timeline.css "v27.44.2 exact timeline and monthly shared-shift summary"
contains docs/SHARED_AVAILABILITY_EXACT_TIMELINE_SHARED_SHIFT_SUMMARY_V27.44.2.md "850000 B raw"
contains docs/SHARED_AVAILABILITY_EXACT_TIMELINE_SHARED_SHIFT_SUMMARY_V27.44.2.md "250000 B gzip"

# v27.44.1 Shared Availability UX & Shared Shifts
contains CHANGES.md "v27.44.1 — Shared Availability UX & Shared Shifts"
contains README.md "DutyLog v27.44.1 — Shared Availability UX & Shared Shifts"
contains docs/SHARED_AVAILABILITY_UX_SHARED_SHIFTS_V27.44.1.md "Shared Availability"
contains frontend/src/features/calendar-timeline/types/domain.ts "sharedBusyWindows"
contains frontend/src/features/calendar-timeline/types/model.ts "sharedBusyWindows"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "Совместные смены"
contains docs/SHARED_AVAILABILITY_UX_SHARED_SHIFTS_V27.44.1.md "847303 B raw"
contains docs/SHARED_AVAILABILITY_UX_SHARED_SHIFTS_V27.44.1.md "850000 B"
contains docs/SHARED_AVAILABILITY_UX_SHARED_SHIFTS_V27.44.1.md "250000 B gzip"

# v27.44.0 Shared Availability
contains CHANGES.md "v27.44.0 — Shared Availability"
contains README.md "DutyLog v27.44.0 — Shared Availability"
contains docs/SHARED_AVAILABILITY_V27.44.0.md "OpenAPI 126/132"
contains docs/SHARED_AVAILABILITY_V27.44.0.md "Flyway V48"
contains frontend/src/features/calendar-timeline/types/domain.ts "export interface SharedAvailabilityDay"
contains frontend/src/features/calendar-timeline/types/model.ts "export function sharedAvailabilityForDate"
contains frontend/src/features/calendar-timeline/types/model.ts "calendarFactualAbsence(facts)"
contains frontend/src/features/calendar-timeline/components/SharedAvailabilityCard.vue "data-shared-availability"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue 'import SharedAvailabilityCard from "./SharedAvailabilityCard.vue";'
contains CHANGES.md "Browser budgets stay 845000 B raw / 250000 B gzip total pending exact Node 20 CI measurement."
contains CHANGES.md "# v27.44.0 — Shared Availability"

# v27.43.0 People Profiles Managed Schedule Overrides
contains CHANGES.md "v27.43.0 — People Profiles: Managed Schedule Overrides"
contains README.md "DutyLog v27.43.0 — People Profiles: Managed Schedule Overrides"
contains docs/PEOPLE_PROFILES_MANAGED_SCHEDULE_OVERRIDES_V27.43.0.md "People Profiles: Managed Schedule Overrides"
contains docs/PEOPLE_PROFILES_MANAGED_SCHEDULE_OVERRIDES_V27.43.0.md "126 operations / 132 schemas"
contains docs/PEOPLE_PROFILES_MANAGED_SCHEDULE_OVERRIDES_V27.43.0.md "Flyway: **V48**"
contains src/main/resources/db/migration/postgresql/V48__calendar_layer_schedule_overrides.sql "CREATE TABLE calendar_layer_overrides"
contains src/main/resources/db/migration/postgresql/V48__calendar_layer_schedule_overrides.sql "UNIQUE (layer_id, source_date)"
contains src/main/resources/db/migration/postgresql/V48__calendar_layer_schedule_overrides.sql "ON DELETE CASCADE"
contains src/main/java/ru/daniil/shifts/model/CalendarLayerOverride.java "class CalendarLayerOverride"
contains src/main/java/ru/daniil/shifts/repo/CalendarLayerOverrideRepository.java "findByLayerAndSourceDate"
contains src/main/java/ru/daniil/shifts/service/CalendarLayerService.java "upsertOverride(AppUser user"
contains src/main/java/ru/daniil/shifts/service/CalendarLayerService.java "deleteOverride(AppUser user"
contains src/main/java/ru/daniil/shifts/service/CalendarLayerService.java "findByLayerAndSourceDateBetweenOrderBySourceDateAsc"
contains src/main/java/ru/daniil/shifts/web/CalendarLayerController.java '@PutMapping("/{id}/overrides/{date}")'
contains src/main/java/ru/daniil/shifts/web/CalendarLayerController.java '@DeleteMapping("/{id}/overrides/{date}")'
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: upsertCalendarLayerOverride"
contains src/main/resources/static/openapi/dutylog-v1.yaml "operationId: deleteCalendarLayerOverride"
contains frontend/src/generated/dutylog-api.ts '"upsertCalendarLayerOverride": { method: "PUT", path: "/api/v1/calendar-layers/{id}/overrides/{date}" }'
contains frontend/src/generated/dutylog-api.ts '"deleteCalendarLayerOverride": { method: "DELETE", path: "/api/v1/calendar-layers/{id}/overrides/{date}" }'
contains frontend/src/generated/dutylog-api.ts "scheduleEditable: true;"
contains frontend/src/features/calendar-timeline/components/ManagedProfileDayCard.vue "data-managed-profile-day"
contains frontend/src/features/calendar-timeline/components/ManagedProfileDayCard.vue "data-profile-override-save"
contains frontend/src/features/calendar-timeline/components/CalendarPage.vue "<ManagedProfileDayCard"
contains e2e/schedule-templates-calendar-layers.spec.js "data-profile-override-kind"
contains e2e/schedule-templates-calendar-layers.spec.js '/overrides/${date}'
contains src/test/java/ru/daniil/shifts/service/ScheduleTemplatesAndLayersServiceTest.java '"OFF", "TIME_OFF"'
contains src/test/java/ru/daniil/shifts/web/ScheduleTemplatesAndLayersControllerTest.java 'overrides/{date}'

echo

flush_release_matcher

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

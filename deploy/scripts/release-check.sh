#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog local release gate.
# Runs fast static checks that should pass before creating an archive/tag.
# It intentionally avoids printing secrets and does not require a running server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${DUTYLOG_RELEASE_VERSION:-27.32.0}"
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
  "js/45-payroll.js"
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

coproc RELEASE_CHECK_MATCHER {
  python3 -u -c '
import pathlib
import sys

cache = {}
for raw in sys.stdin:
    raw = raw.rstrip("\n")
    if not raw:
        continue
    kind, file_name, text = raw.split("\t", 2)
    path = pathlib.Path(file_name)
    if not path.is_file():
        print("MISSING", flush=True)
        continue
    content = cache.get(file_name)
    if content is None:
        content = path.read_text(encoding="utf-8", errors="replace")
        cache[file_name] = content
    found = text in content
    print("YES" if found else "NO", flush=True)
'
}
RELEASE_CHECK_MATCHER_OUT="${RELEASE_CHECK_MATCHER[0]}"
RELEASE_CHECK_MATCHER_IN="${RELEASE_CHECK_MATCHER[1]}"

match_file_text() {
  local kind="$1"
  local file="$2"
  local text="$3"
  local result
  printf '%s\t%s\t%s\n' "$kind" "$file" "$text" >&"$RELEASE_CHECK_MATCHER_IN"
  IFS= read -r result <&"$RELEASE_CHECK_MATCHER_OUT"
  printf -v RELEASE_CHECK_MATCH_RESULT '%s' "$result"
}

contains() {
  local file="$1"
  local text="$2"
  match_file_text contains "$file" "$text"
  case "$RELEASE_CHECK_MATCH_RESULT" in
    YES) ok "$file contains: $text" ;;
    MISSING) fail "$file is missing" ;;
    *) fail "$file does not contain expected text: $text" ;;
  esac
}

not_contains() {
  local file="$1"
  local text="$2"
  match_file_text not_contains "$file" "$text"
  case "$RELEASE_CHECK_MATCH_RESULT" in
    NO) ok "$file does not contain forbidden text: $text" ;;
    MISSING) fail "$file is missing" ;;
    *) fail "$file contains forbidden text: $text" ;;
  esac
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
contains docs/API.md "# DutyLog API v27.32.0"
contains docs/RELEASE_CHECKLIST.md "Status: v27.32.0."
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
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
contains e2e/overtime-next.spec.js 'state.overtimeAccount?.usages?.[0]?.hours || 0'
contains e2e/helpers.js "element.classList.contains('sel')"
contains e2e/helpers.js 'await expect(cell).toHaveClass(/sel/);'
contains e2e/important-timezone.spec.js "const shiftDate = await firstDay.getAttribute('data-date');"
contains e2e/important-timezone.spec.js 'await selectDate(page, shiftDate);'

  # v27.18.3 UI Settings & Button Variants Quality Hotfix
contains CHANGES.md "v27.18.3 — UI Settings & Button Variants Quality Hotfix"
contains README.md "v27.18.3 — UI Settings & Button Variants Quality Hotfix"
contains docs/UI_SETTINGS_BUTTON_VARIANTS_QUALITY_HOTFIX_V27.18.3.md "UI Settings & Button Variants Quality Hotfix"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"

  # v27.24.0 Calendar Comfort & Correctness
contains CHANGES.md "v27.24.0 — Calendar Comfort & Correctness"
contains README.md "v27.24.0 — Calendar Comfort & Correctness"
contains docs/CALENDAR_COMFORT_CORRECTNESS_V27.24.0.md "Contextual return to today"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"

  # v27.23.1 Calendar Sync JSON UTF-8 Contract Hotfix
contains CHANGES.md "v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix"
contains README.md "v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix"
contains docs/CALENDAR_SYNC_JSON_UTF8_CONTRACT_HOTFIX_V27.23.1.md "getContentAsString(StandardCharsets.UTF_8)"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "getContentAsString(StandardCharsets.UTF_8)"
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "contains(\"\\u2026\")"
not_contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java ".getContentAsString();"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"

  # v27.23.0 External Calendar Sync
contains CHANGES.md "v27.23.0 — External Calendar Sync"
contains README.md "v27.23.0 — External Calendar Sync"
contains docs/EXTERNAL_CALENDAR_SYNC_V27.23.0.md "SHA-256-only persistent storage"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
  contains e2e/task-modules.spec.js "page.locator('#view-tasks')"
  contains e2e/task-modules.spec.js "not.toHaveClass(/moduleHidden/)"
  contains e2e/task-modules.spec.js "toHaveClass(/moduleHidden/)"
  not_contains e2e/mobile-layout.spec.js "#tabbar a[data-view=\"tasks\"]').click"
  not_contains e2e/task-details.spec.js "#tabbar a[data-view=\"tasks\"]').click"
  not_contains e2e/task-modules.spec.js "#tabbar a[data-view=\"tasks\"]').click"
  not_contains e2e/task-modules.spec.js "page.locator('#tabbar a[data-view=\"tasks\"]')"

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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains src/test/java/ru/daniil/shifts/web/TodayDashboardFrontendContractTest.java 'js/35-today.js?v='
contains src/test/java/ru/daniil/shifts/web/UiCoreWorkspaceFrontendContractTest.java 'js/12-ui-platform.js?v='
contains src/test/java/ru/daniil/shifts/web/CalendarMobileExperienceFrontendContractTest.java 'js/37-calendar-experience.js?v='
contains src/test/java/ru/daniil/shifts/web/DesignSystemMobileShellFrontendContractTest.java 'design-system.css?v='

  # v27.19.3 Task Deadline Validation E2E Contract Hotfix
contains CHANGES.md "v27.19.3 — Task Deadline Validation E2E Contract Hotfix"
contains README.md "v27.19.3 — Task Deadline Validation E2E Contract Hotfix"
contains docs/TASK_DEADLINE_VALIDATION_E2E_CONTRACT_HOTFIX_V27.19.3.md "Task Deadline Validation E2E Contract Hotfix"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains e2e/task-modules.spec.js "Дедлайн не может быть раньше окончания запланированного интервала."

  # v27.19.4 Ghost Button Transition E2E Stabilization Hotfix
contains CHANGES.md "v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix"
contains README.md "v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix"
contains docs/GHOST_BUTTON_TRANSITION_E2E_STABILIZATION_HOTFIX_V27.19.4.md "Ghost Button Transition E2E Stabilization Hotfix"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains e2e/appearance-quality.spec.js "const borderAlpha = context.getImageData(0, 0, 1, 1).data[3];"
contains e2e/appearance-quality.spec.js "await expect.poll(async () => (await previewStyle(page)).borderAlpha).toBe(0);"
not_contains e2e/appearance-quality.spec.js "expect(ghost.borderColor).toBe('rgba(0, 0, 0, 0)');"

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
  find src/test/java/ru/daniil/shifts/web -maxdepth 1 -name '*FrontendContractTest.java' -print0 | sort -z
)
if (( ${#FRONTEND_CONTRACT_TESTS[@]} == 0 )); then
  fail "no static frontend contract Java sources found"
else
  javac -encoding UTF-8 -proc:none -cp "$JAVA_CONTRACT_TMP/classes" \
    -d "$JAVA_CONTRACT_TMP/classes" "${FRONTEND_CONTRACT_TESTS[@]}"
  ok "Static frontend contract Java sources compile"
fi
cleanup_java_contract_tmp
trap - EXIT

if python3 - <<'PY_FRONTEND_ASSET_CONTRACTS'
from pathlib import Path
import re

pattern = re.compile(r'\?v=\d+\.\d+\.\d+')
violations = []
for path in sorted(Path('src/test/java/ru/daniil/shifts/web').glob('*FrontendContractTest.java')):
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
    'js/45-payroll.js',
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
contains docs/SECURITY_REVIEW.md "Status: v27.32.0."
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
contains docs/RELEASE_CHECKLIST.md "git tag -a v27.32.0"

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
contains docs/SECURITY_REVIEW.md "Status: v27.32.0."
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
if python3 deploy/scripts/smoke-test-regression.py >/dev/null; then
  ok "authenticated deployment smoke-test regression"
else
  fail "authenticated deployment smoke-test regression failed"
fi

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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"

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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.absences.slice(0, 3))'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.partialAbsences)'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'for (const absence of facts.absences.filter(item => item.coverage !== \"PARTIAL\"))'
contains src/test/java/ru/daniil/shifts/web/VacationPlannerFrontendContractTest.java 'absenceExperienceKeepsWeekAgendaBoundedAndDayModesSeparated'
not_contains src/main/resources/static/js/37-calendar-experience.js 'for (const absence of facts.absences)'

# v27.25.1 Absence Preview Lambda Compile Hotfix
contains CHANGES.md "v27.25.1 — Absence Preview Lambda Compile Hotfix"
contains README.md "v27.25.1 — Absence Preview Lambda Compile Hotfix"
contains docs/ABSENCE_PREVIEW_LAMBDA_COMPILE_HOTFIX_V27.25.1.md "effectively-final"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "LocalDate previewDate = date;"
contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "filter(period -> covers(period, previewDate))"
not_contains src/main/java/ru/daniil/shifts/service/VacationPlannerService.java "filter(period -> covers(period, date))"
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java "previewLoopSnapshotsItsMutableDateBeforeTheOverlapLambda"
contains src/test/java/ru/daniil/shifts/web/AbsenceTimeOffOverhaulContractTest.java "assertFalse(service.contains(\"filter(period -> covers(period, date))\"))"

# v27.25.0 Absence & Time-Off Overhaul
contains CHANGES.md "v27.25.0 — Absence & Time-Off Overhaul"
contains README.md "v27.25.0 — Absence & Time-Off Overhaul"
contains docs/ABSENCE_TIME_OFF_OVERHAUL_V27.25.0.md "planned shift from the work schedule"
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
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
contains docs/ROADMAP.md "Current release: **v27.32.0 — Absence & Time Bank Experience**"
contains docs/REGRESSION_TEST_BASELINE.md "Current extension: v27.32.0"
contains docs/API.md "# DutyLog API v27.32.0"
contains docs/RELEASE_CHECKLIST.md "Status: v27.32.0."
contains docs/SECURITY_REVIEW.md "Status: v27.32.0."
contains docs/MODULE_CONTRACTS.md "Status: v27.32.0."
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
contains e2e/task-modules.spec.js "page.locator('#view-tasks')"
not_contains e2e/task-modules.spec.js "page.locator('#tabbar a[data-view=\"tasks\"]')"
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
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "RoundingMode.HALF_UP"
contains src/main/java/ru/daniil/shifts/service/PayrollService.java "previous.supersedeWith(created)"
contains src/main/java/ru/daniil/shifts/web/PayrollController.java '"/api/v1/payroll"'
contains src/main/java/ru/daniil/shifts/web/PayrollController.java "CacheControl.noStore()"
contains src/main/resources/static/index.html 'id="view-payroll"'
contains src/main/resources/static/index.html "js/45-payroll.js?v=$VERSION"
contains src/main/resources/static/js/45-payroll.js "window.__dutylogPayrollReady"
contains src/main/resources/static/js/70-user-boot.js "openPayrollView(true)"
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

E2E_TESTS=$(grep -R --include='*.spec.js' -h -E '^[[:space:]]*test\(' e2e | wc -l | tr -d ' ')
if [[ "$E2E_TESTS" == "42" ]]; then
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
contains e2e/editor-modals.spec.js "const calendarReloaded = waitForApi(page, 'GET', '/api/calendar')"
contains e2e/important-timezone.spec.js 'sourceDisplay:`03.${month}`'
contains e2e/important-timezone.spec.js "dates.source"

  # v27.11.3 Shift Template & Reminder Timezone Hotfix
contains CHANGES.md "v27.11.3 — Shift Template & Reminder Timezone Hotfix"
contains README.md "v27.11.3 — Shift Template & Reminder Timezone Hotfix"
contains docs/SHIFT_TEMPLATE_REMINDER_TIMEZONE_HOTFIX_V27.11.3.md "Shift Template & Reminder Timezone Hotfix"
contains src/main/java/ru/daniil/shifts/web/ProfileController.java "shiftTypeService.rebaseForTimezoneChange"
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
contains e2e/calendar-persistence.spec.js "const noteCreated = waitForApi(page, 'POST', '/api/notes', 201)"
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
contains docs/API.md "# DutyLog API v27.32.0"
contains docs/RELEASE_CHECKLIST.md "Status: v27.32.0."
contains src/main/resources/static/js/60-settings.js "let timeSettingsApplyQueue = Promise.resolve();"
contains src/main/resources/static/js/60-settings.js "const pending = timeSettingsApplyQueue.then(operation, operation);"
contains src/main/resources/static/js/60-settings.js "function readShiftDefaultsDraft()"
contains src/main/resources/static/js/60-settings.js "preserveShiftDefaults = timeSettingsDefaultsDirty()"
contains src/test/java/ru/daniil/shifts/web/ImportantDatesTimezoneOvertimeFrontendContractTest.java "let timeSettingsApplyQueue = Promise.resolve();"



  # v27.32.0 Absence & Time Bank Experience
contains CHANGES.md "v27.32.0 — Absence & Time Bank Experience"
contains README.md "v27.32.0 — Absence & Time Bank Experience"
contains docs/ABSENCE_TIME_BANK_EXPERIENCE_V27.32.0.md "one canonical absence event"
contains docs/ROADMAP.md "v27.32.0 — Absence & Time Bank Experience — current"
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
contains src/test/java/ru/daniil/shifts/web/CalendarSyncControllerTest.java "PRODID:-//DutyLog//Time and Overtime 27.32.0//RU"
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

  ok "Playwright test baseline: 42"
else
  fail "expected 42 Playwright tests, found $E2E_TESTS"
fi

TEST_METHODS=$(grep -R --include='*.java' -h -E '^[[:space:]]*@Test([[:space:]]|$)' src/test/java | wc -l | tr -d ' ')
TEST_CLASSES=$(find src/test/java -name '*Test.java' -type f | wc -l | tr -d ' ')
if [[ "$TEST_METHODS" == "633" ]]; then
  ok "test method baseline: 633"
else
  fail "expected 633 @Test methods, found $TEST_METHODS"
fi
if [[ "$TEST_CLASSES" == "129" ]]; then
  ok "test class baseline: 129"
else
  fail "expected 129 test classes, found $TEST_CLASSES"
fi

# Close the matcher input so the coprocess sees EOF and can terminate.
# Without this explicit lifecycle boundary bash waits forever for the helper at script exit.
exec {RELEASE_CHECK_MATCHER_IN}>&-
wait "$RELEASE_CHECK_MATCHER_PID"
exec {RELEASE_CHECK_MATCHER_OUT}<&-

echo

if (( ERRORS > 0 )); then
  echo "Release check failed: $ERRORS error(s)." >&2
  exit 1
fi

echo "Release check passed."

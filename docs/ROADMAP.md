# Roadmap до полноценного продукта

Current release: **v27.30.0 — Unified Absence Composer & Calendar Projection**.



## v27.30.0 — Unified Absence Composer & Calendar Projection — current

- [x] Reuse one absence form across Vacation, Quick Add, Calendar and Overtime entry points.
- [x] Route new overtime-backed time off through linked absence creation instead of detached raw usage.
- [x] Show the selected absence type, compensation source, available balance and projected remainder.
- [x] Require a user-facing reason while retaining API compatibility for historical records.
- [x] Preserve the planned shift under a full-day factual absence.
- [x] Render partial absence as a typed interval over the planned shift.
- [x] Add type glyphs and status-aware calendar treatment without relying on color alone.
- [x] Keep Payroll, Unified Ledger, approval workflow, PostgreSQL and Flyway V46 unchanged.
- [x] Isolate the dirtied Telegram detached-owner integration context in its own H2 database so test order cannot drop the shared schema.
- [x] Advance baseline to 123 Java classes / 616 tests / 39 Playwright scenarios.

Next product stage after green CI and staging acceptance: **v27.31.0 — One-Tap Calendar Connect**.

## v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix — completed

- [x] Reproduce the remaining Workspace Studio failure after profile autosave.
- [x] Preserve an explicit allowed Today-card order in `safeTodayWidgets(...)`.
- [x] Insert mandatory Shift only when the incoming selection omits it.
- [x] Add HTTP persistence and focused source regression coverage.
- [x] Keep the strict 38-scenario Playwright fixture unchanged.
- [x] Advance baseline to 122 Java classes / 614 tests / 38 Playwright scenarios.
- [x] Keep PostgreSQL and Flyway V46 unchanged.

## v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix — completed

- [x] Preserve the active preset's Today card selection when creating Custom Workspace.
- [x] Keep Shift mandatory and preserve the existing widget allowlist.
- [x] Verify Overtime and Tasks inheritance before later Studio edits.
- [x] Keep the strict Playwright fixture unchanged.
- [x] Advance baseline to 121 Java classes / 612 tests / 38 Playwright scenarios.
- [x] Keep PostgreSQL and Flyway V46 unchanged.

## v27.29.1 — Theme Package Token Scope Contract Hotfix — completed

- [x] Reproduce the single Maven failure at `WorkspaceLayoutThemeStudioFrontendContractTest:74`.
- [x] Confirm the runtime theme package metadata already contains the correct unescaped JavaScript template literal.
- [x] Remove the phantom-backslash expectation from the Java source contract.
- [x] Add a focused regression contract for Java-source escaping versus runtime JavaScript content.
- [x] Keep Workspace Studio runtime, themes, layouts, profile persistence, APIs, PostgreSQL and Flyway V46 unchanged.
- [x] Advance baseline to 120 Java classes / 611 tests / 38 Playwright scenarios.


## v27.29.0 — Workspace, Layout & Theme Studio — completed

- [x] Advance the single DutyLog UI Core to contract v2.
- [x] Add a safe custom workspace with ordered/visible primary navigation.
- [x] Keep Today and Settings mandatory and cap primary navigation at five items.
- [x] Add independent Today-card order/visibility while keeping Shift mandatory.
- [x] Add Sidebar and Mobile Flow layout packages without copied screens.
- [x] Add compact calendar density and pill/dot schedule-layer presentation.
- [x] Add a pointer-free calm-grid decoration package.
- [x] Publish theme package metadata and preserve semantic-token isolation.
- [x] Extend server-side profile whitelisting and synchronous bootstrap to UI contract v2.
- [x] Keep Payroll, Unified Ledger, Vacation, Calendar APIs, PostgreSQL and Flyway V46 unchanged.
- [x] Advance baseline to 119 Java classes / 610 tests / 38 Playwright scenarios.

Stabilized by: **v27.29.1 — Theme Package Token Scope Contract Hotfix**, **v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix** and **v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix**.


## v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix — completed

- [x] Keep released V45 immutable and preserve its Flyway checksum.
- [x] Add forward-only V46 to convert `payroll_snapshots.calculation_hash` from `CHAR(64)` to `VARCHAR(64)` using `BTRIM(...)`.
- [x] Preserve `NOT NULL` and the existing lowercase 64-character hexadecimal check constraint.
- [x] Add a regression contract for the V45 checksum, V46 type alignment and the unchanged JPA `length = 64` mapping.
- [x] Keep Payroll calculations, API, OpenAPI, UI and revision semantics unchanged.
- [x] Advance Flyway to V46 and the baseline to 118 Java classes / 605 tests / 37 Playwright scenarios.

Next product stage after a fully green clean-migration CI: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.2 — Calendar Persistence Reload Readiness Hotfix — completed

- [x] Publish one calendar-navigation readiness promise for Month, Week and Day header routes.
- [x] Wait for completed calendar and ledger projections before persistence tests perform a full reload.
- [x] Guard both calendar-persistence reload paths with the existing application-idle contract.
- [x] Keep the strict runtime fixture unchanged; do not suppress aborted reads globally.
- [x] Keep production Payroll, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- [x] Baseline advances to 117 Java classes / 604 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.1 — Payroll Module Registry Contract Hotfix — completed

- [x] Reproduce the Maven failure in `PayrollFoundationContractTest`.
- [x] Replace the brittle `ModuleService.PAYROLL` source-string expectation with canonical `ModuleKeys.PAYROLL` and real registry-shape assertions.
- [x] Keep production Payroll runtime, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- [x] Keep the baseline at 116 Java classes / 603 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.0 — Payroll Foundation — completed

- [x] Add V45 payroll settings, append-only monetary adjustments and immutable versioned snapshots.
- [x] Read canonical posted-only time from `TimeCompensationService` instead of reinterpreting calendar tables.
- [x] Require a closed month, healthy ledger and positive hourly rate for final calculation.
- [x] Store money in minor units and apply one `HALF_UP` rounding step.
- [x] Expose transparent preview, additions, deductions, total and calculation hash.
- [x] Add `/api/v1/payroll` and a responsive Payroll workspace.
- [x] Advance Flyway to V45 and baseline to 116 Java classes / 603 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.27.2 — Ledger Browser State & Visibility Hotfix — completed

- [x] Refresh Vacation Planner on every route entry so shared overtime-bank totals cannot stay stale.
- [x] Expose Vacation and timezone-save readiness without bypassing real application routes.
- [x] Keep strict browser error detection while consuming only a marked expected-status resource console message.
- [x] Make Overtime Next current-month data deterministic on the first day of a month.
- [x] Scope responsive Unified Ledger assertions to the visible desktop table.
- [x] Keep API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- [x] Baseline advances to 114 Java test classes / 600 tests / 36 Playwright scenarios.

## v27.27.1 — Ledger Workflow Browser Contract Hotfix — completed

- [x] Refresh Overtime account and projections whenever the route opens after hidden Vacation Planner mutations.
- [x] Serialize integrity reconciliation before time-compensation/actual-work reads.
- [x] Keep strict browser failure detection while marking only the intentional closed-period `409`.
- [x] Replace the July-fixed timezone E2E data with the current browser month.
- [x] Make posted-compensation scenarios explicitly `APPROVED`.
- [x] Wait for application and ledger readiness around reload and route transitions.
- [x] Keep API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- [x] Baseline advances to 113 Java test classes / 599 tests / 36 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.


## v27.27.0 — Ledger Integrity & Approval Workflow — completed

- [x] Add draft, planned, submitted, approved, rejected, cancelled and completed absence states.
- [x] Reserve overtime-bank minutes for planned/submitted requests and post them for approved/completed absences.
- [x] Keep an append-only audit with explicit reversal entries and closed-period corrections.
- [x] Add owner-scoped integrity reconciliation for linked usages, FIFO allocations and V43 opening credits.
- [x] Add close/reopen accounting periods and reject silent mutations in closed months.
- [x] Freeze payroll-affecting planned shifts in closed months across day edits, mobile sync, bulk fill, schedule-template apply and shift-type deletion, while notes and markers remain editable.
- [x] Add explicit factual work intervals while preserving plan-as-fact as the default.
- [x] Extend the no-store time-compensation snapshot for Payroll Foundation.
- [x] Flyway V44 remains additive and does not alter `day_entries`.
- [x] Baseline advances to 112 Java test classes / 598 tests / 36 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.


## v27.26.2 — Canonical Lineage Recovery — completed

- [x] Restore the accepted v27.26.x product stack on top of the actually deployed canonical v27.23.0 branch.
- [x] Preserve the current Workspace Route E2E navigation contract instead of reintroducing the older workspace-aware tab assertions.
- [x] Keep V41 External Calendar Sync, V42 Absence & Time-Off and V43 Unified Time & Compensation Ledger exactly once.
- [x] Preserve consolidated UTF-8, browser-boot, modal-panel, lambda, frontend-contract and constructor compile fixes.
- [x] Add lineage integrity contracts that fail if V41–V43, plan/fact absences or source-linked overtime usages disappear.
- [x] Baseline advances to 110 Java test classes / 592 tests / 35 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.26.1 — Absence Request Constructor Compile Hotfix — completed

- [x] Repair four stale nine-argument `AbsencePeriodCreateRequest` fixtures after the compensation-source field became explicit.
- [x] Preserve `OVERTIME_BANK` semantics for partial, full-day and insufficient-balance service tests.
- [x] Add Java/static and release-gate protection against returning to the removed constructor shape.
- [x] Keep production runtime, API, OpenAPI, PostgreSQL and Flyway V43 unchanged.
- [x] Baseline advances to 110 Java test classes / 591 tests / 35 Playwright scenarios.

## v27.26.0 — Unified Time & Compensation Ledger — completed

- [x] Existing overtime credits/usages/allocations become the canonical compensatory-time bank.
- [x] `OVERTIME_BANK` absences own one FIFO usage; edits reallocate it and deletion restores minutes.
- [x] Manual ledger mutation is blocked for absence-linked usages, preventing double compensation.
- [x] Explicit vacation, overtime, sick, unpaid and no-coverage policies join Plan → Fact → Compensation.
- [x] `/api/time-compensation` and the monthly unified UI expose planned, worked, earned, used, covered and unpaid time.
- [x] Flyway V43 migrates the standalone V42 balance into an opening credit and preserves `day_entries`.
- [x] Baseline advances to 110 Java test classes / 590 tests / 35 Playwright scenarios.

Next product stage: **v27.27.0 — Ledger Integrity & Approval Workflow**.

## v27.25.2 — Absence Experience Frontend Contract Hotfix — completed

- [x] Confirmed Maven compilation and 579 passing tests before the one failing static frontend contract.
- [x] Replaced the stale unbounded absence-loop expectation with the accepted bounded Week agenda contract.
- [x] Protected timed partial absences and full-day all-day composition as separate paths.
- [x] Production runtime, API, OpenAPI, database and Flyway V42 remain unchanged.
- [x] Baseline advances to 109 Java test classes / 581 tests / 34 Playwright scenarios.

## v27.25.1 — Absence Preview Lambda Compile Hotfix — completed

- [x] Fixed the Java compiler blocker in the absence preview overlap lookup.
- [x] Snapshot the mutable loop date before lambda capture; preview semantics remain unchanged.
- [x] Added Java/static and release-gate protection against direct capture of the incremented loop variable.
- [x] API, OpenAPI, database and Flyway V42 remain unchanged.
- [x] Baseline advances to 109 Java test classes / 580 tests / 34 Playwright scenarios.

## v27.25.0 — Absence & Time-Off Overhaul — completed

- [x] Separate planned shifts from factual day status without deleting schedule data.
- [x] Full-day absences visually replace the shift while retaining plan context.
- [x] Partial time off stores exact hours and preserves the shift surface.
- [x] Independent `VACATION_DAYS`, `TIME_OFF_HOURS` and `NONE` balance policies.
- [x] Built-in Time Off type, configurable hour bank and full-day charge duration.
- [x] Month / Week / Day / selected-day plan-fact composition and monthly summaries.
- [x] Timed `.ics` projection for partial absence and Flyway V42.
- [x] Baseline 109 Java test classes / 579 tests / 34 Playwright scenarios.

## v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix — completed

- GitHub Actions confirmed Maven and reached the new Calendar Comfort browser scenario.
- Month-mode `↺ Сегодня` correctly selects today and opens the mobile modal day panel.
- The failed test attempted to click `#next` through the blocking backdrop instead of closing the panel.
- The scenario now follows the user route `Сегодня → #pClose → next month`; production UI and Flyway V41 are unchanged.
- Baseline remains 108 Java classes / 569 tests / 33 Playwright scenarios.

## v27.24.0 — Calendar Comfort & Correctness — completed

- [x] Contextual cozy «Сегодня» control across Month / Week / Day.
- [x] Selected calendar date owns contextual important-day creation.
- [x] Important-event checkboxes follow the 18px design-system control size.
- [x] Overnight Today shift separates compact time and the two-date range.
- [x] Calendar refresh preserves the existing grid and exposes a calm live status.
- [x] Bounded in-memory load metrics and `dutylog:calendar-load` diagnostics start before the final optimization cycle.
- [x] Companion schedule layers use compact accessible visibility pills.
- [x] Flyway stays V41; baseline advances to 108 / 569 / 33.

## v27.23.2 — Calendar Sync Runtime Boot Hotfix — completed

- [x] Removed the uncaught `ReferenceError: localDateKey is not defined` from `.ics` range initialization.
- [x] Default range boundaries now use the canonical local `keyOf(...)` helper.
- [x] Added Java/static and release-gate protection against the undefined helper.
- [x] API, token lifecycle, nginx protection and Flyway V41 remain unchanged.
- [x] Baseline advances to 107 / 564 / 32.

Next product stage: **v27.25.0 — Absence & Time-Off Overhaul**.


## v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix — completed

- [x] MockMvc subscription JSON is decoded explicitly with `StandardCharsets.UTF_8`.
- [x] Token hint stays `prefix…suffix`; the test protects U+2026 without mojibake.
- [x] Runtime, HTTP API, nginx protection and Flyway V41 remain unchanged.
- [x] Baseline stays 107 / 563 / 32.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.


## v27.23.0 — External Calendar Sync — completed

- [x] RFC 5545 export for a selected range and one important event.
- [x] Shifts, tasks, important events and absences compose into read-only `.ics`.
- [x] Private rolling subscription with SHA-256-only token storage.
- [x] Issue, rotate, revoke and immediate old-token invalidation.
- [x] Responsive Settings UI, Web/v1 API, OpenAPI and browser lifecycle coverage.
- [x] Flyway V41; baseline 107 / 563 / 32.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.

## v27.22.2 — Workspace Route E2E Navigation Hotfix — completed

- [x] Stale browser flows use the shared workspace-route `openView()` helper instead of a hidden Tasks tab.
- [x] Tasks module enablement is asserted on `#view-tasks`, independently from workspace placement.
- [x] Shift Worker navigation remains intentionally task-tab-free; runtime, API and Flyway remain unchanged.
- [x] Baseline stays 103 / 544 / 31.


## v27.22.1 — Vacation Planner Frontend Contract Hotfix — completed

- [x] Shift Worker static contract includes `vacation` and the accepted Today widget order.
- [x] Vacation Month/Week/Day static contract follows the real all-day absence composition path.
- [x] Persisted switchable-module count is derived from `DutyLogModules.ALL` instead of a hardcoded value.
- [x] Runtime, API and Flyway remain unchanged; baseline stays 103 / 544 / 31.


## v27.22.0 — Vacation Planner — completed

- [x] Separate absence model; vacation never becomes a shift row.
- [x] Annual allowance and carryover.
- [x] Configurable work-year boundary.
- [x] Calendar-day or Monday-Friday counting.
- [x] 14 / 28 / 35-day presets and custom periods.
- [x] Preview with shift and absence conflicts.
- [x] Overlap and allowance protection.
- [x] Built-in and custom absence types.
- [x] Month / Week / Day / selected-day composition.
- [x] Flyway V40, Web/v1 API, Java/static/Playwright contracts.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.

## Текущая продуктовая точка — Unified Time & Compensation Ledger

Статус: **v27.26.2** восстанавливает единую каноническую линию; **v27.26.0** объединяет плановые смены, фактические отсутствия и компенсационные движения. Отгул за ранее отработанное время больше не списывает отдельное число: он владеет FIFO usage в каноническом overtime ledger. Flyway V43 переносит старый баланс в opening credit, сохраняет `day_entries`, а salary-правила остаются задачей Payroll Foundation.

Закрыто:

- независимые политики `VACATION_DAYS`, `TIME_OFF_HOURS` и `NONE`;
- встроенный тип «Отгул» и отдельный банк часов;
- полный и частичный охват с проверкой пересечений;
- списание полной смены по её net duration либо по настраиваемой длительности дня;
- план-факт детали и полноценная визуализация в Month / Week / Day;
- сводка по видам отсутствий и частичным часам;
- timed `.ics` для частичного отгула;
- Flyway V42, OpenAPI, Java/static/Playwright contracts;
- baseline 109 Java test classes / 581 tests / 34 Playwright scenarios.

Следующий этап: **v27.27.0 — Ledger Integrity & Approval Workflow**.

## Ближайшая продуктовая очередь после v27.26.2

### v27.26.0 — Unified Time & Compensation Ledger — completed

См. завершённый релиз выше и `docs/UNIFIED_TIME_COMPENSATION_LEDGER_V27.26.0.md`.

### v27.27.0 — Ledger Integrity & Approval Workflow

- статусы отсутствий: черновик, запланировано, подано, утверждено, отклонено, отменено и завершено;
- резервирование и окончательное проведение отпускных/компенсационных операций;
- неизменяемые reversal-записи вместо скрытого переписывания истории;
- сверка целостности ledger и безопасное восстановление осиротевших связей;
- закрытие расчётных периодов и корректировки задним числом;
- явная фиксация фактически отработанных интервалов поверх плановой смены.

### v27.28.0 — Payroll Foundation

- простой и расширенный расчёт оплачиваемого времени;
- влияние отпусков, больничных, отсутствий без содержания и отгулов из банка переработок;
- базовая ставка, ночные, сверхурочные, премии, удержания и налоги;
- объяснимый расчёт, читающий единый ledger вместо повторного угадывания календаря.

### Следующие продуктовые циклы

- `v27.29.0` — Workspace, Layout & Theme Studio;
- `v27.30.0` — Unified Absence Composer & Calendar Projection;
- `v27.31.0` — One-Tap Calendar Connect для Google / Apple / Outlook;
- `v27.32.0` — архив «Все заметки» и визуальные коллизии задач;
- `v27.33.0` — Telegram inline-действия и понятные пошаговые команды;
- затем — контекстные обучалки и отдельный финальный performance / production-readiness цикл.

## Этап 2 — нормальная API-архитектура

Статус: основа сделана в v10.

Сделано:

- бизнес-логика вынесена из контроллеров в сервисы;
- добавлен единый формат ошибок;
- добавлен endpoint диапазона дат: `GET /api/calendar?from=...&to=...`;
- добавлен endpoint баланса переработки: `GET /api/overtime/balance?from=...&to=...`;
- добавлен endpoint журнала переработок.

Осталось:

- разнести DTO из одного `Dtos.java` по отдельным файлам.

OpenAPI v1 уже поддерживается и расширяется вместе с доменными релизами.

## Этап 3 — важные дни и задачи

Статус: сделано в v11.

Сделано:

- задачи дня с чекбоксами;
- индикатор невыполненных задач на календаре;
- важные дни;
- повторения важных дней: один раз, каждый месяц, каждый год;
- отдельные endpoint'ы под задачи и важные дни;
- `/api/calendar?from=&to=` отдаёт задачи и важные дни вместе с календарём.

## Этап 4 — авторизация для Android

Статус: сделано в v12.

Сделано:

- `POST /api/mobile/auth/login`;
- `POST /api/mobile/auth/refresh`;
- `POST /api/mobile/auth/logout`;
- `GET /api/mobile/auth/me`;
- `GET /api/mobile/auth/sessions`;
- `DELETE /api/mobile/auth/sessions/{id}`;
- access token;
- refresh token с ротацией;
- таблица `mobile_auth_tokens`;
- выход с конкретного устройства;
- хранение хэшей токенов вместо сырых токенов.

Веб оставлен на сессиях, Android/API ходят через Bearer token.

## Этап 5 — бухгалтерия переработок

Статус: сделано в v13.

Сделано:

- начисления переработки вынесены в отдельную сущность;
- списания отгулов вынесены в отдельную сущность;
- списания автоматически распределяются по старым начислениям по FIFO;
- переработка не сгорает при переходе между месяцами;
- добавлена таблица: день, время, часы, причина, использовано, куда списано, остаток;
- добавлены endpoint’ы `/api/overtime/account`, `/api/overtime/credits`, `/api/overtime/usages`.

## Этап 6 — план/факт

Текущая модель уже умеет смену дня + переработку + отгул. Для взрослого табеля лучше разделить:

- плановая смена;
- фактическая работа;
- события/ППР;
- списание отгула;
- комментарии;
- подтверждение месяца.

Пример будущей структуры:

```text
DayEntry
├─ plannedShiftType
├─ actualShiftType
├─ overtimeEvents[]
├─ timeOffEvents[]
└─ note
```

## Этап 7 — быстрые действия

Очень полезные кнопки для реальной жизни:

- «Остался в ночь» → автоматом ставит `+7` сегодня и `+8` завтра;
- «Не вышел после ППР» → списывает часы плановой смены;
- «Списать весь день»;
- «ППР до 00:00»;
- «ППР после 00:00».

## Этап 8 — уведомления

Для PWA/web:

- напоминание перед сменой;
- напоминание вечером заполнить день;
- Telegram-уведомления.

Для Android:

- локальные notifications;
- WorkManager;
- AlarmManager для настоящего будильника.

## Этап 9 — Android

API уже достаточно стабилен для первого Android-клиента. Дальше:

- Kotlin;
- Jetpack Compose;
- Retrofit/Ktor Client;
- Room для offline-кэша;
- sync queue для изменений без интернета;
- локальные уведомления;
- экран календаря;
- экран дня;
- экран баланса переработки.

## Этап 10 — отчёты

- экспорт CSV;
- экспорт Excel;
- отчёт по месяцу;
- отчёт по переработке;
- журнал списаний;
- статистика за год.

## Completed in v27.11.3

- Canonical timezone rebases future shift templates.
- Existing dated shifts remain immutable.
- Shift reminders and Telegram delivery use occurrence instants, including next-day projection.

## Completed in v27.11.4

- Timed task deadlines preserve one absolute instant across canonical timezone changes.
- Task overdue/upcoming classification is instant-based for absolute deadlines.
- Deadline projections may cross calendar dates without moving the task's organisational day.
- Date-only deadlines stay floating.
- Legacy timed task deadlines can be explicitly linked to their real source IANA timezone.
- Browser, mobile and Telegram task reminders consume the same authoritative instant.


## Completed in v27.12.0

- Exact overtime credits are split at midnight in the current canonical timezone.
- Exact FIFO allocation minutes follow the same daily projection without being rebuilt.
- Daily earned, used and remaining totals are additive projections, not persisted copies.
- Ledger filters and exports operate on projected local dates.
- Source-credit edit/delete integrity is preserved across multi-day projections.


## DutyLog Next UI platform sequence after v27.17.3

- `v27.17.3` — Java Contract Build Gate Hotfix.
- `v27.17.4` — UI Core & Workspace Foundation.
- `v27.17.5` — UI Core E2E Accordion Hotfix.
- `v27.17.6` — Classic Sunset.
- `v27.18.0` — Overtime Next.
- `v27.18.1` — Overtime Next E2E Contract Hotfix.
- `v27.18.2` — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix.
- `v27.18.3` — UI Settings & Button Variants Quality Hotfix.
- `v27.19.0` — Tasks & Inbox Next.
- `v27.19.1` — Task Board Date Range Compatibility Hotfix.
- `v27.19.2` — Frontend Asset Contract Stability Hotfix.
- `v27.19.3` — Task Deadline Validation E2E Contract Hotfix.
- `v27.19.4` — Ghost Button Transition E2E Stabilization Hotfix.
- `v27.19.0` — Tasks & Inbox Next, including independent planned task intervals (`start → end`), duration, deadlines and timeline cards.
- `v27.20.0` — Notes & Important Events Next, including all-day/timed/multi-day events, place, description, reminders and read-first event cards.

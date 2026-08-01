package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeCreditDto;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationRequest;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationResultDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountPageDto;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAllocationDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditPreviewDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditRowDto;
import ru.daniil.shifts.dto.Dtos.OvertimeDailyProjectionDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditUpdateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeLedgerItemDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageDto;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageUpdateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageRefDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeUsage;
import ru.daniil.shifts.model.TimeAccountingPeriod;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.repo.TimeAccountingPeriodRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OvertimeService {
    private static final DateTimeFormatter ISO_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DayEntryRepository days;
    private final DayEntryService dayEntryService;
    private final OvertimeCreditRepository credits;
    private final OvertimeUsageRepository usages;
    private final OvertimeAllocationRepository allocations;
    private final TimeAccountingPeriodRepository accountingPeriods;
    private final SecurityEventLogger securityEvents;
    private final UserTimeService userTimeService;

    public OvertimeService(DayEntryRepository days,
                           DayEntryService dayEntryService,
                           OvertimeCreditRepository credits,
                           OvertimeUsageRepository usages,
                           OvertimeAllocationRepository allocations,
                           TimeAccountingPeriodRepository accountingPeriods,
                           SecurityEventLogger securityEvents,
                           UserTimeService userTimeService) {
        this.days = days;
        this.dayEntryService = dayEntryService;
        this.credits = credits;
        this.usages = usages;
        this.allocations = allocations;
        this.accountingPeriods = accountingPeriods;
        this.securityEvents = securityEvents;
        this.userTimeService = userTimeService;
    }

    /**
     * Compatibility summary backed by the same timezone-aware projection as
     * the main overtime account. Legacy day_entries values are deliberately
     * ignored so a zero projected balance can never resurrect stale hours.
     */
    @Transactional
    public OvertimeSummaryDto summary(AppUser user, LocalDate from, LocalDate to) {
        List<OvertimeCreditRowDto> rows = projectedRowsInRange(user, from, to);
        double overtime = rows.stream().mapToDouble(OvertimeCreditRowDto::hours).sum();
        double timeOff = rows.stream().mapToDouble(OvertimeCreditRowDto::usedHours).sum();
        return new OvertimeSummaryDto(
                from.toString(),
                to.toString(),
                round2(overtime),
                round2(timeOff),
                round2(overtime - timeOff)
        );
    }

    /**
     * Compatibility daily ledger backed by projected credit/allocation slices.
     * Each date is the user's current local calendar date, not the historical
     * source date stored in day_entries.
     */
    @Transactional
    public List<OvertimeLedgerItemDto> ledger(AppUser user, LocalDate from, LocalDate to) {
        List<OvertimeCreditRowDto> rows = projectedRowsInRange(user, from, to);
        Map<LocalDate, DayEntry> dayEntries = entries(user, from, to).stream()
                .collect(Collectors.toMap(DayEntry::getDate, entry -> entry, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<OvertimeCreditRowDto>> byDate = rows.stream()
                .collect(Collectors.groupingBy(
                        OvertimeCreditRowDto::workedDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<OvertimeLedgerItemDto> result = new ArrayList<>();
        for (Map.Entry<String, List<OvertimeCreditRowDto>> item : byDate.entrySet()) {
            LocalDate date = LocalDate.parse(item.getKey());
            DayEntry day = dayEntries.get(date);
            double earned = round2(item.getValue().stream().mapToDouble(OvertimeCreditRowDto::hours).sum());
            double used = round2(item.getValue().stream().mapToDouble(OvertimeCreditRowDto::usedHours).sum());
            result.add(new OvertimeLedgerItemDto(
                    item.getKey(),
                    day != null && day.getShiftType() != null ? day.getShiftType().getId() : null,
                    day != null && day.getShiftType() != null ? day.getShiftType().getName() : null,
                    earned,
                    used,
                    round2(earned - used),
                    day != null && day.getNote() != null && !day.getNote().isBlank()
            ));
        }
        return List.copyOf(result);
    }

    /**
     * Полная бухгалтерия переработок. Не ограничивается текущим месяцем:
     * начисления из мая могут быть списаны в августе.
     */
    @Transactional
    public OvertimeAccountDto account(AppUser user) {
        ensureAllocationConsistency(user);
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        List<OvertimeAllocation> allocationList = allocations.findAllByOwner(user);

        AccountProjection projection = projectAccount(user, creditList, usageList, allocationList);
        List<OvertimeCreditRowDto> creditRows = projection.creditRows();
        List<OvertimeUsageDto> usageRows = projection.usageRows();

        int earnedMinutes = creditList.stream().mapToInt(OvertimeCredit::getCreditedMinutes).sum();
        int usedMinutes = usageList.stream().mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        return new OvertimeAccountDto(hoursFromMinutes(earnedMinutes), hoursFromMinutes(usedMinutes),
                hoursFromMinutes(earnedMinutes - usedMinutes), creditRows, usageRows);
    }

    /**
     * Страничный ответ для экрана переработок: итог аккаунта считается полностью,
     * клиент получает текущую страницу начислений и полный canonical usage snapshot.
     */
    @Transactional
    public OvertimeAccountPageDto accountPage(AppUser user, String from, String to, String status, String q, int page, int size) {
        OvertimeAccountDto account = account(user);
        List<OvertimeCreditRowDto> filtered = filterCreditRows(account.credits(), from, to, status, q);
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        return new OvertimeAccountPageDto(
                account.totalEarnedHours(),
                account.totalUsedHours(),
                account.balanceHours(),
                PageDto.of(pageSlice(filtered, safePage, safeSize), safePage, safeSize, filtered.size()),
                account.usages()
        );
    }

    /**
     * Экспорт журнала переработок в CSV. Фильтры совпадают с таблицей на фронте.
     */
    @Transactional
    public byte[] exportAccountCsv(AppUser user, String from, String to, String status, String q) {
        List<OvertimeCreditRowDto> rows = filterCreditRows(account(user).credits(), from, to, status, q);
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // BOM, чтобы Excel нормально открыл кириллицу
        appendCsvLine(sb, List.of(
                "День переработки",
                "Время",
                "Начислено, ч",
                "Причина переработки",
                "Использовано, ч",
                "Куда списано",
                "Остаток, ч",
                "Обед, мин",
                "Вычтено плана, ч",
                "Рассчитано автоматически"
        ));
        for (OvertimeCreditRowDto row : rows) {
            appendCsvLine(sb, List.of(
                    row.workedDate(),
                    exportTimeRange(row),
                    fmt(row.hours()),
                    value(row.reason()),
                    fmt(row.usedHours()),
                    usagesText(row),
                    fmt(row.remainingHours()),
                    String.valueOf(row.breakMinutes()),
                    fmt(row.plannedHours()),
                    row.calculated() ? "да" : "нет"
            ));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Excel-совместимый .xls без тяжёлых зависимостей: обычная HTML-таблица, которую Excel открывает как книгу.
     */
    @Transactional
    public byte[] exportAccountXls(AppUser user, String from, String to, String status, String q) {
        OvertimeAccountDto account = account(user);
        List<OvertimeCreditRowDto> rows = filterCreditRows(account.credits(), from, to, status, q);
        double earned = rows.stream().mapToDouble(OvertimeCreditRowDto::hours).sum();
        double used = rows.stream().mapToDouble(OvertimeCreditRowDto::usedHours).sum();
        double remain = rows.stream().mapToDouble(OvertimeCreditRowDto::remainingHours).sum();

        StringBuilder html = new StringBuilder();
        html.append("\ufeff");
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif} table{border-collapse:collapse} th,td{border:1px solid #999;padding:6px;vertical-align:top} th{background:#f2f2f2} .num{mso-number-format:'0.00'}");
        html.append("</style></head><body>");
        html.append("<h2>Журнал переработок</h2>");
        html.append("<p>Всего остаток аккаунта: ").append(escHtml(fmt(account.balanceHours()))).append(" ч</p>");
        html.append("<p>По выгрузке: записей ").append(rows.size())
                .append(", начислено ").append(escHtml(fmt(earned)))
                .append(" ч, использовано ").append(escHtml(fmt(used)))
                .append(" ч, остаток ").append(escHtml(fmt(remain))).append(" ч</p>");
        html.append("<table><thead><tr>");
        for (String h : List.of("День переработки", "Время", "Начислено, ч", "Причина переработки", "Использовано, ч", "Куда списано", "Остаток, ч", "Обед, мин", "Вычтено плана, ч", "Авторасчёт")) {
            html.append("<th>").append(escHtml(h)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (OvertimeCreditRowDto row : rows) {
            html.append("<tr>")
                    .append("<td>").append(escHtml(row.workedDate())).append("</td>")
                    .append("<td>").append(escHtml(exportTimeRange(row))).append("</td>")
                    .append("<td class=\"num\">").append(escHtml(fmt(row.hours()))).append("</td>")
                    .append("<td>").append(escHtml(value(row.reason()))).append("</td>")
                    .append("<td class=\"num\">").append(escHtml(fmt(row.usedHours()))).append("</td>")
                    .append("<td>").append(escHtml(usagesText(row)).replace("\n", "<br>")).append("</td>")
                    .append("<td class=\"num\">").append(escHtml(fmt(row.remainingHours()))).append("</td>")
                    .append("<td>").append(row.breakMinutes()).append("</td>")
                    .append("<td class=\"num\">").append(escHtml(fmt(row.plannedHours()))).append("</td>")
                    .append("<td>").append(row.calculated() ? "да" : "нет").append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonical server-side preview for the overtime editor. Browser/device
     * timezone is never consulted; DST gaps and overlaps use UserTimeService's
     * deterministic policy in the user's canonical IANA zone.
     */
    @Transactional(readOnly = true)
    public OvertimeCreditPreviewDto previewCredit(AppUser user, OvertimeCreditCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        CalculatedCredit calculated = calculateCredit(user, req);
        if (!calculated.calculated()) {
            int creditedMinutes = minutesFromHours(calculated.hours());
            return new OvertimeCreditPreviewDto(
                    false,
                    creditedMinutes,
                    hoursFromMinutes(creditedMinutes),
                    0,
                    0,
                    0.0,
                    creditedMinutes,
                    hoursFromMinutes(creditedMinutes),
                    null,
                    null,
                    null
            );
        }
        int elapsedMinutes = Math.toIntExact(Duration.between(
                calculated.startInstant(), calculated.endInstant()).toMinutes());
        int plannedMinutes = (int) Math.round(calculated.plannedHours() * 60.0);
        int creditedMinutes = elapsedMinutes - calculated.breakMinutes() - plannedMinutes;
        return new OvertimeCreditPreviewDto(
                true,
                elapsedMinutes,
                hoursFromMinutes(elapsedMinutes),
                calculated.breakMinutes(),
                plannedMinutes,
                calculated.plannedHours(),
                creditedMinutes,
                hoursFromMinutes(creditedMinutes),
                calculated.sourceTimezone(),
                calculated.startInstant().toString(),
                calculated.endInstant().toString()
        );
    }

    @Transactional
    public OvertimeAccountDto createCredit(AppUser user, OvertimeCreditCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }

        CalculatedCredit calculated = calculateCredit(user, req);
        if (!calculated.calculated()) {
            LocalDate date = dayEntryService.parseDate(req.date(), "Дата переработки должна быть в формате yyyy-MM-dd");
            assertPeriodOpen(user, date);
            credits.save(new OvertimeCredit(
                    user,
                    date,
                    normalize(calculated.timeRange()),
                    calculated.hours(),
                    normalize(req.reason())
            ));
            rebuildAllAllocations(user);
            return account(user);
        }

        List<CreditSegment> segments = splitCalculatedCredit(user, calculated);
        if (segments.isEmpty()) {
            throw ApiException.badRequest("После разбиения по датам переработка получилась 0 или меньше");
        }

        for (CreditSegment segment : segments) {
            assertPeriodOpen(user, segment.workDate());
            ensureNoOvertimeOverlap(user, segment.startAt(), segment.endAt(), segment.startInstant(), segment.endInstant(), null);
        }

        String reason = normalize(req.reason());
        for (CreditSegment segment : segments) {
            OvertimeCredit credit = new OvertimeCredit(
                    user,
                    segment.workDate(),
                    formatTimeRange(segment.startAt(), segment.endAt()),
                    segment.hours(),
                    reason,
                    segment.startAt(),
                    segment.endAt(),
                    segment.breakMinutes(),
                    segment.plannedHours(),
                    true
            );
            applyAbsoluteIdentity(credit, segment);
            credits.save(credit);
        }
        rebuildAllAllocations(user);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto createUsage(AppUser user, OvertimeUsageCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate date = dayEntryService.parseDate(req.date(), "Дата списания должна быть в формате yyyy-MM-dd");
        assertPeriodOpen(user, date);
        int requestedMinutes = requirePositiveMinutes(req.hours(), "Укажи часы списания больше 0");

        validateUsageCapacity(user, null, requestedMinutes);

        OvertimeUsage usage = new OvertimeUsage(user, date, hoursFromMinutes(requestedMinutes), normalize(req.reason()));
        usage.setRequestedMinutes(requestedMinutes);
        usage.setSourceKind("MANUAL");
        usage.setSourceAbsenceId(null);
        usages.save(usage);
        rebuildAllAllocations(user);
        return account(user);
    }

    /** Current canonical FIFO balance in integer minutes. */
    @Transactional
    public int balanceMinutes(AppUser user) {
        ensureAllocationConsistency(user);
        return totalEarnedMinutes(user) - totalUsedMinutes(user);
    }

    @Transactional(readOnly = true)
    public int totalEarnedMinutes(AppUser user) {
        return credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .mapToInt(OvertimeCredit::getCreditedMinutes).sum();
    }

    @Transactional(readOnly = true)
    public int totalUsedMinutes(AppUser user) {
        return usages.findByOwnerOrderByUsageDateAscIdAsc(user).stream()
                .mapToInt(OvertimeUsage::getRequestedMinutes).sum();
    }

    /** Available balance while editing one source-linked absence. */
    @Transactional(readOnly = true)
    public int availableMinutesForAbsence(AppUser user, Long absenceId) {
        Long excludedUsageId = absenceId == null ? null : usages.findByOwnerAndSourceAbsenceId(user, absenceId)
                .map(OvertimeUsage::getId).orElse(null);
        int earned = totalEarnedMinutes(user);
        int used = usages.findByOwnerOrderByUsageDateAscIdAsc(user).stream()
                .filter(usage -> excludedUsageId == null || !Objects.equals(usage.getId(), excludedUsageId))
                .mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        return Math.max(0, earned - used);
    }

    @Transactional(readOnly = true)
    public Long linkedUsageId(AppUser user, Long absenceId) {
        if (absenceId == null) return null;
        return usages.findByOwnerAndSourceAbsenceId(user, absenceId).map(OvertimeUsage::getId).orElse(null);
    }

    /**
     * Creates or atomically replaces the FIFO usage owned by one absence.
     * The absence remains the only editor for this usage; manual ledger endpoints
     * reject direct changes so compensation can never drift away from the calendar.
     */
    @Transactional
    public Long upsertLinkedAbsenceUsage(AppUser user,
                                         Long absenceId,
                                         LocalDate usageDate,
                                         int requestedMinutes,
                                         String reason) {
        return upsertLinkedAbsenceUsage(user, absenceId, usageDate, requestedMinutes, reason, "POSTED");
    }

    @Transactional
    public Long upsertLinkedAbsenceUsage(AppUser user,
                                         Long absenceId,
                                         LocalDate usageDate,
                                         int requestedMinutes,
                                         String reason,
                                         String postingState) {
        if (absenceId == null) throw ApiException.badRequest("Отсутствие должно быть сохранено до списания переработки");
        assertPeriodOpen(user, usageDate);
        if (requestedMinutes <= 0) throw ApiException.badRequest("Связанное списание должно быть больше 0 минут");
        OvertimeUsage usage = usages.findByOwnerAndSourceAbsenceId(user, absenceId).orElse(null);
        Long excludedId = usage == null ? null : usage.getId();
        validateUsageCapacity(user, excludedId, requestedMinutes);
        if (usage == null) usage = new OvertimeUsage(user, usageDate, hoursFromMinutes(requestedMinutes), normalize(reason));
        usage.setUsageDate(usageDate);
        usage.setRequestedMinutes(requestedMinutes);
        usage.setReason(normalize(reason));
        usage.setSourceKind("ABSENCE");
        usage.setSourceAbsenceId(absenceId);
        usage.setPostingState(postingState);
        usages.saveAndFlush(usage);
        rebuildAllAllocations(user);
        return usage.getId();
    }

    @Transactional
    public void deleteLinkedAbsenceUsage(AppUser user, Long absenceId) {
        if (absenceId == null) return;
        OvertimeUsage usage = usages.findByOwnerAndSourceAbsenceId(user, absenceId).orElse(null);
        if (usage == null) return;
        allocations.deleteByUsage(usage);
        allocations.flush();
        usages.delete(usage);
        usages.flush();
        rebuildAllAllocations(user);
    }

    @Transactional
    public OvertimeAccountDto updateCredit(AppUser user, long id, OvertimeCreditUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        ensureAllocationConsistency(user);
        OvertimeCredit credit = requireOwnedCredit(user, id);
        assertPeriodOpen(user, credit.getWorkDate());
        double used = hoursFromMinutes(allocations.findByCredit(credit).stream().mapToInt(OvertimeAllocation::getAllocatedMinutes).sum());
        boolean keepLegacyLocalIdentity = credit.getStartAtInstant() == null
                && req.date() == null
                && req.startDateTime() == null
                && req.endDateTime() == null
                && req.breakMinutes() == null
                && req.plannedHours() == null;

        OvertimeCreditCreateRequest normalized = normalizeCreditUpdateRequest(credit, req);
        String reason = req.reason() != null ? normalize(req.reason()) : credit.getReason();

        // A calculated interval owns the timezone in which its wall-clock values
        // were entered. Merely opening and saving the editor after changing the
        // account work timezone must never move the already stored instant.
        if (sameCalculatedDefinition(credit, normalized)) {
            credit.setReason(reason);
            if (req.timeRange() != null) credit.setTimeRange(normalize(req.timeRange()));
            credits.save(credit);
            return account(user);
        }

        String preferredSourceTimezone = credit.isCalculated() ? credit.getSourceTimezone() : null;
        CalculatedCredit calculated = calculateCredit(user, normalized, preferredSourceTimezone);

        if (!calculated.calculated()) {
            if (calculated.hours() + 0.00001 < used) {
                throw ApiException.badRequest("Нельзя уменьшить начисление до " + fmt(calculated.hours())
                        + " ч: из него уже списано " + fmt(used) + " ч");
            }
            LocalDate date = dayEntryService.parseDate(normalized.date(), "Дата переработки должна быть в формате yyyy-MM-dd");
            assertPeriodOpen(user, date);
            credit.setWorkDate(date);
            credit.setTimeRange(normalize(calculated.timeRange()));
            credit.setStartAt(null);
            credit.setEndAt(null);
            credit.setStartAtInstant(null);
            credit.setEndAtInstant(null);
            credit.setCreditedStartAtInstant(null);
            credit.setCreditedEndAtInstant(null);
            credit.setSourceTimezone(null);
            credit.setMigratedFromLegacy(false);
            credit.setBreakMinutes(0);
            credit.setPlannedHours(0.0);
            credit.setCalculated(false);
            credit.setHours(calculated.hours());
            credit.setReason(reason);
            credits.save(credit);
            rebuildAllAllocations(user);
            return account(user);
        }

        List<CreditSegment> segments = splitCalculatedCredit(user, calculated);
        if (segments.isEmpty()) {
            throw ApiException.badRequest("После разбиения по датам переработка получилась 0 или меньше");
        }

        for (CreditSegment segment : segments) {
            assertPeriodOpen(user, segment.workDate());
            ensureNoOvertimeOverlap(user, segment.startAt(), segment.endAt(), segment.startInstant(), segment.endInstant(), credit.getId());
        }

        if (segments.size() > 1) {
            if (used > 0.00001) {
                throw ApiException.badRequest("Нельзя заменить уже использованное начисление на несколько строк. Сначала удали списания, которые его используют.");
            }
            credits.delete(credit);
            for (CreditSegment segment : segments) {
                OvertimeCredit replacement = new OvertimeCredit(
                        user,
                        segment.workDate(),
                        formatTimeRange(segment.startAt(), segment.endAt()),
                        segment.hours(),
                        reason,
                        segment.startAt(),
                        segment.endAt(),
                        segment.breakMinutes(),
                        segment.plannedHours(),
                        true
                );
                applyAbsoluteIdentity(replacement, segment);
                credits.save(replacement);
            }
            rebuildAllAllocations(user);
            return account(user);
        }

        CreditSegment segment = segments.get(0);
        if (segment.hours() + 0.00001 < used) {
            throw ApiException.badRequest("Нельзя уменьшить начисление до " + fmt(segment.hours())
                    + " ч: из него уже списано " + fmt(used) + " ч");
        }
        credit.setWorkDate(segment.workDate());
        credit.setTimeRange(formatTimeRange(segment.startAt(), segment.endAt()));
        credit.setStartAt(segment.startAt());
        credit.setEndAt(segment.endAt());
        if (keepLegacyLocalIdentity) {
            credit.setStartAtInstant(null);
            credit.setEndAtInstant(null);
            credit.setSourceTimezone(null);
        } else {
            applyAbsoluteIdentity(credit, segment);
        }
        credit.setBreakMinutes(segment.breakMinutes());
        credit.setPlannedHours(segment.plannedHours());
        credit.setCalculated(true);
        credit.setHours(segment.hours());
        credit.setReason(reason);
        credits.save(credit);
        rebuildAllAllocations(user);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto updateUsage(AppUser user, long id, OvertimeUsageUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        ensureAllocationConsistency(user);
        OvertimeUsage usage = requireOwnedUsage(user, id);
        assertPeriodOpen(user, usage.getUsageDate());
        if (usage.isAbsenceLinked()) {
            throw ApiException.conflict("LINKED_USAGE_MANAGED_BY_ABSENCE",
                    "Это списание создано отсутствием и редактируется только через Vacation Planner");
        }

        LocalDate date = hasText(req.date())
                ? dayEntryService.parseDate(req.date(), "Дата списания должна быть в формате yyyy-MM-dd")
                : usage.getUsageDate();
        assertPeriodOpen(user, date);
        int requestedMinutes = req.hours() != null
                ? requirePositiveMinutes(req.hours(), "Укажи часы списания больше 0")
                : usage.getRequestedMinutes();
        String reason = req.reason() != null ? normalize(req.reason()) : usage.getReason();

        validateUsageCapacity(user, usage.getId(), requestedMinutes);

        usage.setUsageDate(date);
        usage.setRequestedMinutes(requestedMinutes);
        usage.setReason(reason);
        usages.save(usage);
        rebuildAllAllocations(user);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteCredit(AppUser user, long id) {
        ensureAllocationConsistency(user);
        OvertimeCredit credit = requireOwnedCredit(user, id);
        assertPeriodOpen(user, credit.getWorkDate());
        double used = hoursFromMinutes(allocations.findByCredit(credit).stream().mapToInt(OvertimeAllocation::getAllocatedMinutes).sum());
        if (used > 0.00001) {
            throw ApiException.badRequest("Нельзя удалить начисление, из которого уже списано " + fmt(used) + " ч. Сначала удали соответствующее списание.");
        }
        credits.delete(credit);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteUsage(AppUser user, long id) {
        ensureAllocationConsistency(user);
        OvertimeUsage usage = requireOwnedUsage(user, id);
        assertPeriodOpen(user, usage.getUsageDate());
        if (usage.isAbsenceLinked()) {
            throw ApiException.conflict("LINKED_USAGE_MANAGED_BY_ABSENCE",
                    "Связанное списание удаляется вместе с отсутствием в Vacation Planner");
        }
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        List<OvertimeUsage> remainingUsages = usages.findByOwnerOrderByUsageDateAscIdAsc(user).stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), usage.getId()))
                .toList();

        AllocationPlan plan = buildAllocationPlan(creditList, remainingUsages);

        // Remove the complete old ledger only after the replacement plan is valid.
        // The usage itself is then deleted and the surviving ledger is persisted atomically.
        allocations.deleteAllByOwner(user);
        allocations.flush();
        OvertimeUsage managedUsage = requireOwnedUsage(user, id);
        usages.delete(managedUsage);
        usages.flush();
        persistAllocationPlan(user, plan);
        verifyLedgerIntegrity(user, creditList, remainingUsages);
        return account(user);
    }

    /**
     * V43 can introduce source-linked usages before allocation rows exist.
     * Repair only when persisted totals disagree; normal reads remain no-op.
     */
    private void ensureAllocationConsistency(AppUser user) {
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        int requested = usageList.stream().mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        List<OvertimeAllocation> allocationList = allocations.findAllByOwner(user);
        int allocated = allocationList.stream().mapToInt(OvertimeAllocation::getAllocatedMinutes).sum();
        Map<Long, Integer> byUsage = allocationList.stream().collect(Collectors.groupingBy(
                allocation -> allocation.getUsage().getId(),
                Collectors.summingInt(OvertimeAllocation::getAllocatedMinutes)));
        boolean mismatch = requested != allocated || usageList.stream().anyMatch(
                usage -> byUsage.getOrDefault(usage.getId(), 0) != usage.getRequestedMinutes());
        if (mismatch) rebuildAllAllocations(user);
    }

    /**
     * Rebuilds the complete FIFO ledger in deterministic date/id order.
     * The replacement plan is calculated fully in memory before any stored allocation
     * is removed, so a validation or planning error can never leave a half-written ledger.
     */
    private void rebuildAllAllocations(AppUser user) {
        // Preserve the historical FIFO contract: work date first, insertion id second.
        // Exact instants explain which minutes were consumed inside a credit, but a
        // partial legacy migration must never reorder same-day source credits.
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        AllocationPlan plan = buildAllocationPlan(creditList, usageList);

        allocations.deleteAllByOwner(user);
        allocations.flush();
        persistAllocationPlan(user, plan);
        verifyLedgerIntegrity(user, creditList, usageList);
    }

    /**
     * Rejects an impossible usage before a new/edited managed entity is written.
     * This keeps failed commands side-effect free even when the service participates
     * in a wider transaction whose caller catches the domain exception.
     */
    private void validateUsageCapacity(AppUser user, Long excludedUsageId, int proposedMinutes) {
        int availableMinutes = credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .mapToInt(OvertimeCredit::getCreditedMinutes)
                .sum();
        int requestedMinutes = usages.findByOwnerOrderByUsageDateAscIdAsc(user).stream()
                .filter(usage -> excludedUsageId == null || !Objects.equals(usage.getId(), excludedUsageId))
                .mapToInt(OvertimeUsage::getRequestedMinutes)
                .sum() + proposedMinutes;
        if (requestedMinutes > availableMinutes) {
            throw ApiException.badRequest("Недостаточно переработки: доступно "
                    + fmt(hoursFromMinutes(availableMinutes)) + " ч, списать хочешь "
                    + fmt(hoursFromMinutes(requestedMinutes)) + " ч");
        }
    }

    private AllocationPlan buildAllocationPlan(List<OvertimeCredit> creditList,
                                               List<OvertimeUsage> usageList) {
        int availableMinutes = creditList.stream().mapToInt(OvertimeCredit::getCreditedMinutes).sum();
        int requestedMinutes = usageList.stream().mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        if (requestedMinutes > availableMinutes) {
            throw ApiException.badRequest("Недостаточно переработки: доступно "
                    + fmt(hoursFromMinutes(availableMinutes)) + " ч, списать хочешь "
                    + fmt(hoursFromMinutes(requestedMinutes)) + " ч");
        }

        List<AllocationPlanItem> items = new ArrayList<>();
        Map<Long, Integer> consumedByCredit = new LinkedHashMap<>();
        int creditIndex = 0;
        for (OvertimeUsage usage : usageList) {
            int left = usage.getRequestedMinutes();
            while (left > 0 && creditIndex < creditList.size()) {
                OvertimeCredit credit = creditList.get(creditIndex);
                int consumed = consumedByCredit.getOrDefault(credit.getId(), 0);
                int available = Math.max(0, credit.getCreditedMinutes() - consumed);
                if (available == 0) {
                    creditIndex++;
                    continue;
                }
                int take = Math.min(available, left);
                items.add(new AllocationPlanItem(credit.getId(), usage.getId(), consumed, take));
                consumedByCredit.put(credit.getId(), consumed + take);
                left -= take;
                if (consumed + take >= credit.getCreditedMinutes()) creditIndex++;
            }
            if (left > 0) {
                throw ApiException.badRequest("Не удалось распределить " + left + " мин переработки по FIFO");
            }
        }
        return new AllocationPlan(List.copyOf(items), requestedMinutes);
    }

    private void persistAllocationPlan(AppUser user, AllocationPlan plan) {
        Map<Long, OvertimeCredit> creditsById = credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .collect(Collectors.toMap(OvertimeCredit::getId, credit -> credit));
        Map<Long, OvertimeUsage> usagesById = usages.findByOwnerOrderByUsageDateAscIdAsc(user).stream()
                .collect(Collectors.toMap(OvertimeUsage::getId, usage -> usage));

        int persistedMinutes = 0;
        for (AllocationPlanItem item : plan.items()) {
            OvertimeCredit credit = creditsById.get(item.creditId());
            OvertimeUsage usage = usagesById.get(item.usageId());
            if (credit == null || usage == null) {
                throw new IllegalStateException("FIFO plan references a missing overtime entity");
            }
            OvertimeAllocation allocation = new OvertimeAllocation(credit, usage, item.allocatedMinutes());
            applyAllocationInterval(allocation, credit, item.alreadyConsumedMinutes(), item.allocatedMinutes());
            allocations.save(allocation);
            persistedMinutes += item.allocatedMinutes();
        }
        if (persistedMinutes != plan.requestedMinutes()) {
            throw new IllegalStateException("FIFO plan minute total changed before persistence");
        }
        allocations.flush();
    }

    private void verifyLedgerIntegrity(AppUser user,
                                       List<OvertimeCredit> expectedCredits,
                                       List<OvertimeUsage> expectedUsages) {
        List<Long> expectedCreditIds = expectedCredits.stream().map(OvertimeCredit::getId).sorted().toList();
        List<Long> actualCreditIds = credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .map(OvertimeCredit::getId).sorted().toList();
        if (!expectedCreditIds.equals(actualCreditIds)) {
            throw new IllegalStateException("Overtime credit set changed during FIFO rebuild");
        }

        List<Long> expectedUsageIds = expectedUsages.stream().map(OvertimeUsage::getId).sorted().toList();
        List<OvertimeUsage> actualUsages = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        List<Long> actualUsageIds = actualUsages.stream().map(OvertimeUsage::getId).sorted().toList();
        if (!expectedUsageIds.equals(actualUsageIds)) {
            throw new IllegalStateException("Overtime usage set changed during FIFO rebuild");
        }

        List<OvertimeAllocation> actualAllocations = allocations.findAllByOwner(user);
        Map<Long, Integer> allocatedByUsage = actualAllocations.stream().collect(Collectors.groupingBy(
                allocation -> allocation.getUsage().getId(),
                Collectors.summingInt(OvertimeAllocation::getAllocatedMinutes)));
        for (OvertimeUsage usage : actualUsages) {
            int allocated = allocatedByUsage.getOrDefault(usage.getId(), 0);
            if (allocated != usage.getRequestedMinutes()) {
                throw new IllegalStateException("Usage " + usage.getId() + " has " + allocated
                        + " allocated minutes instead of " + usage.getRequestedMinutes());
            }
        }

        Map<Long, Integer> allocatedByCredit = actualAllocations.stream().collect(Collectors.groupingBy(
                allocation -> allocation.getCredit().getId(),
                Collectors.summingInt(OvertimeAllocation::getAllocatedMinutes)));
        for (OvertimeCredit credit : expectedCredits) {
            int allocated = allocatedByCredit.getOrDefault(credit.getId(), 0);
            if (allocated > credit.getCreditedMinutes()) {
                throw new IllegalStateException("Credit " + credit.getId() + " is over-allocated");
            }
        }
    }

    private record AllocationPlan(List<AllocationPlanItem> items, int requestedMinutes) {}
    private record AllocationPlanItem(long creditId, long usageId,
                                      int alreadyConsumedMinutes, int allocatedMinutes) {}

    private void applyAllocationInterval(OvertimeAllocation allocation,
                                         OvertimeCredit credit,
                                         int alreadyConsumedMinutes,
                                         int allocatedMinutes) {
        Instant creditedStart = credit.getCreditedStartAtInstant();
        Instant creditedEnd = credit.getCreditedEndAtInstant();
        if (creditedStart == null || creditedEnd == null) return;
        Instant start = creditedStart.plusSeconds(alreadyConsumedMinutes * 60L);
        Instant end = start.plusSeconds(allocatedMinutes * 60L);
        if (end.isAfter(creditedEnd)) end = creditedEnd;
        allocation.setStartAtInstant(start);
        allocation.setEndAtInstant(end);
        allocation.setSourceTimezone(credit.getSourceTimezone());
        allocation.setReconstructed(credit.isMigratedFromLegacy());
    }


    @Transactional(readOnly = true)
    public LegacyOvertimeMigrationPreviewDto previewLegacyCredits(AppUser user, LegacyOvertimeMigrationRequest request) {
        String zone = migrationZone(user, request == null ? null : request.sourceTimezone());
        List<OvertimeCredit> selected = selectedLegacyCredits(user, request == null ? null : request.creditIds());
        List<LegacyOvertimeCreditDto> rows = selected.stream()
                .map(c -> legacyCreditDto(user, c, zone))
                .toList();
        int migratable = (int) rows.stream().filter(LegacyOvertimeCreditDto::migratable).count();
        return new LegacyOvertimeMigrationPreviewDto(zone, rows.size(), migratable, rows.size() - migratable, rows);
    }

    @Transactional
    public LegacyOvertimeMigrationResultDto migrateLegacyCredits(AppUser user, LegacyOvertimeMigrationRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        if (request.creditIds() == null || request.creditIds().isEmpty()) {
            throw ApiException.badRequest("Выбери хотя бы одну старую запись переработки");
        }
        LegacyOvertimeMigrationPreviewDto preview = previewLegacyCredits(user, request);
        if (preview.requestedCount() == 0) {
            throw ApiException.badRequest("Выбери хотя бы одну старую запись переработки");
        }
        Map<Long, LegacyOvertimeCreditDto> previewById = preview.credits().stream()
                .collect(Collectors.toMap(LegacyOvertimeCreditDto::id, row -> row));
        int migrated = 0;
        int skipped = 0;
        for (OvertimeCredit credit : selectedLegacyCredits(user, request.creditIds())) {
            LegacyOvertimeCreditDto row = previewById.get(credit.getId());
            if (row == null || !row.migratable()) {
                skipped++;
                continue;
            }
            ZoneId zone = userTimeService.resolveZone(preview.sourceTimezone(), userTimeService.workZone(user));
            Instant rawStart = userTimeService.resolveLocalDateTime(credit.getStartAt(), zone).toInstant();
            Instant rawEnd = userTimeService.resolveLocalDateTime(credit.getEndAt(), zone).toInstant();
            int minutes = credit.getCreditedMinutes();
            credit.setStartAtInstant(rawStart);
            credit.setEndAtInstant(rawEnd);
            credit.setSourceTimezone(zone.getId());
            credit.setCreditedMinutes(minutes);
            credit.setCreditedEndAtInstant(rawEnd);
            credit.setCreditedStartAtInstant(rawEnd.minusSeconds(minutes * 60L));
            credit.setMigratedFromLegacy(true);
            credits.save(credit);
            migrated++;
        }
        rebuildAllAllocations(user);
        return new LegacyOvertimeMigrationResultDto(migrated, skipped, account(user));
    }

    private List<OvertimeCredit> selectedLegacyCredits(AppUser user, List<Long> requestedIds) {
        List<OvertimeCredit> legacy = credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .filter(c -> c.getCreditedStartAtInstant() == null || c.getCreditedEndAtInstant() == null)
                .toList();
        if (requestedIds == null || requestedIds.isEmpty()) return legacy;
        var wanted = requestedIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return legacy.stream().filter(c -> wanted.contains(c.getId())).toList();
    }

    private LegacyOvertimeCreditDto legacyCreditDto(AppUser user, OvertimeCredit credit, String zoneId) {
        String blocked = null;
        Instant rawStart = null;
        Instant rawEnd = null;
        Instant creditedStart = null;
        if (credit.getStartAt() == null || credit.getEndAt() == null) {
            blocked = "Нет точных локальных полей начала и конца";
        } else {
            ZoneId zone = userTimeService.resolveZone(zoneId, userTimeService.workZone(user));
            rawStart = userTimeService.resolveLocalDateTime(credit.getStartAt(), zone).toInstant();
            rawEnd = userTimeService.resolveLocalDateTime(credit.getEndAt(), zone).toInstant();
            int minutes = credit.getCreditedMinutes();
            if (!rawEnd.isAfter(rawStart)) {
                blocked = "Конец должен быть позже начала";
            } else if (Duration.between(rawStart, rawEnd).toMinutes() < minutes) {
                blocked = "Начисленные минуты больше длительности локального интервала";
            } else {
                creditedStart = rawEnd.minusSeconds(minutes * 60L);
            }
        }
        return new LegacyOvertimeCreditDto(
                credit.getId(), credit.getWorkDate().toString(),
                credit.getStartAt() == null ? null : credit.getStartAt().toString(),
                credit.getEndAt() == null ? null : credit.getEndAt().toString(),
                credit.getTimeRange(), hoursFromMinutes(credit.getCreditedMinutes()), credit.getCreditedMinutes(),
                credit.getReason(), blocked == null, blocked, zoneId,
                displayLocal(user, rawStart), displayLocal(user, rawEnd),
                displayLocal(user, creditedStart), displayLocal(user, rawEnd)
        );
    }

    private String migrationZone(AppUser user, String requested) {
        ZoneId fallback = userTimeService.workZone(user);
        ZoneId resolved = userTimeService.resolveZone(requested, fallback);
        if (requested != null && !requested.isBlank() && !resolved.getId().equals(requested.trim())) {
            throw ApiException.badRequest("Часовой пояс должен быть IANA-идентификатором, например Asia/Yekaterinburg");
        }
        return resolved.getId();
    }

    private OvertimeCredit requireOwnedCredit(AppUser user, long id) {
        return credits.findByOwnerAndId(user, id).orElseThrow(() -> {
            if (credits.existsById(id)) {
                securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                        "resource=overtime_credit id=" + id);
            }
            return ApiException.notFound("Начисление переработки не найдено");
        });
    }

    private OvertimeUsage requireOwnedUsage(AppUser user, long id) {
        return usages.findByOwnerAndId(user, id).orElseThrow(() -> {
            if (usages.existsById(id)) {
                securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                        "resource=overtime_usage id=" + id);
            }
            return ApiException.notFound("Списание отгула не найдено");
        });
    }

    private OvertimeCreditCreateRequest normalizeCreditUpdateRequest(OvertimeCredit old, OvertimeCreditUpdateRequest req) {
        String date = hasText(req.date()) ? req.date().trim() : old.getWorkDate().toString();
        String start = req.startDateTime() != null ? normalize(req.startDateTime()) : (old.getStartAt() == null ? null : old.getStartAt().toString());
        String end = req.endDateTime() != null ? normalize(req.endDateTime()) : (old.getEndAt() == null ? null : old.getEndAt().toString());
        boolean calculated = hasText(start) || hasText(end);
        String timeRange = req.timeRange() != null ? normalize(req.timeRange()) : old.getTimeRange();
        Integer breakMinutes = req.breakMinutes() != null ? req.breakMinutes() : old.getBreakMinutes();
        Double plannedHours = req.plannedHours() != null ? req.plannedHours() : old.getPlannedHours();
        Double hours = req.hours() != null ? req.hours() : old.getHours();

        if (!calculated) {
            start = null;
            end = null;
            breakMinutes = 0;
            plannedHours = 0.0;
        }

        return new OvertimeCreditCreateRequest(
                date,
                timeRange,
                start,
                end,
                breakMinutes,
                plannedHours,
                hours,
                req.reason() != null ? normalize(req.reason()) : old.getReason()
        );
    }

    /**
     * Builds a pure display projection. Persisted credits and FIFO allocations
     * are never rewritten when the canonical timezone changes.
     */
    private AccountProjection projectAccount(AppUser user,
                                             List<OvertimeCredit> creditList,
                                             List<OvertimeUsage> usageList,
                                             List<OvertimeAllocation> allocationList) {
        ZoneId displayZone = userTimeService.displayZone(user);

        List<AllocationFragment> allFragments = allocationList.stream()
                .flatMap(allocation -> projectAllocation(allocation, displayZone).stream())
                .toList();
        Map<Long, List<AllocationFragment>> fragmentsByCredit = allFragments.stream()
                .collect(Collectors.groupingBy(fragment -> fragment.allocation().getCredit().getId()));
        Map<Long, List<AllocationFragment>> fragmentsByUsage = allFragments.stream()
                .collect(Collectors.groupingBy(fragment -> fragment.allocation().getUsage().getId()));
        Map<AllocationFragment, AllocationPartPosition> partPositions = allocationPartPositions(fragmentsByUsage);

        List<OvertimeCreditRowDto> projectedRows = new ArrayList<>();
        for (OvertimeCredit credit : creditList) {
            List<CreditProjectionSlice> slices = projectCredit(credit, displayZone);
            List<OvertimeAllocation> creditAllocations = allocationList.stream()
                    .filter(allocation -> Objects.equals(allocation.getCredit().getId(), credit.getId()))
                    .toList();
            List<AllocationFragment> creditFragments = fragmentsByCredit.getOrDefault(credit.getId(), List.of());
            int sourceUsedMinutes = creditAllocations.stream()
                    .mapToInt(OvertimeAllocation::getAllocatedMinutes)
                    .sum();
            for (int i = 0; i < slices.size(); i++) {
                projectedRows.add(projectedCreditRow(
                        user,
                        credit,
                        slices.get(i),
                        i + 1,
                        slices.size(),
                        sourceUsedMinutes,
                        creditFragments,
                        partPositions
                ));
            }
        }

        projectedRows.sort(Comparator
                .comparing(OvertimeCreditRowDto::workedDate)
                .thenComparing(row -> row.displayStart() == null ? "" : row.displayStart())
                .thenComparing(OvertimeCreditRowDto::id)
                .thenComparing(row -> row.projection() == null ? 1 : row.projection().partIndex()));
        List<OvertimeCreditRowDto> rowsWithDaySummaries = attachDaySummaries(projectedRows);

        List<OvertimeUsageDto> projectedUsages = usageList.stream()
                .map(usage -> projectedUsageRow(
                        user,
                        usage,
                        fragmentsByUsage.getOrDefault(usage.getId(), List.of())
                ))
                .toList();

        return new AccountProjection(rowsWithDaySummaries, projectedUsages);
    }

    private List<CreditProjectionSlice> projectCredit(OvertimeCredit credit, ZoneId displayZone) {
        Instant start = credit.getCreditedStartAtInstant();
        int creditedMinutes = credit.getCreditedMinutes();
        if (start == null || creditedMinutes <= 0) {
            return List.of(new CreditProjectionSlice(
                    credit.getWorkDate(), null, null, creditedMinutes, false));
        }

        // Integer minutes are the accounting authority. Historical rows may
        // contain an end value reconstructed by an older release, so derive the
        // effective end from the authoritative minute count.
        Instant effectiveEnd = start.plusSeconds(creditedMinutes * 60L);
        List<DailyInstantSegment> daily = splitByLocalDay(start, effectiveEnd, displayZone);
        if (daily.isEmpty()) {
            return List.of(new CreditProjectionSlice(
                    credit.getWorkDate(), null, null, creditedMinutes, false));
        }
        return daily.stream()
                .map(segment -> new CreditProjectionSlice(
                        segment.date(),
                        segment.start(),
                        segment.end(),
                        segment.minutes(),
                        true
                ))
                .toList();
    }

    private List<AllocationFragment> projectAllocation(OvertimeAllocation allocation, ZoneId displayZone) {
        Instant start = allocation.getStartAtInstant();
        int allocatedMinutes = allocation.getAllocatedMinutes();
        if (start == null || allocatedMinutes <= 0) {
            return List.of(new AllocationFragment(
                    allocation,
                    allocation.getCredit().getWorkDate(),
                    null,
                    null,
                    allocatedMinutes,
                    false
            ));
        }
        Instant effectiveEnd = start.plusSeconds(allocatedMinutes * 60L);
        List<DailyInstantSegment> segments = splitByLocalDay(start, effectiveEnd, displayZone);
        if (segments.isEmpty()) {
            return List.of(new AllocationFragment(
                    allocation,
                    allocation.getCredit().getWorkDate(),
                    null,
                    null,
                    allocatedMinutes,
                    false
            ));
        }
        return segments.stream()
                .map(segment -> new AllocationFragment(
                        allocation,
                        segment.date(),
                        segment.start(),
                        segment.end(),
                        segment.minutes(),
                        true
                ))
                .toList();
    }

    private List<DailyInstantSegment> splitByLocalDay(Instant start, Instant end, ZoneId zone) {
        if (start == null || end == null || !end.isAfter(start)) return List.of();
        List<DailyInstantSegment> result = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            ZonedDateTime localCursor = cursor.atZone(zone);
            LocalDate date = localCursor.toLocalDate();
            Instant nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant();
            Instant segmentEnd = nextMidnight.isBefore(end) ? nextMidnight : end;
            if (!segmentEnd.isAfter(cursor)) {
                throw new IllegalStateException("Timezone projection did not advance at " + cursor + " in " + zone);
            }
            int minutes = Math.toIntExact(Duration.between(cursor, segmentEnd).toMinutes());
            if (minutes > 0) {
                result.add(new DailyInstantSegment(date, cursor, segmentEnd, minutes));
            }
            cursor = segmentEnd;
        }
        return List.copyOf(result);
    }

    private Map<AllocationFragment, AllocationPartPosition> allocationPartPositions(
            Map<Long, List<AllocationFragment>> fragmentsByUsage) {
        Map<AllocationFragment, AllocationPartPosition> result = new LinkedHashMap<>();
        for (List<AllocationFragment> usageFragments : fragmentsByUsage.values()) {
            List<AllocationFragment> sorted = usageFragments.stream()
                    .sorted(Comparator
                            .comparing((AllocationFragment fragment) -> fragment.start() == null
                                    ? Instant.MAX : fragment.start())
                            .thenComparing(fragment -> fragment.allocation().getCredit().getWorkDate())
                            .thenComparing(fragment -> fragment.allocation().getCredit().getId())
                            .thenComparing(fragment -> fragment.allocation().getId()))
                    .toList();
            int count = Math.max(1, sorted.size());
            for (int i = 0; i < sorted.size(); i++) {
                result.put(sorted.get(i), new AllocationPartPosition(i + 1, count));
            }
        }
        return result;
    }

    private OvertimeCreditRowDto projectedCreditRow(AppUser user,
                                                     OvertimeCredit credit,
                                                     CreditProjectionSlice slice,
                                                     int partIndex,
                                                     int partCount,
                                                     int sourceUsedMinutes,
                                                     List<AllocationFragment> creditFragments,
                                                     Map<AllocationFragment, AllocationPartPosition> partPositions) {
        List<AllocationFragment> fragments = creditFragments.stream()
                .filter(fragment -> fragmentBelongsToSlice(fragment, slice))
                .sorted(Comparator
                        .comparing((AllocationFragment fragment) -> fragment.start() == null
                                ? Instant.MAX : fragment.start())
                        .thenComparing(fragment -> fragment.allocation().getId()))
                .toList();
        int sliceUsedMinutes = fragments.stream().mapToInt(AllocationFragment::minutes).sum();
        List<OvertimeUsageRefDto> usageRefs = fragments.stream()
                .map(fragment -> usageRef(user, fragment, partPositions.get(fragment)))
                .toList();

        int sourceMinutes = credit.getCreditedMinutes();
        int sourceRemainingMinutes = Math.max(0, sourceMinutes - sourceUsedMinutes);
        int sliceRemainingMinutes = Math.max(0, slice.minutes() - sliceUsedMinutes);
        String displayStart = slice.exact() ? displayLocal(user, slice.start()) : displayLocal(user, credit.getStartAtInstant());
        String displayEnd = slice.exact() ? displayLocal(user, slice.end()) : displayLocal(user, credit.getEndAtInstant());
        String displayTimezone = slice.exact() ? userTimeService.displayZone(user).getId()
                : (credit.getStartAtInstant() == null ? null : userTimeService.displayZone(user).getId());

        OvertimeDailyProjectionDto projection = new OvertimeDailyProjectionDto(
                credit.getWorkDate().toString(),
                credit.getTimeRange(),
                partIndex,
                partCount,
                1,
                1,
                hoursFromMinutes(slice.minutes()),
                hoursFromMinutes(sliceUsedMinutes),
                hoursFromMinutes(sliceRemainingMinutes),
                hoursFromMinutes(sourceMinutes),
                hoursFromMinutes(sourceUsedMinutes),
                hoursFromMinutes(sourceRemainingMinutes),
                slice.exact()
        );

        return new OvertimeCreditRowDto(
                credit.getId(),
                slice.date().toString(),
                credit.getTimeRange(),
                credit.getStartAt() == null ? null : credit.getStartAt().toString(),
                credit.getEndAt() == null ? null : credit.getEndAt().toString(),
                credit.getBreakMinutes(),
                round2(credit.getPlannedHours()),
                credit.isCalculated(),
                hoursFromMinutes(slice.minutes()),
                credit.getReason(),
                hoursFromMinutes(sliceUsedMinutes),
                hoursFromMinutes(sliceRemainingMinutes),
                usageRefs,
                instantText(credit.getStartAtInstant()),
                instantText(credit.getEndAtInstant()),
                credit.getSourceTimezone(),
                displayStart,
                displayEnd,
                displayTimezone,
                slice.minutes(),
                instantText(slice.exact() ? slice.start() : credit.getCreditedStartAtInstant()),
                instantText(slice.exact() ? slice.end() : credit.getCreditedEndAtInstant()),
                slice.exact() ? displayStart : displayLocal(user, credit.getCreditedStartAtInstant()),
                slice.exact() ? displayEnd : displayLocal(user, credit.getCreditedEndAtInstant()),
                credit.isMigratedFromLegacy(),
                credit.getCreditedStartAtInstant() == null || credit.getCreditedEndAtInstant() == null,
                projection
        );
    }

    private boolean fragmentBelongsToSlice(AllocationFragment fragment, CreditProjectionSlice slice) {
        if (!slice.exact()) return !fragment.exact();
        if (!fragment.exact()) return false;
        return fragment.start().isBefore(slice.end()) && fragment.end().isAfter(slice.start());
    }

    private OvertimeUsageRefDto usageRef(AppUser user,
                                         AllocationFragment fragment,
                                         AllocationPartPosition position) {
        OvertimeAllocation allocation = fragment.allocation();
        AllocationPartPosition safePosition = position == null
                ? new AllocationPartPosition(1, 1)
                : position;
        return new OvertimeUsageRefDto(
                allocation.getUsage().getId(),
                allocation.getUsage().getUsageDate().toString(),
                hoursFromMinutes(fragment.minutes()),
                allocation.getUsage().getReason(),
                fragment.minutes(),
                instantText(fragment.start()),
                instantText(fragment.end()),
                displayLocal(user, fragment.start()),
                displayLocal(user, fragment.end()),
                allocation.getSourceTimezone(),
                safePosition.index(),
                safePosition.count(),
                fragment.exact(),
                allocation.isReconstructed()
        );
    }

    private OvertimeUsageDto projectedUsageRow(AppUser user,
                                               OvertimeUsage usage,
                                               List<AllocationFragment> fragments) {
        List<OvertimeAllocationDto> refs = fragments.stream()
                .sorted(Comparator
                        .comparing((AllocationFragment fragment) -> fragment.start() == null
                                ? Instant.MAX : fragment.start())
                        .thenComparing(fragment -> fragment.allocation().getCredit().getId()))
                .map(fragment -> {
                    OvertimeAllocation allocation = fragment.allocation();
                    return new OvertimeAllocationDto(
                            allocation.getCredit().getId(),
                            fragment.date().toString(),
                            fragment.exact()
                                    ? projectedRange(fragment.start(), fragment.end(), userTimeService.displayZone(user))
                                    : allocation.getCredit().getTimeRange(),
                            hoursFromMinutes(fragment.minutes()),
                            allocation.getCredit().getReason(),
                            fragment.minutes(),
                            instantText(fragment.start()),
                            instantText(fragment.end()),
                            displayLocal(user, fragment.start()),
                            displayLocal(user, fragment.end()),
                            allocation.getSourceTimezone(),
                            fragment.exact(),
                            allocation.isReconstructed()
                    );
                })
                .toList();
        return new OvertimeUsageDto(
                usage.getId(),
                usage.getUsageDate().toString(),
                hoursFromMinutes(usage.getRequestedMinutes()),
                usage.getReason(),
                refs,
                usage.getRequestedMinutes(),
                usage.getSourceKind(),
                usage.getSourceAbsenceId(),
                !usage.isAbsenceLinked(),
                usage.getPostingState(),
                usage.isReserved()
        );
    }

    private String projectedRange(Instant start, Instant end, ZoneId zone) {
        if (start == null || end == null) return null;
        LocalDateTime localStart = start.atZone(zone).toLocalDateTime();
        LocalDateTime localEnd = end.atZone(zone).toLocalDateTime();
        return formatTimeRange(localStart, localEnd);
    }

    private List<OvertimeCreditRowDto> attachDaySummaries(List<OvertimeCreditRowDto> rows) {
        Map<String, List<OvertimeCreditRowDto>> byDay = rows.stream()
                .collect(Collectors.groupingBy(
                        OvertimeCreditRowDto::workedDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<OvertimeCreditRowDto> result = new ArrayList<>(rows.size());
        for (List<OvertimeCreditRowDto> dayRows : byDay.values()) {
            double earned = round2(dayRows.stream().mapToDouble(OvertimeCreditRowDto::hours).sum());
            double used = round2(dayRows.stream().mapToDouble(OvertimeCreditRowDto::usedHours).sum());
            double remaining = round2(dayRows.stream().mapToDouble(OvertimeCreditRowDto::remainingHours).sum());
            for (int i = 0; i < dayRows.size(); i++) {
                OvertimeCreditRowDto row = dayRows.get(i);
                OvertimeDailyProjectionDto base = row.projection();
                OvertimeDailyProjectionDto projection = new OvertimeDailyProjectionDto(
                        base.sourceWorkedDate(),
                        base.sourceTimeRange(),
                        base.partIndex(),
                        base.partCount(),
                        i + 1,
                        dayRows.size(),
                        earned,
                        used,
                        remaining,
                        base.sourceCreditHours(),
                        base.sourceUsedHours(),
                        base.sourceRemainingHours(),
                        base.exact()
                );
                result.add(copyWithProjection(row, projection));
            }
        }
        return List.copyOf(result);
    }

    private OvertimeCreditRowDto copyWithProjection(OvertimeCreditRowDto row,
                                                     OvertimeDailyProjectionDto projection) {
        return new OvertimeCreditRowDto(
                row.id(), row.workedDate(), row.timeRange(), row.startDateTime(), row.endDateTime(),
                row.breakMinutes(), row.plannedHours(), row.calculated(), row.hours(), row.reason(),
                row.usedHours(), row.remainingHours(), row.usages(), row.startInstant(), row.endInstant(),
                row.sourceTimezone(), row.displayStart(), row.displayEnd(), row.displayTimezone(),
                row.creditedMinutes(), row.creditedStartInstant(), row.creditedEndInstant(),
                row.creditedDisplayStart(), row.creditedDisplayEnd(), row.migratedFromLegacy(),
                row.legacyTimezoneRequired(), projection
        );
    }


    private List<OvertimeCreditRowDto> projectedRowsInRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        return account(user).credits().stream()
                .filter(row -> row.workedDate().compareTo(from.toString()) >= 0)
                .filter(row -> row.workedDate().compareTo(to.toString()) <= 0)
                .toList();
    }

    private List<DayEntry> entries(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        return days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to);
    }


    private int safePage(int page) {
        return Math.max(0, page);
    }

    private int safeSize(int size) {
        if (size <= 0) return 50;
        return Math.min(100, Math.max(10, size));
    }

    private <T> List<T> pageSlice(List<T> list, int page, int size) {
        int from = Math.min(list.size(), page * size);
        int to = Math.min(list.size(), from + size);
        return list.subList(from, to);
    }

    private List<OvertimeCreditRowDto> filterCreditRows(List<OvertimeCreditRowDto> rows, String from, String to, String status, String q) {
        String normalizedStatus = hasText(status) ? status.trim().toLowerCase() : "all";
        String normalizedQ = hasText(q) ? q.trim().toLowerCase() : "";
        return rows.stream()
                .filter(row -> !hasText(from) || row.workedDate().compareTo(from.trim()) >= 0)
                .filter(row -> !hasText(to) || row.workedDate().compareTo(to.trim()) <= 0)
                .filter(row -> statusMatches(row, normalizedStatus))
                .filter(row -> normalizedQ.isBlank() || exportSearchHaystack(row).contains(normalizedQ))
                .toList();
    }

    private boolean statusMatches(OvertimeCreditRowDto row, String status) {
        double remaining = row.remainingHours();
        double used = row.usedHours();
        return switch (status) {
            case "open" -> remaining > 0.00001;
            case "partial" -> remaining > 0.00001 && used > 0.00001;
            case "closed" -> remaining <= 0.00001;
            default -> true;
        };
    }

    private String exportSearchHaystack(OvertimeCreditRowDto row) {
        OvertimeDailyProjectionDto projection = row.projection();
        String sourceDate = projection == null ? "" : value(projection.sourceWorkedDate());
        String sourceRange = projection == null ? "" : value(projection.sourceTimeRange());
        return (row.workedDate() + " " + sourceDate + " " + value(row.timeRange()) + " " + sourceRange + " "
                + value(row.displayStart()) + " " + value(row.displayEnd()) + " "
                + value(row.sourceTimezone()) + " " + value(row.displayTimezone()) + " "
                + fmt(row.hours()) + " " + value(row.reason()) + " " + usagesText(row)).toLowerCase();
    }

    private String exportTimeRange(OvertimeCreditRowDto row) {
        if (hasText(row.displayStart()) && hasText(row.displayEnd())) {
            try {
                return formatTimeRange(
                        LocalDateTime.parse(row.displayStart()),
                        LocalDateTime.parse(row.displayEnd())
                );
            } catch (RuntimeException ignored) {
                // Keep the historical source range for malformed legacy display values.
            }
        }
        return value(row.timeRange());
    }

    private String usagesText(OvertimeCreditRowDto row) {
        if (row.usages() == null || row.usages().isEmpty()) {
            return "не списывалось";
        }
        return row.usages().stream()
                .map(u -> u.usageDate() + ": " + fmt(u.hours()) + " ч"
                        + (hasText(u.displayStart()) && hasText(u.displayEnd())
                            ? " [" + u.displayStart() + "–" + u.displayEnd() + "]"
                            : " [без точного интервала]")
                        + (hasText(u.reason()) ? " — " + u.reason() : ""))
                .collect(Collectors.joining("\n"));
    }

    private void appendCsvLine(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(csvCell(cells.get(i)));
        }
        sb.append('\n');
    }

    private String csvCell(String value) {
        String v = value(value).replace("\r", " ");
        boolean quote = v.contains(";") || v.contains("\n") || v.contains("\"");
        v = v.replace("\"", "\"\"");
        return quote ? "\"" + v + "\"" : v;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String escHtml(String value) {
        return value(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private boolean sameCalculatedDefinition(OvertimeCredit credit, OvertimeCreditCreateRequest request) {
        if (credit == null || request == null || !credit.isCalculated()
                || credit.getStartAt() == null || credit.getEndAt() == null) {
            return false;
        }
        if (!hasText(request.startDateTime()) || !hasText(request.endDateTime())) {
            return false;
        }
        LocalDateTime requestedStart = parseDateTime(
                request.startDateTime(), "Начало должно быть в формате yyyy-MM-ddTHH:mm");
        LocalDateTime requestedEnd = parseDateTime(
                request.endDateTime(), "Конец должен быть в формате yyyy-MM-ddTHH:mm");
        int requestedBreak = sanitizeMinutes(request.breakMinutes());
        double requestedPlan = sanitizePlannedHours(request.plannedHours());
        return Objects.equals(credit.getStartAt(), requestedStart)
                && Objects.equals(credit.getEndAt(), requestedEnd)
                && credit.getBreakMinutes() == requestedBreak
                && Math.abs(credit.getPlannedHours() - requestedPlan) < 0.00001;
    }

    private CalculatedCredit calculateCredit(AppUser user, OvertimeCreditCreateRequest req) {
        return calculateCredit(user, req, null);
    }

    private CalculatedCredit calculateCredit(AppUser user,
                                             OvertimeCreditCreateRequest req,
                                             String preferredSourceTimezone) {
        boolean hasStart = hasText(req.startDateTime());
        boolean hasEnd = hasText(req.endDateTime());
        if (hasStart || hasEnd) {
            if (!hasStart || !hasEnd) {
                throw ApiException.badRequest("Для автоподсчёта нужны и начало, и конец переработки");
            }
            LocalDateTime start = parseDateTime(req.startDateTime(), "Начало должно быть в формате yyyy-MM-ddTHH:mm");
            LocalDateTime end = parseDateTime(req.endDateTime(), "Конец должен быть в формате yyyy-MM-ddTHH:mm");
            ZoneId sourceZone = userTimeService.resolveZone(
                    preferredSourceTimezone,
                    userTimeService.workZone(user)
            );
            Instant startInstant = userTimeService.resolveLocalDateTime(start, sourceZone).toInstant();
            Instant endInstant = userTimeService.resolveLocalDateTime(end, sourceZone).toInstant();
            if (!endInstant.isAfter(startInstant)) {
                throw ApiException.badRequest("Конец должен быть позже начала. Для ночной работы укажи следующий день в поле конца.");
            }
            long totalMinutes = Duration.between(startInstant, endInstant).toMinutes();
            int breakMinutes = sanitizeMinutes(req.breakMinutes());
            double plannedHours = sanitizePlannedHours(req.plannedHours());
            long plannedMinutes = Math.round(plannedHours * 60.0);
            long creditedMinutes = totalMinutes - breakMinutes - plannedMinutes;
            if (creditedMinutes <= 0) {
                throw ApiException.badRequest("После вычета обеда и плановых часов переработка получилась 0 или меньше");
            }
            double hours = requirePositiveHours(creditedMinutes / 60.0, "Переработка должна быть больше 0");
            String timeRange = hasText(req.timeRange()) ? req.timeRange().trim() : formatTimeRange(start, end);
            return new CalculatedCredit(
                    timeRange,
                    hours,
                    start,
                    end,
                    startInstant,
                    endInstant,
                    sourceZone.getId(),
                    breakMinutes,
                    plannedHours,
                    true
            );
        }

        double hours = requirePositiveHours(req.hours(), "Укажи часы переработки больше 0");
        return new CalculatedCredit(normalize(req.timeRange()), hours, null, null,
                null, null, null, 0, 0.0, false);
    }

    /**
     * Рассчитанную переработку раскладываем на отдельные начисления.
     * Так сутки не падают одной строкой на дату начала, а становятся несколькими строками журнала.
     *
     * Правила:
     * - обычные интервалы через полночь режутся по календарной полуночи;
     * - ровные сутки вида 08:00 → 08:00 следующего дня режутся пополам,
     *   чтобы в календаре было две понятные половины: дата начала и дата конца;
     * - обед и вычтенный план снимаются с самых ранних минут интервала.
     */
    private List<CreditSegment> splitCalculatedCredit(AppUser user, CalculatedCredit credit) {
        LocalDateTime start = credit.startAt();
        LocalDateTime end = credit.endAt();
        long totalMinutes = Duration.between(credit.startInstant(), credit.endInstant()).toMinutes();
        ZoneId sourceZone = userTimeService.resolveZone(credit.sourceTimezone(), userTimeService.workZone(user));
        if (totalMinutes <= 0) {
            return List.of();
        }

        List<RawSegment> rawSegments = new ArrayList<>();
        long daysBetween = Duration.between(start.toLocalDate().atStartOfDay(), end.toLocalDate().atStartOfDay()).toDays();
        boolean oneExactDaySameTime = daysBetween == 1 && start.toLocalTime().equals(end.toLocalTime());

        if (oneExactDaySameTime) {
            Instant midInstant = credit.startInstant().plusSeconds((totalMinutes / 2) * 60);
            LocalDateTime mid = midInstant.atZone(sourceZone).toLocalDateTime();
            rawSegments.add(new RawSegment(start.toLocalDate(), start, mid, credit.startInstant(), midInstant));
            rawSegments.add(new RawSegment(end.toLocalDate(), mid, end, midInstant, credit.endInstant()));
        } else {
            LocalDateTime cursor = start;
            Instant cursorInstant = credit.startInstant();
            while (cursor.isBefore(end)) {
                LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
                LocalDateTime segmentEnd = nextMidnight.isBefore(end) ? nextMidnight : end;
                Instant segmentEndInstant = segmentEnd.equals(end)
                        ? credit.endInstant()
                        : userTimeService.resolveLocalDateTime(segmentEnd, sourceZone).toInstant();
                rawSegments.add(new RawSegment(cursor.toLocalDate(), cursor, segmentEnd, cursorInstant, segmentEndInstant));
                cursor = segmentEnd;
                cursorInstant = segmentEndInstant;
            }
        }

        int breakLeft = credit.breakMinutes();
        int plannedLeft = (int) Math.round(credit.plannedHours() * 60.0);
        List<CreditSegment> result = new ArrayList<>();

        for (RawSegment raw : rawSegments) {
            int rawMinutes = (int) Duration.between(raw.startInstant(), raw.endInstant()).toMinutes();
            if (rawMinutes <= 0) continue;

            int segmentBreak = Math.min(breakLeft, rawMinutes);
            breakLeft -= segmentBreak;

            int afterBreak = rawMinutes - segmentBreak;
            int segmentPlan = Math.min(plannedLeft, Math.max(0, afterBreak));
            plannedLeft -= segmentPlan;

            int creditedMinutes = rawMinutes - segmentBreak - segmentPlan;
            if (creditedMinutes <= 0) continue;

            result.add(new CreditSegment(
                    raw.workDate(),
                    raw.startAt(),
                    raw.endAt(),
                    raw.startInstant(),
                    raw.endInstant(),
                    credit.sourceTimezone(),
                    segmentBreak,
                    round2(segmentPlan / 60.0),
                    requirePositiveHours(creditedMinutes / 60.0, "Переработка должна быть больше 0")
            ));
        }

        return result;
    }

    /**
     * Запрещаем пересечения по времени. Это защищает от двойного начисления
     * одного и того же периода, например два раза 03.07 20:00 → 04.07 08:00.
     */
    private void ensureNoOvertimeOverlap(AppUser user,
                                         LocalDateTime start,
                                         LocalDateTime end,
                                         Instant startInstant,
                                         Instant endInstant,
                                         Long ignoreCreditId) {
        List<OvertimeCredit> absoluteOverlaps = credits
                .findByOwnerAndStartAtInstantLessThanAndEndAtInstantGreaterThan(user, endInstant, startInstant)
                .stream()
                .filter(c -> ignoreCreditId == null || !ignoreCreditId.equals(c.getId()))
                .toList();

        // Historical rows have no trustworthy source timezone. Keep the legacy
        // wall-clock comparison only for those rows instead of inventing instants.
        List<OvertimeCredit> legacyOverlaps = credits
                .findByOwnerAndStartAtLessThanAndEndAtGreaterThan(user, end, start)
                .stream()
                .filter(c -> c.getStartAtInstant() == null)
                .filter(c -> ignoreCreditId == null || !ignoreCreditId.equals(c.getId()))
                .toList();

        OvertimeCredit first = !absoluteOverlaps.isEmpty()
                ? absoluteOverlaps.get(0)
                : (legacyOverlaps.isEmpty() ? null : legacyOverlaps.get(0));
        if (first == null) return;

        throw ApiException.badRequest(
                "Этот период пересекается с уже записанной переработкой: "
                        + formatTimeRange(first.getStartAt(), first.getEndAt())
                        + " (" + fmt(first.getHours()) + " ч). Удали старую запись или измени время."
        );
    }

    private void applyAbsoluteIdentity(OvertimeCredit credit, CreditSegment segment) {
        int creditedMinutes = minutesFromHours(segment.hours());
        credit.setStartAtInstant(segment.startInstant());
        credit.setEndAtInstant(segment.endInstant());
        credit.setSourceTimezone(segment.sourceTimezone());
        credit.setCreditedMinutes(creditedMinutes);
        credit.setCreditedEndAtInstant(segment.endInstant());
        credit.setCreditedStartAtInstant(segment.endInstant().minusSeconds(creditedMinutes * 60L));
        credit.setMigratedFromLegacy(false);
    }

    private LocalDateTime parseDateTime(String value, String message) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value.trim(), ISO_MINUTES);
            } catch (Exception ignoredAgain) {
                throw ApiException.badRequest(message);
            }
        }
    }

    private int sanitizeMinutes(Integer value) {
        if (value == null) return 0;
        if (value < 0 || value > 1440) {
            throw ApiException.badRequest("Обед должен быть от 0 до 1440 минут");
        }
        return value;
    }

    private double sanitizePlannedHours(Double value) {
        if (value == null) return 0.0;
        if (!Double.isFinite(value) || value < 0 || value > 100.0) {
            throw ApiException.badRequest("Плановые часы должны быть от 0 до 100");
        }
        return round2(value);
    }

    private String formatTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return start.format(SHORT_TIME) + "–" + end.format(SHORT_TIME);
        }
        return start.format(SHORT_DATE_TIME) + "–" + end.format(SHORT_DATE_TIME);
    }

    private String instantText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String displayLocal(AppUser user, Instant instant) {
        return instant == null ? null : userTimeService.inDisplayZone(instant, user).toLocalDateTime().toString();
    }

    private int minutesFromHours(double hours) {
        return (int) Math.max(0L, Math.round(hours * 60.0));
    }

    private int requirePositiveMinutes(Double hours, String message) {
        double validated = requirePositiveHours(hours, message);
        int minutes = minutesFromHours(validated);
        if (minutes <= 0) throw ApiException.badRequest(message);
        return minutes;
    }

    private double hoursFromMinutes(int minutes) {
        return round2(Math.max(0, minutes) / 60.0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double requirePositiveHours(Double value, String message) {
        if (value == null || !Double.isFinite(value) || value <= 0.00001) {
            throw ApiException.badRequest(message);
        }
        if (value > 100.0) {
            throw ApiException.badRequest("Одна запись не может быть больше 100 часов");
        }
        return round2(value);
    }

    private void assertPeriodOpen(AppUser user, LocalDate date) {
        if (date == null) return;
        LocalDate month = YearMonth.from(date).atDay(1);
        if (accountingPeriods.findByOwnerAndPeriodMonth(user, month)
                .map(TimeAccountingPeriod::isClosed).orElse(false)) {
            throw ApiException.conflict("PERIOD_CLOSED",
                    "Расчётный период " + YearMonth.from(date) + " закрыт. Добавь корректировку или сначала открой период.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String fmt(double value) {
        String s = String.valueOf(round2(value));
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }


    private record AccountProjection(
            List<OvertimeCreditRowDto> creditRows,
            List<OvertimeUsageDto> usageRows
    ) {}

    private record DailyInstantSegment(
            LocalDate date,
            Instant start,
            Instant end,
            int minutes
    ) {}

    private record CreditProjectionSlice(
            LocalDate date,
            Instant start,
            Instant end,
            int minutes,
            boolean exact
    ) {}

    private record AllocationFragment(
            OvertimeAllocation allocation,
            LocalDate date,
            Instant start,
            Instant end,
            int minutes,
            boolean exact
    ) {}

    private record AllocationPartPosition(int index, int count) {}

    private record CalculatedCredit(
            String timeRange,
            double hours,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            int breakMinutes,
            double plannedHours,
            boolean calculated
    ) {}

    private record RawSegment(
            LocalDate workDate,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Instant startInstant,
            Instant endInstant
    ) {}

    private record CreditSegment(
            LocalDate workDate,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            int breakMinutes,
            double plannedHours,
            double hours
    ) {}
}

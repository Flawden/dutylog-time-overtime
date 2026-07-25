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
import ru.daniil.shifts.dto.Dtos.OvertimeCreditRowDto;
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
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final SecurityEventLogger securityEvents;
    private final UserTimeService userTimeService;

    public OvertimeService(DayEntryRepository days,
                           DayEntryService dayEntryService,
                           OvertimeCreditRepository credits,
                           OvertimeUsageRepository usages,
                           OvertimeAllocationRepository allocations,
                           SecurityEventLogger securityEvents,
                           UserTimeService userTimeService) {
        this.days = days;
        this.dayEntryService = dayEntryService;
        this.credits = credits;
        this.usages = usages;
        this.allocations = allocations;
        this.securityEvents = securityEvents;
        this.userTimeService = userTimeService;
    }

    /**
     * Старый быстрый отчёт по day_entries оставлен для обратной совместимости.
     * Полноценная бухгалтерия часов живёт в account().
     */
    @Transactional(readOnly = true)
    public OvertimeSummaryDto summary(AppUser user, LocalDate from, LocalDate to) {
        List<DayEntry> entries = entries(user, from, to);
        double overtime = entries.stream().mapToDouble(DayEntry::getOvertimeHours).sum();
        double timeOff = entries.stream().mapToDouble(DayEntry::getTimeOffHours).sum();
        return new OvertimeSummaryDto(from.toString(), to.toString(), round2(overtime), round2(timeOff), round2(overtime - timeOff));
    }

    /** Старый журнал по day_entries оставлен для совместимости с v10 API. */
    @Transactional(readOnly = true)
    public List<OvertimeLedgerItemDto> ledger(AppUser user, LocalDate from, LocalDate to) {
        return entries(user, from, to).stream()
                .filter(e -> Math.abs(e.getOvertimeHours()) > 0.00001 || Math.abs(e.getTimeOffHours()) > 0.00001)
                .map(OvertimeLedgerItemDto::from)
                .toList();
    }

    /**
     * Полная бухгалтерия переработок. Не ограничивается текущим месяцем:
     * начисления из мая могут быть списаны в августе.
     */
    @Transactional(readOnly = true)
    public OvertimeAccountDto account(AppUser user) {
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        List<OvertimeAllocation> allocationList = allocations.findAllByOwner(user);

        Map<Long, List<OvertimeAllocation>> byCredit = allocationList.stream()
                .collect(Collectors.groupingBy(a -> a.getCredit().getId()));
        Map<Long, List<OvertimeAllocation>> byUsage = allocationList.stream()
                .collect(Collectors.groupingBy(a -> a.getUsage().getId()));

        List<OvertimeCreditRowDto> creditRows = creditList.stream()
                .map(c -> creditRow(user, c, byCredit.getOrDefault(c.getId(), List.of())))
                .toList();

        List<OvertimeUsageDto> usageRows = usageList.stream()
                .map(u -> usageRow(user, u, byUsage.getOrDefault(u.getId(), List.of())))
                .toList();

        int earnedMinutes = creditList.stream().mapToInt(OvertimeCredit::getCreditedMinutes).sum();
        int usedMinutes = usageList.stream().mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        return new OvertimeAccountDto(hoursFromMinutes(earnedMinutes), hoursFromMinutes(usedMinutes),
                hoursFromMinutes(earnedMinutes - usedMinutes), creditRows, usageRows);
    }

    /**
     * Страничный ответ для таблицы переработок: итог аккаунта считается полностью,
     * но клиент получает только текущую страницу строк журнала.
     */
    @Transactional(readOnly = true)
    public OvertimeAccountPageDto accountPage(AppUser user, String from, String to, String status, String q, int page, int size) {
        OvertimeAccountDto account = account(user);
        List<OvertimeCreditRowDto> filtered = filterCreditRows(account.credits(), from, to, status, q);
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        return new OvertimeAccountPageDto(
                account.totalEarnedHours(),
                account.totalUsedHours(),
                account.balanceHours(),
                PageDto.of(pageSlice(filtered, safePage, safeSize), safePage, safeSize, filtered.size())
        );
    }

    /**
     * Экспорт журнала переработок в CSV. Фильтры совпадают с таблицей на фронте.
     */
    @Transactional(readOnly = true)
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
                    value(row.timeRange()),
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
    @Transactional(readOnly = true)
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
                    .append("<td>").append(escHtml(value(row.timeRange()))).append("</td>")
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

    @Transactional
    public OvertimeAccountDto createCredit(AppUser user, OvertimeCreditCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }

        CalculatedCredit calculated = calculateCredit(user, req);
        if (!calculated.calculated()) {
            LocalDate date = dayEntryService.parseDate(req.date(), "Дата переработки должна быть в формате yyyy-MM-dd");
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
        int requestedMinutes = requirePositiveMinutes(req.hours(), "Укажи часы списания больше 0");

        OvertimeUsage usage = new OvertimeUsage(user, date, hoursFromMinutes(requestedMinutes), normalize(req.reason()));
        usage.setRequestedMinutes(requestedMinutes);
        usages.save(usage);
        rebuildAllAllocations(user);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto updateCredit(AppUser user, long id, OvertimeCreditUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        OvertimeCredit credit = requireOwnedCredit(user, id);
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
        OvertimeUsage usage = requireOwnedUsage(user, id);

        LocalDate date = hasText(req.date())
                ? dayEntryService.parseDate(req.date(), "Дата списания должна быть в формате yyyy-MM-dd")
                : usage.getUsageDate();
        int requestedMinutes = req.hours() != null
                ? requirePositiveMinutes(req.hours(), "Укажи часы списания больше 0")
                : usage.getRequestedMinutes();
        String reason = req.reason() != null ? normalize(req.reason()) : usage.getReason();

        usage.setUsageDate(date);
        usage.setRequestedMinutes(requestedMinutes);
        usage.setReason(reason);
        usages.save(usage);
        rebuildAllAllocations(user);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteCredit(AppUser user, long id) {
        OvertimeCredit credit = requireOwnedCredit(user, id);
        double used = hoursFromMinutes(allocations.findByCredit(credit).stream().mapToInt(OvertimeAllocation::getAllocatedMinutes).sum());
        if (used > 0.00001) {
            throw ApiException.badRequest("Нельзя удалить начисление, из которого уже списано " + fmt(used) + " ч. Сначала удали соответствующее списание.");
        }
        credits.delete(credit);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteUsage(AppUser user, long id) {
        OvertimeUsage usage = requireOwnedUsage(user, id);
        allocations.deleteByUsage(usage);
        usages.delete(usage);
        usages.flush();
        rebuildAllAllocations(user);
        return account(user);
    }

    /**
     * Rebuilds the complete FIFO ledger in deterministic date/id order.
     * This makes create, edit and delete symmetric: cancelling a time-off restores
     * the exact source minutes and all later usages return to their original FIFO order.
     */
    private void rebuildAllAllocations(AppUser user) {
        allocations.deleteAllByOwner(user);
        allocations.flush();

        // Preserve the historical FIFO contract: work date first, insertion id second.
        // Exact instants explain which minutes were consumed inside a credit, but a
        // partial legacy migration must never reorder same-day source credits.
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);

        int availableMinutes = creditList.stream().mapToInt(OvertimeCredit::getCreditedMinutes).sum();
        int requestedMinutes = usageList.stream().mapToInt(OvertimeUsage::getRequestedMinutes).sum();
        if (requestedMinutes > availableMinutes) {
            throw ApiException.badRequest("Недостаточно переработки: доступно "
                    + fmt(hoursFromMinutes(availableMinutes)) + " ч, списать хочешь "
                    + fmt(hoursFromMinutes(requestedMinutes)) + " ч");
        }

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
                OvertimeAllocation allocation = new OvertimeAllocation(credit, usage, take);
                applyAllocationInterval(allocation, credit, consumed, take);
                allocations.save(allocation);
                consumedByCredit.put(credit.getId(), consumed + take);
                left -= take;
                if (consumed + take >= credit.getCreditedMinutes()) creditIndex++;
            }
            if (left > 0) {
                throw ApiException.badRequest("Не удалось распределить " + left + " мин переработки по FIFO");
            }
        }
        allocations.flush();
    }

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

    private OvertimeCreditRowDto creditRow(AppUser user, OvertimeCredit credit, List<OvertimeAllocation> allocationList) {
        List<OvertimeAllocation> sorted = allocationList.stream()
                .sorted(Comparator.comparing((OvertimeAllocation a) -> a.getUsage().getUsageDate())
                        .thenComparing(OvertimeAllocation::getId))
                .toList();
        int usedMinutes = sorted.stream().mapToInt(OvertimeAllocation::getAllocatedMinutes).sum();
        List<OvertimeUsageRefDto> usageRefs = sorted.stream()
                .map(a -> new OvertimeUsageRefDto(
                        a.getUsage().getId(),
                        a.getUsage().getUsageDate().toString(),
                        hoursFromMinutes(a.getAllocatedMinutes()),
                        a.getUsage().getReason(),
                        a.getAllocatedMinutes(),
                        instantText(a.getStartAtInstant()),
                        instantText(a.getEndAtInstant()),
                        displayLocal(user, a.getStartAtInstant()),
                        displayLocal(user, a.getEndAtInstant()),
                        a.getSourceTimezone(),
                        a.getStartAtInstant() != null && a.getEndAtInstant() != null,
                        a.isReconstructed()
                ))
                .toList();
        String displayStart = displayLocal(user, credit.getStartAtInstant());
        String displayEnd = displayLocal(user, credit.getEndAtInstant());
        String displayTimezone = credit.getStartAtInstant() == null ? null : userTimeService.displayZone(user).getId();
        return new OvertimeCreditRowDto(
                credit.getId(),
                credit.getWorkDate().toString(),
                credit.getTimeRange(),
                credit.getStartAt() == null ? null : credit.getStartAt().toString(),
                credit.getEndAt() == null ? null : credit.getEndAt().toString(),
                credit.getBreakMinutes(),
                round2(credit.getPlannedHours()),
                credit.isCalculated(),
                hoursFromMinutes(credit.getCreditedMinutes()),
                credit.getReason(),
                hoursFromMinutes(usedMinutes),
                hoursFromMinutes(credit.getCreditedMinutes() - usedMinutes),
                usageRefs,
                instantText(credit.getStartAtInstant()),
                instantText(credit.getEndAtInstant()),
                credit.getSourceTimezone(),
                displayStart,
                displayEnd,
                displayTimezone,
                credit.getCreditedMinutes(),
                instantText(credit.getCreditedStartAtInstant()),
                instantText(credit.getCreditedEndAtInstant()),
                displayLocal(user, credit.getCreditedStartAtInstant()),
                displayLocal(user, credit.getCreditedEndAtInstant()),
                credit.isMigratedFromLegacy(),
                credit.getCreditedStartAtInstant() == null || credit.getCreditedEndAtInstant() == null
        );
    }


    private OvertimeUsageDto usageRow(AppUser user, OvertimeUsage usage, List<OvertimeAllocation> allocationList) {
        List<OvertimeAllocationDto> refs = allocationList.stream()
                .sorted(Comparator.comparing((OvertimeAllocation a) -> a.getCredit().getWorkDate())
                        .thenComparing(a -> a.getCredit().getId()))
                .map(a -> new OvertimeAllocationDto(
                        a.getCredit().getId(),
                        a.getCredit().getWorkDate().toString(),
                        a.getCredit().getTimeRange(),
                        hoursFromMinutes(a.getAllocatedMinutes()),
                        a.getCredit().getReason(),
                        a.getAllocatedMinutes(),
                        instantText(a.getStartAtInstant()),
                        instantText(a.getEndAtInstant()),
                        displayLocal(user, a.getStartAtInstant()),
                        displayLocal(user, a.getEndAtInstant()),
                        a.getSourceTimezone(),
                        a.getStartAtInstant() != null && a.getEndAtInstant() != null,
                        a.isReconstructed()
                ))
                .toList();
        return new OvertimeUsageDto(usage.getId(), usage.getUsageDate().toString(),
                hoursFromMinutes(usage.getRequestedMinutes()), usage.getReason(), refs,
                usage.getRequestedMinutes());
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
        return (row.workedDate() + " " + value(row.timeRange()) + " "
                + value(row.displayStart()) + " " + value(row.displayEnd()) + " "
                + value(row.sourceTimezone()) + " " + value(row.displayTimezone()) + " "
                + fmt(row.hours()) + " " + value(row.reason()) + " " + usagesText(row)).toLowerCase();
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

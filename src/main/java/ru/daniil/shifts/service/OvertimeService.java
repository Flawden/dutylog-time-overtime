package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public OvertimeService(DayEntryRepository days,
                           DayEntryService dayEntryService,
                           OvertimeCreditRepository credits,
                           OvertimeUsageRepository usages,
                           OvertimeAllocationRepository allocations,
                           SecurityEventLogger securityEvents) {
        this.days = days;
        this.dayEntryService = dayEntryService;
        this.credits = credits;
        this.usages = usages;
        this.allocations = allocations;
        this.securityEvents = securityEvents;
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
                .map(c -> creditRow(c, byCredit.getOrDefault(c.getId(), List.of())))
                .toList();

        List<OvertimeUsageDto> usageRows = usageList.stream()
                .map(u -> usageRow(u, byUsage.getOrDefault(u.getId(), List.of())))
                .toList();

        double earned = creditList.stream().mapToDouble(OvertimeCredit::getHours).sum();
        double used = usageList.stream().mapToDouble(OvertimeUsage::getHours).sum();
        return new OvertimeAccountDto(round2(earned), round2(used), round2(earned - used), creditRows, usageRows);
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

        CalculatedCredit calculated = calculateCredit(req);
        if (!calculated.calculated()) {
            LocalDate date = dayEntryService.parseDate(req.date(), "Дата переработки должна быть в формате yyyy-MM-dd");
            credits.save(new OvertimeCredit(
                    user,
                    date,
                    normalize(calculated.timeRange()),
                    calculated.hours(),
                    normalize(req.reason())
            ));
            return account(user);
        }

        List<CreditSegment> segments = splitCalculatedCredit(calculated);
        if (segments.isEmpty()) {
            throw ApiException.badRequest("После разбиения по датам переработка получилась 0 или меньше");
        }

        for (CreditSegment segment : segments) {
            ensureNoOvertimeOverlap(user, segment.startAt(), segment.endAt(), null);
        }

        String reason = normalize(req.reason());
        for (CreditSegment segment : segments) {
            credits.save(new OvertimeCredit(
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
            ));
        }
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto createUsage(AppUser user, OvertimeUsageCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate date = dayEntryService.parseDate(req.date(), "Дата списания должна быть в формате yyyy-MM-dd");
        double requested = requirePositiveHours(req.hours(), "Укажи часы списания больше 0");

        OvertimeUsage usage = usages.save(new OvertimeUsage(user, date, requested, normalize(req.reason())));
        allocateUsageFifo(user, usage, requested);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto updateCredit(AppUser user, long id, OvertimeCreditUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        OvertimeCredit credit = requireOwnedCredit(user, id);
        double used = allocations.sumHoursByCredit(credit);

        OvertimeCreditCreateRequest normalized = normalizeCreditUpdateRequest(credit, req);
        CalculatedCredit calculated = calculateCredit(normalized);
        String reason = req.reason() != null ? normalize(req.reason()) : credit.getReason();

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
            credit.setBreakMinutes(0);
            credit.setPlannedHours(0.0);
            credit.setCalculated(false);
            credit.setHours(calculated.hours());
            credit.setReason(reason);
            credits.save(credit);
            return account(user);
        }

        List<CreditSegment> segments = splitCalculatedCredit(calculated);
        if (segments.isEmpty()) {
            throw ApiException.badRequest("После разбиения по датам переработка получилась 0 или меньше");
        }

        for (CreditSegment segment : segments) {
            ensureNoOvertimeOverlap(user, segment.startAt(), segment.endAt(), credit.getId());
        }

        if (segments.size() > 1) {
            if (used > 0.00001) {
                throw ApiException.badRequest("Нельзя заменить уже использованное начисление на несколько строк. Сначала удали списания, которые его используют.");
            }
            credits.delete(credit);
            for (CreditSegment segment : segments) {
                credits.save(new OvertimeCredit(
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
                ));
            }
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
        credit.setBreakMinutes(segment.breakMinutes());
        credit.setPlannedHours(segment.plannedHours());
        credit.setCalculated(true);
        credit.setHours(segment.hours());
        credit.setReason(reason);
        credits.save(credit);
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
        double hours = req.hours() != null
                ? requirePositiveHours(req.hours(), "Укажи часы списания больше 0")
                : usage.getHours();
        String reason = req.reason() != null ? normalize(req.reason()) : usage.getReason();

        allocations.deleteByUsage(usage);
        allocations.flush();
        usage.setUsageDate(date);
        usage.setHours(hours);
        usage.setReason(reason);
        usages.save(usage);
        allocateUsageFifo(user, usage, hours);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteCredit(AppUser user, long id) {
        OvertimeCredit credit = requireOwnedCredit(user, id);
        double used = allocations.sumHoursByCredit(credit);
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
        return account(user);
    }

    private void allocateUsageFifo(AppUser user, OvertimeUsage usage, double requested) {
        List<OvertimeCredit> creditList = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        Map<Long, Double> usedByCredit = allocations.findAllByOwner(user).stream()
                .collect(Collectors.groupingBy(a -> a.getCredit().getId(), Collectors.summingDouble(OvertimeAllocation::getHours)));

        double available = creditList.stream()
                .mapToDouble(c -> c.getHours() - usedByCredit.getOrDefault(c.getId(), 0.0))
                .filter(v -> v > 0.00001)
                .sum();

        if (requested - available > 0.00001) {
            throw ApiException.badRequest("Недостаточно переработки: доступно " + fmt(available) + " ч, списать хочешь " + fmt(requested) + " ч");
        }

        double left = requested;
        for (OvertimeCredit credit : creditList) {
            if (left <= 0.00001) break;
            double alreadyUsed = usedByCredit.getOrDefault(credit.getId(), 0.0);
            double remain = credit.getHours() - alreadyUsed;
            if (remain <= 0.00001) continue;
            double take = round2(Math.min(remain, left));
            if (take <= 0.00001) continue;
            allocations.save(new OvertimeAllocation(credit, usage, take));
            left = round2(left - take);
        }
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

    private OvertimeCreditRowDto creditRow(OvertimeCredit credit, List<OvertimeAllocation> allocationList) {
        List<OvertimeAllocation> sorted = allocationList.stream()
                .sorted(Comparator.comparing((OvertimeAllocation a) -> a.getUsage().getUsageDate()).thenComparing(OvertimeAllocation::getId))
                .toList();
        double used = sorted.stream().mapToDouble(OvertimeAllocation::getHours).sum();
        List<OvertimeUsageRefDto> usageRefs = sorted.stream()
                .map(a -> new OvertimeUsageRefDto(
                        a.getUsage().getId(),
                        a.getUsage().getUsageDate().toString(),
                        round2(a.getHours()),
                        a.getUsage().getReason()
                ))
                .toList();
        return new OvertimeCreditRowDto(
                credit.getId(),
                credit.getWorkDate().toString(),
                credit.getTimeRange(),
                credit.getStartAt() == null ? null : credit.getStartAt().toString(),
                credit.getEndAt() == null ? null : credit.getEndAt().toString(),
                credit.getBreakMinutes(),
                round2(credit.getPlannedHours()),
                credit.isCalculated(),
                round2(credit.getHours()),
                credit.getReason(),
                round2(used),
                round2(credit.getHours() - used),
                usageRefs
        );
    }

    private OvertimeUsageDto usageRow(OvertimeUsage usage, List<OvertimeAllocation> allocationList) {
        List<OvertimeAllocationDto> refs = allocationList.stream()
                .sorted(Comparator.comparing((OvertimeAllocation a) -> a.getCredit().getWorkDate()).thenComparing(a -> a.getCredit().getId()))
                .map(a -> new OvertimeAllocationDto(
                        a.getCredit().getId(),
                        a.getCredit().getWorkDate().toString(),
                        a.getCredit().getTimeRange(),
                        round2(a.getHours()),
                        a.getCredit().getReason()
                ))
                .toList();
        return new OvertimeUsageDto(usage.getId(), usage.getUsageDate().toString(), round2(usage.getHours()), usage.getReason(), refs);
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
        return (row.workedDate() + " " + value(row.timeRange()) + " " + fmt(row.hours()) + " "
                + value(row.reason()) + " " + usagesText(row)).toLowerCase();
    }

    private String usagesText(OvertimeCreditRowDto row) {
        if (row.usages() == null || row.usages().isEmpty()) {
            return "не списывалось";
        }
        return row.usages().stream()
                .map(u -> u.usageDate() + ": " + fmt(u.hours()) + " ч" + (hasText(u.reason()) ? " — " + u.reason() : ""))
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

    private CalculatedCredit calculateCredit(OvertimeCreditCreateRequest req) {
        boolean hasStart = hasText(req.startDateTime());
        boolean hasEnd = hasText(req.endDateTime());
        if (hasStart || hasEnd) {
            if (!hasStart || !hasEnd) {
                throw ApiException.badRequest("Для автоподсчёта нужны и начало, и конец переработки");
            }
            LocalDateTime start = parseDateTime(req.startDateTime(), "Начало должно быть в формате yyyy-MM-ddTHH:mm");
            LocalDateTime end = parseDateTime(req.endDateTime(), "Конец должен быть в формате yyyy-MM-ddTHH:mm");
            if (!end.isAfter(start)) {
                throw ApiException.badRequest("Конец должен быть позже начала. Для ночной работы укажи следующий день в поле конца.");
            }
            long totalMinutes = Duration.between(start, end).toMinutes();
            int breakMinutes = sanitizeMinutes(req.breakMinutes());
            double plannedHours = sanitizePlannedHours(req.plannedHours());
            long plannedMinutes = Math.round(plannedHours * 60.0);
            long creditedMinutes = totalMinutes - breakMinutes - plannedMinutes;
            if (creditedMinutes <= 0) {
                throw ApiException.badRequest("После вычета обеда и плановых часов переработка получилась 0 или меньше");
            }
            double hours = requirePositiveHours(creditedMinutes / 60.0, "Переработка должна быть больше 0");
            String timeRange = hasText(req.timeRange()) ? req.timeRange().trim() : formatTimeRange(start, end);
            return new CalculatedCredit(timeRange, hours, start, end, breakMinutes, plannedHours, true);
        }

        double hours = requirePositiveHours(req.hours(), "Укажи часы переработки больше 0");
        return new CalculatedCredit(normalize(req.timeRange()), hours, null, null, 0, 0.0, false);
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
    private List<CreditSegment> splitCalculatedCredit(CalculatedCredit credit) {
        LocalDateTime start = credit.startAt();
        LocalDateTime end = credit.endAt();
        long totalMinutes = Duration.between(start, end).toMinutes();
        if (totalMinutes <= 0) {
            return List.of();
        }

        List<RawSegment> rawSegments = new ArrayList<>();
        long daysBetween = Duration.between(start.toLocalDate().atStartOfDay(), end.toLocalDate().atStartOfDay()).toDays();
        boolean oneExactDaySameTime = daysBetween == 1 && start.toLocalTime().equals(end.toLocalTime());

        if (oneExactDaySameTime) {
            LocalDateTime mid = start.plusMinutes(totalMinutes / 2);
            rawSegments.add(new RawSegment(start.toLocalDate(), start, mid));
            rawSegments.add(new RawSegment(end.toLocalDate(), mid, end));
        } else {
            LocalDateTime cursor = start;
            while (cursor.isBefore(end)) {
                LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
                LocalDateTime segmentEnd = nextMidnight.isBefore(end) ? nextMidnight : end;
                rawSegments.add(new RawSegment(cursor.toLocalDate(), cursor, segmentEnd));
                cursor = segmentEnd;
            }
        }

        int breakLeft = credit.breakMinutes();
        int plannedLeft = (int) Math.round(credit.plannedHours() * 60.0);
        List<CreditSegment> result = new ArrayList<>();

        for (RawSegment raw : rawSegments) {
            int rawMinutes = (int) Duration.between(raw.startAt(), raw.endAt()).toMinutes();
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
    private void ensureNoOvertimeOverlap(AppUser user, LocalDateTime start, LocalDateTime end, Long ignoreCreditId) {
        List<OvertimeCredit> overlaps = credits.findByOwnerAndStartAtLessThanAndEndAtGreaterThan(user, end, start).stream()
                .filter(c -> ignoreCreditId == null || !ignoreCreditId.equals(c.getId()))
                .toList();
        if (overlaps.isEmpty()) return;

        OvertimeCredit first = overlaps.get(0);
        throw ApiException.badRequest(
                "Этот период пересекается с уже записанной переработкой: "
                        + formatTimeRange(first.getStartAt(), first.getEndAt())
                        + " (" + fmt(first.getHours()) + " ч). Удали старую запись или измени время."
        );
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
            int breakMinutes,
            double plannedHours,
            boolean calculated
    ) {}

    private record RawSegment(
            LocalDate workDate,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {}

    private record CreditSegment(
            LocalDate workDate,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int breakMinutes,
            double plannedHours,
            double hours
    ) {}
}

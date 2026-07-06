package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAllocationDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditRowDto;
import ru.daniil.shifts.dto.Dtos.OvertimeLedgerItemDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageDto;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OvertimeService {
    private final DayEntryRepository days;
    private final DayEntryService dayEntryService;
    private final OvertimeCreditRepository credits;
    private final OvertimeUsageRepository usages;
    private final OvertimeAllocationRepository allocations;

    public OvertimeService(DayEntryRepository days,
                           DayEntryService dayEntryService,
                           OvertimeCreditRepository credits,
                           OvertimeUsageRepository usages,
                           OvertimeAllocationRepository allocations) {
        this.days = days;
        this.dayEntryService = dayEntryService;
        this.credits = credits;
        this.usages = usages;
        this.allocations = allocations;
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

    @Transactional
    public OvertimeAccountDto createCredit(AppUser user, OvertimeCreditCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate date = dayEntryService.parseDate(req.date(), "Дата переработки должна быть в формате yyyy-MM-dd");
        double hours = requirePositiveHours(req.hours(), "Укажи часы переработки больше 0");
        credits.save(new OvertimeCredit(user, date, normalize(req.timeRange()), hours, normalize(req.reason())));
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto createUsage(AppUser user, OvertimeUsageCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate date = dayEntryService.parseDate(req.date(), "Дата списания должна быть в формате yyyy-MM-dd");
        double requested = requirePositiveHours(req.hours(), "Укажи часы списания больше 0");

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

        OvertimeUsage usage = usages.save(new OvertimeUsage(user, date, requested, normalize(req.reason())));
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

        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteCredit(AppUser user, long id) {
        OvertimeCredit credit = credits.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Начисление переработки не найдено"));
        double used = allocations.sumHoursByCredit(credit);
        if (used > 0.00001) {
            throw ApiException.badRequest("Нельзя удалить начисление, из которого уже списано " + fmt(used) + " ч. Сначала удали соответствующее списание.");
        }
        credits.delete(credit);
        return account(user);
    }

    @Transactional
    public OvertimeAccountDto deleteUsage(AppUser user, long id) {
        OvertimeUsage usage = usages.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Списание отгула не найдено"));
        allocations.deleteByUsage(usage);
        usages.delete(usage);
        return account(user);
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
}

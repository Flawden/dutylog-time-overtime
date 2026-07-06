package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeLedgerItemDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class OvertimeService {
    private final DayEntryRepository days;
    private final DayEntryService dayEntryService;

    public OvertimeService(DayEntryRepository days, DayEntryService dayEntryService) {
        this.days = days;
        this.dayEntryService = dayEntryService;
    }

    @Transactional(readOnly = true)
    public OvertimeSummaryDto summary(AppUser user, LocalDate from, LocalDate to) {
        List<DayEntry> entries = entries(user, from, to);
        double overtime = entries.stream().mapToDouble(DayEntry::getOvertimeHours).sum();
        double timeOff = entries.stream().mapToDouble(DayEntry::getTimeOffHours).sum();
        return new OvertimeSummaryDto(from.toString(), to.toString(), round2(overtime), round2(timeOff), round2(overtime - timeOff));
    }

    @Transactional(readOnly = true)
    public List<OvertimeLedgerItemDto> ledger(AppUser user, LocalDate from, LocalDate to) {
        return entries(user, from, to).stream()
                .filter(e -> Math.abs(e.getOvertimeHours()) > 0.00001 || Math.abs(e.getTimeOffHours()) > 0.00001)
                .map(OvertimeLedgerItemDto::from)
                .toList();
    }

    private List<DayEntry> entries(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        return days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

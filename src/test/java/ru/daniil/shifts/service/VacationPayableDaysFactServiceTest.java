package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AbsencePeriodRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VacationPayableDaysFactServiceTest {
    private static final long PERIOD_ID = 77L;
    private AbsencePeriodRepository absences;
    private LedgerIntegrityService ledgerIntegrity;
    private ProductionCalendarService productionCalendar;
    private VacationPayableDaysFactService service;
    private AppUser user;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        absences = mock(AbsencePeriodRepository.class);
        ledgerIntegrity = mock(LedgerIntegrityService.class);
        productionCalendar = mock(ProductionCalendarService.class);
        service = new VacationPayableDaysFactService(absences, ledgerIntegrity, productionCalendar);
        user = mock(AppUser.class);
        owner = mock(AppUser.class);
        when(user.getId()).thenReturn(10L);
        when(owner.getId()).thenReturn(10L);
        when(productionCalendar.resolvedDay(eq(user), any(LocalDate.class)))
                .thenAnswer(invocation -> day(invocation.getArgument(1), "NORMAL"));
    }

    @Test
    void approvedAnnualVacationProducesExactInclusivePayableSpan() {
        LocalDate from = LocalDate.of(2026, 8, 17);
        LocalDate to = LocalDate.of(2026, 8, 21);
        stub(canonical(from, to, "APPROVED", "FULL_DAY"), true);
        var result = service.resolve(user, from, PERIOD_ID);
        assertTrue(result.ready());
        assertEquals(5, result.physicalCalendarDays());
        assertEquals(5, result.payableCalendarDays());
        assertEquals(0, result.excludedHolidayCalendarDays());
        assertEquals(List.of(from, from.plusDays(1), from.plusDays(2), from.plusDays(3), to), result.payableDates());
    }

    @Test
    void completedAnnualVacationIsPostedAuthority() {
        LocalDate date = LocalDate.of(2026, 8, 31);
        stub(canonical(date, date, "COMPLETED", "FULL_DAY"), true);
        var result = service.resolve(user, date, PERIOD_ID);
        assertTrue(result.ready());
        assertEquals("COMPLETED", result.status());
        assertEquals(1, result.payableCalendarDays());
    }

    @Test
    void weekendDatesRemainPayableCalendarVacationDays() {
        LocalDate saturday = LocalDate.of(2026, 8, 29);
        LocalDate sunday = LocalDate.of(2026, 8, 30);
        stub(canonical(saturday, sunday, "APPROVED", "FULL_DAY"), true);
        var result = service.resolve(user, saturday, PERIOD_ID);
        assertEquals(List.of(saturday, sunday), result.payableDates());
        assertTrue(result.excludedHolidayDates().isEmpty());
    }

    @Test
    void federalHolidayIsExcludedWithoutProductionCalendarOverride() {
        LocalDate from = LocalDate.of(2026, 2, 22);
        LocalDate holiday = LocalDate.of(2026, 2, 23);
        LocalDate to = LocalDate.of(2026, 2, 24);
        stub(canonical(from, to, "APPROVED", "FULL_DAY"), true);
        var result = service.resolve(user, from, PERIOD_ID);
        assertEquals(List.of(from, to), result.payableDates());
        assertEquals(List.of(holiday), result.excludedHolidayDates());
        verify(productionCalendar, never()).resolvedDay(user, holiday);
    }

    @Test
    void configuredRegionalHolidayIsExcluded() {
        LocalDate from = LocalDate.of(2026, 6, 14);
        LocalDate regional = LocalDate.of(2026, 6, 15);
        LocalDate to = LocalDate.of(2026, 6, 16);
        stub(canonical(from, to, "APPROVED", "FULL_DAY"), true);
        when(productionCalendar.resolvedDay(user, regional)).thenReturn(day(regional, "HOLIDAY"));
        var result = service.resolve(user, from, PERIOD_ID);
        assertEquals(List.of(from, to), result.payableDates());
        assertEquals(List.of(regional), result.excludedHolidayDates());
    }

    @Test
    void transferredDayOffRemainsPayable() {
        LocalDate date = LocalDate.of(2026, 5, 4);
        stub(canonical(date, date, "APPROVED", "FULL_DAY"), true);
        when(productionCalendar.resolvedDay(user, date)).thenReturn(day(date, "TRANSFERRED_DAY_OFF"));
        var result = service.resolve(user, date, PERIOD_ID);
        assertEquals(1, result.payableCalendarDays());
        assertEquals(0, result.excludedHolidayCalendarDays());
    }

    @Test
    void entirelyFederalHolidaySpanIsProvenReadyZero() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        stub(canonical(date, date, "APPROVED", "FULL_DAY"), true);
        var result = service.resolve(user, date, PERIOD_ID);
        assertTrue(result.ready());
        assertEquals(1, result.physicalCalendarDays());
        assertEquals(0, result.payableCalendarDays());
        assertEquals(1, result.excludedHolidayCalendarDays());
        assertTrue(result.payableDates().isEmpty());
    }

    @Test
    void readyResultCarriesOneHolidayFactPerPhysicalDate() {
        LocalDate from = LocalDate.of(2026, 11, 3);
        LocalDate holiday = LocalDate.of(2026, 11, 4);
        LocalDate to = LocalDate.of(2026, 11, 5);
        stub(canonical(from, to, "APPROVED", "FULL_DAY"), true);
        var result = service.resolve(user, from, PERIOD_ID);
        assertEquals(result.physicalSpanDates().size(), result.holidayFacts().size());
        assertEquals(holiday, result.holidayFacts().get(1).date());
        assertTrue(result.holidayFacts().get(1).nonWorkingHoliday());
    }

    @Test
    void nullRequestedIdBlocksBeforeRepository() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        var result = service.resolve(user, event, null);
        assertFalse(result.ready());
        assertEquals(VacationPayableDaysFactService.ABSENCE_MISSING, result.blockingReason());
        verifyNoInteractions(absences, ledgerIntegrity, productionCalendar);
    }

    @Test
    void missingAbsenceBlocksWithoutPartialFact() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.empty());
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.ABSENCE_MISSING);
        verifyNoInteractions(ledgerIntegrity, productionCalendar);
    }

    @Test
    void ownershipMismatchBlocksBeforeStatusOrCalendar() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        when(owner.getId()).thenReturn(99L);
        AbsencePeriod period = canonical(event, event, "APPROVED", "FULL_DAY");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.OWNERSHIP_MISMATCH);
        verifyNoInteractions(ledgerIntegrity, productionCalendar);
    }

    @Test
    void invalidPhysicalSpanBlocksFailClosed() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event.minusDays(1), "APPROVED", "FULL_DAY");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.SPAN_INVALID);
        verifyNoInteractions(ledgerIntegrity, productionCalendar);
    }

    @Test
    void eventDateMustEqualExactVacationStart() {
        LocalDate from = LocalDate.of(2026, 8, 30);
        AbsencePeriod period = canonical(from, from.plusDays(2), "APPROVED", "FULL_DAY");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, from.plusDays(1), PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.EVENT_IDENTITY_MISMATCH);
        verifyNoInteractions(ledgerIntegrity, productionCalendar);
    }

    @Test
    void nonVacationSystemCodeBlocks() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event, "APPROVED", "FULL_DAY");
        period.getType().setSystemCode("SICK");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.ANNUAL_PAID_VACATION_REQUIRED);
    }

    @Test
    void nonVacationBalancePolicyBlocks() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event, "APPROVED", "FULL_DAY");
        period.getType().setBalancePolicy("NONE");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.ANNUAL_PAID_VACATION_REQUIRED);
    }

    @Test
    void nonVacationCompensationPolicyBlocks() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event, "APPROVED", "FULL_DAY");
        period.setCompensationPolicy("NONE");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.ANNUAL_PAID_VACATION_REQUIRED);
    }

    @Test
    void partialCoverageBlocksWithoutFractionalDayInference() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event, "APPROVED", "PARTIAL");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.FULL_DAY_REQUIRED);
        verifyNoInteractions(ledgerIntegrity, productionCalendar);
    }

    @Test
    void hoursOnlyCoverageBlocksWithoutCalendarDayInference() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        AbsencePeriod period = canonical(event, event, "APPROVED", "HOURS_ONLY");
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.FULL_DAY_REQUIRED);
    }

    @Test
    void plannedStatusIsNotPayableAuthority() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        stub(canonical(event, event, "PLANNED", "FULL_DAY"), false);
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.POSTED_STATUS_REQUIRED);
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void submittedStatusIsNotPayableAuthority() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        stub(canonical(event, event, "SUBMITTED", "FULL_DAY"), false);
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.POSTED_STATUS_REQUIRED);
    }

    @Test
    void unavailableHolidayClassificationBlocksWithoutPartialDates() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        stub(canonical(event, event, "APPROVED", "FULL_DAY"), true);
        when(productionCalendar.resolvedDay(user, event)).thenReturn(null);
        var result = service.resolve(user, event, PERIOD_ID);
        assertBlocked(result, VacationPayableDaysFactService.HOLIDAY_AUTHORITY_UNAVAILABLE);
    }

    @Test
    void blockedResultDerivedCountsAreZeroNotPartialEvidence() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.empty());
        var result = service.resolve(user, event, PERIOD_ID);
        assertEquals(0, result.physicalCalendarDays());
        assertEquals(0, result.payableCalendarDays());
        assertEquals(0, result.excludedHolidayCalendarDays());
    }

    @Test
    void readyResultRejectsMismatchedRequestedAbsenceIdentity() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        var fact = new AnnualPaidVacationHolidayPolicy.HolidayFact(
                event, false, "AUTH", "NORMAL");
        assertThrows(IllegalArgumentException.class, () ->
                new VacationPayableDaysFactService.Resolution(
                        event, java.time.YearMonth.from(event), 1L, true,
                        null, null, 2L, event, event, "APPROVED",
                        List.of(event), List.of(event), List.of(), List.of(fact)));
    }

    @Test
    void blockedResultRejectsPartialVacationIdentity() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        assertThrows(IllegalArgumentException.class, () ->
                new VacationPayableDaysFactService.Resolution(
                        event, java.time.YearMonth.from(event), PERIOD_ID, false,
                        "BLOCK", "blocked", null, event, null, null,
                        null, null, null, null));
    }

    @Test
    void readyResultRejectsOverlappingPartition() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        var fact = new AnnualPaidVacationHolidayPolicy.HolidayFact(
                event, true, "AUTH", "HOLIDAY");
        assertThrows(IllegalArgumentException.class, () ->
                VacationPayableDaysFactService.Resolution.ready(
                        event, PERIOD_ID, event, event, "APPROVED",
                        List.of(event), List.of(event), List.of(event), List.of(fact)));
    }

    @Test
    void readyResultRejectsPartitionThatDoesNotCoverPhysicalSpan() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        var fact = new AnnualPaidVacationHolidayPolicy.HolidayFact(
                event, false, "AUTH", "NORMAL");
        assertThrows(IllegalArgumentException.class, () ->
                VacationPayableDaysFactService.Resolution.ready(
                        event, PERIOD_ID, event, event, "APPROVED",
                        List.of(event), List.of(), List.of(), List.of(fact)));
    }

    @Test
    void readyResultRejectsMissingHolidayProvenance() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        assertThrows(IllegalArgumentException.class, () ->
                VacationPayableDaysFactService.Resolution.ready(
                        event, PERIOD_ID, event, event, "APPROVED",
                        List.of(event), List.of(event), List.of(), List.of()));
    }

    @Test
    void readyResultRejectsHolidayProvenanceContradiction() {
        LocalDate event = LocalDate.of(2026, 8, 31);
        var holidayFact = new AnnualPaidVacationHolidayPolicy.HolidayFact(
                event, true, "AUTH", "HOLIDAY");
        assertThrows(IllegalArgumentException.class, () ->
                VacationPayableDaysFactService.Resolution.ready(
                        event, PERIOD_ID, event, event, "APPROVED",
                        List.of(event), List.of(event), List.of(), List.of(holidayFact)));
    }

    private void stub(AbsencePeriod period, boolean posted) {
        when(absences.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(ledgerIntegrity.posts(period.getStatus())).thenReturn(posted);
    }

    private AbsencePeriod canonical(LocalDate from, LocalDate to, String status, String coverage) {
        AbsenceType type = new AbsenceType(owner);
        type.setSystemCode("VACATION");
        type.setBalancePolicy("VACATION_DAYS");
        AbsencePeriod period = new AbsencePeriod(owner);
        period.setType(type);
        period.setStartDate(from);
        period.setEndDate(to);
        period.setStatus(status);
        period.setCoverage(coverage);
        period.setCompensationPolicy("VACATION_ALLOWANCE");
        return period;
    }

    private ProductionCalendarDayDto day(LocalDate date, String kind) {
        return new ProductionCalendarDayDto(
                date.toString(), kind, "NONE", null, "NONE", null,
                "NONE", null, false, 0, 0, 0);
    }

    private void assertBlocked(VacationPayableDaysFactService.Resolution result, String reason) {
        assertFalse(result.ready());
        assertEquals(reason, result.blockingReason());
        assertNull(result.absencePeriodId());
        assertNull(result.vacationFrom());
        assertNull(result.vacationTo());
        assertNull(result.physicalSpanDates());
        assertNull(result.payableDates());
        assertNull(result.excludedHolidayDates());
        assertNull(result.holidayFacts());
    }
}

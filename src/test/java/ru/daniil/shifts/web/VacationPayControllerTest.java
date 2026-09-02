package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import ru.daniil.shifts.dto.Dtos.VacationPayPreviewDto;
import ru.daniil.shifts.dto.Dtos.VacationPayPreviewRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AverageEarningsOrderedFallbackResolver;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.VacationPayApplicationService;
import ru.daniil.shifts.service.VacationPayOrchestrator;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationPayControllerTest {
    @Mock private CurrentUserService users;
    @Mock private ModuleService modules;
    @Mock private VacationPayApplicationService application;
    @Mock private AppUser user;
    @Mock private VacationPayApplicationService.Resolution resolution;

    private final Principal principal = () -> "vacation-http-user";
    private VacationPayController controller;

    @BeforeEach
    void setUp() {
        controller = new VacationPayController(users, modules, application);
    }

    @Test
    void readyPreviewUsesAuthenticatedPayrollBoundaryAndCanonicalApplicationInputs() {
        authenticate();
        List<YearMonth> proofs = List.of(YearMonth.of(2025, 11), YearMonth.of(2025, 12));
        stubIdentity(LocalDate.of(2026, 9, 15), 77L, YearMonth.of(2026, 8), proofs);
        when(resolution.ready()).thenReturn(true);
        when(resolution.selectedBasis()).thenReturn(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        when(resolution.currencyCode()).thenReturn("RUB");
        when(resolution.vacationPayMinor()).thenReturn(456_700L);
        when(resolution.payableCalendarDays()).thenReturn(14);
        when(application.resolve(
                user,
                LocalDate.of(2026, 9, 15),
                77L,
                YearMonth.of(2026, 8),
                proofs
        )).thenReturn(resolution);

        ResponseEntity<VacationPayPreviewDto> response = controller.preview(
                request("2026-09-15", 77L, "2026-08", List.of("2025-11", "2025-12")),
                principal
        );

        verify(users).requireUser(principal);
        verify(modules).requireEnabled(user, ModuleService.PAYROLL);
        verify(application).resolve(
                user,
                LocalDate.of(2026, 9, 15),
                77L,
                YearMonth.of(2026, 8),
                proofs
        );
        assertEquals("no-store", response.getHeaders().getCacheControl());

        VacationPayPreviewDto body = response.getBody();
        assertEquals("2026-09-15", body.eventDate());
        assertEquals("2026-09", body.eventMonth());
        assertEquals(77L, body.requestedAbsencePeriodId());
        assertEquals("2026-08", body.discoveryThroughMonth());
        assertEquals(List.of("2025-11", "2025-12"), body.provenNoPayrollMonths());
        assertTrue(body.ready());
        assertEquals("PRIMARY_REFERENCE_PERIOD", body.selectedBasis());
        assertNull(body.blockingStage());
        assertEquals("RUB", body.currencyCode());
        assertEquals(456_700L, body.vacationPayMinor());
        assertEquals(14, body.payableCalendarDays());
    }

    @Test
    void blockedPreviewKeepsFlatBlockerEnvelopeWithoutInventingMoney() {
        authenticate();
        stubIdentity(LocalDate.of(2026, 9, 15), 77L, YearMonth.of(2026, 8), List.of());
        when(resolution.ready()).thenReturn(false);
        when(resolution.blockingStage()).thenReturn(VacationPayOrchestrator.BlockingStage.DAILY_AUTHORITY);
        when(resolution.blockingReason()).thenReturn(
                "DUTYLOG_VACATION_PAY_ORCHESTRATOR_DAILY_AUTHORITY_BLOCKED"
        );
        when(resolution.upstreamBlockingReason()).thenReturn(
                "PP_540_ORDERED_FALLBACK_AUTHORITY_EVENT_IDENTITY_MISMATCH"
        );
        // Mockito's default answer returns zero for numeric wrapper accessors.
        // This blocker contract specifically proves that unavailable money
        // remains absent rather than being reinterpreted as a zero amount.
        when(resolution.vacationPayMinor()).thenReturn((Long) null);
        when(resolution.payableCalendarDays()).thenReturn(0);
        when(application.resolve(
                user,
                LocalDate.of(2026, 9, 15),
                77L,
                YearMonth.of(2026, 8),
                List.of()
        )).thenReturn(resolution);

        VacationPayPreviewDto body = controller.preview(
                request("2026-09-15", 77L, "2026-08", List.of()),
                principal
        ).getBody();

        assertFalse(body.ready());
        assertNull(body.selectedBasis());
        assertEquals("DAILY_AUTHORITY", body.blockingStage());
        assertEquals(
                "DUTYLOG_VACATION_PAY_ORCHESTRATOR_DAILY_AUTHORITY_BLOCKED",
                body.blockingReason()
        );
        assertEquals(
                "PP_540_ORDERED_FALLBACK_AUTHORITY_EVENT_IDENTITY_MISMATCH",
                body.upstreamBlockingReason()
        );
        assertNull(body.currencyCode());
        assertNull(body.vacationPayMinor());
        assertEquals(0, body.payableCalendarDays());
    }

    @Test
    void nullBodyFailsClosedAfterAuthenticationWithoutCallingApplication() {
        authenticate();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.preview(null, principal)
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verify(modules).requireEnabled(user, ModuleService.PAYROLL);
        verifyNoInteractions(application);
    }

    @Test
    void impossibleEventDateIsRejectedWithoutCallingApplication() {
        authenticate();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.preview(
                        request("2026-02-30", 1L, "2026-01", List.of()),
                        principal
                )
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verifyNoInteractions(application);
    }

    @Test
    void impossibleDiscoveryMonthIsRejectedWithoutCallingApplication() {
        authenticate();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.preview(
                        request("2026-09-15", 1L, "2026-13", List.of()),
                        principal
                )
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verifyNoInteractions(application);
    }

    @Test
    void impossibleProofMonthIsRejectedWithoutCallingApplication() {
        authenticate();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.preview(
                        request("2026-09-15", 1L, "2026-08", List.of("2025-12", "2026-00")),
                        principal
                )
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verifyNoInteractions(application);
    }

    @Test
    void explicitProofOrderAndDuplicatesAreForwardedWithoutHttpReinterpretation() {
        authenticate();
        List<YearMonth> expected = List.of(
                YearMonth.of(2025, 12),
                YearMonth.of(2025, 11),
                YearMonth.of(2025, 12)
        );
        stubIdentity(LocalDate.of(2026, 9, 15), null, YearMonth.of(2026, 8), expected);
        when(resolution.ready()).thenReturn(false);
        when(resolution.blockingStage()).thenReturn(
                VacationPayOrchestrator.BlockingStage.PAYABLE_DAYS_AUTHORITY
        );
        when(resolution.blockingReason()).thenReturn("PAYABLE_BLOCKED");
        when(resolution.payableCalendarDays()).thenReturn(0);
        when(application.resolve(
                user,
                LocalDate.of(2026, 9, 15),
                null,
                YearMonth.of(2026, 8),
                expected
        )).thenReturn(resolution);

        VacationPayPreviewDto body = controller.preview(
                request("2026-09-15", null, "2026-08", List.of("2025-12", "2025-11", "2025-12")),
                principal
        ).getBody();

        verify(application).resolve(
                user,
                LocalDate.of(2026, 9, 15),
                null,
                YearMonth.of(2026, 8),
                expected
        );
        assertEquals(List.of("2025-12", "2025-11", "2025-12"), body.provenNoPayrollMonths());
    }

    @Test
    void disabledPayrollModuleShortCircuitsBeforeApplication() {
        when(users.requireUser(principal)).thenReturn(user);
        doThrow(ApiException.forbidden("MODULE_DISABLED:payroll"))
                .when(modules)
                .requireEnabled(user, ModuleService.PAYROLL);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.preview(
                        request("2026-09-15", 1L, "2026-08", List.of()),
                        principal
                )
        );

        assertEquals("MODULE_DISABLED", ex.getCode());
        verifyNoInteractions(application);
    }

    private void authenticate() {
        when(users.requireUser(principal)).thenReturn(user);
    }

    private VacationPayPreviewRequest request(
            String eventDate,
            Long absencePeriodId,
            String discoveryThroughMonth,
            List<String> proofs
    ) {
        return new VacationPayPreviewRequest(
                eventDate,
                absencePeriodId,
                discoveryThroughMonth,
                proofs
        );
    }

    private void stubIdentity(
            LocalDate eventDate,
            Long absencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> proofs
    ) {
        when(resolution.eventDate()).thenReturn(eventDate);
        when(resolution.eventMonth()).thenReturn(YearMonth.from(eventDate));
        when(resolution.requestedAbsencePeriodId()).thenReturn(absencePeriodId);
        when(resolution.discoveryThroughMonth()).thenReturn(discoveryThroughMonth);
        when(resolution.provenNoPayrollMonths()).thenReturn(proofs);
    }
}

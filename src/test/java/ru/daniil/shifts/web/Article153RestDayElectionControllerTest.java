package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.Article153RestDayElectionAuthorityService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Article153RestDayElectionControllerTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void strictInputRejectsCoercionDuplicateUnknownMissingAndOwnerOverride() {
        for (String input : List.of(
                "{}",
                "{\"workDate\":\"2026-05-09\",\"sourceKind\":\"EXPLICIT\",\"sourceEntityId\":11,\"expectedSourceFingerprint\":\"" + HASH + "\",\"ownerId\":2}",
                "{\"workDate\":\"2026-05-09\",\"sourceKind\":\"EXPLICIT\",\"sourceEntityId\":11.5,\"expectedSourceFingerprint\":\"" + HASH + "\"}",
                "{\"workDate\":\"2026-05-09\",\"sourceKind\":\"EXPLICIT\",\"sourceEntityId\":\"11\",\"expectedSourceFingerprint\":\"" + HASH + "\"}",
                "{\"workDate\":\"2026-05-09\",\"sourceKind\":\"EXPLICIT\",\"sourceEntityId\":11,\"sourceEntityId\":12,\"expectedSourceFingerprint\":\"" + HASH + "\"}"
        )) {
            assertEquals("ARTICLE153_ELECTION_INPUT",
                    assertThrows(ApiException.class,
                            () -> Article153RestDayElectionController.decode(input)).getCode());
        }
    }

    @Test
    void workspaceIsOwnerScopedAndNoStore() {
        var users = mock(CurrentUserService.class);
        var modules = mock(ModuleService.class);
        var authority = mock(Article153RestDayElectionAuthorityService.class);
        var owner = mock(AppUser.class);
        Principal principal = () -> "alice";
        when(users.requireUser(principal)).thenReturn(owner);
        var expected = new Article153RestDayElectionAuthorityService.MonthlyWorkspace(
                "2026-05", true, List.of(), List.of());
        when(authority.workspace(owner, YearMonth.of(2026, 5))).thenReturn(expected);
        var controller = new Article153RestDayElectionController(users, modules, authority);

        var response = controller.workspace("2026-05", principal);
        assertSame(expected, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(modules).requireEnabled(owner, ModuleService.PAYROLL);
        verify(authority).workspace(owner, YearMonth.of(2026, 5));
    }

    @Test
    void electionRequiresExactMonthAndFreshFingerprintAndMapsConflict() {
        var users = mock(CurrentUserService.class);
        var modules = mock(ModuleService.class);
        var authority = mock(Article153RestDayElectionAuthorityService.class);
        var owner = mock(AppUser.class);
        Principal principal = () -> "alice";
        when(users.requireUser(principal)).thenReturn(owner);
        var controller = new Article153RestDayElectionController(users, modules, authority);
        String input = "{\"workDate\":\"2026-05-09\",\"sourceKind\":\"EXPLICIT\",\"sourceEntityId\":11,\"expectedSourceFingerprint\":\"" + HASH + "\"}";

        assertEquals("ARTICLE153_ELECTION_PERIOD",
                assertThrows(ApiException.class, () -> controller.elect("2026-04", input, principal)).getCode());
        verifyNoInteractions(authority);

        when(authority.elect(owner, LocalDate.of(2026, 5, 9), SourceKind.EXPLICIT, 11L, HASH))
                .thenThrow(new IllegalStateException("ARTICLE153_REST_DAY_ELECTION_STALE_SOURCE:EXPLICIT:11"));
        var error = assertThrows(ApiException.class,
                () -> controller.elect("2026-05", input, principal));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("ARTICLE153_ELECTION_BLOCKED", error.getCode());
        verify(modules, times(2)).requireEnabled(owner, ModuleService.PAYROLL);
    }
}

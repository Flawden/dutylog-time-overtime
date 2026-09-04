package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatutoryPublicHolidayAuthorityServiceTest {
    WorkJurisdictionHistoryService jurisdiction;
    StatutoryPublicHolidayAuthorityService service;
    AppUser owner;

    @BeforeEach
    void setUp() {
        jurisdiction =
                mock(WorkJurisdictionHistoryService.class);
        service =
                new StatutoryPublicHolidayAuthorityService(
                        jurisdiction
                );
        owner =
                new AppUser(
                        "statutory-holiday-owner",
                        "{noop}irrelevant"
                );
    }

    @Test
    void missingJurisdictionPropagatesFailClosed() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        String blocker =
                WorkJurisdictionHistoryService.JURISDICTION_FACT_MISSING
                        + ":"
                        + date;

        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        WorkJurisdictionHistoryService.Resolution.blocked(
                                date,
                                blocker
                        )
                );

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(blocker, result.blockingReason());
    }

    @Test
    void federalHolidayResolvesWithoutRegionalPolicy() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "RU",
                                null
                        )
                );

        var result = service.resolve(owner, date);

        assertTrue(result.ready());
        assertTrue(result.nonWorkingPublicHoliday());
        assertEquals(
                RuFederalStatutoryHolidayPolicy.HolidayCode.VICTORY_DAY,
                result.provenance().holidayCode()
        );
        assertEquals(
                RuFederalStatutoryHolidayPolicy.LEGAL_REGIME,
                result.provenance().legalRegime()
        );
    }

    @Test
    void federalHolidayResolvesEvenWhenRegionFactExists() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "RU",
                                "RU-KYA"
                        )
                );

        var result = service.resolve(owner, date);

        assertTrue(result.ready());
        assertEquals(
                "RU-KYA",
                result.provenance().regionCode()
        );
    }

    @Test
    void defensiveUnsupportedReadyJurisdictionFailsClosed() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "DE",
                                null
                        )
                );

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                StatutoryPublicHolidayAuthorityService.JURISDICTION_UNSUPPORTED
                        + ":DE",
                result.blockingReason()
        );
    }

    @Test
    void ordinaryDateWithoutRegionalAuthorityFailsClosed() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "RU",
                                null
                        )
                );

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                StatutoryPublicHolidayAuthorityService.REGIONAL_AUTHORITY_MISSING
                        + ":"
                        + date,
                result.blockingReason()
        );
    }

    @Test
    void ordinaryDateWithRegionFailsClosedUntilRegionalPolicyExists() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "RU",
                                "RU-KYA"
                        )
                );

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertEquals(
                StatutoryPublicHolidayAuthorityService.REGIONAL_POLICY_UNIMPLEMENTED
                        + ":RU-KYA:"
                        + date,
                result.blockingReason()
        );
    }

    @Test
    void transferredDayOffNeverMasqueradesAsFederalHoliday() {
        LocalDate date = LocalDate.of(2026, 1, 9);
        when(jurisdiction.resolveAt(owner, date))
                .thenReturn(
                        readyJurisdiction(
                                date,
                                "RU",
                                "RU-KYA"
                        )
                );

        var result = service.resolve(owner, date);

        assertFalse(result.ready());
        assertTrue(
                result.blockingReason()
                        .startsWith(
                                StatutoryPublicHolidayAuthorityService.REGIONAL_POLICY_UNIMPLEMENTED
                        )
        );
    }

    @Test
    void unsupportedLegalWindowAndResolutionInvariantsFailClosed() {
        LocalDate outside = LocalDate.of(2027, 1, 1);
        when(jurisdiction.resolveAt(owner, outside))
                .thenReturn(
                        readyJurisdiction(
                                outside,
                                "RU",
                                null
                        )
                );

        var unresolved = service.resolve(owner, outside);

        assertFalse(unresolved.ready());
        assertEquals(
                StatutoryPublicHolidayAuthorityService.LEGAL_WINDOW_UNSUPPORTED
                        + ":"
                        + outside,
                unresolved.blockingReason()
        );

        LocalDate date = LocalDate.of(2026, 5, 9);
        assertThrows(
                IllegalArgumentException.class,
                () -> new StatutoryPublicHolidayAuthorityService.Resolution(
                        date,
                        StatutoryPublicHolidayAuthorityService.Status.UNRESOLVED,
                        null,
                        null
                )
        );
    }

    private WorkJurisdictionHistoryService.Resolution readyJurisdiction(
            LocalDate date,
            String jurisdictionCode,
            String regionCode
    ) {
        return WorkJurisdictionHistoryService.Resolution.ready(
                date,
                new WorkJurisdictionHistoryService.JurisdictionFact(
                        91L,
                        LocalDate.of(2026, 1, 1),
                        jurisdictionCode,
                        regionCode
                )
        );
    }
}

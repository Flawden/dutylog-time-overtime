package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkJurisdictionTerm;
import ru.daniil.shifts.repo.WorkJurisdictionTermRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkJurisdictionHistoryServiceTest {
    WorkJurisdictionTermRepository terms;
    WorkJurisdictionHistoryService service;
    AppUser owner;
    LocalDate date;

    @BeforeEach
    void setUp() {
        terms = mock(WorkJurisdictionTermRepository.class);
        service = new WorkJurisdictionHistoryService(terms);

        owner = new AppUser(
                "jurisdiction-owner",
                "{noop}irrelevant"
        );
        owner.setLanguagePreference("ru");
        owner.setWorkTimezone("Europe/Moscow");

        date = LocalDate.of(2026, 9, 3);
    }

    @Test
    void missingTermFailsClosedWithoutTimezoneOrLanguageInference() {
        var result = service.resolveAt(owner, date);

        assertFalse(result.ready());
        assertEquals(
                WorkJurisdictionHistoryService.JURISDICTION_FACT_MISSING
                        + ":"
                        + date,
                result.blockingReason()
        );
    }

    @Test
    void persistedRuTermResolvesWithImmutableTermIdentity() {
        WorkJurisdictionTerm term =
                persisted(
                        new WorkJurisdictionTerm(
                                owner,
                                LocalDate.of(2025, 1, 1),
                                "RU",
                                null
                        ),
                        41L
                );

        when(
                terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        owner,
                        date
                )
        ).thenReturn(Optional.of(term));

        var result = service.resolveAt(owner, date);

        assertTrue(result.ready());
        assertEquals(41L, result.fact().termId());
        assertEquals("RU", result.fact().jurisdictionCode());
        assertNull(result.fact().regionCode());
    }

    @Test
    void regionFactIsPreservedWithoutChangingFederalJurisdiction() {
        WorkJurisdictionTerm term =
                persisted(
                        new WorkJurisdictionTerm(
                                owner,
                                LocalDate.of(2026, 1, 1),
                                "RU",
                                "RU-KYA"
                        ),
                        42L
                );

        when(
                terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        owner,
                        date
                )
        ).thenReturn(Optional.of(term));

        var result = service.resolveAt(owner, date);

        assertTrue(result.ready());
        assertEquals("RU-KYA", result.fact().regionCode());
    }

    @Test
    void unsupportedPersistedJurisdictionFailsClosed() {
        WorkJurisdictionTerm term =
                persisted(
                        new WorkJurisdictionTerm(
                                owner,
                                date,
                                "DE",
                                null
                        ),
                        43L
                );

        when(
                terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        owner,
                        date
                )
        ).thenReturn(Optional.of(term));

        var result = service.resolveAt(owner, date);

        assertFalse(result.ready());
        assertEquals(
                WorkJurisdictionHistoryService.JURISDICTION_UNSUPPORTED
                        + ":DE",
                result.blockingReason()
        );
    }

    @Test
    void upsertCanonicalizesSupportedCountryAndRegion() {
        AtomicLong ids = new AtomicLong(100L);

        when(
                terms.findByOwnerAndEffectiveFrom(
                        owner,
                        date
                )
        ).thenReturn(Optional.empty());

        when(
                terms.saveAndFlush(
                        any(WorkJurisdictionTerm.class)
                )
        ).thenAnswer(
                invocation ->
                        persisted(
                                invocation.getArgument(0),
                                ids.incrementAndGet()
                        )
        );

        var fact =
                service.upsert(
                        owner,
                        date,
                        " ru ",
                        " ru-mow "
                );

        assertEquals("RU", fact.jurisdictionCode());
        assertEquals("RU-MOW", fact.regionCode());
        assertTrue(fact.termId() > 0L);
    }

    @Test
    void invalidRegionAndUnsupportedRequestedCountryAreRejectedBeforePersistence() {
        IllegalArgumentException badRegion =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.upsert(
                                owner,
                                date,
                                "RU",
                                "DE-BE"
                        )
                );

        assertTrue(
                badRegion.getMessage()
                        .startsWith(
                                WorkJurisdictionHistoryService.REGION_INVALID
                        )
        );

        IllegalArgumentException unsupported =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.upsert(
                                owner,
                                date,
                                "US",
                                null
                        )
                );

        assertTrue(
                unsupported.getMessage()
                        .startsWith(
                                WorkJurisdictionHistoryService.JURISDICTION_UNSUPPORTED
                        )
        );

        verify(terms, never()).saveAndFlush(any());
    }

    private WorkJurisdictionTerm persisted(
            WorkJurisdictionTerm term,
            long id
    ) {
        try {
            Field field =
                    WorkJurisdictionTerm.class
                            .getDeclaredField("id");
            field.setAccessible(true);
            field.set(term, id);
            return term;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}

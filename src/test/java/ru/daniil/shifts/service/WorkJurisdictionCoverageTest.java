package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkJurisdictionTerm;
import ru.daniil.shifts.repo.WorkJurisdictionTermRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkJurisdictionCoverageTest {
    WorkJurisdictionTermRepository terms;
    WorkJurisdictionHistoryService service;
    AppUser owner;
    LocalDate date;

    @BeforeEach
    void setUp() {
        terms = mock(WorkJurisdictionTermRepository.class);
        service = new WorkJurisdictionHistoryService(terms);
        owner = new AppUser("jurisdiction-coverage-owner", "{noop}irrelevant");
        date = LocalDate.of(2026, 9, 3);
    }

    @Test
    void historyReturnsFactsWithStableIds() {
        WorkJurisdictionTerm first = persisted(term("RU", null), 301L);
        WorkJurisdictionTerm second = persisted(term("RU", "RU-KYA"), 302L);
        when(terms.findByOwnerOrderByEffectiveFromAscIdAsc(owner))
                .thenReturn(java.util.List.of(first, second));

        var result = service.history(owner);

        assertEquals(2, result.size());
        assertEquals(301L, result.get(0).termId());
        assertEquals("RU-KYA", result.get(1).regionCode());
    }

    @Test
    void historyRejectsNullRepositoryResult() {
        when(terms.findByOwnerOrderByEffectiveFromAscIdAsc(owner))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.history(owner));
    }

    @Test
    void historyRejectsUnsupportedPersistedFact() {
        WorkJurisdictionTerm invalid = persisted(term("DE", null), 303L);
        when(terms.findByOwnerOrderByEffectiveFromAscIdAsc(owner))
                .thenReturn(java.util.List.of(invalid));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> service.history(owner));
        assertTrue(failure.getMessage().contains(
                WorkJurisdictionHistoryService.JURISDICTION_UNSUPPORTED));
    }

    @Test
    void upsertUpdatesExistingTerm() {
        WorkJurisdictionTerm existing = persisted(term("RU", null), 304L);
        when(terms.findByOwnerAndEffectiveFrom(owner, date))
                .thenReturn(Optional.of(existing));
        when(terms.saveAndFlush(existing)).thenReturn(existing);

        var fact = service.upsert(owner, date, "RU", "RU-MOW");

        assertEquals(304L, fact.termId());
        assertEquals("RU-MOW", fact.regionCode());
    }

    @Test
    void invalidRepositoryValueAfterSaveFailsClosed() {
        when(terms.findByOwnerAndEffectiveFrom(owner, date))
                .thenReturn(Optional.empty());
        when(terms.saveAndFlush(any(WorkJurisdictionTerm.class)))
                .thenReturn(persisted(term("DE", null), 305L));

        assertThrows(
                IllegalStateException.class,
                () -> service.upsert(owner, date, "RU", null)
        );
    }

    @Test
    void deleteExistingTermDeletesAndFlushes() {
        WorkJurisdictionTerm existing = persisted(term("RU", null), 306L);
        when(terms.findByOwnerAndEffectiveFrom(owner, date))
                .thenReturn(Optional.of(existing));

        service.delete(owner, date);

        verify(terms).delete(existing);
        verify(terms).flush();
    }

    @Test
    void deleteMissingTermOnlyFlushes() {
        when(terms.findByOwnerAndEffectiveFrom(owner, date))
                .thenReturn(Optional.empty());

        service.delete(owner, date);

        verify(terms, never()).delete(any());
        verify(terms).flush();
    }

    @Test
    void resolvedFactWithoutPersistedIdIsRejected() {
        WorkJurisdictionTerm noId = term("RU", null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                owner, date))
                .thenReturn(Optional.of(noId));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveAt(owner, date)
        );
    }

    @Test
    void missingEffectiveDateInPersistedFactFailsClosed() {
        WorkJurisdictionTerm broken = persisted(term("RU", null), 307L);
        setField(broken, "effectiveFrom", null);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                owner, date))
                .thenReturn(Optional.of(broken));

        var result = service.resolveAt(owner, date);

        assertFalse(result.ready());
        assertEquals(
                WorkJurisdictionHistoryService.JURISDICTION_FACT_MISSING,
                result.blockingReason()
        );
    }

    @Test
    void nullJurisdictionRequestFailsClosed() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.upsert(owner, date, null, null)
                );
        assertEquals(
                WorkJurisdictionHistoryService.JURISDICTION_UNSUPPORTED + ":NULL",
                failure.getMessage()
        );
    }

    @Test
    void blankJurisdictionRequestFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(owner, date, "   ", null)
        );
    }

    @Test
    void shortRegionCodeFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(owner, date, "RU", "RU-")
        );
    }

    @Test
    void longRegionCodeFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(
                        owner,
                        date,
                        "RU",
                        "RU-THIS-REGION-CODE-IS-DELIBERATELY-TOO-LONG"
                )
        );
    }

    @Test
    void invalidRegionCharactersFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(owner, date, "RU", "RU-M@W")
        );
    }

    @Test
    void resultRecordInvariantsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkJurisdictionHistoryService.JurisdictionFact(
                        0L, date, "RU", null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new WorkJurisdictionHistoryService.JurisdictionFact(
                        1L, null, "RU", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkJurisdictionHistoryService.JurisdictionFact(
                        1L, date, " ", null)
        );

        var fact =
                new WorkJurisdictionHistoryService.JurisdictionFact(
                        1L, date, "RU", null);

        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkJurisdictionHistoryService.Resolution(
                        date, true, "contradictory", fact)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkJurisdictionHistoryService.Resolution(
                        date, true, null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkJurisdictionHistoryService.Resolution.blocked(
                        date, " ")
        );
    }

    private WorkJurisdictionTerm term(String jurisdiction, String region) {
        return new WorkJurisdictionTerm(owner, date, jurisdiction, region);
    }

    private WorkJurisdictionTerm persisted(WorkJurisdictionTerm term, long id) {
        setField(term, "id", id);
        return term;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}

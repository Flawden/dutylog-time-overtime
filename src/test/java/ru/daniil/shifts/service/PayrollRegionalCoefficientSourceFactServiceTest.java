package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.PayrollRegionalCoefficientSourceFact;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollRegionalCoefficientSourceFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayrollRegionalCoefficientSourceFactServiceTest {

    private PayrollRegionalCoefficientSourceFactRepository facts;
    private CompensationComponentRepository components;
    private PayrollRegionalCoefficientSourceFactService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        facts = mock(PayrollRegionalCoefficientSourceFactRepository.class);
        components = mock(CompensationComponentRepository.class);
        user = mock(AppUser.class);
        service = new PayrollRegionalCoefficientSourceFactService(facts, components);
    }

    @Test
    void resolveMonthPreservesExplicitSourcePeriodMoneyAndCurrency() {
        var fact = persisted(
                10L,
                42L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                1_339_212L,
                "RUB"
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        )).thenReturn(List.of(fact));

        var result = service.resolveMonth(user, YearMonth.of(2026, 3));

        assertEquals(1, result.size());
        assertEquals(42L, result.get(0).componentId());
        assertEquals(LocalDate.of(2026, 3, 1), result.get(0).periodFrom());
        assertEquals(LocalDate.of(2026, 3, 31), result.get(0).periodTo());
        assertEquals(1_339_212L, result.get(0).amountMinor());
        assertEquals("RUB", result.get(0).currencyCode());
    }

    @Test
    void emptyMonthMeansNoExplicitRegionalPeriodEvidence() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of());

        assertTrue(service.resolveMonth(user, YearMonth.of(2026, 8)).isEmpty());
        verifyNoInteractions(components);
    }

    @Test
    void monthResolutionRejectsNullRepositoryResultOrLeakedMonth() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8))
        );

        var wrongMonth = persisted(
                10L,
                42L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                100_000L,
                "RUB"
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of(wrongMonth));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8))
        );
    }

    @Test
    void monthResolutionRejectsRepositoryOrderingCorruption() {
        var secondComponent = persisted(
                10L, 99L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10),
                50_000L, "RUB"
        );
        var firstComponent = persisted(
                11L, 42L,
                LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 20),
                60_000L, "RUB"
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of(secondComponent, firstComponent));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8))
        );
    }

    @Test
    void overlappingPersistedFactsForOneComponentFailClosed() {
        var first = persisted(
                10L, 42L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100_000L, "RUB"
        );
        var overlap = persisted(
                11L, 42L,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 31),
                50_000L, "RUB"
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of(first, overlap));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveComponent(user, 42L)
        );
    }

    @Test
    void createRequiresOwnedStableComponentAndRejectsOverlap() {
        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        139_800L, "RUB"
                )
        );

        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(mock(CompensationComponent.class)));

        PayrollRegionalCoefficientSourceFact existing = persisted(
                1L, 42L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100_000L, "RUB"
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user, 42L,
                        LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 31),
                        39_800L, "RUB"
                )
        );
    }

    @Test
    void createNormalizesCurrencyAndPersistsExplicitFact() {
        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(mock(CompensationComponent.class)));
        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of());
        when(facts.saveAndFlush(any(PayrollRegionalCoefficientSourceFact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(
                user, 42L,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3),
                139_800L, " rub "
        );

        assertEquals(42L, created.getComponentId());
        assertEquals("RUB", created.getCurrencyCode());
        assertEquals(LocalDate.of(2026, 8, 3), created.getPeriodFrom());
        assertEquals(139_800L, created.getAmountMinor());
    }

    @Test
    void updateRejectsMissingIdentityUnknownFactAndOverlap() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        user, null,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        139_800L, "RUB"
                )
        );

        when(facts.findByOwnerAndId(user, 999L))
                .thenReturn(Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        user, 999L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        139_800L, "RUB"
                )
        );

        PayrollRegionalCoefficientSourceFact current = mock(PayrollRegionalCoefficientSourceFact.class);
        when(current.getComponentId()).thenReturn(42L);
        when(facts.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(current));

        PayrollRegionalCoefficientSourceFact overlap = persisted(
                11L, 42L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100_000L, "RUB"
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                .thenReturn(List.of(overlap));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        user, 10L,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 31),
                        139_800L, "RUB"
                )
        );
    }

    @Test
    void updatePersistsValidatedShapeWhenNoOverlapExists() {
        PayrollRegionalCoefficientSourceFact current = mock(PayrollRegionalCoefficientSourceFact.class);
        when(current.getComponentId()).thenReturn(42L);
        when(facts.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(current));
        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                .thenReturn(List.of());
        when(facts.saveAndFlush(current)).thenReturn(current);

        var updated = service.update(
                user, 10L,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3),
                139_800L, "RUB"
        );

        assertSame(current, updated);
        verify(current).update(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 3),
                139_800L,
                "RUB"
        );
    }

    @Test
    void deleteRemovesOwnedFactAndRejectsUnknownFact() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(user, null)
        );

        when(facts.findByOwnerAndId(user, 999L)).thenReturn(Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(user, 999L)
        );

        PayrollRegionalCoefficientSourceFact current = mock(PayrollRegionalCoefficientSourceFact.class);
        when(facts.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(current));

        service.delete(user, 10L);

        verify(facts).delete(current);
        verify(facts).flush();
    }

    @Test
    void publicOperationsRejectNonPositiveComponentIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveComponent(user, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user, -1L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        10_000L, "RUB"
                )
        );
    }

    @Test
    void persistedValidationRejectsCorruptStoredShapes() {
        List<PayrollRegionalCoefficientSourceFact> corrupt = List.of(
                persistedWithShape(null, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        10_000L, "RUB"),
                persistedWithShape(1L, 0L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        10_000L, "RUB"),
                persistedWithShape(1L, 42L,
                        null, LocalDate.of(2026, 8, 2),
                        10_000L, "RUB"),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2),
                        10_000L, "RUB"),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1),
                        10_000L, "RUB"),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        0L, "RUB"),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        1_000_000_000_001L, "RUB"),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        10_000L, null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        10_000L, "rub")
        );

        for (var invalid : corrupt) {
            when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                    .thenReturn(List.of(invalid));
            assertThrows(
                    IllegalStateException.class,
                    () -> service.resolveComponent(user, 42L)
            );
        }
    }

    @Test
    void sourceFactRecordRejectsInvalidPublicShape() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        0L, 42L, from, to, 10_000L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 0L, from, to, 10_000L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 42L, null, to, 10_000L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 42L, to, from, 10_000L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 42L, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1),
                        10_000L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 42L, from, to, 0L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFactService.SourceFact(
                        1L, 42L, from, to, 10_000L, "rub"));

        var valid = new PayrollRegionalCoefficientSourceFactService.SourceFact(
                1L, 42L, from, to, 10_000L, "RUB"
        );
        assertEquals(42L, valid.componentId());
    }

    private static PayrollRegionalCoefficientSourceFact persistedWithShape(
            Long id,
            long componentId,
            LocalDate from,
            LocalDate to,
            long amountMinor,
            String currency
    ) {
        PayrollRegionalCoefficientSourceFact fact = mock(PayrollRegionalCoefficientSourceFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getComponentId()).thenReturn(componentId);
        when(fact.getPeriodFrom()).thenReturn(from);
        when(fact.getPeriodTo()).thenReturn(to);
        when(fact.getAmountMinor()).thenReturn(amountMinor);
        when(fact.getCurrencyCode()).thenReturn(currency);
        return fact;
    }

    private static PayrollRegionalCoefficientSourceFact persisted(
            long id,
            long componentId,
            LocalDate from,
            LocalDate to,
            long amountMinor,
            String currency
    ) {
        return persistedWithShape(
                id,
                componentId,
                from,
                to,
                amountMinor,
                currency
        );
    }
}

package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.PayrollBonusSourceFact;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollBonusSourceFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayrollBonusSourceFactServiceTest {

    private PayrollBonusSourceFactRepository facts;
    private CompensationComponentRepository components;
    private PayrollBonusSourceFactService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        facts = mock(PayrollBonusSourceFactRepository.class);
        components = mock(CompensationComponentRepository.class);
        user = mock(AppUser.class);
        service = new PayrollBonusSourceFactService(facts, components);
    }

    @Test
    void resolveMonthPreservesMonthlyAndOneTimeFacts() {
        var monthly = persisted(
                10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                2_518_797L, "RUB"
        );
        var oneTime = persisted(
                11L, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                962_700L, "RUB"
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28)
        )).thenReturn(List.of(monthly, oneTime));

        var result = service.resolveMonth(user, YearMonth.of(2026, 2));

        assertEquals(2, result.size());
        assertEquals(PayrollEarningKind.MONTHLY_BONUS, result.get(0).earningKind());
        assertEquals(2_518_797L, result.get(0).amountMinor());
        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, result.get(1).earningKind());
        assertEquals(962_700L, result.get(1).amountMinor());
        assertEquals("RUB", result.get(1).currencyCode());
    }

    @Test
    void emptyMonthMeansNoExactBonusPeriodEvidence() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of());

        assertTrue(service.resolveMonth(user, YearMonth.of(2026, 8)).isEmpty());
        verifyNoInteractions(components);
    }

    @Test
    void monthResolutionRejectsNullLeakedMonthAndOrderingCorruption() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8)));

        var wrongMonth = persisted(
                10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                100L, "RUB"
        );
        when(facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of(wrongMonth));
        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8)));

        var oneTime = persisted(
                11L, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        );
        var monthly = persisted(
                12L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        );
        when(facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of(oneTime, monthly));
        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 8)));
    }

    @Test
    void overlappingPersistedFactsForSameComponentAndKindFailClosed() {
        var first = persisted(
                10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100L, "RUB"
        );
        var overlap = persisted(
                11L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        );

        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS
        )).thenReturn(List.of(first, overlap));

        assertThrows(IllegalStateException.class,
                () -> service.resolveComponent(
                        user, 42L, PayrollEarningKind.MONTHLY_BONUS
                ));
    }

    @Test
    void createRequiresOwnedComponentAndRejectsOverlap() {
        when(components.findByOwnerAndId(user, 42L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));

        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(mock(CompensationComponent.class)));
        var existing = persisted(
                10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100L, "RUB"
        );
        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS
        )).thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));
    }

    @Test
    void createPreservesObservedKindAndNormalizesCurrency() {
        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(mock(CompensationComponent.class)));
        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.ONE_TIME_BONUS
        )).thenReturn(List.of());
        when(facts.saveAndFlush(any(PayrollBonusSourceFact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(
                user, 42L, PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                1_905_000L, " rub "
        );

        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, created.getEarningKind());
        assertEquals("RUB", created.getCurrencyCode());
        assertEquals(1_905_000L, created.getAmountMinor());
    }

    @Test
    void createAndResolveRejectUnsupportedKindOrIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveComponent(user, 0L, PayrollEarningKind.MONTHLY_BONUS));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveComponent(user, 42L, PayrollEarningKind.REGIONAL_COEFFICIENT));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                user, 42L, PayrollEarningKind.REGIONAL_COEFFICIENT,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));
    }

    @Test
    void updateRejectsMissingUnknownAndOverlapButPreservesKind() {
        assertThrows(IllegalArgumentException.class, () -> service.update(
                user, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));

        when(facts.findByOwnerAndId(user, 999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.update(
                user, 999L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));

        PayrollBonusSourceFact current = mock(PayrollBonusSourceFact.class);
        when(current.getComponentId()).thenReturn(42L);
        when(current.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        when(facts.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(current));

        var overlap = persisted(
                11L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                100L, "RUB"
        );
        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS
        )).thenReturn(List.of(overlap));

        assertThrows(IllegalArgumentException.class, () -> service.update(
                user, 10L,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 31),
                100L, "RUB"
        ));
    }

    @Test
    void updateSucceedsWithoutOverlapAndDeleteIsOwnerScoped() {
        PayrollBonusSourceFact current = mock(PayrollBonusSourceFact.class);
        when(current.getComponentId()).thenReturn(42L);
        when(current.getEarningKind()).thenReturn(PayrollEarningKind.ONE_TIME_BONUS);
        when(facts.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(current));
        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.ONE_TIME_BONUS
        )).thenReturn(List.of());
        when(facts.saveAndFlush(current)).thenReturn(current);

        assertSame(current, service.update(
                user, 10L,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                1_905_000L, "RUB"
        ));
        verify(current).update(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                1_905_000L, "RUB"
        );

        service.delete(user, 10L);
        verify(facts).delete(current);
        verify(facts).flush();
    }

    @Test
    void deleteRejectsMissingIdentityAndUnknownFact() {
        assertThrows(IllegalArgumentException.class, () -> service.delete(user, null));
        when(facts.findByOwnerAndId(user, 999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.delete(user, 999L));
    }

    @Test
    void persistedValidationRejectsMixedIdentityAndCorruptShapes() {
        var wrongKind = persisted(
                1L, 42L, PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                100L, "RUB"
        );
        when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                user, 42L, PayrollEarningKind.MONTHLY_BONUS
        )).thenReturn(List.of(wrongKind));
        assertThrows(IllegalStateException.class,
                () -> service.resolveComponent(user, 42L, PayrollEarningKind.MONTHLY_BONUS));

        List<PayrollBonusSourceFact> corrupt = List.of(
                persistedWithShape(null, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 100L, "RUB"),
                persistedWithShape(1L, 0L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 100L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.REGIONAL_COEFFICIENT,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 100L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        null, LocalDate.of(2026, 8, 2), 100L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2), 100L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1), 100L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 0L, "RUB"),
                persistedWithShape(1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 100L, "rub")
        );

        for (var invalid : corrupt) {
            when(facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                    user, 42L, PayrollEarningKind.MONTHLY_BONUS
            )).thenReturn(List.of(invalid));
            assertThrows(IllegalStateException.class,
                    () -> service.resolveComponent(user, 42L, PayrollEarningKind.MONTHLY_BONUS));
        }
    }

    @Test
    void bonusFactRecordRejectsInvalidShape() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new PayrollBonusSourceFactService.BonusFact(
                        0L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        from, to, 100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollBonusSourceFactService.BonusFact(
                        1L, 42L, PayrollEarningKind.REGIONAL_COEFFICIENT,
                        from, to, 100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollBonusSourceFactService.BonusFact(
                        1L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                        from, to, 100L, "rub"));

        var valid = new PayrollBonusSourceFactService.BonusFact(
                1L, 42L, PayrollEarningKind.ONE_TIME_BONUS,
                from, to, 100L, "RUB"
        );
        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, valid.earningKind());
    }

    private static PayrollBonusSourceFact persistedWithShape(
            Long id,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate from,
            LocalDate to,
            long amountMinor,
            String currency
    ) {
        PayrollBonusSourceFact fact = mock(PayrollBonusSourceFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getComponentId()).thenReturn(componentId);
        when(fact.getEarningKind()).thenReturn(earningKind);
        when(fact.getPeriodFrom()).thenReturn(from);
        when(fact.getPeriodTo()).thenReturn(to);
        when(fact.getAmountMinor()).thenReturn(amountMinor);
        when(fact.getCurrencyCode()).thenReturn(currency);
        return fact;
    }

    private static PayrollBonusSourceFact persisted(
            long id,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate from,
            LocalDate to,
            long amountMinor,
            String currency
    ) {
        return persistedWithShape(
                id, componentId, earningKind,
                from, to, amountMinor, currency
        );
    }
}

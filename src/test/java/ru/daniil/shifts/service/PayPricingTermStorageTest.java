package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PayPricingTermStorageTest {

    @Autowired
    PayPricingTermRepository pricingTerms;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(
                new AppUser(
                        "pricing-"
                                + UUID.randomUUID()
                                .toString()
                                .substring(
                                        0,
                                        12
                                ),
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void effectiveDateLookupUsesLatestTermNotAfterRequestedDate() {
        PayPricingTerm june =
                new PayPricingTerm(
                        user,
                        LocalDate.of(
                                2026,
                                6,
                                1
                        )
                );

        june.addRule(
                "NIGHT",
                "NIGHT",
                2_000,
                0,
                null,
                null
        );

        pricingTerms.saveAndFlush(june);

        /*
         * Deliberately mid-month:
         * pricing history is effective-date,
         * not effective-month.
         */
        PayPricingTerm august =
                new PayPricingTerm(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                15
                        )
                );

        august.addRule(
                "NIGHT",
                "NIGHT",
                3_000,
                0,
                null,
                null
        );

        august.addRule(
                "OT_TIER_1",
                "OVERTIME",
                5_000,
                0,
                120,
                null
        );

        pricingTerms.saveAndFlush(
                august
        );

        assertTrue(
                pricingTerms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        5,
                                        31
                                )
                        )
                        .isEmpty()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        6,
                        1
                ),
                pricingTerms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        14
                                )
                        )
                        .orElseThrow()
                        .getEffectiveFrom()
        );

        PayPricingTerm effective =
                pricingTerms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        15
                                )
                        )
                        .orElseThrow();

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        15
                ),
                effective.getEffectiveFrom()
        );

        assertEquals(
                2,
                effective.getRules().size()
        );
    }

    @Test
    void persistedRuleShapeKeepsPremiumTierAndExclusiveGroup() {
        PayPricingTerm term =
                new PayPricingTerm(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                3
                        )
                );

        term.addRule(
                "HOLIDAY_PRIMARY",
                "HOLIDAY",
                10_000,
                0,
                null,
                "PRIMARY"
        );

        term.addRule(
                "OT_TIER_2",
                "OVERTIME",
                10_000,
                120,
                null,
                "PRIMARY"
        );

        PayPricingTerm saved =
                pricingTerms.saveAndFlush(
                        term
                );

        PayPricingTerm reloaded =
                pricingTerms
                        .findByOwnerAndEffectiveFrom(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        3
                                )
                        )
                        .orElseThrow();

        assertEquals(
                saved.getId(),
                reloaded.getId()
        );

        var holiday =
                reloaded.getRules()
                        .stream()
                        .filter(rule ->
                                "HOLIDAY_PRIMARY"
                                        .equals(
                                                rule.getCode()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                "HOLIDAY",
                holiday.getDimension()
        );

        assertEquals(
                10_000,
                holiday.getPremiumBps()
        );

        assertEquals(
                "PRIMARY",
                holiday.getExclusiveGroup()
        );

        var tier =
                reloaded.getRules()
                        .stream()
                        .filter(rule ->
                                "OT_TIER_2"
                                        .equals(
                                                rule.getCode()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                120,
                tier.getFromMinute()
        );

        assertNull(
                tier.getToMinuteExclusive()
        );
    }

    @Test
    void invalidAndDuplicateRuleShapesFailBeforePersistence() {
        PayPricingTerm term =
                new PayPricingTerm(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        )
                );

        term.addRule(
                "NIGHT",
                "NIGHT",
                2_000,
                0,
                null,
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        term.addRule(
                                "NIGHT",
                                "NIGHT",
                                3_000,
                                0,
                                null,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        term.addRule(
                                "BAD_NIGHT_RANGE",
                                "NIGHT",
                                2_000,
                                60,
                                null,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        term.addRule(
                                "BAD_OT_RANGE",
                                "OVERTIME",
                                5_000,
                                120,
                                120,
                                null
                        )
        );
    }
}

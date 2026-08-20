package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PayPricingRuleRequest;
import ru.daniil.shifts.dto.Dtos.PayPricingTermRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class PayPricingConfigurationServiceTest {

    @Autowired
    PayPricingConfigurationService service;

    @Autowired
    PayPricingTermRepository terms;

    @Autowired
    UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(
                new AppUser(
                        "pricing-config-"
                                + UUID.randomUUID()
                                .toString()
                                .substring(0, 12),
                        "{noop}irrelevant"
                )
        );
    }

    @Test
    void upsertAllowsFutureDatesAndFullyReplacesOneVersion() {
        LocalDate effective =
                LocalDate.now().plusYears(2);

        var first = service.upsert(
                user,
                effective.toString(),
                term(
                        night(2_000),
                        overtime(
                                "OT_1",
                                5_000,
                                0,
                                120
                        )
                )
        );

        Long id = first.id();

        var replaced = service.upsert(
                user,
                effective.toString(),
                term(
                        night(3_000)
                )
        );

        assertEquals(id, replaced.id());
        assertEquals(1, replaced.rules().size());
        assertEquals(3_000, replaced.rules().get(0).premiumBps());
        assertEquals(
                1,
                terms.findByOwnerAndEffectiveFrom(user, effective)
                        .orElseThrow()
                        .getRules()
                        .size()
        );
    }

    @Test
    void emptyTermIsPersistedAndDeleteRemovesOnlyExactVersion() {
        LocalDate firstDate = LocalDate.of(2026, 8, 1);
        LocalDate secondDate = LocalDate.of(2026, 9, 1);

        service.upsert(
                user,
                firstDate.toString(),
                term(night(2_000))
        );

        var empty = service.upsert(
                user,
                secondDate.toString(),
                new PayPricingTermRequest(List.of())
        );

        assertTrue(empty.rules().isEmpty());
        assertEquals(2, service.history(user).size());

        service.delete(user, secondDate.toString());

        assertEquals(1, service.history(user).size());
        assertTrue(
                terms.findByOwnerAndEffectiveFrom(
                                user,
                                secondDate
                        )
                        .isEmpty()
        );
    }

    @Test
    void invalidCrossFieldRuleAndDateBecomeStableBadRequest() {
        ApiException range = assertThrows(
                ApiException.class,
                () -> service.upsert(
                        user,
                        "2026-08-20",
                        term(
                                new PayPricingRuleRequest(
                                        "BAD_NIGHT",
                                        "NIGHT",
                                        2_000,
                                        60,
                                        null,
                                        null
                                )
                        )
                )
        );

        assertEquals("PAY_PRICING_INVALID", range.getCode());
        assertEquals(400, range.getStatus().value());

        ApiException date = assertThrows(
                ApiException.class,
                () -> service.delete(
                        user,
                        "not-a-date"
                )
        );

        assertEquals("PAY_PRICING_INVALID", date.getCode());
        assertEquals(400, date.getStatus().value());
    }

    private PayPricingTermRequest term(
            PayPricingRuleRequest... rules
    ) {
        return new PayPricingTermRequest(
                List.of(rules)
        );
    }

    private PayPricingRuleRequest night(
            int premiumBps
    ) {
        return new PayPricingRuleRequest(
                "NIGHT",
                "NIGHT",
                premiumBps,
                0,
                null,
                null
        );
    }

    private PayPricingRuleRequest overtime(
            String code,
            int premiumBps,
            int from,
            Integer to
    ) {
        return new PayPricingRuleRequest(
                code,
                "OVERTIME",
                premiumBps,
                from,
                to,
                null
        );
    }
}

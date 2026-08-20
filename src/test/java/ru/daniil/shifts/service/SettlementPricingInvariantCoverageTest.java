package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.PricedRateBucket;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SettlementMoneyProjection;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SourceValuation;
import ru.daniil.shifts.service.PayrollSettlementPreviewService.SettlementPreview;
import ru.daniil.shifts.service.PayrollSettlementPricingService.PayrollSettlementPricing;
import ru.daniil.shifts.service.PayrollSettlementPricingService.SettlementLine;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementPricingInvariantCoverageTest {

    private final LocalDate sourceDate =
            LocalDate.of(2026, 8, 10);

    private final YearMonth payrollMonth =
            YearMonth.of(2026, 8);

    private final AppUser user =
            new AppUser(
                    "settlement-invariant-coverage",
                    "{noop}unused"
            );

    @Test
    void sourceValuationRejectsIncompleteAndHistoricallyImpossibleIdentity() {
        assertInvalidSource(
                null, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, null, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 0, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, -1, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, -1,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                null,
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                null,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                null,
                "HOURLY", "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                null, "RUB", 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", null, 100_000L
        );

        assertInvalidSource(
                3L, sourceDate, 60, 0, 480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY", "RUB", 0L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.minusDays(1),
                        YearMonth.of(2026, 7),
                        LocalDate.of(2026, 7, 1),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.plusDays(1),
                        payrollMonth,
                        payrollMonth.atDay(1),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.minusDays(1),
                        payrollMonth,
                        payrollMonth.atDay(2),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        SourceValuation valid =
                validSource();

        assertEquals(60, valid.minutes());
        assertEquals("RUB", valid.currencyCode());
    }

    @Test
    void sourceValuationDeepPricingSlicesMustPreserveMinutes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.minusDays(1),
                        payrollMonth,
                        payrollMonth.atDay(1),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null,
                        false,
                        false,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.minusDays(1),
                        payrollMonth,
                        payrollMonth.atDay(1),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null,
                        true,
                        false,
                        List.of(
                                new PricingSlice(
                                        30,
                                        List.of()
                                )
                        )
                )
        );

        SourceValuation deep =
                new SourceValuation(
                        1L,
                        2L,
                        3L,
                        sourceDate,
                        60,
                        0,
                        480,
                        sourceDate.minusDays(1),
                        payrollMonth,
                        payrollMonth.atDay(1),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null,
                        true,
                        false,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of()
                                )
                        )
                );

        assertTrue(deep.night());
        assertFalse(deep.holiday());
        assertEquals(1, deep.pricingSlices().size());
    }

    @Test
    void pricedRateBucketRejectsInvalidMoneyAndNormalizesPremiumList() {
        assertInvalidBucket(0, 60, 100, 50, 150);
        assertInvalidBucket(100, 0, 100, 50, 150);
        assertInvalidBucket(100, 60, -1, 50, 49);
        assertInvalidBucket(100, 60, 100, -1, 99);
        assertInvalidBucket(100, 60, 100, 50, -1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new PricedRateBucket(
                        100,
                        60,
                        100,
                        50,
                        149,
                        List.of()
                )
        );

        assertThrows(
                ArithmeticException.class,
                () -> new PricedRateBucket(
                        Long.MAX_VALUE,
                        60,
                        Long.MAX_VALUE,
                        1,
                        Long.MAX_VALUE,
                        List.of()
                )
        );

        PricedRateBucket valid =
                new PricedRateBucket(
                        100_000,
                        60,
                        100_000,
                        50_000,
                        150_000,
                        null
                );

        assertTrue(valid.premiums().isEmpty());
    }

    @Test
    void settlementMoneyProjectionRejectsBrokenIdentityMoneyAndMinuteConservation() {
        PricedRateBucket bucket = validBucket();
        SourceValuation source = validSource();

        assertInvalidMoney(
                null, sourceDate, "RUB",
                60, 100_000, 50_000, 150_000,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, null, "RUB",
                60, 100_000, 50_000, 150_000,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, null,
                60, 100_000, 50_000, 150_000,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, "   ",
                60, 100_000, 50_000, 150_000,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, "RUB",
                0, 100_000, 50_000, 150_000,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, "RUB",
                60, -1, 50_000, 49_999,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, "RUB",
                60, 100_000, -1, 99_999,
                List.of(bucket), List.of(source)
        );

        assertInvalidMoney(
                10L, sourceDate, "RUB",
                60, 100_000, 50_000, -1,
                List.of(bucket), List.of(source)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementMoneyProjection(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100_000,
                        50_000,
                        149_999,
                        List.of(bucket),
                        List.of(source)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementMoneyProjection(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100_000,
                        50_000,
                        150_000,
                        null,
                        List.of(source)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementMoneyProjection(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100_000,
                        50_000,
                        150_000,
                        List.of(bucket),
                        null
                )
        );

        PricedRateBucket thirty =
                new PricedRateBucket(
                        100_000,
                        30,
                        50_000,
                        25_000,
                        75_000,
                        List.of()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementMoneyProjection(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100_000,
                        50_000,
                        150_000,
                        List.of(thirty),
                        List.of(source)
                )
        );

        SettlementMoneyProjection valid =
                validMoney();

        assertEquals(60, valid.minutes());
        assertTrue(
                valid.pricingFingerprint()
                        .matches("[0-9a-f]{64}")
        );
    }

    @Test
    void payrollSettlementLineRejectsInvalidIdentityFingerprintAndMoney() {
        assertInvalidLine(null, sourceDate, "RUB", 60, 100, 50, 150);
        assertInvalidLine(10L, null, "RUB", 60, 100, 50, 150);
        assertInvalidLine(10L, sourceDate, null, 60, 100, 50, 150);
        assertInvalidLine(10L, sourceDate, " ", 60, 100, 50, 150);
        assertInvalidLine(10L, sourceDate, "RUB", 0, 100, 50, 150);
        assertInvalidLine(10L, sourceDate, "RUB", 60, -1, 50, 49);
        assertInvalidLine(10L, sourceDate, "RUB", 60, 100, -1, 99);
        assertInvalidLine(10L, sourceDate, "RUB", 60, 100, 50, -1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLine(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100,
                        50,
                        150,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLine(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100,
                        50,
                        150,
                        "not-a-sha"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLine(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        100,
                        50,
                        149
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLine(
                        10L,
                        sourceDate,
                        "RUB",
                        60,
                        Long.MAX_VALUE,
                        1,
                        Long.MAX_VALUE
                )
        );

        SettlementLine valid = validLine();

        assertTrue(
                valid.pricingFingerprint()
                        .matches("[0-9a-f]{64}")
        );
    }

    @Test
    void payrollSettlementProjectionRejectsImpossibleEmptyAndNonEmptyStates() {
        SettlementLine line = validLine();

        assertInvalidPayroll(
                null, null, 0, 0, 0, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, -1, 0, 0, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, -1, 0, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, -1, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, 0, -1, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, 0, 0, -1, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 0, 0, 0, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 1, 0, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, 1, 0, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, 0, 1, 0, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 0, 0, 0, 0, 1, List.of()
        );

        assertInvalidPayroll(
                payrollMonth, null, 1, 60,
                100, 50, 150, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, " ", 1, 60,
                100, 50, 150, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 0,
                100, 50, 150, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 2, 60,
                100, 50, 150, List.of(line)
        );

        SettlementLine eur =
                new SettlementLine(
                        11L,
                        sourceDate,
                        "EUR",
                        60,
                        100,
                        50,
                        150
                );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 60,
                100, 50, 150, List.of(eur)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 61,
                100, 50, 150, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 60,
                101, 50, 151, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 60,
                100, 51, 151, List.of(line)
        );

        assertInvalidPayroll(
                payrollMonth, "RUB", 1, 60,
                100, 50, 151, List.of(line)
        );

        PayrollSettlementPricing empty =
                new PayrollSettlementPricing(
                        payrollMonth,
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null
                );

        assertTrue(empty.empty());
        assertNull(empty.pricingFingerprint());

        PayrollSettlementPricing valid =
                validPayroll();

        assertFalse(valid.empty());

        assertTrue(
                valid.pricingFingerprint()
                        .matches("[0-9a-f]{64}")
        );
    }

    @Test
    void settlementPreviewEnforcesReadyAndBlockedStateMachines() {
        SettlementLine line = validLine();
        String fingerprint = "a".repeat(64);

        assertInvalidPreview(
                null, true, null, null,
                0, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                -1, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                0, -1, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                0, 0, -1, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                0, 0, 0, -1, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                0, 0, 0, 0, -1,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, "BLOCK", null,
                0, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, "blocked",
                0, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                1, 60, 100, 50, 150,
                fingerprint, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                0, 0, 0, 0, 0,
                fingerprint, List.of()
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                1, 60, 100, 50, 150,
                null, List.of(line)
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                1, 60, 100, 50, 150,
                "bad", List.of(line)
        );

        assertInvalidPreview(
                payrollMonth, true, null, null,
                1, 60, 100, 50, 149,
                fingerprint, List.of(line)
        );

        assertInvalidPreview(
                payrollMonth, false, null, null,
                0, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, " ", null,
                0, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                1, 0, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 1, 0, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 0, 1, 0, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 0, 0, 1, 0,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 0, 0, 0, 1,
                null, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 0, 0, 0, 0,
                fingerprint, List.of()
        );

        assertInvalidPreview(
                payrollMonth, false, "BLOCK", null,
                0, 0, 0, 0, 0,
                null, List.of(line)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementPreview.ready(null)
        );

        SettlementPreview blocked =
                SettlementPreview.blocked(
                        payrollMonth,
                        "PAY_PRICING_RULES_REQUIRED",
                        "rules missing"
                );

        assertFalse(blocked.ready());
        assertNull(blocked.pricingFingerprint());

        SettlementPreview ready =
                SettlementPreview.ready(
                        validPayroll()
                );

        assertTrue(ready.ready());
        assertNotNull(ready.pricingFingerprint());
    }

    @Test
    void previewServiceRejectsMissingArgumentsAndKeepsOnlyExpectedFailuresSoft() {
        PayrollSettlementPricingService pricing =
                mock(
                        PayrollSettlementPricingService.class
                );

        PayrollSettlementPreviewService service =
                new PayrollSettlementPreviewService(
                        pricing
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(
                        null,
                        payrollMonth,
                        "RUB"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(
                        user,
                        null,
                        "RUB"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(
                        user,
                        payrollMonth,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(
                        user,
                        payrollMonth,
                        " "
                )
        );

        when(
                pricing.project(
                        user,
                        payrollMonth
                )
        ).thenThrow(
                ApiException.conflict(
                        "PAY_PRICING_RULES_REQUIRED",
                        "rules missing"
                )
        );

        SettlementPreview blocked =
                service.preview(
                        user,
                        payrollMonth,
                        "RUB"
                );

        assertFalse(blocked.ready());

        reset(pricing);

        when(
                pricing.project(
                        user,
                        payrollMonth
                )
        ).thenThrow(
                ApiException.badRequest(
                        "PAYROLL_AMOUNT_OVERFLOW",
                        "overflow"
                )
        );

        ApiException escaped =
                assertThrows(
                        ApiException.class,
                        () -> service.preview(
                                user,
                                payrollMonth,
                                "RUB"
                        )
                );

        assertEquals(
                "PAYROLL_AMOUNT_OVERFLOW",
                escaped.getCode()
        );
    }

    @Test
    void payrollSettlementPricingServiceRejectsMissingInvocationIdentity() {
        OvertimeSettlementRepository repository =
                mock(
                        OvertimeSettlementRepository.class
                );

        OvertimeSettlementPricingService pricing =
                mock(
                        OvertimeSettlementPricingService.class
                );

        PayrollSettlementPricingService service =
                new PayrollSettlementPricingService(
                        repository,
                        pricing
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.project(
                        null,
                        payrollMonth
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.project(
                        user,
                        null
                )
        );

        verifyNoInteractions(
                repository,
                pricing
        );
    }

    private SourceValuation validSource() {
        return new SourceValuation(
                1L,
                2L,
                3L,
                sourceDate,
                60,
                0,
                480,
                sourceDate.minusDays(1),
                payrollMonth,
                payrollMonth.atDay(1),
                "HOURLY",
                "RUB",
                100_000L,
                null
        );
    }

    private PricedRateBucket validBucket() {
        return new PricedRateBucket(
                100_000L,
                60,
                100_000L,
                50_000L,
                150_000L,
                List.of()
        );
    }

    private SettlementMoneyProjection validMoney() {
        return new SettlementMoneyProjection(
                10L,
                LocalDate.of(2026, 9, 5),
                "RUB",
                60,
                100_000L,
                50_000L,
                150_000L,
                List.of(
                        validBucket()
                ),
                List.of(
                        validSource()
                )
        );
    }

    private SettlementLine validLine() {
        return new SettlementLine(
                10L,
                sourceDate,
                "RUB",
                60,
                100L,
                50L,
                150L
        );
    }

    private PayrollSettlementPricing validPayroll() {
        SettlementLine line =
                validLine();

        return new PayrollSettlementPricing(
                payrollMonth,
                "RUB",
                1,
                60,
                100L,
                50L,
                150L,
                List.of(line)
        );
    }

    private void assertInvalidSource(
            Long actualId,
            LocalDate factualDate,
            int minutes,
            int creditOffset,
            int ordinal,
            LocalDate pricingEffectiveFrom,
            YearMonth sourceMonth,
            LocalDate compensationEffectiveFrom,
            String mode,
            String currency,
            long hourly
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceValuation(
                        1L,
                        2L,
                        actualId,
                        factualDate,
                        minutes,
                        creditOffset,
                        ordinal,
                        pricingEffectiveFrom,
                        sourceMonth,
                        compensationEffectiveFrom,
                        mode,
                        currency,
                        hourly,
                        null
                )
        );
    }

    private void assertInvalidBucket(
            long rate,
            int minutes,
            long base,
            long premium,
            long total
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PricedRateBucket(
                        rate,
                        minutes,
                        base,
                        premium,
                        total,
                        List.of()
                )
        );
    }

    private void assertInvalidMoney(
            Long id,
            LocalDate date,
            String currency,
            int minutes,
            long base,
            long premium,
            long total,
            List<PricedRateBucket> buckets,
            List<SourceValuation> sources
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementMoneyProjection(
                        id,
                        date,
                        currency,
                        minutes,
                        base,
                        premium,
                        total,
                        buckets,
                        sources
                )
        );
    }

    private void assertInvalidLine(
            Long id,
            LocalDate date,
            String currency,
            int minutes,
            long base,
            long premium,
            long total
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementLine(
                        id,
                        date,
                        currency,
                        minutes,
                        base,
                        premium,
                        total
                )
        );
    }

    private void assertInvalidPayroll(
            YearMonth month,
            String currency,
            int count,
            int minutes,
            long base,
            long premium,
            long total,
            List<SettlementLine> lines
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollSettlementPricing(
                        month,
                        currency,
                        count,
                        minutes,
                        base,
                        premium,
                        total,
                        lines
                )
        );
    }

    private void assertInvalidPreview(
            YearMonth month,
            boolean ready,
            String reason,
            String message,
            int count,
            int minutes,
            long base,
            long premium,
            long total,
            String fingerprint,
            List<SettlementLine> lines
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementPreview(
                        month,
                        ready,
                        reason,
                        message,
                        count,
                        minutes,
                        base,
                        premium,
                        total,
                        fingerprint,
                        lines
                )
        );
    }
}

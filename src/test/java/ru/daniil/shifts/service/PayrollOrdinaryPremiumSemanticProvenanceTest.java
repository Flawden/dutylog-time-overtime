package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.NightPremiumSourceLine;
import ru.daniil.shifts.service.PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PayrollOrdinaryPremiumSemanticProvenanceTest {

    private static final YearMonth MONTH =
            YearMonth.of(
                    2026,
                    8
            );

    @Test
    void exactSingleDateNightMoneyCarriesMinutesAndEarningDate() {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        24
                );

        var preview =
                readyPreview(
                        12_000L,
                        List.of(
                                new NightPremiumSourceLine(
                                        date,
                                        60,
                                        12_000L
                                )
                        )
                );

        var lines =
                PayrollOrdinaryPremiumSemanticProvenance
                        .nightLines(
                                preview
                        );

        assertEquals(1, lines.size());

        var line = lines.get(0);

        assertEquals(PayrollEarningKind.NIGHT_PREMIUM, line.earningKind());
        assertEquals(12_000L, line.amountMinor());
        assertEquals(60L, line.qualifiedQuantity().value());
        assertEquals("MINUTES", line.qualifiedQuantity().unit().name());
        assertEquals(date, line.earningPeriodFrom());
        assertEquals(date, line.earningPeriodTo());
        assertNull(line.coverageFrom());
        assertNull(line.coverageTo());
    }

    @Test
    void exactSubsetKeepsRemainingNightMoneyAsUnattributedResidual() {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        24
                );

        var preview =
                readyPreview(
                        15_000L,
                        List.of(
                                new NightPremiumSourceLine(
                                        date,
                                        60,
                                        12_000L
                                )
                        )
                );

        var lines =
                PayrollOrdinaryPremiumSemanticProvenance
                        .nightLines(
                                preview
                        );

        assertEquals(2, lines.size());

        var residual = lines.get(1);

        assertEquals(PayrollEarningKind.NIGHT_PREMIUM, residual.earningKind());
        assertEquals(3_000L, residual.amountMinor());
        assertNull(residual.qualifiedQuantity());
        assertNull(residual.earningPeriodFrom());
        assertNull(residual.earningPeriodTo());
    }

    @Test
    void noExactSourceLineKeepsAggregateCompatibilityPath() {
        var preview =
                readyPreview(
                        12_000L,
                        List.of()
                );

        assertNull(
                PayrollOrdinaryPremiumSemanticProvenance
                        .nightLines(
                                preview
                        )
        );
    }

    private OrdinaryPremiumPreview readyPreview(
            long nightAmountMinor,
            List<NightPremiumSourceLine> exact
    ) {
        return new OrdinaryPremiumPreview(
                MONTH,
                true,
                null,
                null,
                60,
                60_000L,
                nightAmountMinor,
                nightAmountMinor,
                0L,
                exact,
                false,
                null,
                List.of()
        );
    }
}

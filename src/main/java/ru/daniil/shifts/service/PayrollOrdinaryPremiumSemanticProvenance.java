package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.NightPremiumSourceLine;
import ru.daniil.shifts.service.PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure compatibility-safe bridge from proven ordinary NIGHT pricing source
 * identity into immutable semantic earning source lines.
 *
 * <p>Only exact single-date NIGHT money receives earning-period provenance.
 * Any remaining proven NIGHT aggregate is retained as one residual semantic
 * line with null quantity/period provenance. No money is split by date here.</p>
 */
public final class PayrollOrdinaryPremiumSemanticProvenance {

    private PayrollOrdinaryPremiumSemanticProvenance() {
    }

    /**
     * @return null when no exact source-line provenance is available, keeping
     * the pre-8A4E2B1 aggregate freeze path byte-for-byte semantic-compatible.
     */
    public static List<PayrollSemanticFreezeProjection.SemanticLine>
            nightLines(
                    OrdinaryPremiumPreview preview
            ) {
        Objects.requireNonNull(
                preview,
                "Ordinary premium preview is required"
        );

        if (!preview.ready()) {
            throw new IllegalArgumentException(
                    "Blocked ordinary premium preview cannot feed semantic provenance"
            );
        }

        List<NightPremiumSourceLine> exact =
                preview.exactNightPremiumSourceLines();

        if (exact.isEmpty()) {
            return null;
        }

        List<PayrollSemanticFreezeProjection.SemanticLine> result =
                new ArrayList<>();

        long exactAmount = 0L;

        for (NightPremiumSourceLine sourceLine : exact) {
            exactAmount =
                    Math.addExact(
                            exactAmount,
                            sourceLine.amountMinor()
                    );

            result.add(
                    new PayrollSemanticFreezeProjection.SemanticLine(
                            PayrollEarningKind.NIGHT_PREMIUM,
                            sourceLine.amountMinor(),
                            PayrollQualifiedQuantity.minutes(
                                    sourceLine.minutes()
                            ),
                            sourceLine.earningDate(),
                            sourceLine.earningDate(),
                            null,
                            null
                    )
            );
        }

        long residual =
                Math.subtractExact(
                        preview.nightPremiumAmountMinor(),
                        exactAmount
                );

        if (residual < 0L) {
            throw new IllegalStateException(
                    "Exact NIGHT provenance exceeds Payroll NIGHT aggregate"
            );
        }

        if (residual > 0L) {
            /*
             * The residual is still machine-proven NIGHT money, but its
             * date-level money allocation is not machine-owned. Preserve it
             * without invented quantity/period provenance.
             */
            result.add(
                    new PayrollSemanticFreezeProjection.SemanticLine(
                            PayrollEarningKind.NIGHT_PREMIUM,
                            residual
                    )
            );
        }

        return List.copyOf(
                result
        );
    }
}

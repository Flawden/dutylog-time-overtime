package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshotComponentLine;
import ru.daniil.shifts.service.PayrollSemanticFreezeProjection.ComponentLine;
import ru.daniil.shifts.service.PayrollSemanticFreezeProjection.SemanticLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-driven semantic source-line provenance for frozen generic
 * compensation components.
 *
 * <p>8A4E2B3A is deliberately narrow. Real payroll evidence proves that
 * HARMFUL_CONDITIONS money can follow the corresponding BASE_PAY source
 * periods while its own qualified minutes are not universally equal to
 * BASE_PAY minutes. Therefore this service may derive exact HARMFUL money
 * source-lines only from an explicit PERCENT_OF_BASE / EARNED_BASE_PAY
 * machine formula and already-proven detailed BASE_PAY money lines.</p>
 *
 * <p>The derived HARMFUL lines copy only earning periods. They deliberately do
 * not copy BASE_PAY qualified quantity or coverage. COMBINATION,
 * REGIONAL_COEFFICIENT and bonus kinds remain aggregate-only until their own
 * source/base truth is implemented; displayName and posting month are never
 * used as provenance.</p>
 */
@Service
public class PayrollCompensationComponentSemanticProvenance {

    /**
     * Maps immutable component explainability lines to semantic freeze input.
     * The returned list always preserves one ComponentLine per frozen
     * component line and exact aggregate money/order.
     */
    public List<ComponentLine> lines(
            List<PayrollSnapshotComponentLine> frozenComponentLines,
            List<SemanticLine> basePayLines
    ) {
        if (frozenComponentLines == null) {
            return null;
        }

        List<PayrollSnapshotComponentLine> frozen =
                List.copyOf(frozenComponentLines);

        if (frozen.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Frozen compensation component line is required"
            );
        }

        List<ComponentLine> result =
                new ArrayList<>();

        for (int index = 0; index < frozen.size(); index++) {
            PayrollSnapshotComponentLine line = frozen.get(index);

            if (line.getLineIndex() != index) {
                throw new IllegalStateException(
                        "Frozen compensation component semantic order is invalid"
                );
            }

            List<SemanticLine> detailed =
                    harmfulLines(
                            line,
                            basePayLines
                    );

            result.add(
                    new ComponentLine(
                            index,
                            line.getEarningKind(),
                            line.getAmountMinor(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            detailed
                    )
            );
        }

        return List.copyOf(result);
    }

    private List<SemanticLine> harmfulLines(
            PayrollSnapshotComponentLine component,
            List<SemanticLine> basePayLines
    ) {
        if (component.getEarningKind()
                != PayrollEarningKind.HARMFUL_CONDITIONS) {
            return null;
        }

        if (!"PERCENT_OF_BASE".equals(
                component.getCalculationType()
        )
                || !"EARNED_BASE_PAY".equals(
                component.getCalculationBase()
        )
                || component.getRateBps() == null) {
            return null;
        }

        if (basePayLines == null
                || basePayLines.isEmpty()) {
            return null;
        }

        List<SemanticLine> base =
                List.copyOf(basePayLines);

        long baseTotal = 0L;

        for (SemanticLine line : base) {
            Objects.requireNonNull(
                    line,
                    "Detailed BASE_PAY semantic line is required"
            );

            if (line.earningKind()
                    != PayrollEarningKind.BASE_PAY) {
                throw new IllegalStateException(
                        "HARMFUL provenance requires BASE_PAY source lines"
                );
            }

            if (line.earningPeriodFrom() == null
                    || line.earningPeriodTo() == null) {
                /*
                 * Aggregate BASE_PAY identity does not prove a source period.
                 * Keep HARMFUL aggregate-only rather than inventing one.
                 */
                return null;
            }

            baseTotal =
                    Math.addExact(
                            baseTotal,
                            line.amountMinor()
                    );
        }

        if (baseTotal
                != component.getReferenceBaseMinor()) {
            throw new IllegalStateException(
                    "HARMFUL reference base disagrees with detailed BASE_PAY money"
            );
        }

        int rateBps = component.getRateBps();

        long expectedAmount =
                CompensationComponentCalculationService
                        .percentageMoney(
                                baseTotal,
                                rateBps
                        );

        if (expectedAmount
                != component.getAmountMinor()) {
            throw new IllegalStateException(
                    "HARMFUL provenance formula disagrees with component aggregate"
            );
        }

        List<SemanticLine> result =
                new ArrayList<>();

        long cumulativeBase = 0L;
        long cumulativeAmount = 0L;

        for (SemanticLine baseLine : base) {
            cumulativeBase =
                    Math.addExact(
                            cumulativeBase,
                            baseLine.amountMinor()
                    );

            long pricedCumulative =
                    CompensationComponentCalculationService
                            .percentageMoney(
                                    cumulativeBase,
                                    rateBps
                            );

            long amount =
                    Math.subtractExact(
                            pricedCumulative,
                            cumulativeAmount
                    );

            /*
             * The immutable semantic line schema requires positive money.
             * If rounding creates a zero-money fragment, collapsing source
             * periods would fabricate attribution, so keep the component
             * aggregate-only instead.
             */
            if (amount <= 0L) {
                return null;
            }

            result.add(
                    new SemanticLine(
                            PayrollEarningKind.HARMFUL_CONDITIONS,
                            amount,
                            null,
                            baseLine.earningPeriodFrom(),
                            baseLine.earningPeriodTo(),
                            null,
                            null
                    )
            );

            cumulativeAmount = pricedCumulative;
        }

        if (cumulativeAmount
                != component.getAmountMinor()) {
            throw new IllegalStateException(
                    "HARMFUL source-line allocation lost component money"
            );
        }

        return List.copyOf(result);
    }
}

package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollSnapshotComponentLine;
import ru.daniil.shifts.service.PayrollCombinationEpisodeFactService.EpisodeFact;
import ru.daniil.shifts.service.PayrollRegionalCoefficientSourceFactService.SourceFact;
import ru.daniil.shifts.service.PayrollSemanticFreezeProjection.ComponentLine;
import ru.daniil.shifts.service.PayrollSemanticFreezeProjection.SemanticLine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * not copy BASE_PAY qualified quantity or coverage.</p>
 *
 * <p>8A4E2B3B adds a separate COMBINATION evidence path. Explicit observed
 * episode facts may carry source period, qualified minutes and payout money
 * even while the external substituted-employee salary remains unknown. Those
 * facts are accepted only when their money reconciles exactly to the frozen
 * component aggregate. No reference base is inferred from the episode facts,
 * from displayName or from the local component formula.</p>
 *
 * <p>8A4E2B3C2 adds explicit REGIONAL_COEFFICIENT source facts. Real source
 * evidence shows the regional line has its own observed earning period and is
 * not a synthetic split over every eligible-base source line. Therefore the
 * service accepts only explicit observed regional source lines whose money
 * reconciles to a frozen LOCAL_ELIGIBLE_EARNINGS formula. It never allocates
 * regional money from BASE_PAY/HARMFUL/NIGHT/bonus periods and never uses the
 * posting month as earning provenance.</p>
 *
 * <p>Bonus kinds remain aggregate-only until their own provenance stage.</p>
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
        return lines(
                frozenComponentLines,
                basePayLines,
                null,
                null,
                null
        );
    }

    /**
     * Canonical evidence-aware mapping used by the real Payroll freeze path.
     * A null combination-fact list means no explicit episode authority is
     * available and preserves the pre-B3B aggregate-only behavior.
     */
    public List<ComponentLine> lines(
            List<PayrollSnapshotComponentLine> frozenComponentLines,
            List<SemanticLine> basePayLines,
            List<EpisodeFact> combinationFacts,
            String payrollCurrencyCode
    ) {
        return lines(
                frozenComponentLines,
                basePayLines,
                combinationFacts,
                null,
                payrollCurrencyCode
        );
    }

    /**
     * Canonical B3C2 mapping with explicit COMBINATION and REGIONAL source
     * authorities. Null fact lists mean that exact source provenance is not
     * configured and preserve aggregate-only semantics.
     */
    public List<ComponentLine> lines(
            List<PayrollSnapshotComponentLine> frozenComponentLines,
            List<SemanticLine> basePayLines,
            List<EpisodeFact> combinationFacts,
            List<SourceFact> regionalFacts,
            String payrollCurrencyCode
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

        List<EpisodeFact> combination =
                combinationFacts == null
                        ? null
                        : List.copyOf(combinationFacts);

        List<SourceFact> regional =
                regionalFacts == null
                        ? null
                        : List.copyOf(regionalFacts);

        Set<Long> frozenCombinationComponentIds =
                new HashSet<>();

        Set<Long> frozenRegionalComponentIds =
                new HashSet<>();

        List<ComponentLine> result =
                new ArrayList<>();

        for (int index = 0; index < frozen.size(); index++) {
            PayrollSnapshotComponentLine line = frozen.get(index);

            if (line.getLineIndex() != index) {
                throw new IllegalStateException(
                        "Frozen compensation component semantic order is invalid"
                );
            }

            if (line.getEarningKind()
                    == PayrollEarningKind.COMBINATION) {
                frozenCombinationComponentIds.add(
                        line.getComponentId()
                );
            }

            if (line.getEarningKind()
                    == PayrollEarningKind.REGIONAL_COEFFICIENT) {
                frozenRegionalComponentIds.add(
                        line.getComponentId()
                );
            }

            List<SemanticLine> detailed =
                    harmfulLines(
                            line,
                            basePayLines
                    );

            if (detailed == null) {
                detailed =
                        combinationLines(
                                line,
                                combination,
                                payrollCurrencyCode
                        );
            }

            if (detailed == null) {
                detailed =
                        regionalLines(
                                line,
                                regional,
                                payrollCurrencyCode
                        );
            }

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

        if (combination != null) {
            for (EpisodeFact fact : combination) {
                if (!frozenCombinationComponentIds.contains(
                        fact.componentId()
                )) {
                    throw new IllegalStateException(
                            "Explicit COMBINATION episode fact has no frozen COMBINATION component"
                    );
                }
            }
        }

        if (regional != null) {
            for (SourceFact fact : regional) {
                if (!frozenRegionalComponentIds.contains(
                        fact.componentId()
                )) {
                    throw new IllegalStateException(
                            "Explicit REGIONAL source fact has no frozen REGIONAL_COEFFICIENT component"
                    );
                }
            }
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
    private List<SemanticLine> combinationLines(
            PayrollSnapshotComponentLine component,
            List<EpisodeFact> combinationFacts,
            String payrollCurrencyCode
    ) {
        if (component.getEarningKind()
                != PayrollEarningKind.COMBINATION) {
            return null;
        }

        if (combinationFacts == null
                || combinationFacts.isEmpty()) {
            return null;
        }

        if (payrollCurrencyCode == null
                || !payrollCurrencyCode.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "COMBINATION provenance requires frozen Payroll currency"
            );
        }

        List<EpisodeFact> matching =
                combinationFacts.stream()
                        .filter(fact ->
                                fact.componentId()
                                        == component.getComponentId()
                        )
                        .toList();

        if (matching.isEmpty()) {
            return null;
        }

        long amountTotal = 0L;
        List<SemanticLine> result =
                new ArrayList<>();

        for (EpisodeFact fact : matching) {
            if (!payrollCurrencyCode.equals(
                    fact.currencyCode()
            )) {
                throw new IllegalStateException(
                        "COMBINATION episode currency disagrees with frozen Payroll currency"
                );
            }

            amountTotal =
                    Math.addExact(
                            amountTotal,
                            fact.amountMinor()
                    );

            result.add(
                    new SemanticLine(
                            PayrollEarningKind.COMBINATION,
                            fact.amountMinor(),
                            PayrollQualifiedQuantity.minutes(
                                    fact.qualifiedMinutes()
                            ),
                            fact.periodFrom(),
                            fact.periodTo(),
                            null,
                            null
                    )
            );
        }

        if (amountTotal
                != component.getAmountMinor()) {
            throw new IllegalStateException(
                    "COMBINATION episode money disagrees with frozen component aggregate"
            );
        }

        return List.copyOf(result);
    }

    private List<SemanticLine> regionalLines(
            PayrollSnapshotComponentLine component,
            List<SourceFact> regionalFacts,
            String payrollCurrencyCode
    ) {
        if (component.getEarningKind()
                != PayrollEarningKind.REGIONAL_COEFFICIENT) {
            return null;
        }

        if (regionalFacts == null
                || regionalFacts.isEmpty()) {
            return null;
        }

        List<SourceFact> matching =
                regionalFacts.stream()
                        .filter(fact ->
                                fact.componentId()
                                        == component.getComponentId()
                        )
                        .toList();

        if (matching.isEmpty()) {
            return null;
        }

        if (!"PERCENT_OF_BASE".equals(
                component.getCalculationType()
        )
                || !"LOCAL_ELIGIBLE_EARNINGS".equals(
                component.getCalculationBase()
        )
                || component.getRateBps() == null) {
            throw new IllegalStateException(
                    "Explicit REGIONAL source fact requires frozen LOCAL_ELIGIBLE_EARNINGS percentage formula"
            );
        }

        if (payrollCurrencyCode == null
                || !payrollCurrencyCode.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "REGIONAL provenance requires frozen Payroll currency"
            );
        }

        long expectedAmount =
                CompensationComponentCalculationService
                        .percentageMoney(
                                component.getReferenceBaseMinor(),
                                component.getRateBps()
                        );

        if (expectedAmount
                != component.getAmountMinor()) {
            throw new IllegalStateException(
                    "REGIONAL frozen formula disagrees with component aggregate"
            );
        }

        long amountTotal = 0L;
        List<SemanticLine> result =
                new ArrayList<>();

        for (SourceFact fact : matching) {
            if (!payrollCurrencyCode.equals(
                    fact.currencyCode()
            )) {
                throw new IllegalStateException(
                        "REGIONAL source currency disagrees with frozen Payroll currency"
                );
            }

            amountTotal =
                    Math.addExact(
                            amountTotal,
                            fact.amountMinor()
                    );

            result.add(
                    new SemanticLine(
                            PayrollEarningKind.REGIONAL_COEFFICIENT,
                            fact.amountMinor(),
                            null,
                            fact.periodFrom(),
                            fact.periodTo(),
                            null,
                            null
                    )
            );
        }

        if (amountTotal
                != component.getAmountMinor()) {
            throw new IllegalStateException(
                    "REGIONAL source money disagrees with frozen component aggregate"
            );
        }

        return List.copyOf(result);
    }

}

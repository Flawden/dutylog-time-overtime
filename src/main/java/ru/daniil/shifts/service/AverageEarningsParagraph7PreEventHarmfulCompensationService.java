package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.CompensationComponentCalculationService.CalculatedLine;
import ru.daniil.shifts.service.CompensationComponentCalculationService.ComponentRule;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Context;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Projection;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-7 range-bound HARMFUL_CONDITIONS calculation.
 *
 * <p>The legal source window is exactly {@code [eventMonthStart,eventDate)}.
 * J3B2 already owns the BASE_PAY amount for that range, while J3B4 owns
 * ordinary NIGHT / HOLIDAY premium money. This layer reuses the same
 * effective generic-component resolver and pure compensation-component
 * calculator as Native Payroll, but admits only the one harmful configuration
 * shape whose pre-event base is already proven: PERCENT_OF_BASE over
 * EARNED_BASE_PAY.</p>
 *
 * <p>FIXED_AMOUNT, NOMINAL_SALARY and LOCAL_ELIGIBLE_EARNINGS harmful rules are
 * deliberately blocked here. A monthly/fixed or broader semantic base cannot
 * be sliced to the event date without inventing accrual provenance.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventHarmfulCompensationService {
    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_HARMFUL_WINDOW_MISMATCH";
    public static final String CONFIGURATION_NOT_RANGE_BOUND =
            "PP_540_P7_PRE_EVENT_HARMFUL_CONFIGURATION_NOT_RANGE_BOUND";
    public static final String COMPONENT_INVALID =
            "PP_540_P7_PRE_EVENT_HARMFUL_COMPONENT_INVALID";
    public static final String CALCULATION_BLOCKED =
            "PP_540_P7_PRE_EVENT_HARMFUL_CALCULATION_BLOCKED";

    private final CompensationComponentResolverService resolver;
    private final CompensationComponentCalculationService calculator;

    public AverageEarningsParagraph7PreEventHarmfulCompensationService(
            CompensationComponentResolverService resolver,
            CompensationComponentCalculationService calculator
    ) {
        this.resolver = Objects.requireNonNull(
                resolver,
                "Paragraph-7 harmful compensation requires component resolver"
        );
        this.calculator = Objects.requireNonNull(
                calculator,
                "Paragraph-7 harmful compensation requires canonical component calculator"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinaryPremium
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 harmful compensation requires user"
        );
        Objects.requireNonNull(
                ordinaryPremium,
                "Paragraph-7 harmful compensation requires ordinary-premium authority"
        );
        if (!ordinaryPremium.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 ordinary-premium authority cannot reach harmful money"
            );
        }

        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semanticFacts =
                Objects.requireNonNull(
                        ordinaryPremium.semanticFacts(),
                        "Paragraph-7 harmful compensation lost semantic wage provenance"
                );
        if (!semanticFacts.ready()) {
            throw new IllegalArgumentException(
                    "Blocked paragraph-7 semantic wage authority cannot reach harmful money"
            );
        }

        AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay =
                Objects.requireNonNull(
                        semanticFacts.basePay(),
                        "Paragraph-7 harmful compensation lost BASE_PAY calculation"
                );
        AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority =
                Objects.requireNonNull(
                        basePay.authority(),
                        "Paragraph-7 harmful compensation lost BASE_PAY authority"
                );

        LocalDate eventDate = Objects.requireNonNull(
                ordinaryPremium.eventDate(),
                "Paragraph-7 harmful compensation requires event date"
        );
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (!periodFrom.equals(ordinaryPremium.periodFrom())
                || !cutoffExclusive.equals(ordinaryPremium.cutoffExclusive())
                || !eventDate.equals(semanticFacts.eventDate())
                || !periodFrom.equals(semanticFacts.periodFrom())
                || !cutoffExclusive.equals(semanticFacts.cutoffExclusive())
                || !eventDate.equals(authority.eventDate())
                || !periodFrom.equals(authority.periodFrom())
                || !cutoffExclusive.equals(authority.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    ordinaryPremium,
                    AUTHORITY_WINDOW_MISMATCH,
                    "Paragraph-7 provenance does not match the legal pre-event harmful-compensation window"
            );
        }

        if (!authority.workedTimePresent()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    ordinaryPremium,
                    null,
                    0L,
                    List.of()
            );
        }

        String expectedCurrency = basePay.currencyCode();
        if (expectedCurrency == null || !expectedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Worked paragraph-7 harmful compensation requires canonical BASE_PAY currency"
            );
        }

        YearMonth eventMonth = YearMonth.from(eventDate);
        List<CompensationComponentVersion> resolved = Objects.requireNonNull(
                resolver.resolve(user, eventMonth),
                "Paragraph-7 harmful compensation resolver returned null"
        );
        if (resolved.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException(
                    "Paragraph-7 harmful compensation resolver returned null component version"
            );
        }

        List<CompensationComponentVersion> harmful = resolved.stream()
                .filter(CompensationComponentVersion::isEnabled)
                .filter(version ->
                        version.getEarningKind()
                                == PayrollEarningKind.HARMFUL_CONDITIONS
                )
                .toList();

        if (harmful.isEmpty()) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    ordinaryPremium,
                    expectedCurrency,
                    0L,
                    List.of()
            );
        }

        List<ComponentRule> rules = new ArrayList<>();
        for (CompensationComponentVersion version : harmful) {
            if (version.getCalculationType() != CalculationType.PERCENT_OF_BASE
                    || version.getCalculationBase() != CalculationBase.EARNED_BASE_PAY) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        ordinaryPremium,
                        CONFIGURATION_NOT_RANGE_BOUND,
                        "HARMFUL_CONDITIONS can reach paragraph-7 pre-event money only from EARNED_BASE_PAY"
                );
            }

            LocalDate effectiveFrom = version.getEffectiveFrom();
            if (effectiveFrom == null || effectiveFrom.isAfter(periodFrom)) {
                throw new IllegalStateException(
                        "Paragraph-7 harmful component resolver returned an invalid effective version"
                );
            }

            CompensationComponent component = version.getComponent();
            if (component == null
                    || component.getId() == null
                    || version.getId() == null) {
                throw new IllegalStateException(
                        "Paragraph-7 harmful component identity is incomplete"
                );
            }

            try {
                rules.add(
                        new ComponentRule(
                                component.getId(),
                                version.getId(),
                                effectiveFrom,
                                version.getDisplayName(),
                                version.getEarningKind(),
                                version.getCalculationType(),
                                version.getCalculationBase(),
                                version.getRateBps(),
                                version.getAmountMinor(),
                                version.getCurrencyCode(),
                                true
                        )
                );
            } catch (IllegalArgumentException ex) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        ordinaryPremium,
                        COMPONENT_INVALID,
                        ex.getMessage() == null
                                ? "Invalid paragraph-7 harmful compensation component"
                                : ex.getMessage()
                );
            }
        }

        Context context = new Context(
                expectedCurrency,
                authority.payMode(),
                "SALARY".equals(authority.payMode())
                        ? authority.monthlySalaryMinor()
                        : null,
                basePay.basePayAmountMinor()
        );

        final Projection projection;
        try {
            projection = Objects.requireNonNull(
                    calculator.calculate(
                            context,
                            rules
                    ),
                    "Paragraph-7 harmful compensation calculator returned null"
            );
        } catch (IllegalArgumentException ex) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    ordinaryPremium,
                    CALCULATION_BLOCKED,
                    ex.getMessage() == null
                            ? "Paragraph-7 harmful compensation calculation is blocked"
                            : ex.getMessage()
            );
        }

        if (projection.lines().size() != rules.size()) {
            throw new IllegalStateException(
                    "Paragraph-7 harmful compensation calculator changed component cardinality"
            );
        }

        long sum = 0L;
        for (CalculatedLine line : projection.lines()) {
            if (line.earningKind() != PayrollEarningKind.HARMFUL_CONDITIONS
                    || line.calculationType() != CalculationType.PERCENT_OF_BASE
                    || line.calculationBase() != CalculationBase.EARNED_BASE_PAY
                    || line.referenceBaseMinor() != basePay.basePayAmountMinor()) {
                throw new IllegalStateException(
                        "Paragraph-7 harmful compensation calculator changed proven semantic base"
                );
            }
            sum = Math.addExact(
                    sum,
                    line.amountMinor()
            );
        }
        if (sum != projection.totalAmountMinor()) {
            throw new IllegalStateException(
                    "Paragraph-7 harmful compensation projection does not preserve money"
            );
        }

        return Resolution.ready(
                eventDate,
                periodFrom,
                ordinaryPremium,
                expectedCurrency,
                projection.totalAmountMinor(),
                projection.lines()
        );
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinaryPremium,
            String currencyCode,
            long harmfulAmountMinor,
            List<CalculatedLine> lines
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-7 harmful compensation event date is required"
            );
            Objects.requireNonNull(
                    periodFrom,
                    "Paragraph-7 harmful compensation period start is required"
            );
            Objects.requireNonNull(
                    cutoffExclusive,
                    "Paragraph-7 harmful compensation cutoff is required"
            );
            Objects.requireNonNull(
                    ordinaryPremium,
                    "Paragraph-7 harmful compensation provenance is required"
            );
            lines = lines == null
                    ? List.of()
                    : List.copyOf(lines);

            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)
                    || harmfulAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 harmful compensation resolution is invalid"
                );
            }

            if (ready) {
                if (blockingReason != null || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 harmful compensation cannot contain blocker"
                    );
                }
                if (currencyCode != null && !currencyCode.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 harmful compensation has invalid currency"
                    );
                }
                long lineMoney = 0L;
                for (CalculatedLine line : lines) {
                    lineMoney = Math.addExact(
                            lineMoney,
                            line.amountMinor()
                    );
                }
                if (lineMoney != harmfulAmountMinor) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 harmful compensation lines do not preserve money"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || currencyCode != null
                        || harmfulAmountMinor != 0L
                        || !lines.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 harmful compensation cannot expose partial money"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinaryPremium,
                String currencyCode,
                long harmfulAmountMinor,
                List<CalculatedLine> lines
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    ordinaryPremium,
                    currencyCode,
                    harmfulAmountMinor,
                    lines
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinaryPremium,
                String reason,
                String message
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    message,
                    ordinaryPremium,
                    null,
                    0L,
                    List.of()
            );
        }

        public boolean harmfulMoneyPresent() {
            return ready && harmfulAmountMinor > 0L;
        }
    }
}

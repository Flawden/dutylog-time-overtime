package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.service.CompensationComponentCalculationService.ComponentRule;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Context;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Projection;

import java.time.YearMonth;
import java.util.List;

/**
 * Payroll-facing adapter for generic compensation components.
 *
 * Resolver owns effective-dated configuration identity.
 * Calculator owns pure money semantics.
 * This adapter turns expected historical/configuration inability into an
 * explicit preview readiness state instead of hiding corruption.
 */
@Service
public class PayrollCompensationComponentPreviewService {

    public static final String PAYROLL_CURRENCY_MISMATCH =
            "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH";

    public static final String PAYROLL_BASE_UNAVAILABLE =
            "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE";

    public static final String PAYROLL_COMPONENT_INVALID =
            "PAYROLL_COMP_COMPONENT_INVALID";

    private final CompensationComponentResolverService resolver;
    private final CompensationComponentCalculationService calculator;

    public PayrollCompensationComponentPreviewService(
            CompensationComponentResolverService resolver,
            CompensationComponentCalculationService calculator
    ) {
        this.resolver = resolver;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public ComponentPreview preview(
            AppUser user,
            YearMonth month,
            Context context,
            String unavailableReason
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Payroll component preview requires user"
            );
        }

        if (month == null) {
            throw new IllegalArgumentException(
                    "Payroll component preview requires month"
            );
        }

        List<CompensationComponentVersion> resolved =
                resolver.resolve(
                        user,
                        month
                );

        List<CompensationComponentVersion> enabled =
                resolved.stream()
                        .filter(
                                CompensationComponentVersion::isEnabled
                        )
                        .toList();

        /*
         * No configured/enabled generic component means this phase is a valid
         * zero-money identity even when base compensation itself is not ready.
         * The ordinary Payroll readiness layer still reports its own blocker.
         */
        if (enabled.isEmpty()) {
            return ready(
                    month,
                    emptyProjection()
            );
        }

        if (context == null) {
            return blocked(
                    month,
                    unavailableReason == null
                            ? PAYROLL_BASE_UNAVAILABLE
                            : unavailableReason,
                    "База расчёта generic compensation components недоступна"
            );
        }

        for (CompensationComponentVersion version : enabled) {
            if (version.getCalculationType()
                    == CalculationType.FIXED_AMOUNT
                    && !context.currencyCode().equals(
                            version.getCurrencyCode()
                    )) {
                return blocked(
                        month,
                        PAYROLL_CURRENCY_MISMATCH,
                        "Валюта фиксированного компонента не совпадает с валютой Payroll"
                );
            }

            if (version.getCalculationType()
                    == CalculationType.PERCENT_OF_BASE
                    && version.getCalculationBase()
                    == CalculationBase.NOMINAL_SALARY
                    && (!"SALARY".equals(
                            context.payMode()
                    )
                    || context.monthlySalaryMinor() == null
                    || context.monthlySalaryMinor() <= 0)) {
                return blocked(
                        month,
                        PAYROLL_BASE_UNAVAILABLE,
                        "NOMINAL_SALARY недоступен для этого Payroll"
                );
            }
        }

        try {
            List<ComponentRule> rules =
                    resolved.stream()
                            .map(this::rule)
                            .toList();

            return ready(
                    month,
                    calculator.calculate(
                            context,
                            rules
                    )
            );

        } catch (IllegalArgumentException ex) {
            return blocked(
                    month,
                    PAYROLL_COMPONENT_INVALID,
                    ex.getMessage() == null
                            ? "Некорректный generic compensation component"
                            : ex.getMessage()
            );
        }
    }

    private ComponentRule rule(
            CompensationComponentVersion version
    ) {
        CompensationComponent component =
                version.getComponent();

        if (component == null
                || component.getId() == null
                || version.getId() == null) {
            throw new IllegalArgumentException(
                    "Compensation component identity is incomplete"
            );
        }

        return new ComponentRule(
                component.getId(),
                version.getId(),
                version.getEffectiveFrom(),
                version.getDisplayName(),
                version.getEarningKind(),
                version.getCalculationType(),
                version.getCalculationBase(),
                version.getRateBps(),
                version.getAmountMinor(),
                version.getCurrencyCode(),
                version.isEnabled()
        );
    }

    private static ComponentPreview ready(
            YearMonth month,
            Projection projection
    ) {
        return new ComponentPreview(
                month,
                true,
                null,
                null,
                projection
        );
    }

    private static ComponentPreview blocked(
            YearMonth month,
            String reason,
            String message
    ) {
        return new ComponentPreview(
                month,
                false,
                reason,
                message,
                emptyProjection()
        );
    }

    private static Projection emptyProjection() {
        return new Projection(
                0L,
                List.of(),
                null
        );
    }

    public record ComponentPreview(
            YearMonth month,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            Projection projection
    ) {
        public ComponentPreview {
            if (month == null
                    || projection == null) {
                throw new IllegalArgumentException(
                        "Invalid Payroll component preview"
                );
            }

            if (ready
                    && (blockingReason != null
                    || blockingMessage != null)) {
                throw new IllegalArgumentException(
                        "Ready component preview cannot contain blocker"
                );
            }

            if (!ready
                    && (blockingReason == null
                    || blockingReason.isBlank())) {
                throw new IllegalArgumentException(
                        "Blocked component preview requires reason"
                );
            }
        }
    }
}

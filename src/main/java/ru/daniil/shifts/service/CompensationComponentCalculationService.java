package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure monthly calculation kernel for already-resolved compensation components.
 *
 * This service does not:
 * - load configuration;
 * - select effective versions;
 * - read Payroll repositories;
 * - classify time;
 * - calculate NIGHT/HOLIDAY/OVERTIME;
 * - mutate snapshots.
 *
 * It receives explicit semantic inputs and returns explainable component lines.
 */
@Service
public class CompensationComponentCalculationService {

    public static final int BASIS_POINTS = 10_000;

    public Projection calculate(
            Context context,
            List<ComponentRule> source
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Compensation component context is required"
            );
        }

        List<ComponentRule> rules =
                source == null
                        ? List.of()
                        : List.copyOf(source);

        if (rules.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException(
                    "Compensation component rules cannot contain null"
            );
        }

        Set<Long> componentIds =
                new HashSet<>();

        for (ComponentRule rule : rules) {
            if (!componentIds.add(rule.componentId())) {
                throw new IllegalArgumentException(
                        "More than one active version resolved for compensation component "
                                + rule.componentId()
                );
            }
        }

        List<ComponentRule> ordered =
                rules.stream()
                        .sorted(
                                Comparator
                                        .comparingLong(
                                                ComponentRule::componentId
                                        )
                                        .thenComparingLong(
                                                ComponentRule::versionId
                                        )
                        )
                        .toList();

        List<CalculatedLine> lines =
                new ArrayList<>();

        long total = 0L;

        for (ComponentRule rule : ordered) {
            if (!rule.enabled()) {
                continue;
            }

            CalculatedLine line =
                    calculateLine(
                            context,
                            rule
                    );

            try {
                total =
                        Math.addExact(
                                total,
                                line.amountMinor()
                        );
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "Compensation component total overflow",
                        ex
                );
            }

            lines.add(line);
        }

        List<CalculatedLine> immutable =
                List.copyOf(lines);

        return new Projection(
                total,
                immutable,
                fingerprint(
                        context,
                        immutable
                )
        );
    }

    private CalculatedLine calculateLine(
            Context context,
            ComponentRule rule
    ) {
        return switch (rule.calculationType()) {
            case FIXED_AMOUNT ->
                    fixed(
                            context,
                            rule
                    );

            case PERCENT_OF_BASE ->
                    percentage(
                            context,
                            rule
                    );
        };
    }

    private CalculatedLine fixed(
            Context context,
            ComponentRule rule
    ) {
        if (!context.currencyCode()
                .equals(rule.currencyCode())) {
            throw new IllegalArgumentException(
                    "Fixed compensation component currency "
                            + rule.currencyCode()
                            + " does not match Payroll currency "
                            + context.currencyCode()
            );
        }

        return new CalculatedLine(
                rule.componentId(),
                rule.versionId(),
                rule.effectiveFrom(),
                rule.displayName(),
                rule.earningKind(),
                rule.calculationType(),
                null,
                null,
                rule.amountMinor(),
                rule.currencyCode(),
                0L,
                rule.amountMinor()
        );
    }

    private CalculatedLine percentage(
            Context context,
            ComponentRule rule
    ) {
        long referenceBase =
                switch (rule.calculationBase()) {
                    case EARNED_BASE_PAY ->
                            context.earnedBasePayMinor();

                    case NOMINAL_SALARY -> {
                        if (!"SALARY".equals(
                                context.payMode()
                        )
                                || context.monthlySalaryMinor()
                                == null
                                || context.monthlySalaryMinor()
                                <= 0L) {
                            throw new IllegalArgumentException(
                                    "NOMINAL_SALARY component requires SALARY compensation"
                            );
                        }

                        yield context.monthlySalaryMinor();
                    }
                };

        long amount =
                percentageMoney(
                        referenceBase,
                        rule.rateBps()
                );

        return new CalculatedLine(
                rule.componentId(),
                rule.versionId(),
                rule.effectiveFrom(),
                rule.displayName(),
                rule.earningKind(),
                rule.calculationType(),
                rule.calculationBase(),
                rule.rateBps(),
                null,
                null,
                referenceBase,
                amount
        );
    }

    private long percentageMoney(
            long baseMinor,
            int rateBps
    ) {
        try {
            return BigDecimal
                    .valueOf(baseMinor)
                    .multiply(
                            BigDecimal.valueOf(
                                    rateBps
                            )
                    )
                    .divide(
                            BigDecimal.valueOf(
                                    BASIS_POINTS
                            ),
                            0,
                            RoundingMode.HALF_UP
                    )
                    .longValueExact();

        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Compensation component amount overflow",
                    ex
            );
        }
    }

    private String fingerprint(
            Context context,
            List<CalculatedLine> lines
    ) {
        if (lines.isEmpty()) {
            return null;
        }

        StringBuilder canonical =
                new StringBuilder();

        boolean semanticIdentityPresent =
                lines.stream()
                        .anyMatch(
                                line ->
                                        line.earningKind() != null
                        );

        token(
                canonical,
                semanticIdentityPresent
                        ? "DUTYLOG_COMP_COMPONENT_PROJECTION_V2"
                        : "DUTYLOG_COMP_COMPONENT_PROJECTION_V1"
        );

        token(
                canonical,
                context.currencyCode()
        );
        token(
                canonical,
                context.payMode()
        );
        token(
                canonical,
                lines.size()
        );

        for (CalculatedLine line : lines) {
            token(canonical, line.componentId());
            token(canonical, line.versionId());
            token(canonical, line.effectiveFrom());
            token(canonical, line.displayName());

            if (semanticIdentityPresent) {
                token(
                        canonical,
                        line.earningKind()
                );
            }

            token(canonical, line.calculationType());
            token(canonical, line.calculationBase());
            token(canonical, line.rateBps());
            token(canonical, line.configuredAmountMinor());
            token(canonical, line.configuredCurrencyCode());
            token(canonical, line.referenceBaseMinor());
            token(canonical, line.amountMinor());
        }

        return sha256(
                canonical.toString()
        );
    }

    private static void token(
            StringBuilder target,
            Object value
    ) {
        if (value == null) {
            target.append("-1:|");
            return;
        }

        String text =
                String.valueOf(value);

        target
                .append(text.length())
                .append(':')
                .append(text)
                .append('|');
    }

    private static String sha256(
            String value
    ) {
        try {
            return HexFormat
                    .of()
                    .formatHex(
                            MessageDigest
                                    .getInstance(
                                            "SHA-256"
                                    )
                                    .digest(
                                            value.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    );

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    private static String normalizeCurrency(
            String value
    ) {
        String currency =
                value == null
                        ? ""
                        : value.trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Compensation component currency must contain three letters"
            );
        }

        return currency;
    }

    private static String normalizePayMode(
            String value
    ) {
        String mode =
                value == null
                        ? ""
                        : value.trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

        if (!"HOURLY".equals(mode)
                && !"SALARY".equals(mode)) {
            throw new IllegalArgumentException(
                    "Compensation component context requires HOURLY or SALARY pay mode"
            );
        }

        return mode;
    }

    public record Context(
            String currencyCode,
            String payMode,
            Long monthlySalaryMinor,
            long earnedBasePayMinor
    ) {
        public Context {
            currencyCode =
                    normalizeCurrency(
                            currencyCode
                    );

            payMode =
                    normalizePayMode(
                            payMode
                    );

            if (earnedBasePayMinor < 0L) {
                throw new IllegalArgumentException(
                        "Earned base pay cannot be negative"
                );
            }

            if ("HOURLY".equals(payMode)) {
                if (monthlySalaryMinor != null) {
                    throw new IllegalArgumentException(
                            "HOURLY component context cannot contain monthly salary"
                    );
                }
            } else {
                if (monthlySalaryMinor == null
                        || monthlySalaryMinor <= 0L) {
                    throw new IllegalArgumentException(
                            "SALARY component context requires positive monthly salary"
                    );
                }
            }
        }
    }

    public record ComponentRule(
            long componentId,
            long versionId,
            LocalDate effectiveFrom,
            String displayName,
            PayrollEarningKind earningKind,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long amountMinor,
            String currencyCode,
            boolean enabled
    ) {
        public ComponentRule(
                long componentId,
                long versionId,
                LocalDate effectiveFrom,
                String displayName,
                CalculationType calculationType,
                CalculationBase calculationBase,
                Integer rateBps,
                Long amountMinor,
                String currencyCode,
                boolean enabled
        ) {
            this(
                    componentId,
                    versionId,
                    effectiveFrom,
                    displayName,
                    null,
                    calculationType,
                    calculationBase,
                    rateBps,
                    amountMinor,
                    currencyCode,
                    enabled
            );
        }

        public ComponentRule {
            if (componentId <= 0L
                    || versionId <= 0L) {
                throw new IllegalArgumentException(
                        "Compensation component identity must be positive"
                );
            }

            if (effectiveFrom == null
                    || effectiveFrom.getDayOfMonth() != 1) {
                throw new IllegalArgumentException(
                        "Compensation component effective date must be first day of month"
                );
            }

            displayName =
                    displayName == null
                            ? ""
                            : displayName.trim();

            if (displayName.isEmpty()
                    || displayName.length() > 120) {
                throw new IllegalArgumentException(
                        "Compensation component name must contain 1..120 characters"
                );
            }

            if (calculationType == null) {
                throw new IllegalArgumentException(
                        "Compensation component calculation type is required"
                );
            }

            if (earningKind != null
                    && !earningKind
                            .isGenericCompensationComponentKind()) {
                throw new IllegalArgumentException(
                        "Unsupported generic compensation component earning kind"
                );
            }

            switch (calculationType) {
                case FIXED_AMOUNT -> {
                    if (calculationBase != null
                            || rateBps != null
                            || amountMinor == null
                            || amountMinor < 1L
                            || amountMinor > 1_000_000_000_000L) {
                        throw new IllegalArgumentException(
                                "Invalid fixed compensation component"
                        );
                    }

                    currencyCode =
                            normalizeCurrency(
                                    currencyCode
                            );
                }

                case PERCENT_OF_BASE -> {
                    if (calculationBase == null
                            || rateBps == null
                            || rateBps < 1
                            || rateBps > 10_000_000
                            || amountMinor != null
                            || currencyCode != null) {
                        throw new IllegalArgumentException(
                                "Invalid percentage compensation component"
                        );
                    }
                }
            }
        }
    }

    public record CalculatedLine(
            long componentId,
            long versionId,
            LocalDate effectiveFrom,
            String displayName,
            PayrollEarningKind earningKind,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long configuredAmountMinor,
            String configuredCurrencyCode,
            long referenceBaseMinor,
            long amountMinor
    ) {
        public CalculatedLine(
                long componentId,
                long versionId,
                LocalDate effectiveFrom,
                String displayName,
                CalculationType calculationType,
                CalculationBase calculationBase,
                Integer rateBps,
                Long configuredAmountMinor,
                String configuredCurrencyCode,
                long referenceBaseMinor,
                long amountMinor
        ) {
            this(
                    componentId,
                    versionId,
                    effectiveFrom,
                    displayName,
                    null,
                    calculationType,
                    calculationBase,
                    rateBps,
                    configuredAmountMinor,
                    configuredCurrencyCode,
                    referenceBaseMinor,
                    amountMinor
            );
        }

        public CalculatedLine {
            if (componentId <= 0L
                    || versionId <= 0L
                    || effectiveFrom == null
                    || displayName == null
                    || displayName.isBlank()
                    || calculationType == null
                    || referenceBaseMinor < 0L
                    || amountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Invalid calculated compensation component line"
                );
            }

            if (earningKind != null
                    && !earningKind
                            .isGenericCompensationComponentKind()) {
                throw new IllegalArgumentException(
                        "Unsupported calculated compensation earning kind"
                );
            }
        }
    }

    public record Projection(
            long totalAmountMinor,
            List<CalculatedLine> lines,
            String fingerprint
    ) {
        public Projection {
            if (totalAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Compensation component total cannot be negative"
                );
            }

            lines =
                    lines == null
                            ? List.of()
                            : List.copyOf(lines);

            if (lines.isEmpty()) {
                if (totalAmountMinor != 0L
                        || fingerprint != null) {
                    throw new IllegalArgumentException(
                            "Empty component projection must contain zero money and no fingerprint"
                    );
                }
            } else {
                if (fingerprint == null
                        || !fingerprint.matches(
                                "[0-9a-f]{64}"
                        )) {
                    throw new IllegalArgumentException(
                            "Non-empty component projection requires fingerprint"
                    );
                }
            }
        }
    }
}

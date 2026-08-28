package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollEarningPhase;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollQuantityUnit;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningLine;
import ru.daniil.shifts.model.PayrollSnapshotEarningManifest;
import ru.daniil.shifts.repo.PayrollSnapshotEarningLineRepository;
import ru.daniil.shifts.repo.PayrollSnapshotEarningManifestRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only immutable historical semantic-earnings source.
 *
 * This service deliberately stops before average-earnings law/formulas.
 *
 * Contract:
 * - the normal reference window is the 12 calendar months immediately
 *   preceding the event month;
 * - the latest Payroll snapshot revision is selected independently for every
 *   reference month;
 * - legacy snapshots without machine-owned semantic earning lines block the
 *   source instead of reconstructing meaning from gross, displayName,
 *   generic component names or aggregate money;
 * - no partial 12-month result escapes a blocked resolution.
 */
@Service
public class PayrollHistoricalSemanticEarningsService {

    private final PayrollSnapshotRepository snapshots;
    private final PayrollSnapshotEarningLineRepository earningLines;
    private final PayrollSnapshotEarningManifestRepository earningManifests;

    public PayrollHistoricalSemanticEarningsService(
            PayrollSnapshotRepository snapshots,
            PayrollSnapshotEarningLineRepository earningLines,
            PayrollSnapshotEarningManifestRepository earningManifests
    ) {
        this.snapshots =
                Objects.requireNonNull(
                        snapshots,
                        "Payroll snapshot repository is required"
                );

        this.earningLines =
                Objects.requireNonNull(
                        earningLines,
                        "Payroll snapshot earning line repository is required"
                );

        this.earningManifests =
                Objects.requireNonNull(
                        earningManifests,
                        "Payroll snapshot earning manifest repository is required"
                );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            YearMonth eventMonth
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Historical semantic earnings require user"
            );
        }

        if (eventMonth == null) {
            throw new IllegalArgumentException(
                    "Historical semantic earnings require event month"
            );
        }

        YearMonth referenceFrom =
                eventMonth.minusMonths(
                        12
                );

        YearMonth referenceTo =
                eventMonth.minusMonths(
                        1
                );

        List<YearMonth> required =
                new ArrayList<>(
                        12
                );

        for (int offset = 0;
                offset < 12;
                offset++) {
            required.add(
                    referenceFrom.plusMonths(
                            offset
                    )
            );
        }

        RequiredResolution selected =
                resolveRequiredMonths(
                        user,
                        eventMonth,
                        required
                );

        if (!selected.ready()) {
            return Resolution.blocked(
                    referenceFrom,
                    referenceTo,
                    selected.blockingReason(),
                    selected.blockingPeriod()
            );
        }

        return Resolution.ready(
                referenceFrom,
                referenceTo,
                selected.currencyCode(),
                selected.months()
        );
    }

    /**
     * Resolves only months proven by a higher-level authority to require
     * immutable Payroll history.
     *
     * <p>The ordinary {@link #resolve(AppUser, YearMonth)} contract remains
     * strict 12/12. This boundary exists for integrations such as average
     * earnings, where explicit employment history can prove a whole reference
     * month was outside employment and therefore requires no Payroll snapshot.
     * Required months remain fail-closed with the exact same snapshot, manifest,
     * semantic completeness, fingerprint and currency checks.</p>
     */
    @Transactional(readOnly = true)
    public RequiredResolution resolveRequiredMonths(
            AppUser user,
            YearMonth eventMonth,
            List<YearMonth> requiredMonths
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Historical semantic earnings require user"
            );
        }

        if (eventMonth == null) {
            throw new IllegalArgumentException(
                    "Historical semantic earnings require event month"
            );
        }

        if (requiredMonths == null) {
            throw new IllegalArgumentException(
                    "Historical semantic earnings require required months"
            );
        }

        YearMonth referenceFrom =
                eventMonth.minusMonths(
                        12
                );

        YearMonth referenceTo =
                eventMonth.minusMonths(
                        1
                );

        List<YearMonth> required =
                List.copyOf(
                        requiredMonths
                );

        YearMonth previousRequired = null;

        for (YearMonth requiredMonth
                : required) {

            if (requiredMonth == null
                    || requiredMonth.isBefore(
                    referenceFrom
            )
                    || requiredMonth.isAfter(
                    referenceTo
            )) {
                throw new IllegalArgumentException(
                        "Required historical month lies outside reference window"
                );
            }

            if (previousRequired != null
                    && !requiredMonth.isAfter(
                    previousRequired
            )) {
                throw new IllegalArgumentException(
                        "Required historical months must be strictly ascending and unique"
                );
            }

            previousRequired =
                    requiredMonth;
        }

        if (required.isEmpty()) {
            return RequiredResolution.ready(
                    referenceFrom,
                    referenceTo,
                    null,
                    required,
                    List.of()
            );
        }

        List<PayrollSnapshot> candidates =
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                referenceFrom.atDay(
                                        1
                                ),
                                referenceTo.atDay(
                                        1
                                )
                        );

        Map<YearMonth, PayrollSnapshot> latestByMonth =
                new TreeMap<>();

        if (candidates != null) {
            for (PayrollSnapshot candidate
                    : candidates) {

                if (candidate == null
                        || candidate.getPeriodMonth() == null
                        || candidate.getRevision() <= 0) {
                    throw new IllegalStateException(
                            "Historical Payroll snapshot identity is invalid"
                    );
                }

                YearMonth candidateMonth =
                        YearMonth.from(
                                candidate
                                        .getPeriodMonth()
                        );

                if (candidateMonth.isBefore(
                        referenceFrom
                )
                        || candidateMonth.isAfter(
                        referenceTo
                )) {
                    throw new IllegalStateException(
                            "Historical Payroll repository returned snapshot outside requested window"
                    );
                }

                if (!required.contains(
                        candidateMonth
                )) {
                    continue;
                }

                latestByMonth.merge(
                        candidateMonth,
                        candidate,
                        (left, right) ->
                                left.getRevision()
                                        >= right.getRevision()
                                        ? left
                                        : right
                );
            }
        }

        List<HistoricalMonth> resolved =
                new ArrayList<>();

        String currency = null;

        for (YearMonth month
                : required) {

            PayrollSnapshot snapshot =
                    latestByMonth.get(
                            month
                    );

            if (snapshot == null) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_PAYROLL_SNAPSHOT_MISSING",
                        month
                );
            }

            if (snapshot.getSupersededBy() != null) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_PAYROLL_LATEST_REVISION_SUPERSEDED",
                        month
                );
            }

            String snapshotCurrency =
                    snapshot
                            .getCurrencyCode();

            if (snapshotCurrency == null
                    || !snapshotCurrency.matches(
                            "[A-Z]{3}"
                    )) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_PAYROLL_CURRENCY_INVALID",
                        month
                );
            }

            if (currency == null) {
                currency =
                        snapshotCurrency;

            } else if (!currency.equals(
                    snapshotCurrency
            )) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_PAYROLL_CURRENCY_MISMATCH",
                        month
                );
            }

            PayrollSnapshotEarningManifest manifest =
                    earningManifests
                            .findBySnapshot(
                                    snapshot
                            )
                            .orElse(
                                    null
                            );

            if (manifest == null) {
                /*
                 * Existing pre-8A3D snapshots intentionally land here.
                 *
                 * No non-empty line heuristic, display-name inference, gross
                 * decomposition or local backsolve is allowed.
                 */
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_MANIFEST_MISSING",
                        month
                );
            }

            if (!manifest.isComplete()) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_INCOMPLETE",
                        month
                );
            }

            long manifestSourceAmount;

            try {
                manifestSourceAmount =
                        Math.addExact(
                                manifest.getAmountMinor(),
                                manifest.getUnclassifiedAmountMinor()
                        );
            } catch (ArithmeticException ex) {
                throw new IllegalStateException(
                        "Historical semantic earning manifest amount overflow",
                        ex
                );
            }

            long snapshotSourceAmount =
                    snapshotEarningSourceAmount(
                            snapshot
                    );

            if (manifestSourceAmount
                    != snapshotSourceAmount) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_SOURCE_TOTAL_MISMATCH",
                        month
                );
            }

            List<PayrollSnapshotEarningLine> frozen =
                    earningLines
                            .findBySnapshotOrderByLineIndexAsc(
                                    snapshot
                            );

            if (frozen == null) {
                throw new IllegalStateException(
                        "Historical semantic earning repository returned null"
                );
            }

            if (manifest.getLineCount()
                    != frozen.size()) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_COUNT_MISMATCH",
                        month
                );
            }

            long frozenAmount = 0L;

            for (PayrollSnapshotEarningLine line :
                    frozen) {

                if (line == null) {
                    throw new IllegalStateException(
                            "Historical semantic earning line is null"
                    );
                }

                try {
                    frozenAmount =
                            Math.addExact(
                                    frozenAmount,
                                    line.getAmountMinor()
                            );
                } catch (ArithmeticException ex) {
                    throw new IllegalStateException(
                            "Historical semantic earning amount overflow",
                            ex
                    );
                }
            }

            if (manifest.getAmountMinor()
                    != frozenAmount) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_AMOUNT_MISMATCH",
                        month
                );
            }

            String frozenFingerprint =
                    PayrollSemanticEarningFingerprint
                            .calculate(
                                    frozen
                            );

            if (!manifest.getFingerprint()
                    .equals(
                            frozenFingerprint
                    )) {
                return RequiredResolution.blocked(
                        referenceFrom,
                        referenceTo,
                        "HISTORICAL_SEMANTIC_EARNINGS_FINGERPRINT_MISMATCH",
                        month
                );
            }

            List<HistoricalEarning> earnings =
                    new ArrayList<>();

            for (int index = 0;
                    index < frozen.size();
                    index++) {

                PayrollSnapshotEarningLine line =
                        frozen.get(
                                index
                        );

                if (line == null
                        || line.getLineIndex()
                        != index) {
                    throw new IllegalStateException(
                            "Historical semantic earning line order is invalid"
                    );
                }

                PayrollEarningKind kind =
                        parseKind(
                                line.getEarningKind()
                        );

                PayrollEarningPhase phase =
                        parsePhase(
                                line.getEarningPhase()
                        );

                if (phase
                        != kind.phase()) {
                    throw new IllegalStateException(
                            "Historical semantic earning kind/phase mismatch"
                    );
                }

                if (line.getAmountMinor()
                        < 0) {
                    throw new IllegalStateException(
                            "Historical semantic earning amount is negative"
                    );
                }

                PayrollQualifiedQuantity quantity =
                        qualifiedQuantity(
                                line
                        );

                requireOrderedPair(
                        line.getEarningPeriodFrom(),
                        line.getEarningPeriodTo(),
                        "earning"
                );

                requireOrderedPair(
                        line.getCoverageFrom(),
                        line.getCoverageTo(),
                        "coverage"
                );

                earnings.add(
                        new HistoricalEarning(
                                kind,
                                phase,
                                line.getAmountMinor(),
                                quantity,
                                line.getEarningPeriodFrom(),
                                line.getEarningPeriodTo(),
                                line.getCoverageFrom(),
                                line.getCoverageTo()
                        )
                );
            }

            resolved.add(
                    new HistoricalMonth(
                            month,
                            snapshot.getRevision(),
                            snapshotCurrency,
                            earnings
                    )
            );
        }

        return RequiredResolution.ready(
                referenceFrom,
                referenceTo,
                currency,
                required,
                resolved
        );
    }

    /**
     * Frozen Payroll earning-source money represented by the semantic freeze.
     *
     * Deductions intentionally do not participate: they are not earnings.
     */
    private static long snapshotEarningSourceAmount(
            PayrollSnapshot snapshot
    ) {
        try {
            long total =
                    snapshot.getBasePayMinor();

            total =
                    Math.addExact(
                            total,
                            snapshot.getOrdinaryPremiumPayMinor()
                    );

            total =
                    Math.addExact(
                            total,
                            snapshot.getSettlementPayMinor()
                    );

            total =
                    Math.addExact(
                            total,
                            snapshot.getCompensationComponentEarningsMinor()
                    );

            return Math.addExact(
                    total,
                    snapshot.getAdditionsMinor()
            );
        } catch (ArithmeticException ex) {
            throw new IllegalStateException(
                    "Historical Payroll earning-source amount overflow",
                    ex
            );
        }
    }

    private static PayrollEarningKind parseKind(
            String value
    ) {
        try {
            return PayrollEarningKind.valueOf(
                    value
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Historical semantic earning kind is invalid",
                    ex
            );
        }
    }

    private static PayrollEarningPhase parsePhase(
            String value
    ) {
        try {
            return PayrollEarningPhase.valueOf(
                    value
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Historical semantic earning phase is invalid",
                    ex
            );
        }
    }

    private static PayrollQualifiedQuantity qualifiedQuantity(
            PayrollSnapshotEarningLine line
    ) {
        Long value =
                line.getQualifiedQuantityValue();

        String unit =
                line.getQualifiedQuantityUnit();

        if (value == null
                && unit == null) {
            return null;
        }

        if (value == null
                || unit == null) {
            throw new IllegalStateException(
                    "Historical semantic earning qualified quantity is partial"
            );
        }

        try {
            return new PayrollQualifiedQuantity(
                    value,
                    PayrollQuantityUnit.valueOf(
                            unit
                    )
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Historical semantic earning qualified quantity is invalid",
                    ex
            );
        }
    }

    private static void requireOrderedPair(
            LocalDate from,
            LocalDate to,
            String label
    ) {
        if ((from == null) != (to == null)
                || (from != null
                && to.isBefore(
                        from
                ))) {
            throw new IllegalStateException(
                    "Historical semantic earning "
                            + label
                            + " period is invalid"
            );
        }
    }

    public record HistoricalEarning(
            PayrollEarningKind kind,
            PayrollEarningPhase phase,
            long amountMinor,
            PayrollQualifiedQuantity qualifiedQuantity,
            LocalDate earningPeriodFrom,
            LocalDate earningPeriodTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        public HistoricalEarning {
            Objects.requireNonNull(
                    kind,
                    "Historical earning kind is required"
            );

            Objects.requireNonNull(
                    phase,
                    "Historical earning phase is required"
            );

            if (phase != kind.phase()) {
                throw new IllegalArgumentException(
                        "Historical earning kind/phase mismatch"
                );
            }

            if (amountMinor < 0) {
                throw new IllegalArgumentException(
                        "Historical earning amount must be non-negative"
                );
            }

            requireRecordPair(
                    earningPeriodFrom,
                    earningPeriodTo,
                    "earning"
            );

            requireRecordPair(
                    coverageFrom,
                    coverageTo,
                    "coverage"
            );
        }

        private static void requireRecordPair(
                LocalDate from,
                LocalDate to,
                String label
        ) {
            if ((from == null)
                    != (to == null)
                    || (from != null
                    && to.isBefore(
                            from
                    ))) {
                throw new IllegalArgumentException(
                        "Historical earning "
                                + label
                                + " period is invalid"
                );
            }
        }
    }

    public record HistoricalMonth(
            YearMonth period,
            int snapshotRevision,
            String currencyCode,
            List<HistoricalEarning> earnings
    ) {
        public HistoricalMonth {
            Objects.requireNonNull(
                    period,
                    "Historical month is required"
            );

            if (snapshotRevision <= 0) {
                throw new IllegalArgumentException(
                        "Historical snapshot revision must be positive"
                );
            }

            if (currencyCode == null
                    || !currencyCode.matches(
                            "[A-Z]{3}"
                    )) {
                throw new IllegalArgumentException(
                        "Historical month currency is invalid"
                );
            }

            earnings =
                    List.copyOf(
                            Objects.requireNonNull(
                                    earnings,
                                    "Historical earnings are required"
                            )
                    );

        }
    }

    public record RequiredResolution(
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            List<YearMonth> requiredMonths,
            List<HistoricalMonth> months
    ) {
        public RequiredResolution {
            Objects.requireNonNull(
                    referenceFrom,
                    "Historical reference start is required"
            );

            Objects.requireNonNull(
                    referenceTo,
                    "Historical reference end is required"
            );

            requiredMonths =
                    List.copyOf(
                            Objects.requireNonNull(
                                    requiredMonths,
                                    "Required historical months are required"
                            )
                    );

            months =
                    List.copyOf(
                            Objects.requireNonNull(
                                    months,
                                    "Historical months are required"
                            )
                    );

            if (ready) {
                if (blockingReason != null
                        || blockingPeriod != null
                        || months.size() != requiredMonths.size()
                        || (!months.isEmpty()
                        && (currencyCode == null
                        || !currencyCode.matches(
                        "[A-Z]{3}"
                )))) {
                    throw new IllegalArgumentException(
                            "Ready required historical semantic earnings resolution is invalid"
                    );
                }

                for (int index = 0;
                        index < months.size();
                        index++) {
                    if (!requiredMonths.get(index)
                            .equals(
                                    months.get(index)
                                            .period()
                            )) {
                        throw new IllegalArgumentException(
                                "Required historical semantic earnings month order is invalid"
                        );
                    }
                }

                if (months.isEmpty()
                        && currencyCode != null) {
                    throw new IllegalArgumentException(
                            "Empty required historical semantic earnings cannot invent currency"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingPeriod == null
                        || currencyCode != null
                        || !months.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked required historical semantic earnings resolution is invalid"
                    );
                }
            }
        }

        public static RequiredResolution ready(
                YearMonth from,
                YearMonth to,
                String currency,
                List<YearMonth> requiredMonths,
                List<HistoricalMonth> months
        ) {
            return new RequiredResolution(
                    from,
                    to,
                    true,
                    null,
                    null,
                    currency,
                    requiredMonths,
                    months
            );
        }

        public static RequiredResolution blocked(
                YearMonth from,
                YearMonth to,
                String reason,
                YearMonth period
        ) {
            return new RequiredResolution(
                    from,
                    to,
                    false,
                    reason,
                    period,
                    null,
                    List.of(),
                    List.of()
            );
        }
    }

    public record Resolution(
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            List<HistoricalMonth> months
    ) {
        public Resolution {
            Objects.requireNonNull(
                    referenceFrom,
                    "Historical reference start is required"
            );

            Objects.requireNonNull(
                    referenceTo,
                    "Historical reference end is required"
            );

            months =
                    List.copyOf(
                            Objects.requireNonNull(
                                    months,
                                    "Historical months are required"
                            )
                    );

            if (ready) {
                if (blockingReason != null
                        || blockingPeriod != null
                        || currencyCode == null
                        || !currencyCode.matches(
                                "[A-Z]{3}"
                        )
                        || months.size() != 12) {
                    throw new IllegalArgumentException(
                            "Ready historical semantic earnings resolution is invalid"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingPeriod == null
                        || currencyCode != null
                        || !months.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked historical semantic earnings resolution is invalid"
                    );
                }
            }
        }

        public static Resolution ready(
                YearMonth from,
                YearMonth to,
                String currency,
                List<HistoricalMonth> months
        ) {
            return new Resolution(
                    from,
                    to,
                    true,
                    null,
                    null,
                    currency,
                    months
            );
        }

        public static Resolution blocked(
                YearMonth from,
                YearMonth to,
                String reason,
                YearMonth period
        ) {
            return new Resolution(
                    from,
                    to,
                    false,
                    reason,
                    period,
                    null,
                    List.of()
            );
        }
    }
}

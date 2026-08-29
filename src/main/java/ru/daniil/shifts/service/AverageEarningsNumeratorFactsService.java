package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollEarningPhase;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Trusted factual/policy assembly for the primary average-earnings numerator.
 *
 * <p>This boundary deliberately stops before a legal numerator formula,
 * paragraph-15 premium allocation, paragraph-5 money allocation, fallback
 * periods, average-daily earnings or vacation-pay money.</p>
 *
 * <p>Explicit employment history decides which reference months require an
 * immutable semantic Payroll snapshot. A whole month proven outside
 * employment is an explicit zero month and does not require an impossible
 * snapshot. Any month with at least one employed day remains fail-closed on
 * the historical semantic source.</p>
 *
 * <p>Ordinary remuneration is exposed only as a candidate bucket. Frozen
 * earning/coverage provenance is preserved exactly as stored; null provenance
 * stays null and is never reconstructed from posting month, absences, gross,
 * display labels, schedules or backsolved money.</p>
 */
@Service
public class AverageEarningsNumeratorFactsService {

    private static final String EMPLOYMENT_UNCONFIGURED =
            "AVERAGE_EARNINGS_EMPLOYMENT_HISTORY_UNCONFIGURED";

    private static final String EARNING_TREATMENT_UNRESOLVED =
            "AVERAGE_EARNINGS_EARNING_LEGAL_TREATMENT_UNRESOLVED";

    private final EmploymentHistoryService employment;
    private final PayrollHistoricalSemanticEarningsService historicalEarnings;

    public AverageEarningsNumeratorFactsService(
            EmploymentHistoryService employment,
            PayrollHistoricalSemanticEarningsService historicalEarnings
    ) {
        this.employment =
                Objects.requireNonNull(
                        employment,
                        "Employment history service is required"
                );

        this.historicalEarnings =
                Objects.requireNonNull(
                        historicalEarnings,
                        "Historical semantic earnings service is required"
                );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        return resolve(
                user,
                eventDate,
                AverageEarningsReferenceWindow.primary(eventDate)
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            AverageEarningsReferenceWindow referenceWindow
    ) {
        Objects.requireNonNull(
                user,
                "Average earnings numerator facts require user"
        );

        Objects.requireNonNull(
                eventDate,
                "Average earnings numerator facts require event date"
        );
        Objects.requireNonNull(
                referenceWindow,
                "Average earnings numerator facts require reference window"
        ).requireEventDate(eventDate);

        AverageEarningsLegalPolicy.LegalRegime regime =
                AverageEarningsLegalPolicy
                        .requireRegime(
                                eventDate
                        );

        YearMonth eventMonth = referenceWindow.eventMonth();
        YearMonth referenceFrom = referenceWindow.referenceFrom();
        YearMonth referenceTo = referenceWindow.referenceTo();

        LocalDate referenceFromDate = referenceWindow.referenceFromDate();
        LocalDate referenceToDate = referenceWindow.referenceToDate();

        EmploymentHistoryService.Resolution employmentResolution =
                Objects.requireNonNull(
                        employment.resolve(
                                user,
                                referenceFromDate,
                                referenceToDate
                        ),
                        "Employment history resolution is required"
                );

        validateEmploymentResolution(
                employmentResolution,
                referenceFromDate,
                referenceToDate
        );

        if (!employmentResolution.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    regime,
                    EMPLOYMENT_UNCONFIGURED,
                    null
            );
        }

        Map<YearMonth, List<EmploymentCoverageFact>> coverageByMonth =
                employmentCoverageByMonth(
                        employmentResolution,
                        referenceFrom,
                        referenceTo
                );

        List<YearMonth> requiredSnapshotMonths =
                new ArrayList<>();

        for (int offset = 0;
                offset < 12;
                offset++) {
            YearMonth month =
                    referenceFrom.plusMonths(
                            offset
                    );

            if (!coverageByMonth
                    .getOrDefault(
                            month,
                            List.of()
                    )
                    .isEmpty()) {
                requiredSnapshotMonths.add(
                        month
                );
            }
        }

        PayrollHistoricalSemanticEarningsService.RequiredResolution historical =
                Objects.requireNonNull(
                        referenceWindow.primary()
                                ? historicalEarnings.resolveRequiredMonths(
                                        user,
                                        eventMonth,
                                        requiredSnapshotMonths
                                )
                                : historicalEarnings.resolveRequiredMonths(
                                        user,
                                        referenceWindow,
                                        requiredSnapshotMonths
                                ),
                        "Required historical semantic earnings resolution is required"
                );

        validateHistoricalResolution(
                historical,
                referenceFrom,
                referenceTo,
                requiredSnapshotMonths
        );

        if (!historical.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    regime,
                    historical.blockingReason(),
                    historical.blockingPeriod()
            );
        }

        Map<YearMonth, PayrollHistoricalSemanticEarningsService.HistoricalMonth>
                historicalByMonth =
                new LinkedHashMap<>();

        for (PayrollHistoricalSemanticEarningsService.HistoricalMonth month
                : historical.months()) {
            if (historicalByMonth.put(
                    month.period(),
                    month
            ) != null) {
                throw new IllegalStateException(
                        "Required historical semantic earnings contain duplicate month"
                );
            }
        }

        List<MonthFact> monthFacts =
                new ArrayList<>(
                        12
                );

        long ordinaryCandidate = 0L;
        long premiumSpecial = 0L;
        long excluded = 0L;

        for (int offset = 0;
                offset < 12;
                offset++) {

            YearMonth month =
                    referenceFrom.plusMonths(
                            offset
                    );

            List<EmploymentCoverageFact> monthCoverage =
                    coverageByMonth.getOrDefault(
                            month,
                            List.of()
                    );

            if (monthCoverage.isEmpty()) {
                monthFacts.add(
                        MonthFact.notEmployed(
                                month
                        )
                );

                continue;
            }

            PayrollHistoricalSemanticEarningsService.HistoricalMonth historicalMonth =
                    historicalByMonth.get(
                            month
                    );

            if (historicalMonth == null) {
                throw new IllegalStateException(
                        "Required historical semantic month is missing after ready resolution: "
                                + month
                );
            }

            List<EarningFact> earningFacts =
                    new ArrayList<>();

            long monthOrdinary = 0L;
            long monthPremium = 0L;
            long monthExcluded = 0L;

            for (PayrollHistoricalSemanticEarningsService.HistoricalEarning earning
                    : historicalMonth.earnings()) {

                AverageEarningsLegalPolicy.EarningDecision decision =
                        AverageEarningsLegalPolicy
                                .classifyEarning(
                                        eventDate,
                                        earning
                                );

                if (!decision.resolved()) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            regime,
                            EARNING_TREATMENT_UNRESOLVED,
                            month
                    );
                }

                earningFacts.add(
                        EarningFact.from(
                                earning,
                                decision
                        )
                );

                switch (decision.treatment()) {
                    case ORDINARY_REMUNERATION ->
                            monthOrdinary =
                                    Math.addExact(
                                            monthOrdinary,
                                            earning.amountMinor()
                                    );

                    case PREMIUM_SPECIAL_RULE ->
                            monthPremium =
                                    Math.addExact(
                                            monthPremium,
                                            earning.amountMinor()
                                    );

                    case EXCLUDE_PRESERVED_AVERAGE ->
                            monthExcluded =
                                    Math.addExact(
                                            monthExcluded,
                                            earning.amountMinor()
                                    );

                    case UNRESOLVED ->
                            throw new IllegalStateException(
                                    "Resolved earning decision cannot be unresolved"
                            );
                }
            }

            ordinaryCandidate =
                    Math.addExact(
                            ordinaryCandidate,
                            monthOrdinary
                    );

            premiumSpecial =
                    Math.addExact(
                            premiumSpecial,
                            monthPremium
                    );

            excluded =
                    Math.addExact(
                            excluded,
                            monthExcluded
                    );

            monthFacts.add(
                    MonthFact.employed(
                            month,
                            monthCoverage,
                            historicalMonth.snapshotRevision(),
                            historicalMonth.currencyCode(),
                            earningFacts,
                            monthOrdinary,
                            monthPremium,
                            monthExcluded
                    )
            );
        }

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                regime,
                historical.currencyCode(),
                monthFacts,
                ordinaryCandidate,
                premiumSpecial,
                excluded
        );
    }

    private void validateEmploymentResolution(
            EmploymentHistoryService.Resolution resolution,
            LocalDate expectedFrom,
            LocalDate expectedTo
    ) {
        if (!expectedFrom.equals(
                resolution.from()
        ) || !expectedTo.equals(
                resolution.to()
        )) {
            throw new IllegalStateException(
                    "Employment history reference boundary mismatch"
            );
        }
    }

    private Map<YearMonth, List<EmploymentCoverageFact>> employmentCoverageByMonth(
            EmploymentHistoryService.Resolution resolution,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        Map<YearMonth, List<EmploymentCoverageFact>> result =
                new LinkedHashMap<>();

        for (EmploymentHistoryService.CoverageSlice slice
                : resolution.slices()) {

            if (slice == null
                    || slice.periodId() == null
                    || slice.overlapFrom() == null
                    || slice.overlapTo() == null
                    || slice.overlapTo().isBefore(
                    slice.overlapFrom()
            )) {
                throw new IllegalStateException(
                        "Employment coverage slice is invalid"
                );
            }

            if (YearMonth.from(
                    slice.overlapFrom()
            ).isBefore(
                    referenceFrom
            ) || YearMonth.from(
                    slice.overlapTo()
            ).isAfter(
                    referenceTo
            )) {
                throw new IllegalStateException(
                        "Employment coverage slice exceeds reference boundary"
                );
            }

            LocalDate cursor =
                    slice.overlapFrom();

            while (!cursor.isAfter(
                    slice.overlapTo()
            )) {
                YearMonth month =
                        YearMonth.from(
                                cursor
                        );

                LocalDate segmentTo =
                        slice.overlapTo().isBefore(
                                month.atEndOfMonth()
                        )
                                ? slice.overlapTo()
                                : month.atEndOfMonth();

                result.computeIfAbsent(
                                month,
                                ignored ->
                                        new ArrayList<>()
                        )
                        .add(
                                new EmploymentCoverageFact(
                                        slice.periodId(),
                                        slice.sourceFrom(),
                                        slice.sourceTo(),
                                        cursor,
                                        segmentTo
                                )
                        );

                cursor =
                        segmentTo.plusDays(
                                1
                        );
            }
        }

        Map<YearMonth, List<EmploymentCoverageFact>> frozen =
                new LinkedHashMap<>();

        for (Map.Entry<YearMonth, List<EmploymentCoverageFact>> entry
                : result.entrySet()) {
            frozen.put(
                    entry.getKey(),
                    List.copyOf(
                            entry.getValue()
                    )
            );
        }

        return Map.copyOf(
                frozen
        );
    }

    private void validateHistoricalResolution(
            PayrollHistoricalSemanticEarningsService.RequiredResolution resolution,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            List<YearMonth> requiredMonths
    ) {
        if (!referenceFrom.equals(
                resolution.referenceFrom()
        ) || !referenceTo.equals(
                resolution.referenceTo()
        )) {
            throw new IllegalStateException(
                    "Required historical semantic earnings reference boundary mismatch"
            );
        }

        if (resolution.ready()
                && !requiredMonths.equals(
                resolution.requiredMonths()
        )) {
            throw new IllegalStateException(
                    "Required historical semantic earnings month authority mismatch"
            );
        }
    }

    public record EmploymentCoverageFact(
            Long periodId,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            LocalDate overlapFrom,
            LocalDate overlapTo
    ) {
        public EmploymentCoverageFact {
            Objects.requireNonNull(
                    periodId,
                    "Employment period identity is required"
            );

            Objects.requireNonNull(
                    sourceFrom,
                    "Employment source start is required"
            );

            Objects.requireNonNull(
                    overlapFrom,
                    "Employment overlap start is required"
            );

            Objects.requireNonNull(
                    overlapTo,
                    "Employment overlap end is required"
            );

            if (sourceTo != null
                    && sourceTo.isBefore(
                    sourceFrom
            )) {
                throw new IllegalArgumentException(
                        "Employment source period is invalid"
                );
            }

            if (overlapTo.isBefore(
                    overlapFrom
            )) {
                throw new IllegalArgumentException(
                        "Employment overlap is invalid"
                );
            }
        }
    }

    public record EarningFact(
            PayrollEarningKind kind,
            PayrollEarningPhase phase,
            long amountMinor,
            PayrollQualifiedQuantity qualifiedQuantity,
            LocalDate earningPeriodFrom,
            LocalDate earningPeriodTo,
            LocalDate coverageFrom,
            LocalDate coverageTo,
            AverageEarningsLegalPolicy.EarningTreatment treatment,
            AverageEarningsLegalPolicy.LegalBasis legalBasis
    ) {
        public EarningFact {
            Objects.requireNonNull(
                    kind,
                    "Numerator earning kind is required"
            );

            Objects.requireNonNull(
                    phase,
                    "Numerator earning phase is required"
            );

            Objects.requireNonNull(
                    treatment,
                    "Numerator earning treatment is required"
            );

            Objects.requireNonNull(
                    legalBasis,
                    "Numerator earning legal basis is required"
            );

            if (phase != kind.phase()) {
                throw new IllegalArgumentException(
                        "Numerator earning kind/phase mismatch"
                );
            }

            if (amountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Numerator earning amount must be non-negative"
                );
            }
        }

        private static EarningFact from(
                PayrollHistoricalSemanticEarningsService.HistoricalEarning earning,
                AverageEarningsLegalPolicy.EarningDecision decision
        ) {
            return new EarningFact(
                    earning.kind(),
                    earning.phase(),
                    earning.amountMinor(),
                    earning.qualifiedQuantity(),
                    earning.earningPeriodFrom(),
                    earning.earningPeriodTo(),
                    earning.coverageFrom(),
                    earning.coverageTo(),
                    decision.treatment(),
                    decision.basis()
            );
        }
    }

    public record MonthFact(
            YearMonth period,
            boolean employed,
            List<EmploymentCoverageFact> employmentCoverage,
            Integer snapshotRevision,
            String currencyCode,
            List<EarningFact> earnings,
            long ordinaryCandidateAmountMinor,
            long premiumSpecialAmountMinor,
            long excludedAmountMinor
    ) {
        public MonthFact {
            Objects.requireNonNull(
                    period,
                    "Numerator month is required"
            );

            employmentCoverage =
                    List.copyOf(
                            Objects.requireNonNull(
                                    employmentCoverage,
                                    "Employment coverage facts are required"
                            )
                    );

            earnings =
                    List.copyOf(
                            Objects.requireNonNull(
                                    earnings,
                                    "Numerator earning facts are required"
                            )
                    );

            if (ordinaryCandidateAmountMinor < 0L
                    || premiumSpecialAmountMinor < 0L
                    || excludedAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Numerator month buckets must be non-negative"
                );
            }

            if (employed) {
                if (employmentCoverage.isEmpty()
                        || snapshotRevision == null
                        || snapshotRevision <= 0
                        || currencyCode == null
                        || !currencyCode.matches(
                        "[A-Z]{3}"
                )) {
                    throw new IllegalArgumentException(
                            "Employed numerator month requires snapshot provenance"
                    );
                }
            } else if (!employmentCoverage.isEmpty()
                    || snapshotRevision != null
                    || currencyCode != null
                    || !earnings.isEmpty()
                    || ordinaryCandidateAmountMinor != 0L
                    || premiumSpecialAmountMinor != 0L
                    || excludedAmountMinor != 0L) {
                throw new IllegalArgumentException(
                        "Non-employed numerator month must be explicit zero"
                );
            }
        }

        public static MonthFact notEmployed(
                YearMonth period
        ) {
            return new MonthFact(
                    period,
                    false,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    0L,
                    0L,
                    0L
            );
        }

        public static MonthFact employed(
                YearMonth period,
                List<EmploymentCoverageFact> employmentCoverage,
                int snapshotRevision,
                String currencyCode,
                List<EarningFact> earnings,
                long ordinaryCandidateAmountMinor,
                long premiumSpecialAmountMinor,
                long excludedAmountMinor
        ) {
            return new MonthFact(
                    period,
                    true,
                    employmentCoverage,
                    snapshotRevision,
                    currencyCode,
                    earnings,
                    ordinaryCandidateAmountMinor,
                    premiumSpecialAmountMinor,
                    excludedAmountMinor
            );
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            AverageEarningsLegalPolicy.LegalRegime legalRegime,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            List<MonthFact> months,
            long ordinaryCandidateAmountMinor,
            long premiumSpecialAmountMinor,
            long excludedAmountMinor
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Numerator event date is required"
            );

            Objects.requireNonNull(
                    eventMonth,
                    "Numerator event month is required"
            );

            Objects.requireNonNull(
                    referenceFrom,
                    "Numerator reference start is required"
            );

            Objects.requireNonNull(
                    referenceTo,
                    "Numerator reference end is required"
            );

            Objects.requireNonNull(
                    legalRegime,
                    "Numerator legal regime is required"
            );

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Numerator event month does not match legal event date"
                );
            }
            new AverageEarningsReferenceWindow(
                    eventMonth,
                    referenceFrom,
                    referenceTo
            );

            months =
                    List.copyOf(
                            Objects.requireNonNull(
                                    months,
                                    "Numerator month facts are required"
                            )
                    );

            if (ordinaryCandidateAmountMinor < 0L
                    || premiumSpecialAmountMinor < 0L
                    || excludedAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Numerator buckets must be non-negative"
                );
            }

            if (ready) {
                if (blockingReason != null
                        || blockingPeriod != null
                        || months.size() != 12) {
                    throw new IllegalArgumentException(
                            "Ready numerator facts resolution is invalid"
                    );
                }

                boolean anyEmployed =
                        months.stream()
                                .anyMatch(
                                        MonthFact::employed
                                );

                if (anyEmployed
                        != (currencyCode != null)) {
                    throw new IllegalArgumentException(
                            "Numerator currency provenance is inconsistent with employment"
                    );
                }

                if (currencyCode != null
                        && !currencyCode.matches(
                        "[A-Z]{3}"
                )) {
                    throw new IllegalArgumentException(
                            "Numerator currency is invalid"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || currencyCode != null
                        || !months.isEmpty()
                        || ordinaryCandidateAmountMinor != 0L
                        || premiumSpecialAmountMinor != 0L
                        || excludedAmountMinor != 0L) {
                    throw new IllegalArgumentException(
                            "Blocked numerator facts resolution is invalid"
                    );
                }
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                AverageEarningsLegalPolicy.LegalRegime regime,
                String currency,
                List<MonthFact> months,
                long ordinaryCandidateAmountMinor,
                long premiumSpecialAmountMinor,
                long excludedAmountMinor
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    regime,
                    true,
                    null,
                    null,
                    currency,
                    months,
                    ordinaryCandidateAmountMinor,
                    premiumSpecialAmountMinor,
                    excludedAmountMinor
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                AverageEarningsLegalPolicy.LegalRegime regime,
                String reason,
                YearMonth period
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    regime,
                    false,
                    reason,
                    period,
                    null,
                    List.of(),
                    0L,
                    0L,
                    0L
            );
        }
    }
}

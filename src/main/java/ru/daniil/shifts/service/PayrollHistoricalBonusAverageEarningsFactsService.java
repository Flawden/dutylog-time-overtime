package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsManifest;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsManifestRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only immutable FACT authority for paragraph-15 bonus metadata frozen
 * beside one Payroll snapshot revision.
 *
 * <p>This boundary verifies completeness and fingerprint integrity but does not
 * decide paragraph-15 inclusion, allocation, proportional reduction or money.
 * Legacy snapshots without an F2 manifest and snapshots frozen with missing F1
 * facts are explicit blockers.</p>
 */
@Service
public class PayrollHistoricalBonusAverageEarningsFactsService {

    private final PayrollSnapshotBonusAverageEarningsFactRepository facts;
    private final PayrollSnapshotBonusAverageEarningsManifestRepository manifests;

    public PayrollHistoricalBonusAverageEarningsFactsService(
            PayrollSnapshotBonusAverageEarningsFactRepository facts,
            PayrollSnapshotBonusAverageEarningsManifestRepository manifests
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Historical bonus average-earnings fact repository is required"
        );
        this.manifests = Objects.requireNonNull(
                manifests,
                "Historical bonus average-earnings manifest repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(PayrollSnapshot snapshot) {
        Objects.requireNonNull(
                snapshot,
                "Historical bonus average-earnings facts require snapshot"
        );

        if (snapshot.getPeriodMonth() == null
                || snapshot.getRevision() <= 0
                || snapshot.getCurrencyCode() == null
                || !snapshot.getCurrencyCode().matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Historical Payroll snapshot identity is invalid for bonus facts"
            );
        }

        PayrollSnapshotBonusAverageEarningsManifest manifest =
                manifests.findBySnapshot(snapshot).orElse(null);

        if (manifest == null) {
            return Resolution.blocked(
                    "HISTORICAL_BONUS_AVERAGE_EARNINGS_MANIFEST_MISSING"
            );
        }

        if (!manifest.isComplete()) {
            return Resolution.blocked(
                    "HISTORICAL_BONUS_AVERAGE_EARNINGS_INCOMPLETE"
            );
        }

        List<PayrollSnapshotBonusAverageEarningsFact> frozen =
                facts.findBySnapshotOrderByFactIndexAsc(snapshot);

        if (frozen == null) {
            throw new IllegalStateException(
                    "Historical bonus average-earnings repository returned null"
            );
        }

        if (manifest.getFactCount() != frozen.size()
                || manifest.getSourceFactCount() != frozen.size()) {
            return Resolution.blocked(
                    "HISTORICAL_BONUS_AVERAGE_EARNINGS_COUNT_MISMATCH"
            );
        }

        String fingerprint;
        try {
            fingerprint = PayrollBonusAverageEarningsFingerprint.calculate(
                    manifest.getSourceFactCount(),
                    frozen
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Historical bonus average-earnings fact shape/order is invalid",
                    ex
            );
        }

        if (!manifest.getFingerprint().equals(fingerprint)) {
            return Resolution.blocked(
                    "HISTORICAL_BONUS_AVERAGE_EARNINGS_FINGERPRINT_MISMATCH"
            );
        }

        YearMonth snapshotMonth = YearMonth.from(snapshot.getPeriodMonth());
        String snapshotCurrency = snapshot.getCurrencyCode();
        List<HistoricalBonusFact> result = new ArrayList<>();

        for (int index = 0; index < frozen.size(); index++) {
            PayrollSnapshotBonusAverageEarningsFact fact = frozen.get(index);

            if (fact == null || fact.getFactIndex() != index) {
                throw new IllegalStateException(
                        "Historical bonus average-earnings fact order is invalid"
                );
            }

            if (!snapshotMonth.equals(YearMonth.from(fact.getSourcePeriodFrom()))
                    || !snapshotCurrency.equals(fact.getCurrencyCode())) {
                return Resolution.blocked(
                        "HISTORICAL_BONUS_AVERAGE_EARNINGS_SOURCE_IDENTITY_MISMATCH"
                );
            }

            result.add(
                    new HistoricalBonusFact(
                            fact.getBonusSourceFactId(),
                            fact.getBonusAverageFactId(),
                            fact.getComponentId(),
                            fact.getEarningKind(),
                            fact.getSourcePeriodFrom(),
                            fact.getSourcePeriodTo(),
                            fact.getAmountMinor(),
                            fact.getCurrencyCode(),
                            fact.getIndicatorKey(),
                            fact.getAwardPeriodFrom(),
                            fact.getAwardPeriodTo(),
                            fact.getAnnualResult(),
                            fact.getAccruedForActualWorkTime(),
                            fact.getProratedForPartialAwardPeriod()
                    )
            );
        }

        return Resolution.ready(result);
    }

    public record HistoricalBonusFact(
            long bonusSourceFactId,
            long bonusAverageFactId,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate sourcePeriodFrom,
            LocalDate sourcePeriodTo,
            long amountMinor,
            String currencyCode,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        public HistoricalBonusFact {
            if (bonusSourceFactId <= 0L
                    || bonusAverageFactId <= 0L
                    || componentId <= 0L
                    || (earningKind != PayrollEarningKind.MONTHLY_BONUS
                    && earningKind != PayrollEarningKind.ONE_TIME_BONUS)
                    || sourcePeriodFrom == null
                    || sourcePeriodTo == null
                    || sourcePeriodTo.isBefore(sourcePeriodFrom)
                    || !YearMonth.from(sourcePeriodFrom).equals(YearMonth.from(sourcePeriodTo))
                    || amountMinor <= 0L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")
                    || indicatorKey == null
                    || !indicatorKey.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")
                    || awardPeriodFrom == null
                    || awardPeriodTo == null
                    || awardPeriodTo.isBefore(awardPeriodFrom)) {
                throw new IllegalArgumentException(
                        "Historical bonus average-earnings fact is invalid"
                );
            }

            if (Boolean.TRUE.equals(annualResult)
                    && (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                    || awardPeriodFrom.getMonthValue() != 1
                    || awardPeriodFrom.getDayOfMonth() != 1
                    || awardPeriodTo.getMonthValue() != 12
                    || awardPeriodTo.getDayOfMonth() != 31
                    || awardPeriodFrom.getYear() != awardPeriodTo.getYear())) {
                throw new IllegalArgumentException(
                        "Historical annual-result bonus fact is invalid"
                );
            }
        }
    }

    public record Resolution(
            boolean ready,
            String blockingReason,
            List<HistoricalBonusFact> facts
    ) {
        public Resolution {
            facts = List.copyOf(Objects.requireNonNull(
                    facts,
                    "Historical bonus average-earnings facts are required"
            ));

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Historical bonus average-earnings resolution state is invalid"
                );
            }

            if (!ready && !facts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked historical bonus resolution cannot expose partial facts"
                );
            }
        }

        public static Resolution ready(List<HistoricalBonusFact> facts) {
            return new Resolution(true, null, facts);
        }

        public static Resolution blocked(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Historical bonus blocker reason is required"
                );
            }
            return new Resolution(false, reason, List.of());
        }
    }
}

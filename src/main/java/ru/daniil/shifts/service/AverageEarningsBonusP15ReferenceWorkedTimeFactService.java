package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotP15ScheduledWorkFact;
import ru.daniil.shifts.model.PayrollSnapshotP15WorkTimeManifest;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.repo.PayrollSnapshotP15ScheduledWorkFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotP15WorkTimeManifestRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit;

/**
 * 8A4F3F3 — canonical 12-month paragraph-15 reference worked-time FACT resolver.
 *
 * <p>The resolver consumes only the latest immutable Payroll revision for each
 * required reference month and the F3F2 immutable scheduled-work freeze stored
 * beside that revision. It never derives the P15 coefficient from aggregate
 * Payroll planned/worked minutes and never reads mutable F3F1 history.</p>
 *
 * <p>SUMMARIZED accounting resolves to WORKING_MINUTES using only
 * plannedAndWorkedMinutes / scheduleMinutes. workedOutsidePlanMinutes remains
 * audit provenance and cannot improve the coefficient. DAILY accounting
 * resolves to WORKING_DAYS: one scheduled day contributes one norm day, a fully
 * worked scheduled day contributes one worked day, and a wholly missed day
 * contributes zero. A partially worked scheduled day blocks because F3F3 has
 * no legal authority to round that day to either zero or one.</p>
 *
 * <p>A reference period crossing DAILY/SUMMARIZED authority blocks rather than
 * mixing incompatible units. Whole months proven upstream to require no Payroll
 * snapshot contribute zero units. This service does not invent those proofs
 * from employment, account age, schedules or missing rows.</p>
 *
 * <p>scheduleFullyWorked is factual scheduled-work completeness only. It is not
 * the final paragraph-15 policy boolean: paragraph-5 excluded time remains a
 * separate POLICY concern before F3C is invoked. P13 actual worked hours are
 * also deliberately outside this authority.</p>
 */
@Service
public class AverageEarningsBonusP15ReferenceWorkedTimeFactService {

    public static final String SNAPSHOT_MISSING =
            "P15_REFERENCE_WORK_TIME_PAYROLL_SNAPSHOT_MISSING";
    public static final String NO_PAYROLL_CONTRADICTION =
            "P15_REFERENCE_WORK_TIME_NO_PAYROLL_PROOF_CONTRADICTS_SNAPSHOT";
    public static final String LATEST_SUPERSEDED =
            "P15_REFERENCE_WORK_TIME_LATEST_REVISION_SUPERSEDED";
    public static final String MANIFEST_MISSING =
            "P15_REFERENCE_WORK_TIME_MANIFEST_MISSING";
    public static final String MANIFEST_INCOMPLETE =
            "P15_REFERENCE_WORK_TIME_MANIFEST_INCOMPLETE";
    public static final String MANIFEST_IDENTITY_INVALID =
            "P15_REFERENCE_WORK_TIME_MANIFEST_IDENTITY_INVALID";
    public static final String FACT_COUNT_MISMATCH =
            "P15_REFERENCE_WORK_TIME_FACT_COUNT_MISMATCH";
    public static final String FACT_IDENTITY_INVALID =
            "P15_REFERENCE_WORK_TIME_FACT_IDENTITY_INVALID";
    public static final String FINGERPRINT_MISMATCH =
            "P15_REFERENCE_WORK_TIME_FINGERPRINT_MISMATCH";
    public static final String MIXED_ACCOUNTING_MODE =
            "P15_REFERENCE_WORK_TIME_MIXED_ACCOUNTING_MODE";
    public static final String DAILY_PARTIAL_DAY_UNRESOLVED =
            "P15_REFERENCE_WORK_TIME_DAILY_PARTIAL_DAY_UNRESOLVED";
    public static final String REFERENCE_NORM_ZERO =
            "P15_REFERENCE_WORK_TIME_NORM_ZERO";
    public static final String UNIT_OVERFLOW =
            "P15_REFERENCE_WORK_TIME_UNIT_OVERFLOW";

    private final PayrollSnapshotRepository snapshots;
    private final PayrollSnapshotP15WorkTimeManifestRepository manifests;
    private final PayrollSnapshotP15ScheduledWorkFactRepository facts;

    public AverageEarningsBonusP15ReferenceWorkedTimeFactService(
            PayrollSnapshotRepository snapshots,
            PayrollSnapshotP15WorkTimeManifestRepository manifests,
            PayrollSnapshotP15ScheduledWorkFactRepository facts
    ) {
        this.snapshots = Objects.requireNonNull(
                snapshots,
                "Payroll snapshot repository is required"
        );
        this.manifests = Objects.requireNonNull(
                manifests,
                "P15 work-time manifest repository is required"
        );
        this.facts = Objects.requireNonNull(
                facts,
                "P15 scheduled-work fact repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            List<YearMonth> provenNoPayrollMonths
    ) {
        return resolve(
                user,
                eventDate,
                AverageEarningsReferenceWindow.primary(eventDate),
                provenNoPayrollMonths
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            AverageEarningsReferenceWindow referenceWindow,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "P15 reference worked-time requires user");
        Objects.requireNonNull(eventDate, "P15 reference worked-time requires event date");
        Objects.requireNonNull(
                referenceWindow,
                "P15 reference worked-time requires reference window"
        ).requireEventDate(eventDate);
        Objects.requireNonNull(
                provenNoPayrollMonths,
                "P15 reference worked-time requires explicit no-Payroll proofs"
        );

        YearMonth eventMonth = referenceWindow.eventMonth();
        YearMonth referenceFrom = referenceWindow.referenceFrom();
        YearMonth referenceTo = referenceWindow.referenceTo();
        Set<YearMonth> zeroMonths = validateZeroMonths(
                provenNoPayrollMonths,
                referenceFrom,
                referenceTo
        );

        List<PayrollSnapshot> candidates = snapshots
                .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                        user,
                        referenceFrom.atDay(1),
                        referenceTo.atDay(1)
                );

        Map<YearMonth, PayrollSnapshot> latestByMonth = new TreeMap<>();
        if (candidates != null) {
            for (PayrollSnapshot candidate : candidates) {
                validateCandidate(candidate, referenceFrom, referenceTo);
                YearMonth month = YearMonth.from(candidate.getPeriodMonth());
                latestByMonth.merge(
                        month,
                        candidate,
                        (left, right) -> left.getRevision() >= right.getRevision()
                                ? left
                                : right
                );
            }
        }

        WorkTimeAccountingMode resolvedMode = null;
        long workedUnits = 0L;
        long normUnits = 0L;
        List<ResolvedMonth> resolvedMonths = new ArrayList<>(12);

        for (YearMonth month = referenceFrom;
                !month.isAfter(referenceTo);
                month = month.plusMonths(1)) {

            PayrollSnapshot snapshot = latestByMonth.get(month);
            boolean provenZero = zeroMonths.contains(month);

            if (snapshot == null) {
                if (!provenZero) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            SNAPSHOT_MISSING,
                            month
                    );
                }
                resolvedMonths.add(ResolvedMonth.noPayroll(month));
                continue;
            }

            if (provenZero) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        NO_PAYROLL_CONTRADICTION,
                        month
                );
            }

            if (snapshot.getSupersededBy() != null) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        LATEST_SUPERSEDED,
                        month
                );
            }

            PayrollSnapshotP15WorkTimeManifest manifest = manifests
                    .findBySnapshot(snapshot)
                    .orElse(null);

            if (manifest == null) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        MANIFEST_MISSING,
                        month
                );
            }

            if (!manifest.isComplete()) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        MANIFEST_INCOMPLETE,
                        month
                );
            }

            if (manifest.getSnapshot() == null
                    || manifest.getSnapshot().getId() == null
                    || !snapshot.getId().equals(manifest.getSnapshot().getId())
                    || manifest.getFingerprint() == null
                    || !manifest.getFingerprint().matches("[0-9a-f]{64}")) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        MANIFEST_IDENTITY_INVALID,
                        month
                );
            }

            List<PayrollSnapshotP15ScheduledWorkFact> frozen = facts
                    .findBySnapshotOrderByFactIndexAsc(snapshot);

            if (frozen == null
                    || manifest.getCandidateDayCount() != frozen.size()
                    || manifest.getFactCount() != frozen.size()
                    || manifest.getExactFactCount() != frozen.size()) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        FACT_COUNT_MISMATCH,
                        month
                );
            }

            Validation validation = validateFacts(snapshot, month, frozen);
            if (!validation.ready()) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        validation.blockingReason(),
                        month
                );
            }

            if (!manifest.getFingerprint().equals(manifestFingerprint(frozen))) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        FINGERPRINT_MISMATCH,
                        month
                );
            }

            long monthWorked = 0L;
            long monthNorm = 0L;
            WorkTimeAccountingMode monthMode = null;

            for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
                WorkTimeAccountingMode factMode = fact.getAccountingMode();

                if (monthMode == null) {
                    monthMode = factMode;
                } else if (monthMode != factMode) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            MIXED_ACCOUNTING_MODE,
                            month
                    );
                }

                if (resolvedMode == null) {
                    resolvedMode = factMode;
                } else if (resolvedMode != factMode) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            MIXED_ACCOUNTING_MODE,
                            month
                    );
                }

                try {
                    if (factMode == WorkTimeAccountingMode.SUMMARIZED) {
                        monthNorm = Math.addExact(monthNorm, fact.getScheduleMinutes());
                        monthWorked = Math.addExact(
                                monthWorked,
                                fact.getPlannedAndWorkedMinutes()
                        );
                    } else if (factMode == WorkTimeAccountingMode.DAILY) {
                        if (fact.getScheduleMinutes() == 0) {
                            continue;
                        }

                        monthNorm = Math.addExact(monthNorm, 1L);

                        if (fact.getPlannedAndWorkedMinutes() == fact.getScheduleMinutes()) {
                            monthWorked = Math.addExact(monthWorked, 1L);
                        } else if (fact.getPlannedAndWorkedMinutes() != 0) {
                            return Resolution.blocked(
                                    eventDate,
                                    eventMonth,
                                    referenceFrom,
                                    referenceTo,
                                    DAILY_PARTIAL_DAY_UNRESOLVED + ":" + fact.getSourceDate(),
                                    month
                            );
                        }
                    } else {
                        return Resolution.blocked(
                                eventDate,
                                eventMonth,
                                referenceFrom,
                                referenceTo,
                                FACT_IDENTITY_INVALID,
                                month
                        );
                    }
                } catch (ArithmeticException overflow) {
                    return Resolution.blocked(
                            eventDate,
                            eventMonth,
                            referenceFrom,
                            referenceTo,
                            UNIT_OVERFLOW,
                            month
                    );
                }
            }

            try {
                workedUnits = Math.addExact(workedUnits, monthWorked);
                normUnits = Math.addExact(normUnits, monthNorm);
            } catch (ArithmeticException overflow) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        referenceFrom,
                        referenceTo,
                        UNIT_OVERFLOW,
                        month
                );
            }

            resolvedMonths.add(new ResolvedMonth(
                    month,
                    false,
                    snapshot.getId(),
                    snapshot.getRevision(),
                    monthMode,
                    frozen.size(),
                    monthWorked,
                    monthNorm,
                    manifest.getFingerprint()
            ));
        }

        if (normUnits <= 0L || resolvedMode == null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    REFERENCE_NORM_ZERO,
                    null
            );
        }

        WorkMeasureUnit unit = resolvedMode == WorkTimeAccountingMode.DAILY
                ? WorkMeasureUnit.WORKING_DAYS
                : WorkMeasureUnit.WORKING_MINUTES;

        ReferenceWorkedTimeFact referenceWorkedTime = new ReferenceWorkedTimeFact(
                unit,
                workedUnits,
                normUnits
        );

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                resolvedMode,
                referenceWorkedTime,
                workedUnits == normUnits,
                resolvedMonths
        );
    }

    private Set<YearMonth> validateZeroMonths(
            List<YearMonth> raw,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        Set<YearMonth> result = new HashSet<>();
        for (YearMonth month : raw) {
            if (month == null
                    || month.isBefore(referenceFrom)
                    || month.isAfter(referenceTo)) {
                throw new IllegalArgumentException(
                        "P15 no-Payroll proof lies outside reference window"
                );
            }
            if (!result.add(month)) {
                throw new IllegalArgumentException(
                        "P15 no-Payroll proofs must be unique"
                );
            }
        }
        return Set.copyOf(result);
    }

    private void validateCandidate(
            PayrollSnapshot snapshot,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        if (snapshot == null
                || snapshot.getId() == null
                || snapshot.getId() <= 0L
                || snapshot.getOwner() == null
                || snapshot.getPeriodMonth() == null
                || snapshot.getRevision() <= 0) {
            throw new IllegalStateException(
                    "Historical P15 work-time Payroll snapshot identity is invalid"
            );
        }

        LocalDate periodMonth = snapshot.getPeriodMonth();
        YearMonth month = YearMonth.from(periodMonth);
        if (!periodMonth.equals(month.atDay(1))) {
            throw new IllegalStateException(
                    "Historical P15 work-time Payroll snapshot month is not canonical"
            );
        }
        if (month.isBefore(referenceFrom) || month.isAfter(referenceTo)) {
            throw new IllegalStateException(
                    "Historical P15 work-time repository returned snapshot outside reference window"
            );
        }
    }

    private Validation validateFacts(
            PayrollSnapshot snapshot,
            YearMonth month,
            List<PayrollSnapshotP15ScheduledWorkFact> frozen
    ) {
        LocalDate previousDate = null;

        for (int index = 0; index < frozen.size(); index++) {
            PayrollSnapshotP15ScheduledWorkFact fact = frozen.get(index);
            if (fact == null
                    || fact.getSnapshot() == null
                    || fact.getSnapshot().getId() == null
                    || !snapshot.getId().equals(fact.getSnapshot().getId())
                    || fact.getFactIndex() != index
                    || fact.getSourceDate() == null
                    || !YearMonth.from(fact.getSourceDate()).equals(month)
                    || fact.getWorkTimeAccountingTermId() <= 0L
                    || fact.getWorkTimeAccountingEffectiveFrom() == null
                    || fact.getWorkTimeAccountingEffectiveFrom().isAfter(fact.getSourceDate())
                    || fact.getAccountingMode() == null
                    || fact.getSourceKind() == null
                    || !fact.isSourceIdentityExact()
                    || fact.getPayrollPlannedMinutes() < 0
                    || fact.getPayrollWorkedMinutes() < 0
                    || fact.getPayrollHourlyBaseWorkedMinutes() < 0
                    || fact.getPayrollHourlyBaseWorkedMinutes() > fact.getPayrollWorkedMinutes()
                    || fact.getScheduleMinutes() < 0
                    || fact.getPlannedAndWorkedMinutes() < 0
                    || fact.getPlannedNotWorkedMinutes() < 0
                    || fact.getWorkedOutsidePlanMinutes() < 0
                    || (long) fact.getScheduleMinutes()
                    != (long) fact.getPlannedAndWorkedMinutes() + fact.getPlannedNotWorkedMinutes()
                    || fact.getPlannedDayEntryIds() == null
                    || fact.getActualWorkIntervalIds() == null
                    || fact.getSourceFingerprint() == null
                    || !fact.getSourceFingerprint().matches("[0-9a-f]{64}")) {
                return Validation.blocked(FACT_IDENTITY_INVALID);
            }

            if (previousDate != null && !fact.getSourceDate().isAfter(previousDate)) {
                return Validation.blocked(FACT_IDENTITY_INVALID);
            }
            previousDate = fact.getSourceDate();
        }

        return Validation.ok();
    }

    private String manifestFingerprint(List<PayrollSnapshotP15ScheduledWorkFact> frozen) {
        StringBuilder canonical = new StringBuilder("P15_SCHEDULED_WORK_V1");
        for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
            canonical.append("\nD|").append(fact.getSourceDate());
        }
        for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
            canonical.append("\nF|")
                    .append(fact.getSourceDate()).append('|')
                    .append(fact.isSourceIdentityExact()).append('|')
                    .append(fact.getSourceFingerprint());
        }
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Validation(boolean ready, String blockingReason) {
        static Validation ok() {
            return new Validation(true, null);
        }

        static Validation blocked(String reason) {
            return new Validation(false, Objects.requireNonNull(reason));
        }
    }

    public record ResolvedMonth(
            YearMonth month,
            boolean noPayrollProven,
            Long snapshotId,
            Integer snapshotRevision,
            WorkTimeAccountingMode accountingMode,
            int frozenFactCount,
            long workedUnits,
            long normUnits,
            String manifestFingerprint
    ) {
        public ResolvedMonth {
            Objects.requireNonNull(month, "P15 resolved month is required");
            if (frozenFactCount < 0 || workedUnits < 0L || normUnits < 0L || workedUnits > normUnits) {
                throw new IllegalArgumentException("P15 resolved month quantities are invalid");
            }
            if (noPayrollProven) {
                if (snapshotId != null
                        || snapshotRevision != null
                        || accountingMode != null
                        || frozenFactCount != 0
                        || workedUnits != 0L
                        || normUnits != 0L
                        || manifestFingerprint != null) {
                    throw new IllegalArgumentException(
                            "P15 no-Payroll month cannot expose snapshot facts"
                    );
                }
            } else {
                if (snapshotId == null
                        || snapshotId <= 0L
                        || snapshotRevision == null
                        || snapshotRevision <= 0
                        || (frozenFactCount > 0 && accountingMode == null)
                        || manifestFingerprint == null
                        || !manifestFingerprint.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException(
                            "P15 resolved Payroll month identity is invalid"
                    );
                }
            }
        }

        public static ResolvedMonth noPayroll(YearMonth month) {
            return new ResolvedMonth(
                    month,
                    true,
                    null,
                    null,
                    null,
                    0,
                    0L,
                    0L,
                    null
            );
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            WorkTimeAccountingMode accountingMode,
            ReferenceWorkedTimeFact referenceWorkedTime,
            boolean scheduleFullyWorked,
            List<ResolvedMonth> months
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "P15 reference worked-time event date is required");
            Objects.requireNonNull(eventMonth, "P15 reference worked-time event month is required");
            Objects.requireNonNull(referenceFrom, "P15 reference worked-time start is required");
            Objects.requireNonNull(referenceTo, "P15 reference worked-time end is required");
            months = List.copyOf(Objects.requireNonNull(months, "P15 resolved months are required"));

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "P15 reference worked-time event month does not match legal event date"
                );
            }
            new AverageEarningsReferenceWindow(eventMonth, referenceFrom, referenceTo);
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "P15 reference worked-time resolution state is invalid"
                );
            }
            if (ready) {
                if (blockingPeriod != null
                        || accountingMode == null
                        || referenceWorkedTime == null
                        || months.size() != 12) {
                    throw new IllegalArgumentException(
                            "Ready P15 reference worked-time resolution is incomplete"
                    );
                }
            } else if (accountingMode != null
                    || referenceWorkedTime != null
                    || scheduleFullyWorked
                    || !months.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked P15 reference worked-time resolution cannot expose partial authority"
                );
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                WorkTimeAccountingMode accountingMode,
                ReferenceWorkedTimeFact referenceWorkedTime,
                boolean scheduleFullyWorked,
                List<ResolvedMonth> months
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    true,
                    null,
                    null,
                    accountingMode,
                    referenceWorkedTime,
                    scheduleFullyWorked,
                    months
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                String reason,
                YearMonth blockingPeriod
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "P15 reference worked-time blocker reason is required"
                );
            }
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    false,
                    reason,
                    blockingPeriod,
                    null,
                    null,
                    false,
                    List.of()
            );
        }
    }
}

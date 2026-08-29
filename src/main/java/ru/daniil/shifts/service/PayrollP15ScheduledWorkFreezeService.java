package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.PlannedActualWorkRelationEngine.RelationSlice;
import ru.daniil.shifts.service.PlannedActualWorkRelationEngine.WorkPlanRelation;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

/**
 * 8A4F3F2 immutable paragraph-15 scheduled-work FACT freeze.
 *
 * <p>This service deliberately keeps two factual paths separate:</p>
 * <ul>
 *   <li>PLAN_DERIVED: no explicit factual interval owns the schedule
 *       occurrence; the canonical posted-only Payroll source supplies worked
 *       inside schedule, while derived overtime stays outside schedule.</li>
 *   <li>EXPLICIT_ACTUAL: clock plan and factual intervals are compared only by
 *       PlannedActualWorkRelationEngine, preserving PLANNED_AND_WORKED,
 *       PLANNED_NOT_WORKED and WORKED_OUTSIDE_PLAN independently.</li>
 * </ul>
 *
 * <p>Missing F3F1 mode, ambiguous cross-midnight partial plan-derived work or
 * missing historical clock identity never blocks ordinary Payroll creation.
 * Instead the persisted manifest is incomplete, so future paragraph-15 logic
 * fails closed without inventing historical facts.</p>
 *
 * <p>This is P15 authority only. It must not be reused as the paragraph-13
 * actual-hours denominator, where off-schedule/overtime hours have different
 * legal semantics.</p>
 */
@Service
public class PayrollP15ScheduledWorkFreezeService {

    private final PayrollSnapshotP15ScheduledWorkFactRepository facts;
    private final PayrollSnapshotP15WorkTimeManifestRepository manifests;
    private final WorkTimeAccountingHistoryService accountingHistory;
    private final DayEntryRepository days;
    private final ActualWorkIntervalRepository actualWork;
    private final PlannedWorkDayAllocationService plannedAllocation;
    private final ActualWorkDayAllocationService actualAllocation;
    private final PlannedActualWorkRelationEngine relationEngine;
    private final TimeCompensationService timeCompensation;

    public PayrollP15ScheduledWorkFreezeService(
            PayrollSnapshotP15ScheduledWorkFactRepository facts,
            PayrollSnapshotP15WorkTimeManifestRepository manifests,
            WorkTimeAccountingHistoryService accountingHistory,
            DayEntryRepository days,
            ActualWorkIntervalRepository actualWork,
            PlannedWorkDayAllocationService plannedAllocation,
            ActualWorkDayAllocationService actualAllocation,
            PlannedActualWorkRelationEngine relationEngine,
            TimeCompensationService timeCompensation
    ) {
        this.facts = Objects.requireNonNull(facts, "P15 scheduled-work fact repository is required");
        this.manifests = Objects.requireNonNull(manifests, "P15 work-time manifest repository is required");
        this.accountingHistory = Objects.requireNonNull(accountingHistory, "Work-time accounting history is required");
        this.days = Objects.requireNonNull(days, "DayEntry repository is required");
        this.actualWork = Objects.requireNonNull(actualWork, "Actual-work repository is required");
        this.plannedAllocation = Objects.requireNonNull(plannedAllocation, "Planned allocation service is required");
        this.actualAllocation = Objects.requireNonNull(actualAllocation, "Actual allocation service is required");
        this.relationEngine = Objects.requireNonNull(relationEngine, "Plan/fact relation engine is required");
        this.timeCompensation = Objects.requireNonNull(timeCompensation, "Canonical Payroll time source is required");
    }

    @Transactional
    public FreezeResult freeze(
            PayrollSnapshot snapshot,
            AppUser user,
            PayrollSourceSnapshot source
    ) {
        Objects.requireNonNull(snapshot, "P15 scheduled-work freeze requires snapshot");
        Objects.requireNonNull(user, "P15 scheduled-work freeze requires user");
        Objects.requireNonNull(source, "P15 scheduled-work freeze requires Payroll source");

        LocalDate from = Objects.requireNonNull(
                snapshot.getPeriodMonth(),
                "P15 scheduled-work freeze requires snapshot month"
        ).withDayOfMonth(1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        if (!from.equals(source.from()) || !to.equals(source.to())) {
            throw new IllegalStateException(
                    "P15 scheduled-work freeze requires the exact Payroll source month"
            );
        }

        Map<LocalDate, PayrollSourceDay> currentSource = sourceDays(source, from, to);
        Map<LocalDate, PayrollSourceDay> derivationSource = new LinkedHashMap<>(currentSource);

        LocalDate previousDate = from.minusDays(1);
        PayrollSourceSnapshot previous = timeCompensation.payrollSource(
                user,
                previousDate,
                previousDate
        );
        for (PayrollSourceDay day : previous.days()) {
            if (previousDate.equals(day.date())) {
                putUniqueSourceDay(derivationSource, day);
            }
        }

        List<PlannedOccurrence> occurrences = plannedOccurrences(user, previousDate, to);
        Map<LocalDate, List<PlannedPiece>> plannedByDate = plannedByDate(occurrences, from, to);

        List<ActualOccurrence> actualOccurrences = actualOccurrences(user, from, to);
        Map<LocalDate, List<ActualPiece>> actualByDate = actualByDate(actualOccurrences, from, to);

        ExplicitContext explicitContext = explicitContext(occurrences, actualByDate, from, to);

        TreeSet<LocalDate> candidateDates = new TreeSet<>();
        candidateDates.addAll(currentSource.keySet());
        candidateDates.addAll(plannedByDate.keySet());
        candidateDates.addAll(actualByDate.keySet());

        List<DraftFact> drafts = new ArrayList<>();

        for (LocalDate date : candidateDates) {
            WorkTimeAccountingHistoryService.Resolution mode = accountingHistory.resolveAt(user, date);
            if (!mode.ready() || mode.fact() == null) {
                continue;
            }

            DraftFact draft = explicitContext.dates().contains(date)
                    ? explicitDraft(
                            date,
                            currentSource.get(date),
                            plannedByDate.getOrDefault(date, List.of()),
                            actualByDate.getOrDefault(date, List.of()),
                            explicitContext.support().getOrDefault(date, List.of()),
                            mode.fact()
                    )
                    : planDerivedDraft(
                            date,
                            currentSource.get(date),
                            derivationSource,
                            plannedByDate.getOrDefault(date, List.of()),
                            mode.fact()
                    );

            if (draft != null) {
                drafts.add(draft);
            }
        }

        List<PayrollSnapshotP15ScheduledWorkFact> frozen = new ArrayList<>();

        for (DraftFact draft : drafts) {
            frozen.add(
                    new PayrollSnapshotP15ScheduledWorkFact(
                            snapshot,
                            frozen.size(),
                            draft.date(),
                            draft.mode().termId(),
                            draft.mode().effectiveFrom(),
                            draft.mode().mode(),
                            draft.sourceKind(),
                            draft.payrollPlannedMinutes(),
                            draft.payrollWorkedMinutes(),
                            draft.payrollHourlyBaseWorkedMinutes(),
                            draft.scheduleMinutes(),
                            draft.plannedAndWorkedMinutes(),
                            draft.plannedNotWorkedMinutes(),
                            draft.workedOutsidePlanMinutes(),
                            draft.sourceIdentityExact(),
                            canonicalIds(draft.plannedDayEntryIds()),
                            canonicalIds(draft.actualWorkIntervalIds()),
                            draft.sourceFingerprint()
                    )
            );
        }

        if (!frozen.isEmpty()) {
            facts.saveAll(frozen);
        }

        int exactFactCount = (int) frozen.stream()
                .filter(PayrollSnapshotP15ScheduledWorkFact::isSourceIdentityExact)
                .count();

        String manifestFingerprint = manifestFingerprint(candidateDates, frozen);

        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(
                        snapshot,
                        candidateDates.size(),
                        frozen.size(),
                        exactFactCount,
                        manifestFingerprint
                );

        manifests.saveAndFlush(manifest);

        return new FreezeResult(frozen, manifest);
    }

    private Map<LocalDate, PayrollSourceDay> sourceDays(
            PayrollSourceSnapshot source,
            LocalDate from,
            LocalDate to
    ) {
        LinkedHashMap<LocalDate, PayrollSourceDay> result = new LinkedHashMap<>();
        for (PayrollSourceDay day : source.days()) {
            Objects.requireNonNull(day, "P15 scheduled-work Payroll source day is required");
            if (day.date() == null || day.date().isBefore(from) || day.date().isAfter(to)) {
                throw new IllegalStateException(
                        "P15 scheduled-work Payroll source day is outside snapshot month"
                );
            }
            validateSourceDay(day);
            putUniqueSourceDay(result, day);
        }
        return result;
    }

    private void putUniqueSourceDay(
            Map<LocalDate, PayrollSourceDay> target,
            PayrollSourceDay day
    ) {
        validateSourceDay(day);
        if (target.putIfAbsent(day.date(), day) != null) {
            throw new IllegalStateException(
                    "P15 scheduled-work Payroll source contains duplicate date " + day.date()
            );
        }
    }

    private void validateSourceDay(PayrollSourceDay day) {
        if (day == null
                || day.date() == null
                || day.plannedMinutes() < 0
                || day.workedMinutes() < 0
                || day.hourlyBaseWorkedMinutes() < 0
                || day.hourlyBaseWorkedMinutes() > day.workedMinutes()) {
            throw new IllegalStateException(
                    "P15 scheduled-work Payroll source day is invalid"
            );
        }
    }

    private List<PlannedOccurrence> plannedOccurrences(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        List<PlannedOccurrence> result = new ArrayList<>();

        for (DayEntry day : days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            if (day == null || day.getDate() == null || day.getShiftType() == null) {
                continue;
            }

            List<PlannedWorkDayAllocationService.NetWorkSegment> segments =
                    plannedAllocation.netSegments(user, day);

            if (segments == null) {
                throw new IllegalStateException(
                        "Planned allocation returned null segments"
                );
            }

            int totalMinutes = segments.stream()
                    .mapToInt(PlannedWorkDayAllocationService.NetWorkSegment::minutes)
                    .sum();

            result.add(
                    new PlannedOccurrence(
                            day,
                            List.copyOf(segments),
                            totalMinutes,
                            day.getId() != null
                                    && day.getId() > 0L
                                    && day.hasShiftOccurrenceSnapshot()
                    )
            );
        }

        return List.copyOf(result);
    }

    private Map<LocalDate, List<PlannedPiece>> plannedByDate(
            List<PlannedOccurrence> occurrences,
            LocalDate from,
            LocalDate to
    ) {
        TreeMap<LocalDate, List<PlannedPiece>> result = new TreeMap<>();

        for (PlannedOccurrence occurrence : occurrences) {
            for (PlannedWorkDayAllocationService.NetWorkSegment segment : occurrence.segments()) {
                LocalDate date = segment.sourceDate();
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                result.computeIfAbsent(date, ignored -> new ArrayList<>())
                        .add(new PlannedPiece(occurrence, segment));
            }
        }

        result.replaceAll((ignored, value) -> List.copyOf(value));
        return result;
    }

    private List<ActualOccurrence> actualOccurrences(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        List<ActualOccurrence> result = new ArrayList<>();

        for (ActualWorkInterval interval : actualWork.findOverlappingRange(user, from, to)) {
            if (interval == null) {
                continue;
            }
            List<ActualWorkDayAllocationService.NetWorkSegment> segments =
                    actualAllocation.netSegments(interval);
            if (segments == null) {
                throw new IllegalStateException("Actual allocation returned null segments");
            }
            result.add(
                    new ActualOccurrence(
                            interval,
                            List.copyOf(segments),
                            interval.getId() != null
                                    && interval.getId() > 0L
                                    && interval.hasAbsoluteIdentity()
                    )
            );
        }

        return List.copyOf(result);
    }

    private Map<LocalDate, List<ActualPiece>> actualByDate(
            List<ActualOccurrence> occurrences,
            LocalDate from,
            LocalDate to
    ) {
        TreeMap<LocalDate, List<ActualPiece>> result = new TreeMap<>();

        for (ActualOccurrence occurrence : occurrences) {
            for (ActualWorkDayAllocationService.NetWorkSegment segment : occurrence.segments()) {
                LocalDate date = segment.start().toLocalDate();
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                result.computeIfAbsent(date, ignored -> new ArrayList<>())
                        .add(new ActualPiece(occurrence, segment));
            }
        }

        result.replaceAll((ignored, value) -> List.copyOf(value));
        return result;
    }

    private ExplicitContext explicitContext(
            List<PlannedOccurrence> occurrences,
            Map<LocalDate, List<ActualPiece>> actualByDate,
            LocalDate from,
            LocalDate to
    ) {
        TreeSet<LocalDate> dates = new TreeSet<>(actualByDate.keySet());
        TreeMap<LocalDate, LinkedHashSet<ActualOccurrence>> support = new TreeMap<>();

        for (Map.Entry<LocalDate, List<ActualPiece>> entry : actualByDate.entrySet()) {
            LinkedHashSet<ActualOccurrence> values =
                    support.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>());
            entry.getValue().stream()
                    .map(ActualPiece::occurrence)
                    .forEach(values::add);
        }

        for (PlannedOccurrence occurrence : occurrences) {
            LinkedHashSet<ActualOccurrence> occurrenceSupport = new LinkedHashSet<>();

            for (ActualPiece piece : actualByDate.getOrDefault(occurrence.day().getDate(), List.of())) {
                occurrenceSupport.add(piece.occurrence());
            }

            for (PlannedWorkDayAllocationService.NetWorkSegment segment : occurrence.segments()) {
                LocalDate date = segment.sourceDate();
                List<ActualPiece> actual = actualByDate.getOrDefault(date, List.of());
                if (actual.isEmpty()) {
                    continue;
                }

                List<RelationSlice> slices = relationEngine.compareDay(
                        date,
                        List.of(segment),
                        actual.stream().map(ActualPiece::segment).toList()
                );

                if (minutes(slices, WorkPlanRelation.PLANNED_AND_WORKED) > 0) {
                    actual.stream()
                            .map(ActualPiece::occurrence)
                            .forEach(occurrenceSupport::add);
                }
            }

            if (!occurrenceSupport.isEmpty()) {
                for (PlannedWorkDayAllocationService.NetWorkSegment segment : occurrence.segments()) {
                    LocalDate date = segment.sourceDate();
                    if (!date.isBefore(from) && !date.isAfter(to)) {
                        dates.add(date);
                        support.computeIfAbsent(date, ignored -> new LinkedHashSet<>())
                                .addAll(occurrenceSupport);
                    }
                }
            }
        }

        TreeMap<LocalDate, List<ActualOccurrence>> immutable = new TreeMap<>();
        support.forEach((date, values) -> immutable.put(date, List.copyOf(values)));

        return new ExplicitContext(
                Set.copyOf(dates),
                Map.copyOf(immutable)
        );
    }

    private DraftFact explicitDraft(
            LocalDate date,
            PayrollSourceDay payroll,
            List<PlannedPiece> planned,
            List<ActualPiece> actual,
            List<ActualOccurrence> supportingActual,
            WorkTimeAccountingHistoryService.ModeFact mode
    ) {
        List<PlannedWorkDayAllocationService.NetWorkSegment> plannedSegments =
                planned.stream().map(PlannedPiece::segment).toList();
        List<ActualWorkDayAllocationService.NetWorkSegment> actualSegments =
                actual.stream().map(ActualPiece::segment).toList();

        List<RelationSlice> relation = relationEngine.compareDay(
                date,
                plannedSegments,
                actualSegments
        );

        int inside = minutes(relation, WorkPlanRelation.PLANNED_AND_WORKED);
        int notWorked = minutes(relation, WorkPlanRelation.PLANNED_NOT_WORKED);
        int outside = minutes(relation, WorkPlanRelation.WORKED_OUTSIDE_PLAN);
        int schedule = Math.addExact(inside, notWorked);

        boolean exact = planned.stream().allMatch(piece -> piece.occurrence().exactIdentity())
                && actual.stream().allMatch(piece -> piece.occurrence().exactIdentity())
                && supportingActual.stream().allMatch(ActualOccurrence::exactIdentity);

        List<Long> plannedIds = planned.stream()
                .map(piece -> piece.occurrence().day().getId())
                .filter(Objects::nonNull)
                .filter(id -> id > 0L)
                .distinct()
                .sorted()
                .toList();

        List<Long> actualIds = java.util.stream.Stream.concat(
                        actual.stream().map(ActualPiece::occurrence),
                        supportingActual.stream()
                )
                .map(occurrence -> occurrence.interval().getId())
                .filter(Objects::nonNull)
                .filter(id -> id > 0L)
                .distinct()
                .sorted()
                .toList();

        List<ActualOccurrence> evidenceActual = java.util.stream.Stream.concat(
                        actual.stream().map(ActualPiece::occurrence),
                        supportingActual.stream()
                )
                .distinct()
                .toList();

        String evidence = evidence(
                date,
                payroll,
                planned.stream().map(PlannedPiece::occurrence).distinct().toList(),
                evidenceActual,
                "EXPLICIT:" + schedule + ':' + inside + ':' + notWorked + ':' + outside
        );

        return draft(
                date,
                payroll,
                mode,
                PayrollSnapshotP15ScheduledWorkSourceKind.EXPLICIT_ACTUAL,
                schedule,
                inside,
                notWorked,
                outside,
                exact,
                plannedIds,
                actualIds,
                evidence
        );
    }

    private DraftFact planDerivedDraft(
            LocalDate date,
            PayrollSourceDay payroll,
            Map<LocalDate, PayrollSourceDay> derivationSource,
            List<PlannedPiece> planned,
            WorkTimeAccountingHistoryService.ModeFact mode
    ) {
        LinkedHashMap<LocalDate, PlannedOccurrence> occurrences = new LinkedHashMap<>();
        for (PlannedPiece piece : planned) {
            occurrences.putIfAbsent(piece.occurrence().day().getDate(), piece.occurrence());
        }

        int schedule = 0;
        int inside = 0;
        int outside = 0;
        boolean exact = true;
        List<Long> plannedIds = new ArrayList<>();
        List<String> evidenceParts = new ArrayList<>();

        for (PlannedOccurrence occurrence : occurrences.values()) {
            PayrollSourceDay origin = derivationSource.get(occurrence.day().getDate());
            if (origin == null) {
                return null;
            }
            validateSourceDay(origin);

            if (origin.plannedMinutes() != occurrence.totalMinutes()) {
                return null;
            }

            int originInside = origin.hourlyBaseWorkedMinutes();
            if (originInside > occurrence.totalMinutes()) {
                return null;
            }

            int dateSchedule = occurrence.segments().stream()
                    .filter(segment -> date.equals(segment.sourceDate()))
                    .mapToInt(PlannedWorkDayAllocationService.NetWorkSegment::minutes)
                    .sum();

            if (dateSchedule <= 0) {
                continue;
            }

            int dateInside;
            long calendarDates = occurrence.segments().stream()
                    .map(PlannedWorkDayAllocationService.NetWorkSegment::sourceDate)
                    .distinct()
                    .count();

            if (originInside == 0) {
                dateInside = 0;
            } else if (originInside == occurrence.totalMinutes()) {
                dateInside = dateSchedule;
            } else if (calendarDates == 1L) {
                dateInside = originInside;
            } else {
                // Partial plan-derived overnight work has no authoritative
                // clock allocation; do not guess which calendar side owns it.
                return null;
            }

            schedule = Math.addExact(schedule, dateSchedule);
            inside = Math.addExact(inside, dateInside);

            if (date.equals(occurrence.day().getDate())) {
                outside = Math.addExact(
                        outside,
                        origin.workedMinutes() - origin.hourlyBaseWorkedMinutes()
                );
            }

            exact &= occurrence.exactIdentity();
            if (occurrence.day().getId() != null && occurrence.day().getId() > 0L) {
                plannedIds.add(occurrence.day().getId());
            }
            evidenceParts.add(sourceDayEvidence("ORIGIN", origin));
            evidenceParts.add(plannedEvidence(occurrence));
        }

        if (payroll != null && !occurrences.containsKey(date)) {
            if (payroll.plannedMinutes() > 0 || payroll.hourlyBaseWorkedMinutes() > 0) {
                return null;
            }
            outside = Math.addExact(outside, payroll.workedMinutes());
        }

        int notWorked = schedule - inside;
        if (notWorked < 0) {
            return null;
        }

        String evidence = evidence(
                date,
                payroll,
                occurrences.values().stream().toList(),
                List.of(),
                "PLAN_DERIVED:" + schedule + ':' + inside + ':' + notWorked + ':' + outside
                        + ':' + String.join(";", evidenceParts)
        );

        return draft(
                date,
                payroll,
                mode,
                PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED,
                schedule,
                inside,
                notWorked,
                outside,
                exact,
                plannedIds.stream().distinct().sorted().toList(),
                List.of(),
                evidence
        );
    }

    private DraftFact draft(
            LocalDate date,
            PayrollSourceDay payroll,
            WorkTimeAccountingHistoryService.ModeFact mode,
            PayrollSnapshotP15ScheduledWorkSourceKind sourceKind,
            int schedule,
            int inside,
            int notWorked,
            int outside,
            boolean exact,
            List<Long> plannedIds,
            List<Long> actualIds,
            String evidence
    ) {
        int payrollPlanned = payroll == null ? 0 : payroll.plannedMinutes();
        int payrollWorked = payroll == null ? 0 : payroll.workedMinutes();
        int payrollHourlyBase = payroll == null ? 0 : payroll.hourlyBaseWorkedMinutes();

        String fingerprint = sha256(
                date + "|"
                        + mode.termId() + "|" + mode.effectiveFrom() + "|" + mode.mode() + "|"
                        + sourceKind + "|"
                        + payrollPlanned + "|" + payrollWorked + "|" + payrollHourlyBase + "|"
                        + schedule + "|" + inside + "|" + notWorked + "|" + outside + "|"
                        + exact + "|" + canonicalIds(plannedIds) + "|" + canonicalIds(actualIds) + "|"
                        + evidence
        );

        return new DraftFact(
                date,
                mode,
                sourceKind,
                payrollPlanned,
                payrollWorked,
                payrollHourlyBase,
                schedule,
                inside,
                notWorked,
                outside,
                exact,
                List.copyOf(plannedIds),
                List.copyOf(actualIds),
                fingerprint
        );
    }

    private String evidence(
            LocalDate date,
            PayrollSourceDay payroll,
            List<PlannedOccurrence> planned,
            List<ActualOccurrence> actual,
            String relation
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("DATE=" + date);
        parts.add(sourceDayEvidence("PAYROLL", payroll));
        planned.stream()
                .distinct()
                .sorted(Comparator.comparing(o -> o.day().getDate()))
                .map(this::plannedEvidence)
                .forEach(parts::add);
        actual.stream()
                .distinct()
                .sorted(Comparator.comparing(o -> o.interval().getWorkDate()))
                .map(this::actualEvidence)
                .forEach(parts::add);
        parts.add(relation);
        return String.join("\n", parts);
    }

    private String sourceDayEvidence(String prefix, PayrollSourceDay day) {
        if (day == null) {
            return prefix + "=-";
        }
        return prefix + '=' + day.date()
                + ':' + day.plannedMinutes()
                + ':' + day.workedMinutes()
                + ':' + day.hourlyBaseWorkedMinutes()
                + ':' + day.vacationMinutes()
                + ':' + day.sickMinutes()
                + ':' + day.overtimeCompensatedMinutes()
                + ':' + day.unpaidMinutes();
    }

    private String plannedEvidence(PlannedOccurrence occurrence) {
        DayEntry day = occurrence.day();
        return "PLAN=" + id(day.getId())
                + ':' + day.getDate()
                + ':' + day.getShiftStartInstant()
                + ':' + day.getShiftEndInstant()
                + ':' + day.getShiftSourceTimezone()
                + ':' + day.getShiftBreakMinutes()
                + ':' + day.getShiftNetMinutes()
                + ':' + day.getRowVersion()
                + ':' + occurrence.totalMinutes()
                + ':' + occurrence.exactIdentity();
    }

    private String actualEvidence(ActualOccurrence occurrence) {
        ActualWorkInterval interval = occurrence.interval();
        return "ACTUAL=" + id(interval.getId())
                + ':' + interval.getWorkDate()
                + ':' + interval.getEndDate()
                + ':' + interval.getStartTime()
                + ':' + interval.getEndTime()
                + ':' + interval.getStartInstant()
                + ':' + interval.getEndInstant()
                + ':' + interval.getSourceTimezone()
                + ':' + interval.getBreakMinutes()
                + ':' + interval.getWorkedMinutes()
                + ':' + interval.isIdentityReconstructed()
                + ':' + occurrence.exactIdentity();
    }

    private String id(Long value) {
        return value == null ? "-" : value.toString();
    }

    private int minutes(List<RelationSlice> slices, WorkPlanRelation relation) {
        return slices.stream()
                .filter(slice -> slice.relation() == relation)
                .mapToInt(RelationSlice::minutes)
                .sum();
    }

    private String manifestFingerprint(
            Set<LocalDate> candidateDates,
            List<PayrollSnapshotP15ScheduledWorkFact> frozen
    ) {
        StringBuilder canonical = new StringBuilder("P15_SCHEDULED_WORK_V1");
        for (LocalDate date : candidateDates) {
            canonical.append("\nD|").append(date);
        }
        for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
            canonical.append("\nF|")
                    .append(fact.getSourceDate()).append('|')
                    .append(fact.isSourceIdentityExact()).append('|')
                    .append(fact.getSourceFingerprint());
        }
        return sha256(canonical.toString());
    }

    private static String canonicalIds(Collection<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0L)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record PlannedOccurrence(
            DayEntry day,
            List<PlannedWorkDayAllocationService.NetWorkSegment> segments,
            int totalMinutes,
            boolean exactIdentity
    ) {
    }

    private record PlannedPiece(
            PlannedOccurrence occurrence,
            PlannedWorkDayAllocationService.NetWorkSegment segment
    ) {
    }

    private record ActualOccurrence(
            ActualWorkInterval interval,
            List<ActualWorkDayAllocationService.NetWorkSegment> segments,
            boolean exactIdentity
    ) {
    }

    private record ActualPiece(
            ActualOccurrence occurrence,
            ActualWorkDayAllocationService.NetWorkSegment segment
    ) {
    }

    private record ExplicitContext(
            Set<LocalDate> dates,
            Map<LocalDate, List<ActualOccurrence>> support
    ) {
    }

    private record DraftFact(
            LocalDate date,
            WorkTimeAccountingHistoryService.ModeFact mode,
            PayrollSnapshotP15ScheduledWorkSourceKind sourceKind,
            int payrollPlannedMinutes,
            int payrollWorkedMinutes,
            int payrollHourlyBaseWorkedMinutes,
            int scheduleMinutes,
            int plannedAndWorkedMinutes,
            int plannedNotWorkedMinutes,
            int workedOutsidePlanMinutes,
            boolean sourceIdentityExact,
            List<Long> plannedDayEntryIds,
            List<Long> actualWorkIntervalIds,
            String sourceFingerprint
    ) {
    }

    public record FreezeResult(
            List<PayrollSnapshotP15ScheduledWorkFact> facts,
            PayrollSnapshotP15WorkTimeManifest manifest
    ) {
        public FreezeResult {
            facts = List.copyOf(Objects.requireNonNull(facts, "Frozen P15 scheduled-work facts are required"));
            Objects.requireNonNull(manifest, "Frozen P15 work-time manifest is required");
        }
    }
}

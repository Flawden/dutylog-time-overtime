package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationEngine.SourceWorkSegment;
import ru.daniil.shifts.service.PayClassificationEngine.NightWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Domain context for Native Pay Classification.
 *
 * Reality sources:
 * Actual Work -> factual net segments
 * DayEntry -> ordinary scheduled duration / overtime threshold
 * Production Calendar -> HOLIDAY pay dimension
 *
 * Production norm is intentionally NOT used as the overtime threshold.
 * NORM_OVERRIDE belongs to production/payroll norm semantics and must not
 * silently redefine how much factual work is ordinary for classification.
 *
 * Pricing, Time Bank settlement and Payroll money are outside this service.
 */
@Service
public class PayClassificationService {

    private static final String BASE = "BASE";
    private static final String LOCAL = "LOCAL_OVERRIDE";

    /*
     * v27.46.1 native foundation default.
     * This becomes effective-dated/configurable in the compensation-rule layer;
     * the classifier itself already receives the window explicitly.
     */
    static final NightWindow DEFAULT_NIGHT_WINDOW =
            new NightWindow(
                    LocalTime.of(22, 0),
                    LocalTime.of(6, 0)
            );

    private final ActualWorkIntervalRepository actualWork;
    private final ActualWorkDayAllocationService allocation;
    private final DayEntryRepository scheduleDays;
    private final ProductionCalendarDayRepository productionDays;
    private final WorkNormService workNorm;
    private final PayClassificationEngine engine;

    public PayClassificationService(
            ActualWorkIntervalRepository actualWork,
            ActualWorkDayAllocationService allocation,
            DayEntryRepository scheduleDays,
            ProductionCalendarDayRepository productionDays,
            WorkNormService workNorm,
            PayClassificationEngine engine
    ) {
        this.actualWork = actualWork;
        this.allocation = allocation;
        this.scheduleDays = scheduleDays;
        this.productionDays = productionDays;
        this.workNorm = workNorm;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public DayClassification classify(
            AppUser user,
            LocalDate date
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Classification requires user"
            );
        }

        if (date == null) {
            throw new IllegalArgumentException(
                    "Classification requires date"
            );
        }

        DayEntry requestedSchedule =
                scheduleDays.findByOwnerAndDate(
                        user,
                        date
                ).orElse(null);

        /*
         * Empty factual days still expose the requested calendar date's
         * ordinary schedule capacity. Once factual Actual Work exists,
         * ordinary/overtime ownership moves to each factual source workday.
         */
        int requestedOrdinaryThresholdMinutes =
                workNorm.basePlannedMinutes(
                        requestedSchedule
                );

        ProductionCalendarDay production =
                effectiveProductionDay(
                        user,
                        date
                );

        /*
         * HOLIDAY remains a calendar-date dimension of factual minutes.
         * It intentionally does not move back to the source workday merely
         * because an overnight shift started yesterday.
         */
        boolean holiday =
                production != null
                        && "HOLIDAY".equals(
                                production.getPayrollEffect()
                        );

        List<ActualWorkInterval> intervals =
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                );

        /*
         * SOURCE_WORKDAY_OWNED_ORDINARY_CAPACITY
         *
         * A calendar date may contain both the tail of source workday A and
         * the head of source workday B. Their REGULAR / OVERTIME ordinals
         * must remain independent even when the requested calendar date owns
         * its own positive schedule.
         *
         * NIGHT and HOLIDAY stay requested-calendar-date dimensions. Only
         * ordinary/overtime threshold ownership follows the factual source
         * workday occurrence.
         */
        List<ClassificationSlice> slices =
                new ArrayList<>();

        int ordinaryThresholdMinutes =
                intervals.isEmpty()
                        ? requestedOrdinaryThresholdMinutes
                        : 0;

        if (!intervals.isEmpty()) {
            Map<LocalDate, List<ActualWorkInterval>>
                    intervalsBySourceWorkDate =
                    new TreeMap<>();

            for (ActualWorkInterval interval : intervals) {
                LocalDate sourceWorkDate =
                        interval.getWorkDate() != null
                                ? interval.getWorkDate()
                                : date;

                if (sourceWorkDate.isAfter(date)) {
                    throw new IllegalStateException(
                            "Actual Work source workday cannot start after its classified source date"
                    );
                }

                intervalsBySourceWorkDate
                        .computeIfAbsent(
                                sourceWorkDate,
                                ignored -> new ArrayList<>()
                        )
                        .add(interval);
            }

            for (Map.Entry<LocalDate, List<ActualWorkInterval>> group :
                    intervalsBySourceWorkDate.entrySet()) {

                LocalDate sourceWorkDate =
                        group.getKey();

                DayEntry sourceSchedule =
                        scheduleDays.findByOwnerAndDate(
                                user,
                                sourceWorkDate
                        ).orElse(null);

                int sourceOrdinaryThresholdMinutes =
                        workNorm.basePlannedMinutes(
                                sourceSchedule
                        );

                /*
                 * Zero ordinary capacity is meaningful: factual work on an
                 * unscheduled source workday is all OVERTIME.
                 */
                List<ActualWorkInterval> sourceIntervals;

                if (sourceWorkDate.equals(date)) {
                    sourceIntervals =
                            group.getValue();
                } else {
                    sourceIntervals =
                            actualWork.findOverlappingRange(
                                            user,
                                            sourceWorkDate,
                                            date
                                    ).stream()
                                    .filter(item ->
                                            sourceWorkDate.equals(
                                                    item.getWorkDate()
                                            )
                                    )
                                    .toList();
                }

                int initialWorkedOrdinalMinutes = 0;

                List<SourceWorkSegment> currentSegments =
                        new ArrayList<>();

                for (ActualWorkInterval interval : sourceIntervals) {
                    for (NetWorkSegment segment :
                            allocation.netSegments(interval)) {

                        LocalDate segmentDate =
                                segment.start()
                                        .toLocalDate();

                        if (segmentDate.isBefore(date)) {
                            initialWorkedOrdinalMinutes =
                                    Math.addExact(
                                            initialWorkedOrdinalMinutes,
                                            segment.minutes()
                                    );
                            continue;
                        }

                        if (date.equals(segmentDate)) {
                            currentSegments.add(
                                    new SourceWorkSegment(
                                            interval.getId(),
                                            segment
                                    )
                            );
                        }
                    }
                }

                if (currentSegments.isEmpty()) {
                    continue;
                }

                ordinaryThresholdMinutes =
                        Math.addExact(
                                ordinaryThresholdMinutes,
                                sourceOrdinaryThresholdMinutes
                        );

                slices.addAll(
                        engine.classifyDayWithSources(
                                date,
                                currentSegments,
                                sourceOrdinaryThresholdMinutes,
                                initialWorkedOrdinalMinutes,
                                holiday,
                                DEFAULT_NIGHT_WINDOW
                        )
                );
            }
        }

        /*
         * Different source workdays can theoretically contribute to the same
         * calendar date. Keep public slice ordering factual and deterministic.
         */
        slices.sort(
                (left, right) -> {
                    if (left.exact()
                            && right.exact()) {
                        return left.startInstant()
                                .compareTo(
                                        right.startInstant()
                                );
                    }

                    int local =
                            left.start()
                                    .compareTo(
                                            right.start()
                                    );

                    if (local != 0) {
                        return local;
                    }

                    if (left.exact()
                            != right.exact()) {
                        return left.exact()
                                ? -1
                                : 1;
                    }

                    return 0;
                }
        );

        int workedMinutes =
                slices.stream()
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        int regularMinutes =
                slices.stream()
                        .filter(
                                ClassificationSlice::regular
                        )
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        int nightMinutes =
                slices.stream()
                        .filter(
                                ClassificationSlice::night
                        )
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        int holidayMinutes =
                slices.stream()
                        .filter(
                                ClassificationSlice::holiday
                        )
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        int overtimeMinutes =
                slices.stream()
                        .filter(
                                ClassificationSlice::overtime
                        )
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        if (workedMinutes
                != regularMinutes + overtimeMinutes) {
            throw new IllegalStateException(
                    "REGULAR and OVERTIME must partition worked minutes"
            );
        }

        return new DayClassification(
                date,
                ordinaryThresholdMinutes,
                holiday,
                DEFAULT_NIGHT_WINDOW,
                workedMinutes,
                regularMinutes,
                nightMinutes,
                holidayMinutes,
                overtimeMinutes,
                slices
        );
    }

    private ProductionCalendarDay effectiveProductionDay(
            AppUser user,
            LocalDate date
    ) {
        ProductionCalendarDay local =
                productionDays
                        .findByOwnerAndDateAndLayer(
                                user,
                                date,
                                LOCAL
                        )
                        .orElse(null);

        if (local != null) {
            return local;
        }

        return productionDays
                .findByOwnerAndDateAndLayer(
                        user,
                        date,
                        BASE
                )
                .orElse(null);
    }

    public record DayClassification(
            LocalDate date,
            int ordinaryThresholdMinutes,
            boolean holiday,
            NightWindow nightWindow,
            int workedMinutes,
            int regularMinutes,
            int nightMinutes,
            int holidayMinutes,
            int overtimeMinutes,
            List<ClassificationSlice> slices
    ) {
        public DayClassification {
            if (date == null) {
                throw new IllegalArgumentException(
                        "Classification date is required"
                );
            }

            if (ordinaryThresholdMinutes < 0
                    || workedMinutes < 0
                    || regularMinutes < 0
                    || nightMinutes < 0
                    || holidayMinutes < 0
                    || overtimeMinutes < 0) {
                throw new IllegalArgumentException(
                        "Classification minute totals cannot be negative"
                );
            }

            if (nightWindow == null) {
                throw new IllegalArgumentException(
                        "Classification night window is required"
                );
            }

            slices = slices == null
                    ? List.of()
                    : List.copyOf(slices);

            if (workedMinutes
                    != regularMinutes + overtimeMinutes) {
                throw new IllegalArgumentException(
                        "REGULAR and OVERTIME totals must equal worked minutes"
                );
            }

            if (nightMinutes > workedMinutes
                    || holidayMinutes > workedMinutes) {
                throw new IllegalArgumentException(
                        "Overlapping dimensions cannot exceed worked minutes"
                );
            }

            if (!holiday && holidayMinutes != 0) {
                throw new IllegalArgumentException(
                        "Non-holiday day cannot contain HOLIDAY minutes"
                );
            }

            if (holiday
                    && holidayMinutes != workedMinutes) {
                throw new IllegalArgumentException(
                        "Holiday day must classify every worked minute as HOLIDAY"
                );
            }
        }
    }
}

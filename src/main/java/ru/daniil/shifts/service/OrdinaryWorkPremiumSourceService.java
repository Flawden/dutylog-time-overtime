package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.AbsenceOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.PlannedWorkDayAllocationService.NetWorkSegment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only source truth for ordinary-work NIGHT / HOLIDAY pricing.
 *
 * This layer deliberately stops before:
 * - effective-dated pricing rules;
 * - money;
 * - Payroll assembly / snapshots;
 * - Time Bank / settlement.
 *
 * EXPLICIT:
 * factual Native Pay Classification is authoritative; only REGULAR slices
 * belong to ordinary premium pricing. OVERTIME remains bank-first.
 *
 * PLAN_DERIVED:
 * canonical ordinary quantity comes from the existing payroll semantics,
 * while PlannedWorkDayAllocationService supplies clock identity. Clock-based
 * premium classification is exposed only when the mapping is deterministic.
 */
@Service
public class OrdinaryWorkPremiumSourceService {

    public static final String BLOCK_EXPLICIT_IDENTITY =
            "ORDINARY_PREMIUM_EXPLICIT_IDENTITY_REQUIRED";

    public static final String BLOCK_HOURS_ONLY =
            "ORDINARY_PREMIUM_HOURS_ONLY_CLOCK_REQUIRED";

    public static final String BLOCK_PLANNED_CLOCK =
            "ORDINARY_PREMIUM_PLANNED_CLOCK_REQUIRED";

    public static final String BLOCK_PLANNED_IDENTITY =
            "ORDINARY_PREMIUM_PLANNED_IDENTITY_REQUIRED";

    public static final String BLOCK_CLOCK_QUANTITY =
            "ORDINARY_PREMIUM_CLOCK_QUANTITY_AMBIGUOUS";

    public static final String BLOCK_CROSS_DATE =
            "ORDINARY_PREMIUM_CROSS_DATE_PLAN_OWNERSHIP_REQUIRED";

    private final ActualWorkIntervalRepository actualWork;
    private final ActualWorkDayAllocationService actualAllocation;
    private final DayEntryRepository days;
    private final VacationPlannerService vacationPlanner;
    private final ProductionCalendarService productionCalendar;
    private final PlannedWorkDayAllocationService plannedAllocation;
    private final PayClassificationService classification;

    public OrdinaryWorkPremiumSourceService(
            ActualWorkIntervalRepository actualWork,
            ActualWorkDayAllocationService actualAllocation,
            DayEntryRepository days,
            VacationPlannerService vacationPlanner,
            ProductionCalendarService productionCalendar,
            PlannedWorkDayAllocationService plannedAllocation,
            PayClassificationService classification
    ) {
        this.actualWork = actualWork;
        this.actualAllocation = actualAllocation;
        this.days = days;
        this.vacationPlanner = vacationPlanner;
        this.productionCalendar = productionCalendar;
        this.plannedAllocation = plannedAllocation;
        this.classification = classification;
    }

    @Transactional(readOnly = true)
    public OrdinaryPremiumSource project(
            AppUser user,
            LocalDate payrollDate
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Ordinary premium source requires user"
            );
        }

        if (payrollDate == null) {
            throw new IllegalArgumentException(
                    "Ordinary premium source requires payroll date"
            );
        }

        if (hasExplicitFact(
                user,
                payrollDate
        )) {
            return explicitSource(
                    user,
                    payrollDate
            );
        }

        return planDerivedSource(
                user,
                payrollDate
        );
    }

    private boolean hasExplicitFact(
            AppUser user,
            LocalDate date
    ) {
        List<ActualWorkInterval> intervals =
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                );

        for (ActualWorkInterval interval : intervals) {
            for (ActualWorkDayAllocationService.NetWorkSegment segment :
                    actualAllocation.netSegments(interval)) {

                if (date.equals(
                        segment.start()
                                .toLocalDate()
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    private OrdinaryPremiumSource explicitSource(
            AppUser user,
            LocalDate date
    ) {
        PayClassificationService.DayClassification day =
                classification.classify(
                        user,
                        date
                );

        List<ClassificationSlice> regular =
                day.slices()
                        .stream()
                        .filter(
                                ClassificationSlice::regular
                        )
                        .toList();

        int ordinaryMinutes =
                regular.stream()
                        .mapToInt(
                                ClassificationSlice::minutes
                        )
                        .sum();

        if (ordinaryMinutes
                != day.regularMinutes()) {
            throw new IllegalStateException(
                    "Ordinary premium factual source disagrees with Native Pay Classification"
            );
        }

        if (regular.stream()
                .anyMatch(slice ->
                        !slice.exact()
                                || slice.sourceActualWorkIntervalId() == null
                )) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.EXPLICIT,
                    ordinaryMinutes,
                    BLOCK_EXPLICIT_IDENTITY
            );
        }

        List<SourcePiece> pieces =
                regular.stream()
                        .map(slice ->
                                new SourcePiece(
                                        date,
                                        SourceKind.EXPLICIT,
                                        slice.sourceActualWorkIntervalId(),
                                        null,
                                        slice.startInstant(),
                                        slice.endInstant(),
                                        slice.sourceTimezone(),
                                        slice.minutes(),
                                        slice.night(),
                                        slice.holiday()
                                )
                        )
                        .toList();

        return OrdinaryPremiumSource.ready(
                date,
                SourceKind.EXPLICIT,
                ordinaryMinutes,
                merge(pieces)
        );
    }

    private OrdinaryPremiumSource planDerivedSource(
            AppUser user,
            LocalDate date
    ) {
        DayEntry day =
                days.findByOwnerAndDate(
                        user,
                        date
                ).orElse(null);

        int requiredMinutes =
                productionCalendar.requiredMinutes(
                        user,
                        date,
                        day
                );

        List<AbsenceOccurrenceDto> absences =
                vacationPlanner
                        .occurrences(
                                user,
                                date,
                                date
                        )
                        .stream()
                        .filter(item ->
                                isPostedStatus(
                                        item.status()
                                )
                        )
                        .toList();

        int absenceMinutes =
                canonicalAbsenceMinutes(
                        requiredMinutes,
                        absences
                );

        int ordinaryMinutes =
                Math.max(
                        0,
                        requiredMinutes
                                - Math.min(
                                        requiredMinutes,
                                        absenceMinutes
                                )
                );

        if (ordinaryMinutes == 0) {
            return OrdinaryPremiumSource.ready(
                    date,
                    SourceKind.PLAN_DERIVED,
                    0,
                    List.of()
            );
        }

        if (absences.stream()
                .anyMatch(item ->
                        "HOURS_ONLY".equals(
                                item.coverage()
                        )
                )) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_HOURS_ONLY
            );
        }

        if (day == null) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_PLANNED_CLOCK
            );
        }

        /*
         * DEEP_ORDINARY_SOURCE_IDENTITY
         *
         * A nonzero PLAN_DERIVED premium source must identify the persisted
         * dated shift occurrence, not merely its calendar date.
         */
        if (day.getId() == null
                || day.getId() <= 0) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_PLANNED_IDENTITY
            );
        }

        List<NetWorkSegment> planned =
                plannedAllocation.netSegments(
                        user,
                        day
                );

        if (planned.isEmpty()) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_PLANNED_CLOCK
            );
        }

        /*
         * Payroll currently owns PLAN_DERIVED quantity by DayEntry.date.
         *
         * PlannedWorkDayAllocationService can correctly expose an overnight
         * clock tail on tomorrow's source calendar date, but this source layer
         * must not silently redefine financial ownership. Keep that case
         * explicit and blocked until Payroll-date ownership is decided.
         */
        if (planned.stream()
                .anyMatch(segment ->
                        !date.equals(
                                segment.sourceDate()
                        )
                )) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_CROSS_DATE
            );
        }

        List<SourcePiece> clockPieces =
                classifyPlannedClock(
                        user,
                        date,
                        day.getId(),
                        planned
                );

        int clockMinutes =
                clockPieces.stream()
                        .mapToInt(
                                SourcePiece::minutes
                        )
                        .sum();

        if (clockMinutes
                != planned.stream()
                .mapToInt(
                        NetWorkSegment::minutes
                )
                .sum()) {
            throw new IllegalStateException(
                    "Ordinary premium planned classification changed clock minutes"
            );
        }

        if (ordinaryMinutes == clockMinutes) {
            return OrdinaryPremiumSource.ready(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    clockPieces
            );
        }

        if (ordinaryMinutes > clockMinutes) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_CLOCK_QUANTITY
            );
        }

        /*
         * canonical quantity < clock quantity:
         *
         * NORM_OVERRIDE and quantitative PARTIAL absence do not currently say
         * WHICH planned clock minutes survive. We may still price safely if
         * every possible minute has the same source date + NIGHT + HOLIDAY
         * dimensions, because removing any subset produces the same economic
         * classification.
         */
        if (!sameEconomicSignature(
                clockPieces
        )) {
            return OrdinaryPremiumSource.blocked(
                    date,
                    SourceKind.PLAN_DERIVED,
                    ordinaryMinutes,
                    BLOCK_CLOCK_QUANTITY
            );
        }

        SourcePiece signature =
                clockPieces.get(0);

        return OrdinaryPremiumSource.ready(
                date,
                SourceKind.PLAN_DERIVED,
                ordinaryMinutes,
                List.of(
                        new SourcePiece(
                                signature.sourceDate(),
                                SourceKind.PLAN_DERIVED,
                                null,
                                signature.sourceDayEntryId(),
                                signature.sourceEvidenceStartInstant(),
                                signature.sourceEvidenceEndInstant(),
                                signature.sourceEvidenceTimezone(),
                                ordinaryMinutes,
                                signature.night(),
                                signature.holiday()
                        )
                )
        );
    }

    private List<SourcePiece> classifyPlannedClock(
            AppUser user,
            LocalDate sourceDate,
            Long sourceDayEntryId,
            List<NetWorkSegment> segments
    ) {
        boolean holiday =
                isHoliday(
                        user,
                        sourceDate
                );

        List<SourcePiece> result =
                new ArrayList<>();

        for (NetWorkSegment segment : segments) {
            ZoneId zone =
                    ZoneId.of(
                            segment.sourceTimezone()
                    );

            int minutes =
                    segment.minutes();

            Boolean activeNight = null;
            int groupMinutes = 0;

            for (int offset = 0;
                 offset < minutes;
                 offset++) {

                Instant minuteInstant =
                        segment.startInstant()
                                .plusSeconds(
                                        offset * 60L
                                );

                LocalTime localTime =
                        minuteInstant
                                .atZone(zone)
                                .toLocalTime();

                boolean night =
                        PayClassificationService
                                .DEFAULT_NIGHT_WINDOW
                                .contains(
                                        localTime
                                );

                if (activeNight == null) {
                    activeNight = night;
                    groupMinutes = 1;
                    continue;
                }

                if (activeNight == night) {
                    groupMinutes++;
                    continue;
                }

                result.add(
                        new SourcePiece(
                                sourceDate,
                                SourceKind.PLAN_DERIVED,
                                null,
                                sourceDayEntryId,
                                segment.startInstant(),
                                segment.endInstant(),
                                segment.sourceTimezone(),
                                groupMinutes,
                                activeNight,
                                holiday
                        )
                );

                activeNight = night;
                groupMinutes = 1;
            }

            if (activeNight != null
                    && groupMinutes > 0) {
                result.add(
                        new SourcePiece(
                                sourceDate,
                                SourceKind.PLAN_DERIVED,
                                null,
                                sourceDayEntryId,
                                segment.startInstant(),
                                segment.endInstant(),
                                segment.sourceTimezone(),
                                groupMinutes,
                                activeNight,
                                holiday
                        )
                );
            }
        }

        return merge(result);
    }

    private boolean isHoliday(
            AppUser user,
            LocalDate date
    ) {
        ProductionCalendarDayDto day =
                productionCalendar.resolvedDay(
                        user,
                        date
                );

        return day != null
                && "HOLIDAY".equals(
                        day.payrollEffect()
                );
    }

    private int canonicalAbsenceMinutes(
            int plannedMinutes,
            List<AbsenceOccurrenceDto> absences
    ) {
        if (absences.stream()
                .anyMatch(item ->
                        "FULL_DAY".equals(
                                item.coverage()
                        )
                                && item.replacesShift()
                )) {
            return plannedMinutes;
        }

        int partial =
                absences.stream()
                        .filter(item ->
                                "PARTIAL".equals(
                                        item.coverage()
                                )
                        )
                        .mapToInt(
                                this::partialMinutes
                        )
                        .sum();

        return plannedMinutes > 0
                ? Math.min(
                        plannedMinutes,
                        partial
                )
                : partial;
    }

    private int partialMinutes(
            AbsenceOccurrenceDto item
    ) {
        if (item.startTime() == null
                || item.endTime() == null) {
            return 0;
        }

        return Math.toIntExact(
                Duration.between(
                        LocalTime.parse(
                                item.startTime()
                        ),
                        LocalTime.parse(
                                item.endTime()
                        )
                ).toMinutes()
        );
    }

    private boolean isPostedStatus(
            String status
    ) {
        return "APPROVED".equals(status)
                || "COMPLETED".equals(status);
    }

    private boolean sameEconomicSignature(
            List<SourcePiece> pieces
    ) {
        if (pieces.isEmpty()) {
            return false;
        }

        SourcePiece first =
                pieces.get(0);

        return pieces.stream()
                .allMatch(piece ->
                        piece.sourceDate()
                                .equals(
                                        first.sourceDate()
                                )
                                && piece.night()
                                == first.night()
                                && piece.holiday()
                                == first.holiday()
                );
    }

    private List<SourcePiece> merge(
            List<SourcePiece> source
    ) {
        List<SourcePiece> result =
                new ArrayList<>();

        for (SourcePiece next : source) {
            if (result.isEmpty()) {
                result.add(next);
                continue;
            }

            int lastIndex =
                    result.size() - 1;

            SourcePiece previous =
                    result.get(lastIndex);

            if (!previous.sameIdentityAndDimensions(
                    next
            )) {
                result.add(next);
                continue;
            }

            result.set(
                    lastIndex,
                    new SourcePiece(
                            previous.sourceDate(),
                            previous.sourceKind(),
                            previous.sourceActualWorkIntervalId(),
                            previous.sourceDayEntryId(),
                            previous.sourceEvidenceStartInstant(),
                            previous.sourceEvidenceEndInstant(),
                            previous.sourceEvidenceTimezone(),
                            Math.addExact(
                                    previous.minutes(),
                                    next.minutes()
                            ),
                            previous.night(),
                            previous.holiday()
                    )
            );
        }

        return List.copyOf(result);
    }

    public enum SourceKind {
        EXPLICIT,
        PLAN_DERIVED
    }

    public record SourcePiece(
            LocalDate sourceDate,
            SourceKind sourceKind,
            Long sourceActualWorkIntervalId,
            Long sourceDayEntryId,
            Instant sourceEvidenceStartInstant,
            Instant sourceEvidenceEndInstant,
            String sourceEvidenceTimezone,
            int minutes,
            boolean night,
            boolean holiday
    ) {
        /**
         * Compatibility constructor for older unit/read-model callers.
         *
         * Production OrdinaryWorkPremiumSourceService always supplies the
         * complete deep identity. D1b fingerprinting will fail closed if a
         * premium-bearing production projection somehow lacks it.
         */
        public SourcePiece(
                LocalDate sourceDate,
                SourceKind sourceKind,
                Long sourceActualWorkIntervalId,
                int minutes,
                boolean night,
                boolean holiday
        ) {
            this(
                    sourceDate,
                    sourceKind,
                    sourceActualWorkIntervalId,
                    null,
                    null,
                    null,
                    null,
                    minutes,
                    night,
                    holiday
            );
        }

        public SourcePiece {
            if (sourceDate == null
                    || sourceKind == null
                    || minutes <= 0) {
                throw new IllegalArgumentException(
                        "Ordinary premium source piece is invalid"
                );
            }

            if (sourceKind == SourceKind.EXPLICIT
                    && sourceActualWorkIntervalId == null) {
                throw new IllegalArgumentException(
                        "Explicit ordinary premium source requires Actual Work identity"
                );
            }

            if (sourceKind == SourceKind.EXPLICIT
                    && sourceDayEntryId != null) {
                throw new IllegalArgumentException(
                        "Explicit ordinary premium source cannot own planned DayEntry identity"
                );
            }

            if (sourceKind == SourceKind.PLAN_DERIVED
                    && sourceActualWorkIntervalId != null) {
                throw new IllegalArgumentException(
                        "Plan-derived ordinary premium source cannot own Actual Work identity"
                );
            }

            if (sourceDayEntryId != null
                    && sourceDayEntryId <= 0) {
                throw new IllegalArgumentException(
                        "Planned ordinary premium DayEntry identity must be positive"
                );
            }

            boolean anyEvidence =
                    sourceEvidenceStartInstant != null
                            || sourceEvidenceEndInstant != null
                            || sourceEvidenceTimezone != null;

            if (anyEvidence) {
                if (sourceEvidenceStartInstant == null
                        || sourceEvidenceEndInstant == null
                        || sourceEvidenceTimezone == null
                        || sourceEvidenceTimezone.isBlank()
                        || !sourceEvidenceEndInstant.isAfter(
                                sourceEvidenceStartInstant
                        )) {
                    throw new IllegalArgumentException(
                            "Ordinary premium source clock evidence is incomplete"
                    );
                }

                int evidenceMinutes =
                        Math.toIntExact(
                                Duration.between(
                                        sourceEvidenceStartInstant,
                                        sourceEvidenceEndInstant
                                ).toMinutes()
                        );

                if (sourceKind == SourceKind.EXPLICIT
                        && evidenceMinutes != minutes) {
                    throw new IllegalArgumentException(
                            "Explicit ordinary premium evidence must equal factual slice minutes"
                    );
                }

                /*
                 * PLAN_DERIVED may intentionally retain a larger candidate
                 * clock interval when canonical quantity is smaller but every
                 * candidate minute has the same economic dimensions.
                 */
                if (sourceKind == SourceKind.PLAN_DERIVED
                        && evidenceMinutes < minutes) {
                    throw new IllegalArgumentException(
                            "Planned ordinary premium evidence cannot be shorter than canonical minutes"
                    );
                }
            }
        }

        public boolean deepIdentityComplete() {
            boolean evidenceComplete =
                    sourceEvidenceStartInstant != null
                            && sourceEvidenceEndInstant != null
                            && sourceEvidenceTimezone != null
                            && !sourceEvidenceTimezone.isBlank()
                            && sourceEvidenceEndInstant.isAfter(
                                    sourceEvidenceStartInstant
                            );

            if (!evidenceComplete) {
                return false;
            }

            return switch (sourceKind) {
                case EXPLICIT ->
                        sourceActualWorkIntervalId != null
                                && sourceActualWorkIntervalId > 0
                                && sourceDayEntryId == null;

                case PLAN_DERIVED ->
                        sourceActualWorkIntervalId == null
                                && sourceDayEntryId != null
                                && sourceDayEntryId > 0;
            };
        }

        public ConsumedSlice consumedSlice() {
            return new ConsumedSlice(
                    minutes,
                    night,
                    holiday,
                    false,
                    0
            );
        }

        boolean sameIdentityAndDimensions(
                SourcePiece other
        ) {
            return other != null
                    && sourceDate.equals(
                            other.sourceDate
                    )
                    && sourceKind == other.sourceKind
                    && java.util.Objects.equals(
                            sourceActualWorkIntervalId,
                            other.sourceActualWorkIntervalId
                    )
                    && java.util.Objects.equals(
                            sourceDayEntryId,
                            other.sourceDayEntryId
                    )
                    && java.util.Objects.equals(
                            sourceEvidenceStartInstant,
                            other.sourceEvidenceStartInstant
                    )
                    && java.util.Objects.equals(
                            sourceEvidenceEndInstant,
                            other.sourceEvidenceEndInstant
                    )
                    && java.util.Objects.equals(
                            sourceEvidenceTimezone,
                            other.sourceEvidenceTimezone
                    )
                    && night == other.night
                    && holiday == other.holiday;
        }
    }

    public record OrdinaryPremiumSource(
            LocalDate payrollDate,
            SourceKind sourceKind,
            int canonicalOrdinaryMinutes,
            boolean ready,
            String blockingReason,
            List<SourcePiece> pieces
    ) {
        public OrdinaryPremiumSource {
            if (payrollDate == null
                    || sourceKind == null
                    || canonicalOrdinaryMinutes < 0) {
                throw new IllegalArgumentException(
                        "Ordinary premium source identity is invalid"
                );
            }

            pieces =
                    pieces == null
                            ? List.of()
                            : List.copyOf(
                                    pieces
                            );

            if (ready) {
                if (blockingReason != null) {
                    throw new IllegalArgumentException(
                            "Ready ordinary premium source cannot be blocked"
                    );
                }

                int sourceMinutes =
                        pieces.stream()
                                .mapToInt(
                                        SourcePiece::minutes
                                )
                                .sum();

                if (sourceMinutes
                        != canonicalOrdinaryMinutes) {
                    throw new IllegalArgumentException(
                            "Ordinary premium source must preserve canonical ordinary minutes"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || !pieces.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked ordinary premium source must contain one reason and no speculative pieces"
                    );
                }
            }
        }

        static OrdinaryPremiumSource ready(
                LocalDate date,
                SourceKind kind,
                int minutes,
                List<SourcePiece> pieces
        ) {
            return new OrdinaryPremiumSource(
                    date,
                    kind,
                    minutes,
                    true,
                    null,
                    pieces
            );
        }

        static OrdinaryPremiumSource blocked(
                LocalDate date,
                SourceKind kind,
                int minutes,
                String reason
        ) {
            return new OrdinaryPremiumSource(
                    date,
                    kind,
                    minutes,
                    false,
                    reason,
                    List.of()
            );
        }
    }
}

package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure classification kernel for factual paid work.
 *
 * It assigns independent pay dimensions to already-net Actual Work minutes.
 * It does not price minutes, mutate Time Bank, create overtime credits or
 * persist payroll state.
 *
 * REGULAR and OVERTIME partition factual minutes by ordinal threshold.
 * NIGHT and HOLIDAY are independent dimensions and may overlap either side.
 */
@Service
public class PayClassificationEngine {

    public List<ClassificationSlice> classifyDay(
            LocalDate date,
            List<NetWorkSegment> sourceSegments,
            int ordinaryThresholdMinutes,
            boolean holiday,
            NightWindow nightWindow
    ) {
        List<SourceWorkSegment> wrapped =
                sourceSegments == null
                        ? null
                        : sourceSegments.stream()
                        .filter(segment -> segment != null)
                        .map(segment ->
                                new SourceWorkSegment(
                                        null,
                                        segment
                                )
                        )
                        .toList();

        return classifyDayInternal(
                date,
                wrapped,
                ordinaryThresholdMinutes,
                0,
                holiday,
                nightWindow
        );
    }

    /**
     * Production-domain entry point.
     *
     * The source Actual Work id travels through every resulting slice without
     * changing classification semantics. The pure classifyDay overload remains
     * available for callers that do not need persisted factual provenance.
     */
    public List<ClassificationSlice> classifyDayWithSources(
            LocalDate date,
            List<SourceWorkSegment> sourceSegments,
            int ordinaryThresholdMinutes,
            boolean holiday,
            NightWindow nightWindow
    ) {
        return classifyDayWithSources(
                date,
                sourceSegments,
                ordinaryThresholdMinutes,
                0,
                holiday,
                nightWindow
        );
    }

    /**
     * Source-workday continuation entry point.
     *
     * A factual work interval may cross a source-calendar midnight while still
     * belonging to one source workday. The caller supplies the number of
     * already-worked net minutes from that same source workday so REGULAR /
     * OVERTIME ordinal classification continues instead of resetting at 00:00.
     *
     * NIGHT and HOLIDAY remain dimensions of the requested factual calendar
     * date; this offset affects only the REGULAR / OVERTIME ordinal.
     */
    public List<ClassificationSlice> classifyDayWithSources(
            LocalDate date,
            List<SourceWorkSegment> sourceSegments,
            int ordinaryThresholdMinutes,
            int initialWorkedOrdinalMinutes,
            boolean holiday,
            NightWindow nightWindow
    ) {
        if (sourceSegments != null) {
            for (SourceWorkSegment source : sourceSegments) {
                if (source == null
                        || source.sourceActualWorkIntervalId() == null
                        || source.sourceActualWorkIntervalId() <= 0) {
                    throw new IllegalArgumentException(
                            "Source-aware classification requires persisted Actual Work identity"
                    );
                }
            }
        }

        return classifyDayInternal(
                date,
                sourceSegments,
                ordinaryThresholdMinutes,
                initialWorkedOrdinalMinutes,
                holiday,
                nightWindow
        );
    }

    private List<ClassificationSlice> classifyDayInternal(
            LocalDate date,
            List<SourceWorkSegment> sourceSegments,
            int ordinaryThresholdMinutes,
            int initialWorkedOrdinalMinutes,
            boolean holiday,
            NightWindow nightWindow
    ) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "Classification requires source date"
            );
        }

        if (ordinaryThresholdMinutes < 0) {
            throw new IllegalArgumentException(
                    "Ordinary threshold cannot be negative"
            );
        }

        if (initialWorkedOrdinalMinutes < 0) {
            throw new IllegalArgumentException(
                    "Initial worked ordinal cannot be negative"
            );
        }

        if (nightWindow == null) {
            throw new IllegalArgumentException(
                    "Night window is required"
            );
        }

        if (sourceSegments == null || sourceSegments.isEmpty()) {
            return List.of();
        }

        List<SourceWorkSegment> segments =
                sourceSegments.stream()
                        .filter(source ->
                                source != null
                                        && source.segment() != null
                                        && source.segment().minutes() > 0
                        )
                        .sorted((left, right) ->
                                segmentComparator().compare(
                                        left.segment(),
                                        right.segment()
                                )
                        )
                        .toList();

        int workedOrdinal = initialWorkedOrdinalMinutes;
        List<ClassificationSlice> result =
                new ArrayList<>();

        for (SourceWorkSegment source : segments) {
            NetWorkSegment segment =
                    source.segment();

            if (!segment.start().toLocalDate().equals(date)) {
                throw new IllegalArgumentException(
                        "Classification segment belongs to another source date"
                );
            }

            int segmentMinutes =
                    segment.minutes();

            Flags activeFlags = null;
            int groupStartOffset = 0;
            int groupWorkedOrdinalStart = workedOrdinal;

            for (
                    int offset = 0;
                    offset < segmentMinutes;
                    offset++
            ) {
                LocalDateTime minuteStart =
                        localAtOffset(
                                segment,
                                offset
                        );

                boolean overtime =
                        workedOrdinal
                                >= ordinaryThresholdMinutes;

                Flags flags =
                        new Flags(
                                !overtime,
                                nightWindow.contains(
                                        minuteStart.toLocalTime()
                                ),
                                holiday,
                                overtime
                        );

                if (activeFlags == null) {
                    activeFlags = flags;
                    groupStartOffset = offset;
                    groupWorkedOrdinalStart =
                            workedOrdinal;
                } else if (!activeFlags.equals(flags)) {
                    result.add(
                            buildSlice(
                                    source,
                                    groupStartOffset,
                                    offset,
                                    groupWorkedOrdinalStart,
                                    activeFlags
                            )
                    );

                    activeFlags = flags;
                    groupStartOffset = offset;
                    groupWorkedOrdinalStart =
                            workedOrdinal;
                }

                workedOrdinal++;
            }

            if (activeFlags != null) {
                result.add(
                        buildSlice(
                                source,
                                groupStartOffset,
                                segmentMinutes,
                                groupWorkedOrdinalStart,
                                activeFlags
                        )
                );
            }
        }

        return List.copyOf(result);
    }

    private Comparator<NetWorkSegment> segmentComparator() {
        return (left, right) -> {
            if (left.exact() && right.exact()) {
                return left.startInstant()
                        .compareTo(right.startInstant());
            }

            int local =
                    left.start().compareTo(right.start());

            if (local != 0) {
                return local;
            }

            if (left.exact() && !right.exact()) {
                return -1;
            }

            if (!left.exact() && right.exact()) {
                return 1;
            }

            return 0;
        };
    }

    private LocalDateTime localAtOffset(
            NetWorkSegment segment,
            int offsetMinutes
    ) {
        if (!segment.exact()) {
            return segment.start()
                    .plusMinutes(offsetMinutes);
        }

        ZoneId zone =
                ZoneId.of(segment.sourceTimezone());

        return segment.startInstant()
                .plusSeconds(offsetMinutes * 60L)
                .atZone(zone)
                .toLocalDateTime();
    }

    private ClassificationSlice buildSlice(
            SourceWorkSegment source,
            int fromOffset,
            int toOffset,
            int workedOrdinalStartMinutes,
            Flags flags
    ) {
        if (toOffset <= fromOffset) {
            throw new IllegalArgumentException(
                    "Classification slice must contain minutes"
            );
        }

        NetWorkSegment segment =
                source.segment();

        int minutes =
                toOffset - fromOffset;

        if (!segment.exact()) {
            LocalDateTime start =
                    segment.start()
                            .plusMinutes(fromOffset);

            LocalDateTime end =
                    segment.start()
                            .plusMinutes(toOffset);

            return new ClassificationSlice(
                    start,
                    end,
                    null,
                    null,
                    null,
                    source.sourceActualWorkIntervalId(),
                    workedOrdinalStartMinutes,
                    minutes,
                    flags.regular(),
                    flags.night(),
                    flags.holiday(),
                    flags.overtime()
            );
        }

        ZoneId zone =
                ZoneId.of(
                        segment.sourceTimezone()
                );

        Instant startInstant =
                segment.startInstant()
                        .plusSeconds(
                                fromOffset * 60L
                        );

        Instant endInstant =
                segment.startInstant()
                        .plusSeconds(
                                toOffset * 60L
                        );

        return new ClassificationSlice(
                startInstant.atZone(zone)
                        .toLocalDateTime(),
                endInstant.atZone(zone)
                        .toLocalDateTime(),
                startInstant,
                endInstant,
                zone.getId(),
                source.sourceActualWorkIntervalId(),
                workedOrdinalStartMinutes,
                minutes,
                flags.regular(),
                flags.night(),
                flags.holiday(),
                flags.overtime()
        );
    }

    private record Flags(
            boolean regular,
            boolean night,
            boolean holiday,
            boolean overtime
    ) {}

    public record SourceWorkSegment(
            Long sourceActualWorkIntervalId,
            NetWorkSegment segment
    ) {
        public SourceWorkSegment {
            if (segment == null) {
                throw new IllegalArgumentException(
                        "Source work segment requires net segment"
                );
            }
        }
    }

    public record NightWindow(
            LocalTime start,
            LocalTime end
    ) {
        public NightWindow {
            if (start == null || end == null) {
                throw new IllegalArgumentException(
                        "Night window requires both boundaries"
                );
            }

            if (start.equals(end)) {
                throw new IllegalArgumentException(
                        "Night window boundaries must differ"
                );
            }

            if (start.getSecond() != 0
                    || start.getNano() != 0
                    || end.getSecond() != 0
                    || end.getNano() != 0) {
                throw new IllegalArgumentException(
                        "Night window must use minute precision"
                );
            }
        }

        public boolean contains(LocalTime time) {
            if (start.isBefore(end)) {
                return !time.isBefore(start)
                        && time.isBefore(end);
            }

            return !time.isBefore(start)
                    || time.isBefore(end);
        }
    }

    public record ClassificationSlice(
            LocalDateTime start,
            LocalDateTime end,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            Long sourceActualWorkIntervalId,
            int workedOrdinalStartMinutes,
            int minutes,
            boolean regular,
            boolean night,
            boolean holiday,
            boolean overtime
    ) {
        public ClassificationSlice {
            if (start == null || end == null) {
                throw new IllegalArgumentException(
                        "Classification slice requires local boundaries"
                );
            }

            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Classification slice must contain positive minutes"
                );
            }

            if (workedOrdinalStartMinutes < 0) {
                throw new IllegalArgumentException(
                        "Worked ordinal cannot be negative"
                );
            }

            if (regular == overtime) {
                throw new IllegalArgumentException(
                        "Exactly one of REGULAR or OVERTIME must be true"
                );
            }

            boolean anyAbsolute =
                    startInstant != null
                            || endInstant != null
                            || sourceTimezone != null;

            if (anyAbsolute) {
                if (startInstant == null
                        || endInstant == null
                        || sourceTimezone == null
                        || !endInstant.isAfter(startInstant)) {
                    throw new IllegalArgumentException(
                            "Exact classification slice requires complete absolute identity"
                    );
                }

                int elapsed = Math.toIntExact(
                        Duration.between(
                                startInstant,
                                endInstant
                        ).toMinutes()
                );

                if (elapsed != minutes) {
                    throw new IllegalArgumentException(
                            "Exact classification minutes must match elapsed identity"
                    );
                }
            } else {
                int elapsed = Math.toIntExact(
                        Duration.between(start, end)
                                .toMinutes()
                );

                if (elapsed != minutes) {
                    throw new IllegalArgumentException(
                            "Legacy classification minutes must match local duration"
                    );
                }
            }
        }

        public boolean exact() {
            return startInstant != null
                    && endInstant != null
                    && sourceTimezone != null;
        }
    }
}

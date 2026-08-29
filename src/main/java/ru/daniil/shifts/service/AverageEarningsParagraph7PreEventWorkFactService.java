package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-7 pre-event actually-worked FACT authority.
 *
 * <p>This layer deliberately stops before event-month wage pricing. It exposes
 * only canonical worked-day/worked-minute facts from the beginning of the
 * legal event month through the day immediately preceding the event. It never
 * includes the event date itself and never reads future days in that month.</p>
 *
 * <p>The service does not decide whether paragraph 7 ultimately applies, does
 * not select paragraph 8, does not calculate salary, premiums or average
 * earnings, and does not infer accrued wage money from posting-month totals.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventWorkFactService {

    public static final String SOURCE_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_WORK_SOURCE_WINDOW_MISMATCH";

    private final TimeCompensationService timeCompensation;

    public AverageEarningsParagraph7PreEventWorkFactService(
            TimeCompensationService timeCompensation
    ) {
        this.timeCompensation = Objects.requireNonNull(
                timeCompensation,
                "Paragraph-7 pre-event work facts require time compensation authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 pre-event work facts require user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-7 pre-event work facts require event date"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        LocalDate periodFrom = eventMonth.atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (eventDate.equals(periodFrom)) {
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    cutoffExclusive,
                    0,
                    0L,
                    List.of()
            );
        }

        LocalDate periodTo = eventDate.minusDays(1);

        PayrollSourceSnapshot source = Objects.requireNonNull(
                timeCompensation.payrollSource(
                        user,
                        periodFrom,
                        periodTo
                ),
                "Paragraph-7 pre-event payroll source returned null"
        );

        if (!periodFrom.equals(source.from())
                || !periodTo.equals(source.to())) {
            throw new IllegalStateException(
                    SOURCE_WINDOW_MISMATCH
            );
        }

        List<WorkedDayFact> workedDays = new ArrayList<>();
        LocalDate previous = null;
        long workedMinutes = 0L;

        for (PayrollSourceDay day : source.days()) {
            Objects.requireNonNull(
                    day,
                    "Paragraph-7 pre-event payroll source contains null day"
            );

            LocalDate date = Objects.requireNonNull(
                    day.date(),
                    "Paragraph-7 pre-event payroll source day requires date"
            );

            if (date.isBefore(periodFrom)
                    || !date.isBefore(cutoffExclusive)) {
                throw new IllegalStateException(
                        "Paragraph-7 pre-event payroll source day lies outside legal window"
                );
            }

            if (previous != null
                    && !date.isAfter(previous)) {
                throw new IllegalStateException(
                        "Paragraph-7 pre-event payroll source days must be strictly chronological"
                );
            }
            previous = date;

            requireNonNegative(day);

            workedMinutes = Math.addExact(
                    workedMinutes,
                    day.workedMinutes()
            );

            if (day.workedMinutes() > 0) {
                workedDays.add(
                        new WorkedDayFact(
                                date,
                                day.workedMinutes(),
                                day.plannedMinutes(),
                                day.hourlyBaseWorkedMinutes()
                        )
                );
            }
        }

        if (workedMinutes != source.workedMinutes()) {
            throw new IllegalStateException(
                    "Paragraph-7 pre-event worked-minute aggregate disagrees with canonical Payroll source"
            );
        }

        return Resolution.ready(
                eventDate,
                periodFrom,
                cutoffExclusive,
                workedDays.size(),
                workedMinutes,
                workedDays
        );
    }

    private static void requireNonNegative(
            PayrollSourceDay day
    ) {
        if (day.plannedMinutes() < 0
                || day.workedMinutes() < 0
                || day.vacationMinutes() < 0
                || day.sickMinutes() < 0
                || day.overtimeCompensatedMinutes() < 0
                || day.unpaidMinutes() < 0
                || day.hourlyBaseWorkedMinutes() < 0) {
            throw new IllegalStateException(
                    "Paragraph-7 pre-event Payroll source day contains negative minutes"
            );
        }

        if (day.hourlyBaseWorkedMinutes() > day.workedMinutes()) {
            throw new IllegalStateException(
                    "Paragraph-7 pre-event hourly-base worked minutes exceed factual worked minutes"
            );
        }
    }

    public record WorkedDayFact(
            LocalDate date,
            int workedMinutes,
            int plannedMinutes,
            int hourlyBaseWorkedMinutes
    ) {
        public WorkedDayFact {
            Objects.requireNonNull(
                    date,
                    "Paragraph-7 worked-day date is required"
            );

            if (workedMinutes <= 0
                    || plannedMinutes < 0
                    || hourlyBaseWorkedMinutes < 0
                    || hourlyBaseWorkedMinutes > workedMinutes) {
                throw new IllegalArgumentException(
                        "Paragraph-7 worked-day fact is invalid"
                );
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            int workedDayCount,
            long workedMinutes,
            List<WorkedDayFact> workedDays
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-7 event date is required"
            );
            Objects.requireNonNull(
                    periodFrom,
                    "Paragraph-7 period start is required"
            );
            Objects.requireNonNull(
                    cutoffExclusive,
                    "Paragraph-7 cutoff is required"
            );

            workedDays = List.copyOf(
                    Objects.requireNonNull(
                            workedDays,
                            "Paragraph-7 worked-day facts are required"
                    )
            );

            if (!periodFrom.equals(
                    YearMonth.from(eventDate).atDay(1)
            )
                    || !cutoffExclusive.equals(eventDate)
                    || workedDayCount < 0
                    || workedMinutes < 0L
                    || workedDayCount != workedDays.size()) {
                throw new IllegalArgumentException(
                        "Paragraph-7 pre-event work resolution is invalid"
                );
            }

            long total = 0L;
            LocalDate previous = null;

            for (WorkedDayFact day : workedDays) {
                if (day == null
                        || day.date().isBefore(periodFrom)
                        || !day.date().isBefore(cutoffExclusive)
                        || (previous != null
                        && !day.date().isAfter(previous))) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 worked-day facts exceed legal pre-event window"
                    );
                }

                previous = day.date();
                total = Math.addExact(
                        total,
                        day.workedMinutes()
                );
            }

            if (total != workedMinutes) {
                throw new IllegalArgumentException(
                        "Paragraph-7 worked-day facts do not preserve worked-minute total"
                );
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                LocalDate cutoffExclusive,
                int workedDayCount,
                long workedMinutes,
                List<WorkedDayFact> workedDays
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    cutoffExclusive,
                    workedDayCount,
                    workedMinutes,
                    workedDays
            );
        }

        public boolean workedTimePresent() {
            return workedDayCount > 0;
        }
    }
}

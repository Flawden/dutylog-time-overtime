package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P1B3A machine-owned qualified-time authority for the future HOLIDAY_PAY
 * economic earning.
 *
 * <p>The service deliberately does not consume the legacy {@code holiday}
 * classification bit from {@link SourcePiece}. That bit is a payroll
 * classification dimension and is not sufficient evidence for the legal
 * economic concept "work on a statutory public holiday and/or an employee
 * rest day".</p>
 *
 * <p>Canonical paid REGULAR source pieces still come only from
 * {@link OrdinaryWorkPremiumSourceService}. For every payroll date that owns
 * non-zero REGULAR pieces, this authority independently resolves:</p>
 *
 * <ul>
 *   <li>{@link StatutoryPublicHolidayAuthorityService};</li>
 *   <li>{@link EmployeeRestDayAuthorityService}.</li>
 * </ul>
 *
 * <p>If either authority is unresolved, the entire month fails closed and no
 * partial quantity is returned. If both legal causes apply, the paid REGULAR
 * piece is counted exactly once with {@link Cause#BOTH}.</p>
 *
 * <p>This service calculates no money, chooses no Article 153 multiplier,
 * does not decide compensatory-day election, does not touch Time Bank and does
 * not persist Payroll snapshots. Those are later P1B3 stages.</p>
 */
@Service
public class HolidayPayQualifiedCauseAuthorityService {

    public static final String SOURCE_DATE_MISMATCH =
            "HOLIDAY_PAY_SOURCE_DATE_MISMATCH";

    public static final String SOURCE_IDENTITY_REQUIRED =
            "HOLIDAY_PAY_SOURCE_IDENTITY_REQUIRED";

    private final OrdinaryWorkPremiumSourceService ordinarySource;
    private final StatutoryPublicHolidayAuthorityService statutoryHoliday;
    private final EmployeeRestDayAuthorityService employeeRestDay;

    public HolidayPayQualifiedCauseAuthorityService(
            OrdinaryWorkPremiumSourceService ordinarySource,
            StatutoryPublicHolidayAuthorityService statutoryHoliday,
            EmployeeRestDayAuthorityService employeeRestDay
    ) {
        this.ordinarySource =
                Objects.requireNonNull(
                        ordinarySource,
                        "HOLIDAY_PAY qualified authority requires ordinary REGULAR source"
                );

        this.statutoryHoliday =
                Objects.requireNonNull(
                        statutoryHoliday,
                        "HOLIDAY_PAY qualified authority requires statutory holiday authority"
                );

        this.employeeRestDay =
                Objects.requireNonNull(
                        employeeRestDay,
                        "HOLIDAY_PAY qualified authority requires employee rest-day authority"
                );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            YearMonth payrollMonth
    ) {
        Objects.requireNonNull(
                user,
                "HOLIDAY_PAY qualified authority requires user"
        );
        Objects.requireNonNull(
                payrollMonth,
                "HOLIDAY_PAY qualified authority requires payroll month"
        );

        long qualifiedMinutes = 0L;
        List<QualifiedPiece> qualifiedPieces = new ArrayList<>();
        List<BlockingDay> blockers = new ArrayList<>();

        for (
                LocalDate date = payrollMonth.atDay(1);
                !date.isAfter(payrollMonth.atEndOfMonth());
                date = date.plusDays(1)
        ) {
            OrdinaryPremiumSource source =
                    Objects.requireNonNull(
                            ordinarySource.project(
                                    user,
                                    date
                            ),
                            "Ordinary REGULAR source cannot return null"
                    );

            if (!date.equals(source.payrollDate())) {
                blockers.add(
                        new BlockingDay(
                                date,
                                BlockerKind.SOURCE,
                                SOURCE_DATE_MISMATCH
                                        + ":"
                                        + date
                                        + ":"
                                        + source.payrollDate()
                        )
                );
                continue;
            }

            if (!source.ready()) {
                blockers.add(
                        new BlockingDay(
                                date,
                                BlockerKind.SOURCE,
                                source.blockingReason()
                        )
                );
                continue;
            }

            if (source.pieces().isEmpty()) {
                continue;
            }

            boolean invalidIdentity = false;
            for (SourcePiece piece : source.pieces()) {
                if (!date.equals(piece.sourceDate())
                        || !piece.deepIdentityComplete()) {
                    invalidIdentity = true;
                    break;
                }
            }

            if (invalidIdentity) {
                blockers.add(
                        new BlockingDay(
                                date,
                                BlockerKind.SOURCE,
                                SOURCE_IDENTITY_REQUIRED
                                        + ":"
                                        + date
                        )
                );
                continue;
            }

            StatutoryPublicHolidayAuthorityService.Resolution statutory =
                    Objects.requireNonNull(
                            statutoryHoliday.resolve(
                                    user,
                                    date
                            ),
                            "Statutory holiday authority cannot return null"
                    );

            EmployeeRestDayAuthorityService.Resolution restDay =
                    Objects.requireNonNull(
                            employeeRestDay.resolve(
                                    user,
                                    date
                            ),
                            "Employee rest-day authority cannot return null"
                    );

            boolean dayBlocked = false;

            if (!statutory.ready()) {
                blockers.add(
                        new BlockingDay(
                                date,
                                BlockerKind.STATUTORY_PUBLIC_HOLIDAY,
                                statutory.blockingReason()
                        )
                );
                dayBlocked = true;
            }

            if (!restDay.ready()) {
                blockers.add(
                        new BlockingDay(
                                date,
                                BlockerKind.EMPLOYEE_REST_DAY,
                                restDay.blockingReason()
                        )
                );
                dayBlocked = true;
            }

            if (dayBlocked) {
                continue;
            }

            boolean publicHoliday =
                    statutory.nonWorkingPublicHoliday();

            boolean employeeRest =
                    restDay.restDay();

            Cause cause =
                    cause(
                            publicHoliday,
                            employeeRest
                    );

            if (cause == null) {
                continue;
            }

            for (SourcePiece piece : source.pieces()) {
                qualifiedMinutes =
                        Math.addExact(
                                qualifiedMinutes,
                                piece.minutes()
                        );

                qualifiedPieces.add(
                        new QualifiedPiece(
                                date,
                                cause,
                                piece,
                                statutory,
                                restDay
                        )
                );
            }
        }

        if (!blockers.isEmpty()) {
            return new Resolution(
                    payrollMonth,
                    false,
                    null,
                    List.of(),
                    blockers
            );
        }

        return new Resolution(
                payrollMonth,
                true,
                PayrollQualifiedQuantity.minutes(
                        qualifiedMinutes
                ),
                qualifiedPieces,
                List.of()
        );
    }

    private static Cause cause(
            boolean publicHoliday,
            boolean employeeRestDay
    ) {
        if (publicHoliday && employeeRestDay) {
            return Cause.BOTH;
        }
        if (publicHoliday) {
            return Cause.PUBLIC_HOLIDAY;
        }
        if (employeeRestDay) {
            return Cause.EMPLOYEE_REST_DAY;
        }
        return null;
    }

    public enum Cause {
        PUBLIC_HOLIDAY,
        EMPLOYEE_REST_DAY,
        BOTH
    }

    public enum BlockerKind {
        SOURCE,
        STATUTORY_PUBLIC_HOLIDAY,
        EMPLOYEE_REST_DAY
    }

    public record BlockingDay(
            LocalDate date,
            BlockerKind kind,
            String reason
    ) {
        public BlockingDay {
            Objects.requireNonNull(
                    date,
                    "HOLIDAY_PAY blocker date is required"
            );
            Objects.requireNonNull(
                    kind,
                    "HOLIDAY_PAY blocker kind is required"
            );
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "HOLIDAY_PAY blocker reason is required"
                );
            }
        }
    }

    /**
     * Exact paid REGULAR evidence plus both resolved legal authorities.
     *
     * <p>The negative authority is intentionally retained too: a
     * PUBLIC_HOLIDAY-only piece still freezes the employee working-day answer,
     * and an EMPLOYEE_REST_DAY-only piece still carries complete statutory
     * non-holiday provenance. This prevents later snapshots from explaining a
     * result only by omission.</p>
     */
    public record QualifiedPiece(
            LocalDate payrollDate,
            Cause cause,
            SourcePiece sourcePiece,
            StatutoryPublicHolidayAuthorityService.Resolution statutoryResolution,
            EmployeeRestDayAuthorityService.Resolution restDayResolution
    ) {
        public QualifiedPiece {
            Objects.requireNonNull(
                    payrollDate,
                    "HOLIDAY_PAY qualified piece date is required"
            );
            Objects.requireNonNull(
                    cause,
                    "HOLIDAY_PAY qualified piece cause is required"
            );
            Objects.requireNonNull(
                    sourcePiece,
                    "HOLIDAY_PAY qualified source piece is required"
            );
            Objects.requireNonNull(
                    statutoryResolution,
                    "HOLIDAY_PAY statutory resolution is required"
            );
            Objects.requireNonNull(
                    restDayResolution,
                    "HOLIDAY_PAY rest-day resolution is required"
            );

            if (!payrollDate.equals(sourcePiece.sourceDate())
                    || !payrollDate.equals(statutoryResolution.date())
                    || !payrollDate.equals(restDayResolution.date())) {
                throw new IllegalArgumentException(
                        "HOLIDAY_PAY qualified provenance dates must agree"
                );
            }

            if (!sourcePiece.deepIdentityComplete()) {
                throw new IllegalArgumentException(
                        "HOLIDAY_PAY qualified piece requires deep paid REGULAR identity"
                );
            }

            if (!statutoryResolution.ready()
                    || !restDayResolution.ready()) {
                throw new IllegalArgumentException(
                        "HOLIDAY_PAY qualified piece requires both resolved authorities"
                );
            }

            Cause expected =
                    HolidayPayQualifiedCauseAuthorityService.cause(
                            statutoryResolution.nonWorkingPublicHoliday(),
                            restDayResolution.restDay()
                    );

            if (expected == null || expected != cause) {
                throw new IllegalArgumentException(
                        "HOLIDAY_PAY qualified piece cause disagrees with legal authorities"
                );
            }
        }

        public int minutes() {
            return sourcePiece.minutes();
        }
    }

    public record Resolution(
            YearMonth payrollMonth,
            boolean ready,
            PayrollQualifiedQuantity quantity,
            List<QualifiedPiece> pieces,
            List<BlockingDay> blockers
    ) {
        public Resolution {
            Objects.requireNonNull(
                    payrollMonth,
                    "HOLIDAY_PAY qualified result month is required"
            );

            pieces =
                    List.copyOf(
                            Objects.requireNonNull(
                                    pieces,
                                    "HOLIDAY_PAY qualified pieces are required"
                            )
                    );

            blockers =
                    List.copyOf(
                            Objects.requireNonNull(
                                    blockers,
                                    "HOLIDAY_PAY blockers are required"
                            )
                    );

            if (ready) {
                Objects.requireNonNull(
                        quantity,
                        "Ready HOLIDAY_PAY qualified result requires quantity"
                );

                if (!blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ready HOLIDAY_PAY qualified result cannot have blockers"
                    );
                }

                long pieceMinutes =
                        pieces.stream()
                                .mapToLong(
                                        QualifiedPiece::minutes
                                )
                                .sum();

                if (pieceMinutes != quantity.value()) {
                    throw new IllegalArgumentException(
                            "HOLIDAY_PAY qualified pieces must equal aggregate quantity"
                    );
                }
            } else {
                if (quantity != null
                        || !pieces.isEmpty()
                        || blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked HOLIDAY_PAY qualified result requires blockers and no partial result"
                    );
                }
            }
        }
    }
}

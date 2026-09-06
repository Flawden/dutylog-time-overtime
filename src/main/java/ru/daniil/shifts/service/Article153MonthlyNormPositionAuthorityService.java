package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.service.Article153EconomicLegalPolicy.NormPosition;
import ru.daniil.shifts.service.Article153EconomicLegalPolicy.PayMode;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.QualifiedPiece;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P1B3B2A machine-owned Article 153 monthly-norm-position authority.
 *
 * <p>This stage consumes only already-proven P1B3A qualifying paid REGULAR
 * source pieces. It never invents HOLIDAY_PAY qualification and never changes
 * Payroll money, Time Bank settlement or the native earning registry.</p>
 *
 * <p>For HOURLY compensation the Article 153 monthly salary-norm branch is not
 * applicable. For SALARY compensation this service compares canonical factual
 * worked minutes for the source month against the complete production-calendar
 * norm already owned by {@link HistoricalCompensationRateService}.</p>
 *
 * <p>Day totals are sufficient when the whole qualifying date lies strictly on
 * one side of the monthly norm boundary. If the boundary is crossed inside a
 * date containing a qualifying piece, this authority fails closed rather than
 * guessing the intra-day ordering of qualifying versus other worked minutes.
 * A later refinement may add exact minute-order authority for that edge case.</p>
 */
@Service
public class Article153MonthlyNormPositionAuthorityService {

    public static final String QUALIFIED_AUTHORITY_BLOCKED =
            "ARTICLE153_MONTHLY_NORM_QUALIFIED_AUTHORITY_BLOCKED";
    public static final String PAYROLL_SOURCE_WINDOW_MISMATCH =
            "ARTICLE153_MONTHLY_NORM_PAYROLL_SOURCE_WINDOW_MISMATCH";
    public static final String PAYROLL_SOURCE_DAY_DUPLICATE =
            "ARTICLE153_MONTHLY_NORM_PAYROLL_SOURCE_DAY_DUPLICATE";
    public static final String PAYROLL_SOURCE_TOTAL_MISMATCH =
            "ARTICLE153_MONTHLY_NORM_PAYROLL_SOURCE_TOTAL_MISMATCH";
    public static final String SOURCE_WORKED_DAY_MISSING =
            "ARTICLE153_MONTHLY_NORM_SOURCE_WORKED_DAY_MISSING";
    public static final String PAY_MODE_INCONSISTENT =
            "ARTICLE153_MONTHLY_NORM_PAY_MODE_INCONSISTENT";
    public static final String PRODUCTION_NORM_INCONSISTENT =
            "ARTICLE153_MONTHLY_NORM_PRODUCTION_NORM_INCONSISTENT";
    public static final String MONTHLY_NORM_BOUNDARY_AMBIGUOUS =
            "ARTICLE153_MONTHLY_NORM_BOUNDARY_AMBIGUOUS";

    private final HolidayPayQualifiedCauseAuthorityService qualifiedAuthority;
    private final HistoricalCompensationRateService historicalRate;
    private final TimeCompensationService timeCompensation;

    public Article153MonthlyNormPositionAuthorityService(
            HolidayPayQualifiedCauseAuthorityService qualifiedAuthority,
            HistoricalCompensationRateService historicalRate,
            TimeCompensationService timeCompensation
    ) {
        this.qualifiedAuthority = Objects.requireNonNull(
                qualifiedAuthority,
                "Article 153 norm authority requires P1B3A qualification"
        );
        this.historicalRate = Objects.requireNonNull(
                historicalRate,
                "Article 153 norm authority requires historical compensation"
        );
        this.timeCompensation = Objects.requireNonNull(
                timeCompensation,
                "Article 153 norm authority requires canonical factual work"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            YearMonth payrollMonth
    ) {
        Objects.requireNonNull(user, "Article 153 norm authority requires user");
        Objects.requireNonNull(payrollMonth, "Article 153 norm authority requires month");

        HolidayPayQualifiedCauseAuthorityService.Resolution qualified =
                Objects.requireNonNull(
                        qualifiedAuthority.resolve(user, payrollMonth),
                        "P1B3A qualification cannot return null"
                );

        if (!qualified.ready()) {
            return Resolution.blocked(
                    payrollMonth,
                    List.of(new BlockingFact(
                            payrollMonth.atDay(1),
                            BlockerKind.QUALIFIED_AUTHORITY,
                            QUALIFIED_AUTHORITY_BLOCKED
                                    + ":"
                                    + qualified.blockers().stream()
                                            .map(item -> item.kind() + "=" + item.reason())
                                            .reduce((left, right) -> left + ";" + right)
                                            .orElse("UNKNOWN")
                    ))
            );
        }

        if (qualified.pieces().isEmpty()) {
            return Resolution.ready(
                    payrollMonth,
                    Objects.requireNonNull(
                            qualified.quantity(),
                            "Ready P1B3A qualification requires quantity"
                    ),
                    List.of()
            );
        }

        List<QualifiedPiece> ordered = qualified.pieces().stream()
                .sorted(Comparator
                        .comparing(QualifiedPiece::payrollDate)
                        .thenComparing(item -> item.sourcePiece().sourceEvidenceStartInstant())
                        .thenComparing(item -> item.sourcePiece().sourceEvidenceEndInstant())
                        .thenComparing(item -> sourceIdentityKey(item.sourcePiece())))
                .toList();

        HistoricalBaseRate firstRate =
                Objects.requireNonNull(
                        historicalRate.resolve(user, ordered.get(0).payrollDate()),
                        "Historical compensation cannot return null"
                );

        PayMode payMode = payMode(firstRate);

        if (payMode == PayMode.HOURLY) {
            List<NormPositionPiece> pieces = new ArrayList<>();
            for (QualifiedPiece piece : ordered) {
                HistoricalBaseRate rate = historicalRate.resolve(user, piece.payrollDate());
                if (payMode(rate) != PayMode.HOURLY) {
                    return blocked(
                            payrollMonth,
                            piece.payrollDate(),
                            BlockerKind.COMPENSATION,
                            PAY_MODE_INCONSISTENT
                    );
                }
                pieces.add(resultPiece(
                        piece,
                        rate,
                        NormPosition.NOT_APPLICABLE,
                        null,
                        0,
                        0
                ));
            }
            return Resolution.ready(payrollMonth, qualified.quantity(), pieces);
        }

        int productionNorm = requireProductionNorm(firstRate);

        PayrollSourceSnapshot source = Objects.requireNonNull(
                timeCompensation.payrollSource(
                        user,
                        payrollMonth.atDay(1),
                        payrollMonth.atEndOfMonth()
                ),
                "Canonical Payroll source cannot return null"
        );

        if (!payrollMonth.atDay(1).equals(source.from())
                || !payrollMonth.atEndOfMonth().equals(source.to())) {
            return blocked(
                    payrollMonth,
                    payrollMonth.atDay(1),
                    BlockerKind.PAYROLL_SOURCE,
                    PAYROLL_SOURCE_WINDOW_MISMATCH
            );
        }

        Map<LocalDate, PayrollSourceDay> sourceDays = new LinkedHashMap<>();
        long sourceWorkedSum = 0L;
        for (PayrollSourceDay day : source.days()) {
            if (day == null
                    || day.date() == null
                    || day.date().isBefore(source.from())
                    || day.date().isAfter(source.to())) {
                return blocked(
                        payrollMonth,
                        payrollMonth.atDay(1),
                        BlockerKind.PAYROLL_SOURCE,
                        PAYROLL_SOURCE_WINDOW_MISMATCH
                );
            }
            if (sourceDays.putIfAbsent(day.date(), day) != null) {
                return blocked(
                        payrollMonth,
                        day.date(),
                        BlockerKind.PAYROLL_SOURCE,
                        PAYROLL_SOURCE_DAY_DUPLICATE
                );
            }
            sourceWorkedSum = Math.addExact(sourceWorkedSum, day.workedMinutes());
        }

        if (sourceWorkedSum != source.workedMinutes()) {
            return blocked(
                    payrollMonth,
                    payrollMonth.atDay(1),
                    BlockerKind.PAYROLL_SOURCE,
                    PAYROLL_SOURCE_TOTAL_MISMATCH
            );
        }

        Map<LocalDate, Long> workedBeforeDate = new LinkedHashMap<>();
        long runningWorked = 0L;
        for (LocalDate date = payrollMonth.atDay(1);
             !date.isAfter(payrollMonth.atEndOfMonth());
             date = date.plusDays(1)) {
            workedBeforeDate.put(date, runningWorked);
            PayrollSourceDay day = sourceDays.get(date);
            if (day != null) {
                runningWorked = Math.addExact(runningWorked, day.workedMinutes());
            }
        }

        List<NormPositionPiece> resolved = new ArrayList<>();

        for (QualifiedPiece piece : ordered) {
            LocalDate date = piece.payrollDate();
            HistoricalBaseRate rate = historicalRate.resolve(user, date);

            if (payMode(rate) != PayMode.SALARY) {
                return blocked(
                        payrollMonth,
                        date,
                        BlockerKind.COMPENSATION,
                        PAY_MODE_INCONSISTENT
                );
            }

            int dateNorm = requireProductionNorm(rate);
            if (dateNorm != productionNorm) {
                return blocked(
                        payrollMonth,
                        date,
                        BlockerKind.COMPENSATION,
                        PRODUCTION_NORM_INCONSISTENT
                );
            }

            PayrollSourceDay sourceDay = sourceDays.get(date);
            if (sourceDay == null || sourceDay.workedMinutes() <= 0) {
                return blocked(
                        payrollMonth,
                        date,
                        BlockerKind.PAYROLL_SOURCE,
                        SOURCE_WORKED_DAY_MISSING
                );
            }

            long before = workedBeforeDate.getOrDefault(date, 0L);
            long after = Math.addExact(before, sourceDay.workedMinutes());

            NormPosition position;
            if (before >= productionNorm) {
                position = NormPosition.ABOVE_MONTHLY_NORM;
            } else if (after <= productionNorm) {
                position = NormPosition.WITHIN_MONTHLY_NORM;
            } else {
                return blocked(
                        payrollMonth,
                        date,
                        BlockerKind.MONTHLY_NORM_BOUNDARY,
                        MONTHLY_NORM_BOUNDARY_AMBIGUOUS
                                + ":"
                                + date
                                + ":before="
                                + before
                                + ":day="
                                + sourceDay.workedMinutes()
                                + ":norm="
                                + productionNorm
                );
            }

            resolved.add(resultPiece(
                    piece,
                    rate,
                    position,
                    productionNorm,
                    before,
                    sourceDay.workedMinutes()
            ));
        }

        return Resolution.ready(payrollMonth, qualified.quantity(), resolved);
    }

    private static Resolution blocked(
            YearMonth month,
            LocalDate date,
            BlockerKind kind,
            String reason
    ) {
        return Resolution.blocked(
                month,
                List.of(new BlockingFact(date, kind, reason))
        );
    }

    private static PayMode payMode(HistoricalBaseRate rate) {
        Objects.requireNonNull(rate, "Historical compensation rate is required");
        return switch (rate.payMode()) {
            case "HOURLY" -> PayMode.HOURLY;
            case "SALARY" -> PayMode.SALARY;
            default -> throw new IllegalStateException(
                    "Unsupported historical compensation mode " + rate.payMode()
            );
        };
    }

    private static int requireProductionNorm(HistoricalBaseRate rate) {
        Integer norm = rate.productionNormMinutes();
        if (norm == null || norm <= 0) {
            throw new IllegalStateException(
                    "Salary Article 153 norm authority requires positive production norm"
            );
        }
        return norm;
    }

    private static NormPositionPiece resultPiece(
            QualifiedPiece piece,
            HistoricalBaseRate rate,
            NormPosition position,
            Integer productionNorm,
            long workedBeforeDate,
            int workedOnDate
    ) {
        SourcePiece source = piece.sourcePiece();
        if (!source.deepIdentityComplete()) {
            throw new IllegalStateException(
                    "P1B3A qualified piece lost deep source identity"
            );
        }

        String fingerprint = sha256(
                "ARTICLE153_MONTHLY_NORM_POSITION_V1|"
                        + piece.payrollDate() + '|'
                        + piece.cause() + '|'
                        + sourceIdentityKey(source) + '|'
                        + rate.compensationEffectiveFrom() + '|'
                        + rate.payMode() + '|'
                        + (productionNorm == null ? "-" : productionNorm) + '|'
                        + workedBeforeDate + '|'
                        + workedOnDate + '|'
                        + position
        );

        return new NormPositionPiece(
                piece,
                payMode(rate),
                position,
                rate.compensationEffectiveFrom(),
                productionNorm,
                workedBeforeDate,
                workedOnDate,
                fingerprint
        );
    }

    private static String sourceIdentityKey(SourcePiece source) {
        return source.sourceDate()
                + "|" + source.sourceKind()
                + "|" + Objects.toString(source.sourceActualWorkIntervalId(), "-")
                + "|" + Objects.toString(source.sourceDayEntryId(), "-")
                + "|" + source.sourceEvidenceStartInstant()
                + "|" + source.sourceEvidenceEndInstant()
                + "|" + source.sourceEvidenceTimezone()
                + "|" + source.minutes();
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

    public enum BlockerKind {
        QUALIFIED_AUTHORITY,
        COMPENSATION,
        PAYROLL_SOURCE,
        MONTHLY_NORM_BOUNDARY
    }

    public record BlockingFact(
            LocalDate date,
            BlockerKind kind,
            String reason
    ) {
        public BlockingFact {
            Objects.requireNonNull(date, "Article 153 norm blocker date is required");
            Objects.requireNonNull(kind, "Article 153 norm blocker kind is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Article 153 norm blocker reason is required"
                );
            }
        }
    }

    public record NormPositionPiece(
            QualifiedPiece qualifiedPiece,
            PayMode payMode,
            NormPosition normPosition,
            LocalDate compensationEffectiveFrom,
            Integer productionNormMinutes,
            long workedMinutesBeforeDate,
            int workedMinutesOnDate,
            String decisionFingerprint
    ) {
        public NormPositionPiece {
            Objects.requireNonNull(qualifiedPiece, "Qualified Article 153 piece is required");
            Objects.requireNonNull(payMode, "Article 153 norm pay mode is required");
            Objects.requireNonNull(normPosition, "Article 153 norm position is required");
            Objects.requireNonNull(
                    compensationEffectiveFrom,
                    "Article 153 compensation source identity is required"
            );
            if (workedMinutesBeforeDate < 0 || workedMinutesOnDate < 0) {
                throw new IllegalArgumentException(
                        "Article 153 norm worked-minute evidence cannot be negative"
                );
            }
            if (decisionFingerprint == null
                    || !decisionFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Article 153 norm decision fingerprint is invalid"
                );
            }

            if (payMode == PayMode.HOURLY) {
                if (normPosition != NormPosition.NOT_APPLICABLE
                        || productionNormMinutes != null
                        || workedMinutesBeforeDate != 0
                        || workedMinutesOnDate != 0) {
                    throw new IllegalArgumentException(
                            "Hourly Article 153 norm result must remain not-applicable"
                    );
                }
            } else {
                if (normPosition == NormPosition.NOT_APPLICABLE
                        || productionNormMinutes == null
                        || productionNormMinutes <= 0
                        || workedMinutesOnDate <= 0) {
                    throw new IllegalArgumentException(
                            "Salary Article 153 norm result requires complete monthly-norm evidence"
                    );
                }
            }
        }

        public int minutes() {
            return qualifiedPiece.minutes();
        }
    }

    public record Resolution(
            YearMonth payrollMonth,
            boolean ready,
            PayrollQualifiedQuantity quantity,
            List<NormPositionPiece> pieces,
            List<BlockingFact> blockers
    ) {
        public Resolution {
            Objects.requireNonNull(payrollMonth, "Article 153 norm result month is required");
            pieces = List.copyOf(Objects.requireNonNull(pieces, "Article 153 norm pieces are required"));
            blockers = List.copyOf(Objects.requireNonNull(blockers, "Article 153 norm blockers are required"));

            if (ready) {
                Objects.requireNonNull(quantity, "Ready Article 153 norm result requires quantity");
                if (!blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ready Article 153 norm result cannot contain blockers"
                    );
                }
                long minutes = pieces.stream().mapToLong(NormPositionPiece::minutes).sum();
                if (minutes != quantity.value()) {
                    throw new IllegalArgumentException(
                            "Article 153 norm pieces must preserve P1B3A qualified quantity"
                    );
                }
            } else if (quantity != null || !pieces.isEmpty() || blockers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked Article 153 norm result requires blockers and no partial result"
                );
            }
        }

        static Resolution ready(
                YearMonth month,
                PayrollQualifiedQuantity quantity,
                List<NormPositionPiece> pieces
        ) {
            return new Resolution(month, true, quantity, pieces, List.of());
        }

        static Resolution blocked(
                YearMonth month,
                List<BlockingFact> blockers
        ) {
            return new Resolution(month, false, null, List.of(), blockers);
        }
    }
}

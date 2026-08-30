package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Paragraph-8 established tariff / official-salary FACT authority.
 *
 * <p>Government Resolution No. 540 paragraph 8 uses the tariff rate or
 * salary (official salary) established for the employee when paragraph-8
 * fallback is later proven applicable. DutyLog stores that identity as an
 * effective-month {@link CompensationTerm}. This service exposes that exact
 * configured historical identity for the event month and deliberately stops
 * before any average-earnings formula.</p>
 *
 * <p>HOURLY therefore exposes the configured hourly tariff rate itself.
 * SALARY exposes the configured monthly official salary itself. In
 * particular, SALARY is never converted to an hourly value through a
 * production-calendar norm here. That derived rate belongs to ordinary
 * Payroll pricing and is not paragraph-8 authority.</p>
 *
 * <p>This layer also never decides that paragraph 8 applies. Paragraph-6 and
 * paragraph-7 exhaustion remain separate policy/fallback evidence consumed by
 * a later resolver.</p>
 */
@Service
public class AverageEarningsParagraph8TariffSalaryAuthorityService {

    public static final String RULE_ID = "PP_540_P8";
    public static final String COMPENSATION_TERM_MISSING =
            "PP_540_P8_COMPENSATION_TERM_MISSING";
    public static final String COMPENSATION_TERM_INVALID =
            "PP_540_P8_COMPENSATION_TERM_INVALID";
    public static final String CURRENCY_INVALID =
            "PP_540_P8_CURRENCY_INVALID";

    private final CompensationTermRepository compensationTerms;

    public AverageEarningsParagraph8TariffSalaryAuthorityService(
            CompensationTermRepository compensationTerms
    ) {
        this.compensationTerms = Objects.requireNonNull(
                compensationTerms,
                "Paragraph-8 authority requires compensation-term history"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-8 authority requires user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-8 authority requires event date"
        );

        AverageEarningsLegalPolicy.LegalRegime legalRegime =
                AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        LocalDate compensationBoundary = eventMonth.atDay(1);

        CompensationTerm term = compensationTerms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        compensationBoundary
                )
                .orElse(null);

        if (term == null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    COMPENSATION_TERM_MISSING,
                    "No established tariff rate or official salary for paragraph-8 event month"
            );
        }

        LocalDate effectiveFrom = term.getEffectiveFrom();
        if (effectiveFrom == null
                || effectiveFrom.getDayOfMonth() != 1
                || effectiveFrom.isAfter(compensationBoundary)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    COMPENSATION_TERM_INVALID,
                    "Paragraph-8 compensation-term effective month is invalid"
            );
        }

        String currency = term.getCurrencyCode();
        if (currency == null
                || !currency.matches("[A-Z]{3}")) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    CURRENCY_INVALID,
                    "Paragraph-8 compensation term has invalid currency"
            );
        }

        String payMode = term.getPayMode();
        if ("HOURLY".equals(payMode)) {
            Long hourlyTariff = term.getHourlyRateMinor();
            if (hourlyTariff == null
                    || hourlyTariff <= 0L
                    || term.getMonthlySalaryMinor() != null) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        compensationBoundary,
                        legalRegime,
                        COMPENSATION_TERM_INVALID,
                        "Paragraph-8 hourly term must contain only a positive configured tariff rate"
                );
            }

            return Resolution.ready(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    effectiveFrom,
                    EstablishedBasis.HOURLY_TARIFF_RATE,
                    payMode,
                    currency,
                    hourlyTariff,
                    null
            );
        }

        if ("SALARY".equals(payMode)) {
            Long monthlyOfficialSalary = term.getMonthlySalaryMinor();
            if (monthlyOfficialSalary == null
                    || monthlyOfficialSalary <= 0L
                    || term.getHourlyRateMinor() != null) {
                return Resolution.blocked(
                        eventDate,
                        eventMonth,
                        compensationBoundary,
                        legalRegime,
                        COMPENSATION_TERM_INVALID,
                        "Paragraph-8 salary term must contain only a positive configured monthly official salary"
                );
            }

            return Resolution.ready(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    effectiveFrom,
                    EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                    payMode,
                    currency,
                    null,
                    monthlyOfficialSalary
            );
        }

        return Resolution.blocked(
                eventDate,
                eventMonth,
                compensationBoundary,
                legalRegime,
                COMPENSATION_TERM_INVALID,
                "Paragraph-8 compensation term has unsupported pay mode"
        );
    }

    public enum EstablishedBasis {
        HOURLY_TARIFF_RATE,
        MONTHLY_OFFICIAL_SALARY
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            LocalDate compensationBoundary,
            AverageEarningsLegalPolicy.LegalRegime legalRegime,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            LocalDate compensationEffectiveFrom,
            EstablishedBasis establishedBasis,
            String payMode,
            String currencyCode,
            Long hourlyTariffRateMinor,
            Long monthlyOfficialSalaryMinor
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-8 event date is required"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Paragraph-8 event month is required"
            );
            Objects.requireNonNull(
                    compensationBoundary,
                    "Paragraph-8 compensation boundary is required"
            );
            Objects.requireNonNull(
                    legalRegime,
                    "Paragraph-8 legal regime is required"
            );

            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !compensationBoundary.equals(eventMonth.atDay(1))) {
                throw new IllegalArgumentException(
                        "Paragraph-8 authority event-month identity is invalid"
                );
            }

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || compensationEffectiveFrom == null
                        || compensationEffectiveFrom.getDayOfMonth() != 1
                        || compensationEffectiveFrom.isAfter(compensationBoundary)
                        || establishedBasis == null
                        || payMode == null
                        || currencyCode == null
                        || !currencyCode.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-8 authority is incomplete"
                    );
                }

                if (establishedBasis == EstablishedBasis.HOURLY_TARIFF_RATE) {
                    if (!"HOURLY".equals(payMode)
                            || hourlyTariffRateMinor == null
                            || hourlyTariffRateMinor <= 0L
                            || monthlyOfficialSalaryMinor != null) {
                        throw new IllegalArgumentException(
                                "Ready paragraph-8 hourly authority has invalid tariff identity"
                        );
                    }
                } else if (establishedBasis == EstablishedBasis.MONTHLY_OFFICIAL_SALARY) {
                    if (!"SALARY".equals(payMode)
                            || hourlyTariffRateMinor != null
                            || monthlyOfficialSalaryMinor == null
                            || monthlyOfficialSalaryMinor <= 0L) {
                        throw new IllegalArgumentException(
                                "Ready paragraph-8 salary authority has invalid official-salary identity"
                        );
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Ready paragraph-8 authority has unsupported established basis"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || compensationEffectiveFrom != null
                        || establishedBasis != null
                        || payMode != null
                        || currencyCode != null
                        || hourlyTariffRateMinor != null
                        || monthlyOfficialSalaryMinor != null) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-8 authority cannot expose partial compensation identity"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                LocalDate compensationBoundary,
                AverageEarningsLegalPolicy.LegalRegime legalRegime,
                LocalDate compensationEffectiveFrom,
                EstablishedBasis establishedBasis,
                String payMode,
                String currencyCode,
                Long hourlyTariffRateMinor,
                Long monthlyOfficialSalaryMinor
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    true,
                    null,
                    null,
                    compensationEffectiveFrom,
                    establishedBasis,
                    payMode,
                    currencyCode,
                    hourlyTariffRateMinor,
                    monthlyOfficialSalaryMinor
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                LocalDate compensationBoundary,
                AverageEarningsLegalPolicy.LegalRegime legalRegime,
                String blockingReason,
                String blockingMessage
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    compensationBoundary,
                    legalRegime,
                    false,
                    blockingReason,
                    blockingMessage,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public Long establishedAmountMinor() {
            if (!ready) {
                return null;
            }

            return establishedBasis == EstablishedBasis.HOURLY_TARIFF_RATE
                    ? hourlyTariffRateMinor
                    : monthlyOfficialSalaryMinor;
        }
    }
}

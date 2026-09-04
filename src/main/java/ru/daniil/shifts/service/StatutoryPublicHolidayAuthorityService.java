package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Employee-context statutory public-holiday authority.
 *
 * <p>Federal Article-112 holidays can be proven positively for every supported
 * RU region. A non-federal date is not asserted to be non-holiday until
 * regional statutory holiday authority is available.</p>
 *
 * <p>This service does not classify employee rest days and is not a payroll
 * pricing authority.</p>
 */
@Service
public class StatutoryPublicHolidayAuthorityService {
    public static final String JURISDICTION_UNSUPPORTED =
            "STATUTORY_HOLIDAY_JURISDICTION_UNSUPPORTED";

    public static final String LEGAL_WINDOW_UNSUPPORTED =
            "STATUTORY_HOLIDAY_LEGAL_WINDOW_UNSUPPORTED";

    public static final String REGIONAL_AUTHORITY_MISSING =
            "STATUTORY_HOLIDAY_REGIONAL_AUTHORITY_MISSING";

    public static final String REGIONAL_POLICY_UNIMPLEMENTED =
            "STATUTORY_HOLIDAY_REGIONAL_POLICY_UNIMPLEMENTED";

    private final WorkJurisdictionHistoryService jurisdiction;

    public StatutoryPublicHolidayAuthorityService(
            WorkJurisdictionHistoryService jurisdiction
    ) {
        this.jurisdiction = Objects.requireNonNull(
                jurisdiction,
                "Statutory public-holiday authority requires jurisdiction authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate date
    ) {
        Objects.requireNonNull(
                user,
                "Statutory public-holiday authority requires user"
        );
        Objects.requireNonNull(
                date,
                "Statutory public-holiday authority requires date"
        );

        WorkJurisdictionHistoryService.Resolution jurisdictionResult =
                jurisdiction.resolveAt(
                        user,
                        date
                );

        if (!jurisdictionResult.ready()) {
            return Resolution.unresolved(
                    date,
                    jurisdictionResult.blockingReason()
            );
        }

        WorkJurisdictionHistoryService.JurisdictionFact jurisdictionFact =
                jurisdictionResult.fact();

        if (!WorkJurisdictionHistoryService.RU.equals(
                jurisdictionFact.jurisdictionCode()
        )) {
            return Resolution.unresolved(
                    date,
                    JURISDICTION_UNSUPPORTED
                            + ":"
                            + jurisdictionFact.jurisdictionCode()
            );
        }

        RuFederalStatutoryHolidayPolicy.Decision federal;

        try {
            federal =
                    RuFederalStatutoryHolidayPolicy.classify(
                            date
                    );
        } catch (UnsupportedOperationException ex) {
            return Resolution.unresolved(
                    date,
                    LEGAL_WINDOW_UNSUPPORTED
                            + ":"
                            + date
            );
        }

        if (federal.federalNonWorkingPublicHoliday()) {
            return Resolution.federalHoliday(
                    date,
                    jurisdictionFact,
                    federal
            );
        }

        String regionCode =
                jurisdictionFact.regionCode();

        if (regionCode == null) {
            return Resolution.unresolved(
                    date,
                    REGIONAL_AUTHORITY_MISSING
                            + ":"
                            + date
            );
        }

        return Resolution.unresolved(
                date,
                REGIONAL_POLICY_UNIMPLEMENTED
                        + ":"
                        + regionCode
                        + ":"
                        + date
        );
    }

    public enum Status {
        NON_WORKING_PUBLIC_HOLIDAY,
        UNRESOLVED
    }

    public record Provenance(
            long jurisdictionTermId,
            String jurisdictionCode,
            String regionCode,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            RuFederalStatutoryHolidayPolicy.HolidayCode holidayCode
    ) {
        public Provenance {
            if (jurisdictionTermId <= 0L) {
                throw new IllegalArgumentException(
                        "Statutory holiday jurisdiction term id must be positive"
                );
            }
            requireText(
                    jurisdictionCode,
                    "Statutory holiday jurisdiction is required"
            );
            requireText(
                    legalRegime,
                    "Statutory holiday legal regime is required"
            );
            requireText(
                    legalBasis,
                    "Statutory holiday legal basis is required"
            );
            requireText(
                    sourceRevision,
                    "Statutory holiday source revision is required"
            );
            requireText(
                    sourceReference,
                    "Statutory holiday source reference is required"
            );
            Objects.requireNonNull(
                    holidayCode,
                    "Statutory federal holiday identity is required"
            );
        }
    }

    public record Resolution(
            LocalDate date,
            Status status,
            String blockingReason,
            Provenance provenance
    ) {
        public Resolution {
            Objects.requireNonNull(
                    date,
                    "Statutory holiday resolution date is required"
            );
            Objects.requireNonNull(
                    status,
                    "Statutory holiday resolution status is required"
            );

            if (status == Status.UNRESOLVED) {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || provenance != null) {
                    throw new IllegalArgumentException(
                            "Unresolved statutory holiday requires blocker and no provenance"
                    );
                }
            } else if (blockingReason != null
                    || provenance == null) {
                throw new IllegalArgumentException(
                        "Resolved statutory holiday requires provenance and no blocker"
                );
            }
        }

        public static Resolution unresolved(
                LocalDate date,
                String blocker
        ) {
            return new Resolution(
                    date,
                    Status.UNRESOLVED,
                    blocker,
                    null
            );
        }

        public static Resolution federalHoliday(
                LocalDate date,
                WorkJurisdictionHistoryService.JurisdictionFact jurisdiction,
                RuFederalStatutoryHolidayPolicy.Decision federal
        ) {
            Objects.requireNonNull(
                    jurisdiction,
                    "Federal holiday provenance requires jurisdiction"
            );
            Objects.requireNonNull(
                    federal,
                    "Federal holiday provenance requires legal decision"
            );

            if (!federal.federalNonWorkingPublicHoliday()
                    || federal.holidayCode() == null) {
                throw new IllegalArgumentException(
                        "Federal holiday resolution requires positive federal legal fact"
                );
            }

            return new Resolution(
                    date,
                    Status.NON_WORKING_PUBLIC_HOLIDAY,
                    null,
                    new Provenance(
                            jurisdiction.termId(),
                            jurisdiction.jurisdictionCode(),
                            jurisdiction.regionCode(),
                            federal.legalRegime(),
                            federal.legalBasis(),
                            federal.sourceRevision(),
                            federal.sourceReference(),
                            federal.holidayCode()
                    )
            );
        }

        public boolean ready() {
            return status != Status.UNRESOLVED;
        }

        public boolean nonWorkingPublicHoliday() {
            return status == Status.NON_WORKING_PUBLIC_HOLIDAY;
        }
    }

    private static void requireText(
            String value,
            String message
    ) {
        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}

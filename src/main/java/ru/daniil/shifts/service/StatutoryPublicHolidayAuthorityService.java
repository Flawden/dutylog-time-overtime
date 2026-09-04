package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class StatutoryPublicHolidayAuthorityService {
    public static final String JURISDICTION_UNSUPPORTED = "STATUTORY_HOLIDAY_JURISDICTION_UNSUPPORTED";
    public static final String LEGAL_WINDOW_UNSUPPORTED = "STATUTORY_HOLIDAY_LEGAL_WINDOW_UNSUPPORTED";
    public static final String REGIONAL_AUTHORITY_MISSING = "STATUTORY_HOLIDAY_REGIONAL_AUTHORITY_MISSING";

    private final WorkJurisdictionHistoryService jurisdiction;
    private final RegionalStatutoryHolidayDatasetService regional;

    public StatutoryPublicHolidayAuthorityService(
            WorkJurisdictionHistoryService jurisdiction,
            RegionalStatutoryHolidayDatasetService regional
    ) {
        this.jurisdiction = Objects.requireNonNull(jurisdiction, "Statutory public-holiday authority requires jurisdiction authority");
        this.regional = Objects.requireNonNull(regional, "Statutory public-holiday authority requires regional dataset authority");
    }

    @Transactional(readOnly = true)
    public Resolution resolve(AppUser user, LocalDate date) {
        Objects.requireNonNull(user, "Statutory public-holiday authority requires user");
        Objects.requireNonNull(date, "Statutory public-holiday authority requires date");

        WorkJurisdictionHistoryService.Resolution jurisdictionResult = jurisdiction.resolveAt(user, date);
        if (!jurisdictionResult.ready()) return Resolution.unresolved(date, jurisdictionResult.blockingReason());

        WorkJurisdictionHistoryService.JurisdictionFact jurisdictionFact = jurisdictionResult.fact();
        if (!WorkJurisdictionHistoryService.RU.equals(jurisdictionFact.jurisdictionCode())) {
            return Resolution.unresolved(date, JURISDICTION_UNSUPPORTED + ":" + jurisdictionFact.jurisdictionCode());
        }

        RuFederalStatutoryHolidayPolicy.Decision federal;
        try {
            federal = RuFederalStatutoryHolidayPolicy.classify(date);
        } catch (UnsupportedOperationException ex) {
            return Resolution.unresolved(date, LEGAL_WINDOW_UNSUPPORTED + ":" + date);
        }

        if (federal.federalNonWorkingPublicHoliday()) {
            return Resolution.federalHoliday(date, jurisdictionFact, federal);
        }

        String regionCode = jurisdictionFact.regionCode();
        if (regionCode == null) return Resolution.unresolved(date, REGIONAL_AUTHORITY_MISSING + ":" + date);

        RegionalStatutoryHolidayDatasetService.Decision regionalDecision =
                regional.resolve(jurisdictionFact.jurisdictionCode(), regionCode, date);
        if (!regionalDecision.ready()) return Resolution.unresolved(date, regionalDecision.blockingReason());
        if (regionalDecision.nonWorkingPublicHoliday()) return Resolution.regionalHoliday(date, jurisdictionFact, regionalDecision);
        if (regionalDecision.provenNotPublicHoliday()) return Resolution.regionalNotHoliday(date, jurisdictionFact, regionalDecision);
        throw new IllegalStateException("Ready regional statutory-holiday decision has unsupported status");
    }

    public enum Status { NON_WORKING_PUBLIC_HOLIDAY, NOT_NON_WORKING_PUBLIC_HOLIDAY, UNRESOLVED }
    public enum AuthorityKind { FEDERAL_ARTICLE_112, REGIONAL_DATASET }

    public record Provenance(
            long jurisdictionTermId,
            String jurisdictionCode,
            String regionCode,
            AuthorityKind authorityKind,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            String holidayCode,
            Long regionalDatasetId,
            String regionalDatasetFingerprint,
            Boolean regionalDatasetComplete,
            Long regionalDateFactId
    ) {
        public Provenance {
            if (jurisdictionTermId <= 0L) throw new IllegalArgumentException("Statutory holiday jurisdiction term id must be positive");
            requireText(jurisdictionCode, "Statutory holiday jurisdiction is required");
            Objects.requireNonNull(authorityKind, "Statutory holiday authority kind is required");
            requireText(legalRegime, "Statutory holiday legal regime is required");
            requireText(legalBasis, "Statutory holiday legal basis is required");
            requireText(sourceRevision, "Statutory holiday source revision is required");
            requireText(sourceReference, "Statutory holiday source reference is required");
            if (authorityKind == AuthorityKind.FEDERAL_ARTICLE_112) {
                requireText(holidayCode, "Federal statutory holiday identity is required");
                if (regionalDatasetId != null || regionalDatasetFingerprint != null || regionalDatasetComplete != null || regionalDateFactId != null) {
                    throw new IllegalArgumentException("Federal statutory holiday must not claim regional dataset identity");
                }
            } else {
                requireText(regionCode, "Regional statutory holiday provenance requires region");
                if (regionalDatasetId == null || regionalDatasetId <= 0L || regionalDatasetFingerprint == null || !regionalDatasetFingerprint.matches("[0-9a-f]{64}") || regionalDatasetComplete == null) {
                    throw new IllegalArgumentException("Regional statutory holiday provenance requires immutable dataset identity");
                }
            }
        }
    }

    public record Resolution(LocalDate date, Status status, String blockingReason, Provenance provenance) {
        public Resolution {
            Objects.requireNonNull(date, "Statutory holiday resolution date is required");
            Objects.requireNonNull(status, "Statutory holiday resolution status is required");
            if (status == Status.UNRESOLVED) {
                if (blockingReason == null || blockingReason.isBlank() || provenance != null) throw new IllegalArgumentException("Unresolved statutory holiday requires blocker and no provenance");
            } else if (blockingReason != null || provenance == null) {
                throw new IllegalArgumentException("Resolved statutory holiday requires provenance and no blocker");
            }
            if (status == Status.NON_WORKING_PUBLIC_HOLIDAY && (provenance == null || provenance.holidayCode() == null || provenance.holidayCode().isBlank())) {
                throw new IllegalArgumentException("Positive statutory holiday requires holiday identity");
            }
            if (status == Status.NOT_NON_WORKING_PUBLIC_HOLIDAY && (provenance == null || provenance.authorityKind() != AuthorityKind.REGIONAL_DATASET || !Boolean.TRUE.equals(provenance.regionalDatasetComplete()) || provenance.holidayCode() != null || provenance.regionalDateFactId() != null)) {
                throw new IllegalArgumentException("Negative statutory holiday requires complete regional dataset provenance");
            }
        }

        public static Resolution unresolved(LocalDate date, String blocker) { return new Resolution(date, Status.UNRESOLVED, blocker, null); }

        public static Resolution federalHoliday(
                LocalDate date,
                WorkJurisdictionHistoryService.JurisdictionFact jurisdiction,
                RuFederalStatutoryHolidayPolicy.Decision federal
        ) {
            Objects.requireNonNull(jurisdiction, "Federal holiday provenance requires jurisdiction");
            Objects.requireNonNull(federal, "Federal holiday provenance requires legal decision");
            if (!federal.federalNonWorkingPublicHoliday() || federal.holidayCode() == null) {
                throw new IllegalArgumentException("Federal holiday resolution requires positive federal legal fact");
            }
            return new Resolution(date, Status.NON_WORKING_PUBLIC_HOLIDAY, null,
                    new Provenance(jurisdiction.termId(), jurisdiction.jurisdictionCode(), jurisdiction.regionCode(),
                            AuthorityKind.FEDERAL_ARTICLE_112, federal.legalRegime(), federal.legalBasis(), federal.sourceRevision(),
                            federal.sourceReference(), federal.holidayCode().name(), null, null, null, null));
        }

        public static Resolution regionalHoliday(
                LocalDate date,
                WorkJurisdictionHistoryService.JurisdictionFact jurisdiction,
                RegionalStatutoryHolidayDatasetService.Decision regional
        ) {
            requireRegionalDecision(regional);
            if (!regional.nonWorkingPublicHoliday() || regional.holiday() == null) {
                throw new IllegalArgumentException("Regional holiday resolution requires positive regional fact");
            }
            var dataset = regional.provenance();
            var holiday = regional.holiday();
            return new Resolution(date, Status.NON_WORKING_PUBLIC_HOLIDAY, null,
                    new Provenance(jurisdiction.termId(), jurisdiction.jurisdictionCode(), jurisdiction.regionCode(),
                            AuthorityKind.REGIONAL_DATASET, dataset.legalRegime(), holiday.legalBasis(), dataset.sourceRevision(),
                            holiday.sourceReference(), holiday.holidayCode(), dataset.datasetId(), dataset.fingerprint(), dataset.complete(), holiday.factId()));
        }

        public static Resolution regionalNotHoliday(
                LocalDate date,
                WorkJurisdictionHistoryService.JurisdictionFact jurisdiction,
                RegionalStatutoryHolidayDatasetService.Decision regional
        ) {
            requireRegionalDecision(regional);
            if (!regional.provenNotPublicHoliday() || regional.holiday() != null || !regional.provenance().complete()) {
                throw new IllegalArgumentException("Regional negative resolution requires complete negative authority");
            }
            var dataset = regional.provenance();
            return new Resolution(date, Status.NOT_NON_WORKING_PUBLIC_HOLIDAY, null,
                    new Provenance(jurisdiction.termId(), jurisdiction.jurisdictionCode(), jurisdiction.regionCode(),
                            AuthorityKind.REGIONAL_DATASET, dataset.legalRegime(), dataset.legalBasis(), dataset.sourceRevision(),
                            dataset.sourceReference(), null, dataset.datasetId(), dataset.fingerprint(), dataset.complete(), null));
        }

        private static void requireRegionalDecision(RegionalStatutoryHolidayDatasetService.Decision regional) {
            Objects.requireNonNull(regional, "Regional statutory holiday resolution requires regional decision");
            if (!regional.ready() || regional.provenance() == null) throw new IllegalArgumentException("Regional statutory holiday resolution requires ready dataset provenance");
        }

        public boolean ready() { return status != Status.UNRESOLVED; }
        public boolean nonWorkingPublicHoliday() { return status == Status.NON_WORKING_PUBLIC_HOLIDAY; }
        public boolean provenNotPublicHoliday() { return status == Status.NOT_NON_WORKING_PUBLIC_HOLIDAY; }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}

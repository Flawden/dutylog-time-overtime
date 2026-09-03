package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkJurisdictionTerm;
import ru.daniil.shifts.repo.WorkJurisdictionTermRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical effective-dated work-jurisdiction authority.
 *
 * <p>This service owns jurisdiction FACT only. It does not classify statutory
 * holidays, calculate payroll money, classify rest days, or choose legal
 * formulas.</p>
 *
 * <p>Timezone, language, locale, production-calendar rows and employment dates
 * are explicitly forbidden inference sources. Missing persisted authority
 * remains unresolved.</p>
 */
@Service
public class WorkJurisdictionHistoryService {
    public static final String JURISDICTION_FACT_MISSING =
            "WORK_JURISDICTION_FACT_MISSING";

    public static final String JURISDICTION_UNSUPPORTED =
            "WORK_JURISDICTION_UNSUPPORTED";

    public static final String REGION_INVALID =
            "WORK_JURISDICTION_REGION_INVALID";

    public static final String RU =
            "RU";

    private final WorkJurisdictionTermRepository terms;

    public WorkJurisdictionHistoryService(
            WorkJurisdictionTermRepository terms
    ) {
        this.terms = Objects.requireNonNull(
                terms,
                "Work jurisdiction repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolveAt(
            AppUser user,
            LocalDate date
    ) {
        requireUserAndDate(user, date);

        WorkJurisdictionTerm term =
                terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                date
                        )
                        .orElse(null);

        if (term == null) {
            return Resolution.blocked(
                    date,
                    JURISDICTION_FACT_MISSING + ":" + date
            );
        }

        Validation validated = validatedPersisted(term);

        if (!validated.ready()) {
            return Resolution.blocked(
                    date,
                    validated.blockingReason()
            );
        }

        return Resolution.ready(
                date,
                fact(term, validated)
        );
    }

    @Transactional(readOnly = true)
    public List<JurisdictionFact> history(
            AppUser user
    ) {
        Objects.requireNonNull(
                user,
                "Work jurisdiction authority requires user"
        );

        List<WorkJurisdictionTerm> history =
                terms.findByOwnerOrderByEffectiveFromAscIdAsc(user);

        if (history == null) {
            throw new IllegalStateException(
                    "Work jurisdiction repository returned null history"
            );
        }

        return history.stream()
                .map(term -> {
                    Validation validated = validatedPersisted(term);

                    if (!validated.ready()) {
                        throw new IllegalStateException(
                                "Persisted work jurisdiction history is invalid: "
                                        + validated.blockingReason()
                        );
                    }

                    return fact(term, validated);
                })
                .toList();
    }

    @Transactional
    public JurisdictionFact upsert(
            AppUser user,
            LocalDate effectiveFrom,
            String jurisdictionCode,
            String regionCode
    ) {
        requireUserAndDate(user, effectiveFrom);

        Validation requested =
                validateCodes(
                        jurisdictionCode,
                        regionCode
                );

        if (!requested.ready()) {
            throw new IllegalArgumentException(
                    requested.blockingReason()
            );
        }

        WorkJurisdictionTerm term =
                terms.findByOwnerAndEffectiveFrom(
                                user,
                                effectiveFrom
                        )
                        .orElseGet(() ->
                                new WorkJurisdictionTerm(
                                        user,
                                        effectiveFrom,
                                        requested.jurisdictionCode(),
                                        requested.regionCode()
                                )
                        );

        term.setJurisdiction(
                requested.jurisdictionCode(),
                requested.regionCode()
        );

        WorkJurisdictionTerm saved =
                terms.saveAndFlush(term);

        Validation persisted =
                validatedPersisted(saved);

        if (!persisted.ready()) {
            throw new IllegalStateException(
                    "Persisted work jurisdiction became invalid: "
                            + persisted.blockingReason()
            );
        }

        return fact(saved, persisted);
    }

    @Transactional
    public void delete(
            AppUser user,
            LocalDate effectiveFrom
    ) {
        requireUserAndDate(user, effectiveFrom);

        terms.findByOwnerAndEffectiveFrom(
                        user,
                        effectiveFrom
                )
                .ifPresent(terms::delete);

        terms.flush();
    }

    private JurisdictionFact fact(
            WorkJurisdictionTerm term,
            Validation validated
    ) {
        if (term == null
                || term.getId() == null
                || term.getId() <= 0L
                || term.getEffectiveFrom() == null) {
            throw new IllegalStateException(
                    "Persisted work jurisdiction term lacks immutable identity"
            );
        }

        return new JurisdictionFact(
                term.getId(),
                term.getEffectiveFrom(),
                validated.jurisdictionCode(),
                validated.regionCode()
        );
    }

    private Validation validatedPersisted(
            WorkJurisdictionTerm term
    ) {
        if (term == null
                || term.getEffectiveFrom() == null) {
            return Validation.blocked(
                    JURISDICTION_FACT_MISSING
            );
        }

        return validateCodes(
                term.getJurisdictionCode(),
                term.getRegionCode()
        );
    }

    private Validation validateCodes(
            String jurisdictionCode,
            String regionCode
    ) {
        String jurisdiction =
                normalize(jurisdictionCode);

        if (!RU.equals(jurisdiction)) {
            return Validation.blocked(
                    JURISDICTION_UNSUPPORTED
                            + ":"
                            + (
                            jurisdiction == null
                                    ? "NULL"
                                    : jurisdiction
                    )
            );
        }

        String region =
                normalize(regionCode);

        if (region != null
                && (
                region.length() < 4
                        || region.length() > 32
                        || !region.startsWith(
                        jurisdiction + "-"
                )
                        || !region.matches(
                        "[A-Z0-9-]+"
                )
        )) {
            return Validation.blocked(
                    REGION_INVALID + ":" + region
            );
        }

        return Validation.ready(
                jurisdiction,
                region
        );
    }

    private String normalize(
            String raw
    ) {
        if (raw == null
                || raw.isBlank()) {
            return null;
        }

        return raw.trim()
                .toUpperCase(Locale.ROOT);
    }

    private void requireUserAndDate(
            AppUser user,
            LocalDate date
    ) {
        Objects.requireNonNull(
                user,
                "Work jurisdiction authority requires user"
        );
        Objects.requireNonNull(
                date,
                "Work jurisdiction authority requires date"
        );
    }

    public record JurisdictionFact(
            long termId,
            LocalDate effectiveFrom,
            String jurisdictionCode,
            String regionCode
    ) {
        public JurisdictionFact {
            if (termId <= 0L) {
                throw new IllegalArgumentException(
                        "Work jurisdiction term identity must be positive"
                );
            }
            Objects.requireNonNull(
                    effectiveFrom,
                    "Work jurisdiction effective date is required"
            );
            if (jurisdictionCode == null
                    || jurisdictionCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Work jurisdiction code is required"
                );
            }
        }
    }

    public record Resolution(
            LocalDate date,
            boolean ready,
            String blockingReason,
            JurisdictionFact fact
    ) {
        public Resolution {
            Objects.requireNonNull(
                    date,
                    "Work jurisdiction resolution date is required"
            );

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Work jurisdiction resolution state is invalid"
                );
            }

            if (ready != (fact != null)) {
                throw new IllegalArgumentException(
                        "Work jurisdiction fact exposure is invalid"
                );
            }
        }

        public static Resolution ready(
                LocalDate date,
                JurisdictionFact fact
        ) {
            return new Resolution(
                    date,
                    true,
                    null,
                    Objects.requireNonNull(fact)
            );
        }

        public static Resolution blocked(
                LocalDate date,
                String reason
        ) {
            if (reason == null
                    || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Work jurisdiction blocker is required"
                );
            }

            return new Resolution(
                    date,
                    false,
                    reason,
                    null
            );
        }
    }

    private record Validation(
            boolean ready,
            String jurisdictionCode,
            String regionCode,
            String blockingReason
    ) {
        static Validation ready(
                String jurisdictionCode,
                String regionCode
        ) {
            return new Validation(
                    true,
                    jurisdictionCode,
                    regionCode,
                    null
            );
        }

        static Validation blocked(
                String reason
        ) {
            return new Validation(
                    false,
                    null,
                    null,
                    Objects.requireNonNull(reason)
            );
        }
    }
}

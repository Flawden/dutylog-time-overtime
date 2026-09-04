package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDataset;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDateFact;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDatasetRepository;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDateFactRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service
public class RegionalStatutoryHolidayDatasetService {
    public static final String SOURCE_PACK_SCHEMA_V1 =
            "DUTYLOG_REGIONAL_STATUTORY_HOLIDAY_SOURCE_PACK_V1";

    public static final String DATASET_MISSING =
            "REGIONAL_HOLIDAY_DATASET_MISSING";

    public static final String DATASET_AMBIGUOUS =
            "REGIONAL_HOLIDAY_DATASET_AMBIGUOUS";

    public static final String DATASET_INCOMPLETE =
            "REGIONAL_HOLIDAY_DATASET_INCOMPLETE";

    public static final String DATASET_INTEGRITY_FAILURE =
            "REGIONAL_HOLIDAY_DATASET_INTEGRITY_FAILURE";

    public static final String DATASET_SOURCE_PACK_PROVENANCE_MISSING =
            "REGIONAL_HOLIDAY_DATASET_SOURCE_PACK_PROVENANCE_MISSING";

    public static final String DATASET_SOURCE_PACK_CONFLICT =
            "REGIONAL_HOLIDAY_DATASET_SOURCE_PACK_CONFLICT";

    public static final String JURISDICTION_UNSUPPORTED =
            "REGIONAL_HOLIDAY_DATASET_JURISDICTION_UNSUPPORTED";

    private final RegionalStatutoryHolidayDatasetRepository datasets;
    private final RegionalStatutoryHolidayDateFactRepository facts;

    public RegionalStatutoryHolidayDatasetService(
            RegionalStatutoryHolidayDatasetRepository datasets,
            RegionalStatutoryHolidayDateFactRepository facts
    ) {
        this.datasets = Objects.requireNonNull(
                datasets,
                "Regional statutory holiday dataset repository is required"
        );
        this.facts = Objects.requireNonNull(
                facts,
                "Regional statutory holiday fact repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Decision resolve(
            String jurisdictionCode,
            String regionCode,
            LocalDate date
    ) {
        String jurisdiction = normalizeRequired(
                jurisdictionCode,
                "Regional holiday jurisdiction is required"
        );

        String region = normalizeRequired(
                regionCode,
                "Regional holiday region is required"
        );

        Objects.requireNonNull(
                date,
                "Regional holiday resolution date is required"
        );

        if (!WorkJurisdictionHistoryService.RU.equals(jurisdiction)) {
            return Decision.unresolved(
                    date,
                    JURISDICTION_UNSUPPORTED + ":" + jurisdiction
            );
        }

        List<RegionalStatutoryHolidayDataset> covering =
                datasets.findCovering(
                        jurisdiction,
                        region,
                        date
                );

        if (covering == null
                || covering.isEmpty()) {
            return Decision.unresolved(
                    date,
                    DATASET_MISSING
                            + ":"
                            + region
                            + ":"
                            + date
            );
        }

        if (covering.size() != 1) {
            return Decision.unresolved(
                    date,
                    DATASET_AMBIGUOUS
                            + ":"
                            + region
                            + ":"
                            + date
                            + ":COUNT="
                            + covering.size()
            );
        }

        RegionalStatutoryHolidayDataset dataset =
                covering.get(0);

        if (!sourcePackProvenanceValid(dataset)) {
            return Decision.unresolved(
                    date,
                    DATASET_SOURCE_PACK_PROVENANCE_MISSING
                            + ":"
                            + requiredDatasetId(dataset)
            );
        }

        List<RegionalStatutoryHolidayDateFact> datasetFacts =
                facts.findByDatasetOrderByHolidayDateAscIdAsc(
                        dataset
                );

        if (datasetFacts == null
                || !integrityMatches(
                        dataset,
                        datasetFacts
                )) {
            return Decision.unresolved(
                    date,
                    DATASET_INTEGRITY_FAILURE
                            + ":"
                            + requiredDatasetId(dataset)
            );
        }

        RegionalStatutoryHolidayDateFact positive =
                datasetFacts
                        .stream()
                        .filter(fact ->
                                date.equals(
                                        fact.getHolidayDate()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        DatasetProvenance provenance =
                provenance(
                        dataset
                );

        if (positive != null) {
            return Decision.holiday(
                    date,
                    provenance,
                    positive
            );
        }

        if (!dataset.isComplete()) {
            return Decision.unresolved(
                    date,
                    DATASET_INCOMPLETE
                            + ":"
                            + requiredDatasetId(dataset)
                            + ":"
                            + date
            );
        }

        return Decision.notHoliday(
                date,
                provenance
        );
    }

    /**
     * Package-private trusted persistence boundary.
     *
     * <p>P1B2C2 intentionally routes new installs through
     * {@link RegionalStatutoryHolidaySourcePackService} so exact source-pack
     * bytes are pinned before immutable facts are persisted.</p>
     */
    @Transactional
    InstalledDataset installTrusted(
            DatasetDraft draft,
            SourcePackProvenance sourcePack
    ) {
        DatasetDraft normalized =
                normalizeAndValidate(
                        Objects.requireNonNull(
                                draft,
                                "Regional holiday dataset draft is required"
                        )
                );

        SourcePackProvenance normalizedSourcePack =
                normalizeSourcePack(
                        sourcePack
                );

        String fingerprint =
                fingerprint(
                        normalized
                );

        RegionalStatutoryHolidayDataset existing =
                datasets
                        .findByFingerprint(
                                fingerprint
                        )
                        .orElse(null);

        if (existing != null) {
            List<RegionalStatutoryHolidayDateFact> existingFacts =
                    facts.findByDatasetOrderByHolidayDateAscIdAsc(
                            existing
                    );

            if (existingFacts == null
                    || !sourcePackProvenanceValid(existing)
                    || !integrityMatches(
                            existing,
                            existingFacts
                    )) {
                throw new IllegalStateException(
                        DATASET_INTEGRITY_FAILURE
                                + ":"
                                + requiredDatasetId(existing)
                );
            }

            if (!sameSourcePack(
                    existing,
                    normalizedSourcePack
            )) {
                throw new IllegalStateException(
                        DATASET_SOURCE_PACK_CONFLICT
                                + ":"
                                + requiredDatasetId(existing)
                );
            }

            return new InstalledDataset(
                    requiredDatasetId(existing),
                    existing.getFingerprint(),
                    existing.getSourcePackSha256(),
                    existingFacts.size(),
                    false
            );
        }

        RegionalStatutoryHolidayDataset saved =
                datasets.saveAndFlush(
                        new RegionalStatutoryHolidayDataset(
                                normalized.jurisdictionCode(),
                                normalized.regionCode(),
                                normalized.coverageFrom(),
                                normalized.coverageTo(),
                                normalized.legalRegime(),
                                normalized.legalBasis(),
                                normalized.sourceRevision(),
                                normalized.sourceReference(),
                                normalized.complete(),
                                fingerprint,
                                normalizedSourcePack.schema(),
                                normalizedSourcePack.sha256(),
                                normalizedSourcePack.completenessEvidence()
                        )
                );

        long datasetId =
                requiredDatasetId(
                        saved
                );

        List<RegionalStatutoryHolidayDateFact> rows =
                normalized
                        .holidays()
                        .stream()
                        .map(holiday ->
                                new RegionalStatutoryHolidayDateFact(
                                        saved,
                                        holiday.date(),
                                        holiday.holidayCode(),
                                        holiday.holidayLabel(),
                                        holiday.legalBasis(),
                                        holiday.sourceReference()
                                )
                        )
                        .toList();

        if (!rows.isEmpty()) {
            facts.saveAll(
                    rows
            );
        }

        facts.flush();

        return new InstalledDataset(
                datasetId,
                fingerprint,
                normalizedSourcePack.sha256(),
                rows.size(),
                true
        );
    }

    private boolean sourcePackProvenanceValid(
            RegionalStatutoryHolidayDataset dataset
    ) {
        if (dataset == null) {
            return false;
        }

        return SOURCE_PACK_SCHEMA_V1.equals(
                dataset.getSourcePackSchema()
        )
                && dataset.getSourcePackSha256() != null
                && dataset
                        .getSourcePackSha256()
                        .matches(
                                "[0-9a-f]{64}"
                        )
                && dataset.getCompletenessEvidence() != null
                && !dataset
                        .getCompletenessEvidence()
                        .isBlank();
    }

    private boolean sameSourcePack(
            RegionalStatutoryHolidayDataset dataset,
            SourcePackProvenance sourcePack
    ) {
        return Objects.equals(
                dataset.getSourcePackSchema(),
                sourcePack.schema()
        )
                && Objects.equals(
                dataset.getSourcePackSha256(),
                sourcePack.sha256()
        )
                && Objects.equals(
                dataset.getCompletenessEvidence(),
                sourcePack.completenessEvidence()
        );
    }

    private SourcePackProvenance normalizeSourcePack(
            SourcePackProvenance sourcePack
    ) {
        Objects.requireNonNull(
                sourcePack,
                "Regional holiday source pack provenance is required"
        );

        String schema =
                requireText(
                        sourcePack.schema(),
                        "Regional holiday source pack schema is required"
                );

        if (!SOURCE_PACK_SCHEMA_V1.equals(
                schema
        )) {
            throw new IllegalArgumentException(
                    "Regional holiday source pack schema is unsupported"
            );
        }

        String sha256 =
                requireText(
                        sourcePack.sha256(),
                        "Regional holiday source pack SHA-256 is required"
                );

        if (!sha256.matches(
                "[0-9a-f]{64}"
        )) {
            throw new IllegalArgumentException(
                    "Regional holiday source pack SHA-256 must be lowercase SHA-256"
            );
        }

        String completenessEvidence =
                requireText(
                        sourcePack.completenessEvidence(),
                        "Regional holiday completeness evidence is required"
                );

        return new SourcePackProvenance(
                schema,
                sha256,
                completenessEvidence
        );
    }

    private boolean integrityMatches(
            RegionalStatutoryHolidayDataset dataset,
            List<RegionalStatutoryHolidayDateFact> sourceFacts
    ) {
        try {
            long datasetId =
                    requiredDatasetId(
                            dataset
                    );

            List<HolidayDraft> holidays =
                    new ArrayList<>();

            for (RegionalStatutoryHolidayDateFact fact : sourceFacts) {
                if (fact == null
                        || fact.getDataset() == null
                        || requiredDatasetId(
                                fact.getDataset()
                        ) != datasetId) {
                    return false;
                }

                holidays.add(
                        new HolidayDraft(
                                fact.getHolidayDate(),
                                fact.getHolidayCode(),
                                fact.getHolidayLabel(),
                                fact.getLegalBasis(),
                                fact.getSourceReference()
                        )
                );
            }

            DatasetDraft persisted =
                    new DatasetDraft(
                            dataset.getJurisdictionCode(),
                            dataset.getRegionCode(),
                            dataset.getCoverageFrom(),
                            dataset.getCoverageTo(),
                            dataset.getLegalRegime(),
                            dataset.getLegalBasis(),
                            dataset.getSourceRevision(),
                            dataset.getSourceReference(),
                            dataset.isComplete(),
                            holidays
                    );

            return dataset
                    .getFingerprint()
                    .equals(
                            fingerprint(
                                    normalizeAndValidate(
                                            persisted
                                    )
                            )
                    );
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    private DatasetDraft normalizeAndValidate(
            DatasetDraft draft
    ) {
        String jurisdiction =
                normalizeRequired(
                        draft.jurisdictionCode(),
                        "Regional holiday jurisdiction is required"
                );

        if (!WorkJurisdictionHistoryService.RU.equals(
                jurisdiction
        )) {
            throw new IllegalArgumentException(
                    JURISDICTION_UNSUPPORTED + ":" + jurisdiction
            );
        }

        String region =
                normalizeRequired(
                        draft.regionCode(),
                        "Regional holiday region is required"
                );

        if (!region.startsWith("RU-")
                || region.length() < 4
                || region.length() > 32
                || !region.matches("[A-Z0-9-]+")) {
            throw new IllegalArgumentException(
                    "Regional holiday region code is invalid"
            );
        }

        LocalDate from =
                Objects.requireNonNull(
                        draft.coverageFrom(),
                        "Regional holiday coverage start is required"
                );

        LocalDate to =
                Objects.requireNonNull(
                        draft.coverageTo(),
                        "Regional holiday coverage end is required"
                );

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Regional holiday coverage is reversed"
            );
        }

        String legalRegime =
                requireText(
                        draft.legalRegime(),
                        "Regional holiday legal regime is required"
                );

        String legalBasis =
                requireText(
                        draft.legalBasis(),
                        "Regional holiday legal basis is required"
                );

        String sourceRevision =
                requireText(
                        draft.sourceRevision(),
                        "Regional holiday source revision is required"
                );

        String sourceReference =
                requireText(
                        draft.sourceReference(),
                        "Regional holiday source reference is required"
                );

        List<HolidayDraft> holidays =
                draft.holidays() == null
                        ? List.of()
                        : draft
                                .holidays()
                                .stream()
                                .map(holiday ->
                                        normalizeHoliday(
                                                holiday,
                                                from,
                                                to
                                        )
                                )
                                .sorted(
                                        Comparator.comparing(
                                                HolidayDraft::date
                                        )
                                )
                                .toList();

        Set<LocalDate> uniqueDates =
                new HashSet<>();

        for (HolidayDraft holiday : holidays) {
            if (!uniqueDates.add(
                    holiday.date()
            )) {
                throw new IllegalArgumentException(
                        "Regional holiday dataset contains duplicate date "
                                + holiday.date()
                );
            }
        }

        return new DatasetDraft(
                jurisdiction,
                region,
                from,
                to,
                legalRegime,
                legalBasis,
                sourceRevision,
                sourceReference,
                draft.complete(),
                holidays
        );
    }

    private HolidayDraft normalizeHoliday(
            HolidayDraft holiday,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        Objects.requireNonNull(
                holiday,
                "Regional holiday fact draft is required"
        );

        LocalDate date =
                Objects.requireNonNull(
                        holiday.date(),
                        "Regional holiday fact date is required"
                );

        if (date.isBefore(
                coverageFrom
        )
                || date.isAfter(
                coverageTo
        )) {
            throw new IllegalArgumentException(
                    "Regional holiday fact is outside dataset coverage"
            );
        }

        return new HolidayDraft(
                date,
                normalizeRequired(
                        holiday.holidayCode(),
                        "Regional holiday fact code is required"
                ),
                cleanOptional(
                        holiday.holidayLabel()
                ),
                requireText(
                        holiday.legalBasis(),
                        "Regional holiday fact legal basis is required"
                ),
                requireText(
                        holiday.sourceReference(),
                        "Regional holiday fact source reference is required"
                )
        );
    }

    private DatasetProvenance provenance(
            RegionalStatutoryHolidayDataset dataset
    ) {
        return new DatasetProvenance(
                requiredDatasetId(
                        dataset
                ),
                dataset.getJurisdictionCode(),
                dataset.getRegionCode(),
                dataset.getCoverageFrom(),
                dataset.getCoverageTo(),
                dataset.getLegalRegime(),
                dataset.getLegalBasis(),
                dataset.getSourceRevision(),
                dataset.getSourceReference(),
                dataset.isComplete(),
                dataset.getFingerprint(),
                dataset.getSourcePackSchema(),
                dataset.getSourcePackSha256(),
                dataset.getCompletenessEvidence()
        );
    }

    private String fingerprint(
            DatasetDraft draft
    ) {
        StringBuilder canonical =
                new StringBuilder();

        append(canonical, draft.jurisdictionCode());
        append(canonical, draft.regionCode());
        append(canonical, draft.coverageFrom().toString());
        append(canonical, draft.coverageTo().toString());
        append(canonical, draft.legalRegime());
        append(canonical, draft.legalBasis());
        append(canonical, draft.sourceRevision());
        append(canonical, draft.sourceReference());
        append(canonical, Boolean.toString(draft.complete()));

        for (HolidayDraft holiday : draft.holidays()) {
            append(canonical, holiday.date().toString());
            append(canonical, holiday.holidayCode());
            append(
                    canonical,
                    holiday.holidayLabel() == null
                            ? ""
                            : holiday.holidayLabel()
            );
            append(canonical, holiday.legalBasis());
            append(canonical, holiday.sourceReference());
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    canonical
                                            .toString()
                                            .getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                            )
                    );
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    private static void append(
            StringBuilder target,
            String value
    ) {
        target
                .append(
                        value.length()
                )
                .append(':')
                .append(
                        value
                )
                .append('\n');
    }

    private static long requiredDatasetId(
            RegionalStatutoryHolidayDataset dataset
    ) {
        if (dataset == null
                || dataset.getId() == null
                || dataset.getId() <= 0L) {
            throw new IllegalStateException(
                    "Regional holiday dataset requires immutable persisted id"
            );
        }

        return dataset.getId();
    }

    private static long requiredFactId(
            RegionalStatutoryHolidayDateFact fact
    ) {
        if (fact == null
                || fact.getId() == null
                || fact.getId() <= 0L) {
            throw new IllegalStateException(
                    "Regional holiday date fact requires immutable persisted id"
            );
        }

        return fact.getId();
    }

    private static String normalizeRequired(
            String value,
            String message
    ) {
        return requireText(
                value,
                message
        )
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private static String cleanOptional(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }

    public enum Status {
        NON_WORKING_PUBLIC_HOLIDAY,
        NOT_NON_WORKING_PUBLIC_HOLIDAY,
        UNRESOLVED
    }

    public record HolidayDraft(
            LocalDate date,
            String holidayCode,
            String holidayLabel,
            String legalBasis,
            String sourceReference
    ) {
    }

    public record DatasetDraft(
            String jurisdictionCode,
            String regionCode,
            LocalDate coverageFrom,
            LocalDate coverageTo,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            boolean complete,
            List<HolidayDraft> holidays
    ) {
    }

    public record SourcePackProvenance(
            String schema,
            String sha256,
            String completenessEvidence
    ) {
        public SourcePackProvenance {
            requireText(
                    schema,
                    "Regional holiday source pack schema is required"
            );

            if (sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Regional holiday source pack provenance requires lowercase SHA-256"
                );
            }

            requireText(
                    completenessEvidence,
                    "Regional holiday completeness evidence is required"
            );
        }
    }

    public record DatasetProvenance(
            long datasetId,
            String jurisdictionCode,
            String regionCode,
            LocalDate coverageFrom,
            LocalDate coverageTo,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            boolean complete,
            String fingerprint,
            String sourcePackSchema,
            String sourcePackSha256,
            String completenessEvidence
    ) {
        public DatasetProvenance {
            if (datasetId <= 0L) {
                throw new IllegalArgumentException(
                        "Regional holiday dataset provenance id must be positive"
                );
            }

            if (fingerprint == null
                    || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Regional holiday dataset provenance requires lowercase SHA-256"
                );
            }

            if (!SOURCE_PACK_SCHEMA_V1.equals(
                    sourcePackSchema
            )) {
                throw new IllegalArgumentException(
                        "Regional holiday dataset provenance requires supported source pack schema"
                );
            }

            if (sourcePackSha256 == null
                    || !sourcePackSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Regional holiday dataset provenance requires source pack SHA-256"
                );
            }

            requireText(
                    completenessEvidence,
                    "Regional holiday dataset provenance requires completeness evidence"
            );
        }
    }

    public record HolidayFact(
            long factId,
            String holidayCode,
            String holidayLabel,
            String legalBasis,
            String sourceReference
    ) {
        public HolidayFact {
            if (factId <= 0L) {
                throw new IllegalArgumentException(
                        "Regional holiday fact id must be positive"
                );
            }

            requireText(
                    holidayCode,
                    "Regional holiday fact code is required"
            );
            requireText(
                    legalBasis,
                    "Regional holiday fact legal basis is required"
            );
            requireText(
                    sourceReference,
                    "Regional holiday fact source reference is required"
            );
        }
    }

    public record Decision(
            LocalDate date,
            Status status,
            String blockingReason,
            DatasetProvenance provenance,
            HolidayFact holiday
    ) {
        public Decision {
            Objects.requireNonNull(
                    date,
                    "Regional holiday decision date is required"
            );
            Objects.requireNonNull(
                    status,
                    "Regional holiday decision status is required"
            );

            if (status == Status.UNRESOLVED) {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || provenance != null
                        || holiday != null) {
                    throw new IllegalArgumentException(
                            "Unresolved regional holiday decision is inconsistent"
                    );
                }
            }
            else if (blockingReason != null
                    || provenance == null) {
                throw new IllegalArgumentException(
                        "Resolved regional holiday decision requires provenance"
                );
            }

            if (status == Status.NON_WORKING_PUBLIC_HOLIDAY
                    && holiday == null) {
                throw new IllegalArgumentException(
                        "Positive regional holiday decision requires exact holiday fact"
                );
            }

            if (status == Status.NOT_NON_WORKING_PUBLIC_HOLIDAY
                    && (holiday != null
                    || !provenance.complete())) {
                throw new IllegalArgumentException(
                        "Negative regional holiday decision requires complete dataset and no holiday fact"
                );
            }
        }

        public static Decision unresolved(
                LocalDate date,
                String blocker
        ) {
            return new Decision(
                    date,
                    Status.UNRESOLVED,
                    blocker,
                    null,
                    null
            );
        }

        public static Decision holiday(
                LocalDate date,
                DatasetProvenance provenance,
                RegionalStatutoryHolidayDateFact fact
        ) {
            return new Decision(
                    date,
                    Status.NON_WORKING_PUBLIC_HOLIDAY,
                    null,
                    provenance,
                    new HolidayFact(
                            requiredFactId(
                                    fact
                            ),
                            fact.getHolidayCode(),
                            fact.getHolidayLabel(),
                            fact.getLegalBasis(),
                            fact.getSourceReference()
                    )
            );
        }

        public static Decision notHoliday(
                LocalDate date,
                DatasetProvenance provenance
        ) {
            return new Decision(
                    date,
                    Status.NOT_NON_WORKING_PUBLIC_HOLIDAY,
                    null,
                    provenance,
                    null
            );
        }

        public boolean ready() {
            return status != Status.UNRESOLVED;
        }

        public boolean nonWorkingPublicHoliday() {
            return status == Status.NON_WORKING_PUBLIC_HOLIDAY;
        }

        public boolean provenNotPublicHoliday() {
            return status == Status.NOT_NON_WORKING_PUBLIC_HOLIDAY;
        }
    }

    public record InstalledDataset(
            long datasetId,
            String fingerprint,
            String sourcePackSha256,
            int holidayFactCount,
            boolean created
    ) {
        public InstalledDataset {
            if (datasetId <= 0L) {
                throw new IllegalArgumentException(
                        "Installed regional holiday dataset id must be positive"
                );
            }

            if (fingerprint == null
                    || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Installed regional holiday dataset requires lowercase SHA-256"
                );
            }

            if (sourcePackSha256 == null
                    || !sourcePackSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Installed regional holiday dataset requires source pack SHA-256"
                );
            }

            if (holidayFactCount < 0) {
                throw new IllegalArgumentException(
                        "Installed regional holiday fact count must be non-negative"
                );
            }
        }
    }
}

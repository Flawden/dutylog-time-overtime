package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class RegionalStatutoryHolidaySourcePackService {
    public static final String LEGAL_IDENTITY =
            "NON_WORKING_PUBLIC_HOLIDAY";

    public static final int MAX_SOURCE_PACK_BYTES =
            1_048_576;

    public static final String SOURCE_PACK_SHA_MISMATCH =
            "REGIONAL_HOLIDAY_SOURCE_PACK_SHA_MISMATCH";

    public static final String SOURCE_PACK_TOO_LARGE =
            "REGIONAL_HOLIDAY_SOURCE_PACK_TOO_LARGE";

    public static final String SOURCE_PACK_JSON_INVALID =
            "REGIONAL_HOLIDAY_SOURCE_PACK_JSON_INVALID";

    public static final String SOURCE_PACK_SCHEMA_UNSUPPORTED =
            "REGIONAL_HOLIDAY_SOURCE_PACK_SCHEMA_UNSUPPORTED";

    public static final String SOURCE_PACK_LEGAL_IDENTITY_INVALID =
            "REGIONAL_HOLIDAY_SOURCE_PACK_LEGAL_IDENTITY_INVALID";

    public static final String SOURCE_PACK_UNKNOWN_FIELD =
            "REGIONAL_HOLIDAY_SOURCE_PACK_UNKNOWN_FIELD";

    private static final Set<String> ROOT_FIELDS =
            Set.of(
                    "schema",
                    "legalIdentity",
                    "jurisdictionCode",
                    "regionCode",
                    "coverageFrom",
                    "coverageTo",
                    "legalRegime",
                    "legalBasis",
                    "sourceRevision",
                    "sourceReference",
                    "complete",
                    "completenessEvidence",
                    "holidays"
            );

    private static final Set<String> HOLIDAY_FIELDS =
            Set.of(
                    "date",
                    "holidayCode",
                    "holidayLabel",
                    "legalBasis",
                    "sourceReference"
            );

    private final ObjectMapper mapper;
    private final RegionalStatutoryHolidayDatasetService datasets;

    public RegionalStatutoryHolidaySourcePackService(
            ObjectMapper mapper,
            RegionalStatutoryHolidayDatasetService datasets
    ) {
        this.mapper =
                Objects
                        .requireNonNull(
                                mapper,
                                "Regional holiday source-pack parser is required"
                        )
                        .copy();

        this.mapper.enable(
                JsonParser.Feature.STRICT_DUPLICATE_DETECTION
        );

        this.datasets =
                Objects.requireNonNull(
                        datasets,
                        "Regional holiday dataset authority is required"
                );
    }

    public Preview preview(
            byte[] sourcePack,
            String expectedSha256
    ) {
        byte[] payload =
                Objects.requireNonNull(
                        sourcePack,
                        "Regional holiday source pack is required"
                );

        if (payload.length == 0) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":EMPTY"
            );
        }

        if (payload.length > MAX_SOURCE_PACK_BYTES) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_TOO_LARGE
                            + ":"
                            + payload.length
            );
        }

        String expected =
                requireSha256(
                        expectedSha256,
                        "Expected regional holiday source-pack SHA-256 is required"
                );

        String actual =
                sha256(
                        payload
                );

        if (!actual.equals(
                expected
        )) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_SHA_MISMATCH
                            + ":EXPECTED="
                            + expected
                            + ":ACTUAL="
                            + actual
            );
        }

        JsonNode root;

        try {
            root =
                    mapper.readTree(
                            payload
                    );
        }
        catch (IOException ex) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID,
                    ex
            );
        }

        if (root == null
                || !root.isObject()) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":ROOT_NOT_OBJECT"
            );
        }

        rejectUnknownFields(
                root,
                ROOT_FIELDS,
                "ROOT"
        );

        String schema =
                requiredText(
                        root,
                        "schema"
                );

        if (!RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1.equals(
                schema
        )) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_SCHEMA_UNSUPPORTED
                            + ":"
                            + schema
            );
        }

        String legalIdentity =
                requiredText(
                        root,
                        "legalIdentity"
                );

        if (!LEGAL_IDENTITY.equals(
                legalIdentity
        )) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_LEGAL_IDENTITY_INVALID
                            + ":"
                            + legalIdentity
            );
        }

        boolean complete =
                requiredBoolean(
                        root,
                        "complete"
                );

        String completenessEvidence =
                requiredText(
                        root,
                        "completenessEvidence"
                );

        LocalDate coverageFrom =
                requiredDate(
                        root,
                        "coverageFrom"
                );

        LocalDate coverageTo =
                requiredDate(
                        root,
                        "coverageTo"
                );

        if (coverageTo.isBefore(
                coverageFrom
        )) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":COVERAGE_REVERSED"
            );
        }

        JsonNode holidaysNode =
                root.get(
                        "holidays"
                );

        if (holidaysNode == null
                || !holidaysNode.isArray()) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":HOLIDAYS_NOT_ARRAY"
            );
        }

        List<RegionalStatutoryHolidayDatasetService.HolidayDraft> holidays =
                new ArrayList<>();

        Set<LocalDate> seenDates =
                new HashSet<>();

        int index = 0;

        for (JsonNode holidayNode : holidaysNode) {
            if (holidayNode == null
                    || !holidayNode.isObject()) {
                throw new IllegalArgumentException(
                        SOURCE_PACK_JSON_INVALID
                                + ":HOLIDAY_NOT_OBJECT:"
                                + index
                );
            }

            rejectUnknownFields(
                    holidayNode,
                    HOLIDAY_FIELDS,
                    "HOLIDAY_"
                            + index
            );

            LocalDate holidayDate =
                    requiredDate(
                            holidayNode,
                            "date"
                    );

            if (holidayDate.isBefore(
                    coverageFrom
            )
                    || holidayDate.isAfter(
                    coverageTo
            )) {
                throw new IllegalArgumentException(
                        SOURCE_PACK_JSON_INVALID
                                + ":HOLIDAY_OUTSIDE_COVERAGE:"
                                + holidayDate
                );
            }

            if (!seenDates.add(
                    holidayDate
            )) {
                throw new IllegalArgumentException(
                        SOURCE_PACK_JSON_INVALID
                                + ":HOLIDAY_DATE_DUPLICATE:"
                                + holidayDate
                );
            }

            holidays.add(
                    new RegionalStatutoryHolidayDatasetService.HolidayDraft(
                            holidayDate,
                            requiredText(
                                    holidayNode,
                                    "holidayCode"
                            ),
                            optionalText(
                                    holidayNode,
                                    "holidayLabel"
                            ),
                            requiredText(
                                    holidayNode,
                                    "legalBasis"
                            ),
                            requiredText(
                                    holidayNode,
                                    "sourceReference"
                            )
                    )
            );

            index++;
        }

        RegionalStatutoryHolidayDatasetService.DatasetDraft draft =
                new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                        requiredText(
                                root,
                                "jurisdictionCode"
                        ),
                        requiredText(
                                root,
                                "regionCode"
                        ),
                        coverageFrom,
                        coverageTo,
                        requiredText(
                                root,
                                "legalRegime"
                        ),
                        requiredText(
                                root,
                                "legalBasis"
                        ),
                        requiredText(
                                root,
                                "sourceRevision"
                        ),
                        requiredText(
                                root,
                                "sourceReference"
                        ),
                        complete,
                        List.copyOf(
                                holidays
                        )
                );

        RegionalStatutoryHolidayDatasetService.SourcePackProvenance provenance =
                new RegionalStatutoryHolidayDatasetService.SourcePackProvenance(
                        schema,
                        actual,
                        completenessEvidence
                );

        return new Preview(
                actual,
                draft,
                provenance,
                holidays.size()
        );
    }

    @Transactional
    public ImportResult installTrusted(
            byte[] sourcePack,
            String expectedSha256
    ) {
        Preview preview =
                preview(
                        sourcePack,
                        expectedSha256
                );

        RegionalStatutoryHolidayDatasetService.InstalledDataset installed =
                datasets.installTrusted(
                        preview.dataset(),
                        preview.provenance()
                );

        return new ImportResult(
                preview.sourcePackSha256(),
                installed
        );
    }

    private void rejectUnknownFields(
            JsonNode object,
            Set<String> allowed,
            String location
    ) {
        Iterator<String> fieldNames =
                object.fieldNames();

        while (fieldNames.hasNext()) {
            String field =
                    fieldNames.next();

            if (!allowed.contains(
                    field
            )) {
                throw new IllegalArgumentException(
                        SOURCE_PACK_UNKNOWN_FIELD
                                + ":"
                                + location
                                + ":"
                                + field
                );
            }
        }
    }

    private static String requiredText(
            JsonNode object,
            String field
    ) {
        JsonNode value =
                object.get(
                        field
                );

        if (value == null
                || !value.isTextual()
                || value.textValue() == null
                || value.textValue().isBlank()) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":TEXT_REQUIRED:"
                            + field
            );
        }

        return value.textValue().trim();
    }

    private static String optionalText(
            JsonNode object,
            String field
    ) {
        JsonNode value =
                object.get(
                        field
                );

        if (value == null
                || value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":TEXT_OR_NULL_REQUIRED:"
                            + field
            );
        }

        String cleaned =
                value.textValue().trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }

    private static boolean requiredBoolean(
            JsonNode object,
            String field
    ) {
        JsonNode value =
                object.get(
                        field
                );

        if (value == null
                || !value.isBoolean()) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":BOOLEAN_REQUIRED:"
                            + field
            );
        }

        return value.booleanValue();
    }

    private static LocalDate requiredDate(
            JsonNode object,
            String field
    ) {
        String value =
                requiredText(
                        object,
                        field
                );

        try {
            return LocalDate.parse(
                    value
            );
        }
        catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    SOURCE_PACK_JSON_INVALID
                            + ":DATE_INVALID:"
                            + field
                            + ":"
                            + value,
                    ex
            );
        }
    }

    private static String requireSha256(
            String value,
            String message
    ) {
        if (value == null
                || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }

    private static String sha256(
            byte[] payload
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    payload
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

    public record Preview(
            String sourcePackSha256,
            RegionalStatutoryHolidayDatasetService.DatasetDraft dataset,
            RegionalStatutoryHolidayDatasetService.SourcePackProvenance provenance,
            int holidayFactCount
    ) {
        public Preview {
            requireSha256(
                    sourcePackSha256,
                    "Regional holiday source-pack preview requires SHA-256"
            );

            Objects.requireNonNull(
                    dataset,
                    "Regional holiday source-pack preview requires dataset"
            );
            Objects.requireNonNull(
                    provenance,
                    "Regional holiday source-pack preview requires provenance"
            );

            if (holidayFactCount < 0) {
                throw new IllegalArgumentException(
                        "Regional holiday source-pack fact count must be non-negative"
                );
            }
        }
    }

    public record ImportResult(
            String sourcePackSha256,
            RegionalStatutoryHolidayDatasetService.InstalledDataset installed
    ) {
        public ImportResult {
            requireSha256(
                    sourcePackSha256,
                    "Regional holiday import result requires source-pack SHA-256"
            );

            Objects.requireNonNull(
                    installed,
                    "Regional holiday import result requires installed dataset"
            );

            if (!sourcePackSha256.equals(
                    installed.sourcePackSha256()
            )) {
                throw new IllegalArgumentException(
                        "Regional holiday import result source-pack identity mismatch"
                );
            }
        }
    }
}

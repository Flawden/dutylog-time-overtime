package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegionalStatutoryHolidaySourcePackServiceTest {
    RegionalStatutoryHolidayDatasetService datasets;
    RegionalStatutoryHolidaySourcePackService service;

    @BeforeEach
    void setUp() {
        datasets = mock(RegionalStatutoryHolidayDatasetService.class);
        service = new RegionalStatutoryHolidaySourcePackService(new ObjectMapper(),datasets);
    }

    @Test
    void validCompletePackParsesStrictlyAndInstallsPinnedBytes() {
        byte[] pack = validPack(true,oneHoliday());
        String sha = sha256(pack);

        when(datasets.installTrusted(any(),any())).thenReturn(
                new RegionalStatutoryHolidayDatasetService.InstalledDataset(
                        41L,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        sha,
                        1,
                        true));

        var result = service.installTrusted(pack,sha);

        assertEquals(sha,result.sourcePackSha256());
        assertTrue(result.installed().created());

        verify(datasets).installTrusted(
                argThat(draft ->
                        "RU".equals(draft.jurisdictionCode())
                                && "RU-KYA".equals(draft.regionCode())
                                && draft.complete()
                                && draft.holidays().size() == 1),
                argThat(provenance ->
                        RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1.equals(provenance.schema())
                                && sha.equals(provenance.sha256())
                                && "EXHAUSTIVE REGIONAL LEGAL REVIEW 2026".equals(provenance.completenessEvidence())));
    }

    @Test
    void exactSourcePackShaMismatchFailsBeforeDatasetAuthority() {
        byte[] pack = validPack(true,oneHoliday());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(
                        pack,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(ex.getMessage().startsWith(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_SHA_MISMATCH));
        verifyNoInteractions(datasets);
    }

    @Test
    void unknownRootFieldIsRejected() {
        byte[] pack = validPackJson(true,oneHoliday(),",\"surprise\":\"forbidden\"")
                .getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
        assertTrue(ex.getMessage().contains(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_UNKNOWN_FIELD));
    }

    @Test
    void unknownHolidayFieldIsRejected() {
        String holiday = """
                {
                  "date":"2026-06-24",
                  "holidayCode":"REGIONAL_RELIGIOUS_HOLIDAY",
                  "holidayLabel":"Regional holiday",
                  "legalBasis":"REGIONAL HOLIDAY LEGAL BASIS",
                  "sourceReference":"REGIONAL HOLIDAY SOURCE",
                  "transferDay":true
                }
                """;
        byte[] pack = validPack(true,holiday);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
        assertTrue(ex.getMessage().contains(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_UNKNOWN_FIELD));
    }

    @Test
    void duplicateJsonKeyIsRejected() {
        byte[] pack = validPackJson(true,oneHoliday(),",\"regionCode\":\"RU-ALT\"")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
    }

    @Test
    void unsupportedSchemaIsRejected() {
        byte[] original = validPack(true,oneHoliday());
        byte[] pack = new String(original,StandardCharsets.UTF_8)
                .replace(RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,"UNSUPPORTED_SCHEMA")
                .getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));

        assertTrue(ex.getMessage().startsWith(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_SCHEMA_UNSUPPORTED));
    }

    @Test
    void wrongLegalIdentityIsRejected() {
        byte[] original = validPack(true,oneHoliday());
        byte[] pack = new String(original,StandardCharsets.UTF_8)
                .replace(RegionalStatutoryHolidaySourcePackService.LEGAL_IDENTITY,"TRANSFERRED_REST_DAY")
                .getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));

        assertTrue(ex.getMessage().startsWith(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_LEGAL_IDENTITY_INVALID));
    }

    @Test
    void blankCompletenessEvidenceIsRejected() {
        byte[] pack = validPackJson(false,oneHoliday(),"")
                .replace(
                        "\"completenessEvidence\":\"EXHAUSTIVE REGIONAL LEGAL REVIEW 2026\"",
                        "\"completenessEvidence\":\" \"")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
    }

    @Test
    void completePackWithZeroRegionalHolidaysIsValidNegativeAuthorityInput() {
        byte[] pack = validPack(true,"");
        var preview = service.preview(pack,sha256(pack));
        assertTrue(preview.dataset().complete());
        assertEquals(0,preview.holidayFactCount());
        assertTrue(preview.dataset().holidays().isEmpty());
    }

    @Test
    void invalidDateIsRejected() {
        byte[] pack = validPack(
                true,
                oneHoliday().replace("2026-06-24","2026-02-30"));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
    }

    @Test
    void holidaysMustBeArray() {
        String valid = validPackJson(true,oneHoliday(),"");
        String marker = "\"holidays\":[" + oneHoliday() + "]";
        assertTrue(valid.contains(marker));
        byte[] pack = valid.replace(marker,"\"holidays\":{}")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));
    }

    @Test
    void oversizedPackIsRejectedBeforeParsing() {
        byte[] pack = new byte[
                RegionalStatutoryHolidaySourcePackService.MAX_SOURCE_PACK_BYTES + 1];

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.preview(pack,sha256(pack)));

        assertTrue(ex.getMessage().startsWith(
                RegionalStatutoryHolidaySourcePackService.SOURCE_PACK_TOO_LARGE));
    }

    private byte[] validPack(boolean complete,String holidayJson) {
        return validPackJson(complete,holidayJson,"").getBytes(StandardCharsets.UTF_8);
    }

    private String validPackJson(boolean complete,String holidayJson,String extraRoot) {
        String holidays = holidayJson == null || holidayJson.isBlank() ? "" : holidayJson;
        return """
                {
                  "schema":"%s",
                  "legalIdentity":"%s",
                  "jurisdictionCode":"RU",
                  "regionCode":"RU-KYA",
                  "coverageFrom":"2026-01-01",
                  "coverageTo":"2026-12-31",
                  "legalRegime":"RU_KYA_2026_V1",
                  "legalBasis":"REGIONAL LEGAL BASIS",
                  "sourceRevision":"REVISION-2026-01",
                  "sourceReference":"REGIONAL SOURCE REFERENCE",
                  "complete":%s,
                  "completenessEvidence":"EXHAUSTIVE REGIONAL LEGAL REVIEW 2026",
                  "holidays":[%s]
                  %s
                }
                """.formatted(
                        RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                        RegionalStatutoryHolidaySourcePackService.LEGAL_IDENTITY,
                        Boolean.toString(complete),
                        holidays,
                        extraRoot);
    }

    private String oneHoliday() {
        return """
                {
                  "date":"2026-06-24",
                  "holidayCode":"REGIONAL_RELIGIOUS_HOLIDAY",
                  "holidayLabel":"Regional holiday",
                  "legalBasis":"REGIONAL HOLIDAY LEGAL BASIS",
                  "sourceReference":"REGIONAL HOLIDAY SOURCE"
                }
                """;
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}

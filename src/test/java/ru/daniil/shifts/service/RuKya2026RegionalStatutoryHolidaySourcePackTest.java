package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDataset;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDatasetRepository;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDateFactRepository;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuKya2026RegionalStatutoryHolidaySourcePackTest {
    private static final String RESOURCE =
            "/legal/ru-kya/2026/regional-statutory-holidays.json";

    private static final String RAW_SHA =
            "7ca56e78cb7c5342af5b73ad59a0326daf88d34d69e561e1825aaaa2ac3be9c3";

    private static final String SEMANTIC_FINGERPRINT =
            "3965ebb71bacbc610c799d81b96730399b0e0aba11779776fc9e58c608c27071";

    @Test
    void exactReviewedSourcePackBytesArePinned() throws Exception {
        byte[] bytes =
                resourceBytes();

        assertEquals(
                RAW_SHA,
                sha256(
                        bytes
                )
        );
    }

    @Test
    void packParsesAsCompleteRuKya2026WithZeroRegionalFacts() throws Exception {
        var preview =
                parser().preview(
                        resourceBytes(),
                        RAW_SHA
                );

        assertEquals(
                "RU",
                preview.dataset().jurisdictionCode()
        );
        assertEquals(
                "RU-KYA",
                preview.dataset().regionCode()
        );
        assertEquals(
                LocalDate.of(
                        2026,
                        1,
                        1
                ),
                preview.dataset().coverageFrom()
        );
        assertEquals(
                LocalDate.of(
                        2026,
                        12,
                        31
                ),
                preview.dataset().coverageTo()
        );
        assertTrue(
                preview.dataset().complete()
        );
        assertEquals(
                0,
                preview.holidayFactCount()
        );
        assertTrue(
                preview.dataset().holidays().isEmpty()
        );
    }

    @Test
    void actualDatasetAuthorityProducesExpectedSemanticFingerprint() throws Exception {
        RegionalStatutoryHolidayDatasetRepository datasets =
                mock(
                        RegionalStatutoryHolidayDatasetRepository.class
                );

        RegionalStatutoryHolidayDateFactRepository facts =
                mock(
                        RegionalStatutoryHolidayDateFactRepository.class
                );

        RegionalStatutoryHolidayDatasetService datasetService =
                new RegionalStatutoryHolidayDatasetService(
                        datasets,
                        facts
                );

        RegionalStatutoryHolidaySourcePackService sourcePacks =
                new RegionalStatutoryHolidaySourcePackService(
                        new ObjectMapper(),
                        datasetService
                );

        when(
                datasets.findByFingerprint(
                        anyString()
                )
        )
                .thenReturn(
                        Optional.empty()
                );

        when(
                datasets.saveAndFlush(
                        any(
                                RegionalStatutoryHolidayDataset.class
                        )
                )
        )
                .thenAnswer(invocation -> {
                    RegionalStatutoryHolidayDataset dataset =
                            invocation.getArgument(
                                    0
                            );

                    setId(
                            dataset,
                            8201L
                    );

                    return dataset;
                });

        var preview =
                sourcePacks.preview(
                        resourceBytes(),
                        RAW_SHA
                );

        var installed =
                datasetService.installTrusted(
                        preview.dataset(),
                        preview.provenance()
                );

        assertEquals(
                SEMANTIC_FINGERPRINT,
                installed.fingerprint()
        );
        assertEquals(
                RAW_SHA,
                installed.sourcePackSha256()
        );
        assertEquals(
                0,
                installed.holidayFactCount()
        );

        verify(
                facts,
                never()
        )
                .saveAll(
                        anyList()
                );
    }

    @Test
    void migrationPinsExactPackAndSemanticIdentities() throws Exception {
        String sql =
                migrationText();

        assertTrue(
                sql.contains(
                        "'" + RAW_SHA + "'"
                )
        );
        assertTrue(
                sql.contains(
                        "'" + SEMANTIC_FINGERPRINT + "'"
                )
        );
        assertTrue(
                sql.contains(
                        "'RU-KYA'"
                )
        );
        assertTrue(
                sql.contains(
                        "DATE '2026-01-01'"
                )
        );
        assertTrue(
                sql.contains(
                        "DATE '2026-12-31'"
                )
        );
        assertTrue(
                sql.contains(
                        "TRUE"
                )
        );
    }

    @Test
    void migrationSeedsNoRegionalPositiveDateFacts() throws Exception {
        String sql =
                migrationText();

        assertFalse(
                sql.contains(
                        "INSERT INTO regional_statutory_holiday_date_facts"
                )
        );
        assertTrue(
                sql.contains(
                        "reviewed RU-KYA regional statutory public-holiday positives for 2026 = 0"
                )
        );
    }

    @Test
    void transferredRestDayJanuaryNinthIsNotRegionalHolidayFact() throws Exception {
        var preview =
                parser().preview(
                        resourceBytes(),
                        RAW_SHA
                );

        assertTrue(
                preview.dataset().holidays().stream().noneMatch(
                        holiday ->
                                LocalDate.of(
                                        2026,
                                        1,
                                        9
                                ).equals(
                                        holiday.date()
                                )
                )
        );
    }

    @Test
    void sourcePackIsExplicitlyBoundedTo2026() throws Exception {
        var preview =
                parser().preview(
                        resourceBytes(),
                        RAW_SHA
                );

        assertEquals(
                LocalDate.of(
                        2026,
                        1,
                        1
                ),
                preview.dataset().coverageFrom()
        );
        assertEquals(
                LocalDate.of(
                        2026,
                        12,
                        31
                ),
                preview.dataset().coverageTo()
        );
        assertTrue(
                preview
                        .provenance()
                        .completenessEvidence()
                        .contains(
                                "re-reviewed for later periods"
                        )
        );
    }

    @Test
    void reviewedSourceReferencePinsRegionalIndexAndFederalAuthorityBoundaries() throws Exception {
        var preview =
                parser().preview(
                        resourceBytes(),
                        RAW_SHA
                );

        String reference =
                preview.dataset().sourceReference();

        assertTrue(
                reference.contains(
                        "cons_doc_LAW_311098"
                )
        );
        assertTrue(
                reference.contains(
                        "cons_doc_LAW_34683"
                )
        );
        assertTrue(
                reference.contains(
                        "cons_doc_LAW_16218"
                )
        );
    }

    private RegionalStatutoryHolidaySourcePackService parser() {
        return new RegionalStatutoryHolidaySourcePackService(
                new ObjectMapper(),
                mock(
                        RegionalStatutoryHolidayDatasetService.class
                )
        );
    }

    private byte[] resourceBytes() throws IOException {
        try (var input =
                     getClass().getResourceAsStream(
                             RESOURCE
                     )) {
            assertNotNull(
                    input,
                    "Reviewed RU-KYA source pack resource is missing"
            );

            return input.readAllBytes();
        }
    }

    private String migrationText() throws IOException {
        try (var input =
                     getClass().getResourceAsStream(
                             "/db/migration/postgresql/V82__seed_ru_kya_2026_regional_statutory_holidays.sql"
                     )) {
            assertNotNull(
                    input,
                    "V82 RU-KYA seed migration is missing"
            );

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private static String sha256(
            byte[] bytes
    ) {
        try {
            return HexFormat
                    .of()
                    .formatHex(
                            MessageDigest
                                    .getInstance(
                                            "SHA-256"
                                    )
                                    .digest(
                                            bytes
                                    )
                    );
        }
        catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(
                    ex
            );
        }
    }

    private static void setId(
            RegionalStatutoryHolidayDataset dataset,
            long id
    ) {
        try {
            Field field =
                    RegionalStatutoryHolidayDataset.class
                            .getDeclaredField(
                                    "id"
                            );

            field.setAccessible(
                    true
            );

            field.set(
                    dataset,
                    id
            );
        }
        catch (ReflectiveOperationException ex) {
            throw new AssertionError(
                    ex
            );
        }
    }
}

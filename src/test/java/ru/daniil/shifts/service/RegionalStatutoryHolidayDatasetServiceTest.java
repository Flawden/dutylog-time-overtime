package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDataset;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDateFact;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDatasetRepository;
import ru.daniil.shifts.repo.RegionalStatutoryHolidayDateFactRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegionalStatutoryHolidayDatasetServiceTest {
    private static final String PACK_SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    RegionalStatutoryHolidayDatasetRepository datasets;
    RegionalStatutoryHolidayDateFactRepository facts;
    RegionalStatutoryHolidayDatasetService service;
    AtomicLong ids;

    @BeforeEach
    void setUp() {
        datasets = mock(RegionalStatutoryHolidayDatasetRepository.class);
        facts = mock(RegionalStatutoryHolidayDateFactRepository.class);
        service = new RegionalStatutoryHolidayDatasetService(datasets, facts);
        ids = new AtomicLong(100L);
    }

    @Test
    void missingDatasetFailsClosed() {
        LocalDate date = LocalDate.of(2026,7,15);
        when(datasets.findCovering("RU","RU-KYA",date)).thenReturn(List.of());
        var result = service.resolve("ru","ru-kya",date);
        assertFalse(result.ready());
        assertEquals(RegionalStatutoryHolidayDatasetService.DATASET_MISSING + ":RU-KYA:" + date,
                result.blockingReason());
    }

    @Test
    void overlappingDatasetsFailClosedAsAmbiguous() {
        LocalDate date = LocalDate.of(2026,7,15);
        Fixture first = installFixture(completeDraft(List.of()));
        Fixture second = installFixture(completeDraft(List.of()));
        when(datasets.findCovering("RU","RU-KYA",date)).thenReturn(List.of(first.dataset(),second.dataset()));
        var result = service.resolve("RU","RU-KYA",date);
        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(RegionalStatutoryHolidayDatasetService.DATASET_AMBIGUOUS));
    }

    @Test
    void completeDatasetProvesNegativeDate() {
        LocalDate date = LocalDate.of(2026,7,15);
        Fixture fixture = installFixture(completeDraft(List.of()));
        stubResolve(fixture,date);
        var result = service.resolve("RU","RU-KYA",date);
        assertTrue(result.ready());
        assertTrue(result.provenNotPublicHoliday());
        assertFalse(result.nonWorkingPublicHoliday());
        assertTrue(result.provenance().complete());
        assertEquals(PACK_SHA,result.provenance().sourcePackSha256());
        assertNull(result.holiday());
    }

    @Test
    void incompleteDatasetCannotProveNegativeDate() {
        LocalDate date = LocalDate.of(2026,7,15);
        Fixture fixture = installFixture(draft(false,List.of()));
        stubResolve(fixture,date);
        var result = service.resolve("RU","RU-KYA",date);
        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(RegionalStatutoryHolidayDatasetService.DATASET_INCOMPLETE));
    }

    @Test
    void exactPositiveFactIsProvenEvenWhenDatasetIsIncomplete() {
        LocalDate holiday = LocalDate.of(2026,6,24);
        Fixture fixture = installFixture(draft(false,List.of(holiday(holiday,"REGIONAL_RELIGIOUS_HOLIDAY"))));
        stubResolve(fixture,holiday);
        var result = service.resolve("RU","RU-KYA",holiday);
        assertTrue(result.ready());
        assertTrue(result.nonWorkingPublicHoliday());
        assertEquals("REGIONAL_RELIGIOUS_HOLIDAY",result.holiday().holidayCode());
        assertTrue(result.holiday().factId() > 0L);
    }

    @Test
    void fingerprintTamperingFailsClosed() {
        LocalDate date = LocalDate.of(2026,6,24);
        Fixture fixture = installFixture(completeDraft(List.of(holiday(date,"REGIONAL_RELIGIOUS_HOLIDAY"))));
        setField(fixture.facts().get(0),"holidayCode","TAMPERED");
        stubResolve(fixture,date);
        var result = service.resolve("RU","RU-KYA",date);
        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(RegionalStatutoryHolidayDatasetService.DATASET_INTEGRITY_FAILURE));
    }

    @Test
    void nullFactListFailsClosedAsIntegrityFailure() {
        LocalDate date = LocalDate.of(2026,7,15);
        Fixture fixture = installFixture(completeDraft(List.of()));
        when(datasets.findCovering("RU","RU-KYA",date)).thenReturn(List.of(fixture.dataset()));
        when(facts.findByDatasetOrderByHolidayDateAscIdAsc(fixture.dataset())).thenReturn(null);
        var result = service.resolve("RU","RU-KYA",date);
        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(RegionalStatutoryHolidayDatasetService.DATASET_INTEGRITY_FAILURE));
    }

    @Test
    void unsupportedJurisdictionFailsClosedBeforeDatasetLookup() {
        LocalDate date = LocalDate.of(2026,7,15);
        var result = service.resolve("DE","DE-BE",date);
        assertFalse(result.ready());
        assertEquals(RegionalStatutoryHolidayDatasetService.JURISDICTION_UNSUPPORTED + ":DE",result.blockingReason());
        verifyNoInteractions(datasets,facts);
    }

    @Test
    void trustedInstallNormalizesIdentityAndPersistsFingerprintAndFacts() {
        stubFreshInstall();
        var result = service.installTrusted(
                new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                        " ru "," ru-kya ",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),
                        "REGIME","LEGAL BASIS","REVISION","SOURCE",true,
                        List.of(holiday(LocalDate.of(2026,6,24),"regional_holiday"))),
                sourcePack());
        assertTrue(result.created());
        assertEquals(1,result.holidayFactCount());
        assertTrue(result.fingerprint().matches("[0-9a-f]{64}"));
        assertEquals(PACK_SHA,result.sourcePackSha256());

        ArgumentCaptor<RegionalStatutoryHolidayDataset> captor =
                ArgumentCaptor.forClass(RegionalStatutoryHolidayDataset.class);
        verify(datasets).saveAndFlush(captor.capture());
        assertEquals("RU",captor.getValue().getJurisdictionCode());
        assertEquals("RU-KYA",captor.getValue().getRegionCode());
        assertEquals(PACK_SHA,captor.getValue().getSourcePackSha256());
    }

    @Test
    void trustedInstallIsIdempotentByFingerprintAndExactSourcePack() {
        var draft = completeDraft(List.of(holiday(LocalDate.of(2026,6,24),"REGIONAL_HOLIDAY")));
        Fixture fixture = installFixture(draft);
        reset(datasets,facts);
        when(datasets.findByFingerprint(fixture.installed().fingerprint())).thenReturn(Optional.of(fixture.dataset()));
        when(facts.findByDatasetOrderByHolidayDateAscIdAsc(fixture.dataset())).thenReturn(fixture.facts());
        var second = service.installTrusted(draft,sourcePack());
        assertFalse(second.created());
        assertEquals(fixture.installed().datasetId(),second.datasetId());
        verify(datasets,never()).saveAndFlush(any());
    }

    @Test
    void duplicateHolidayDatesAreRejected() {
        LocalDate date = LocalDate.of(2026,6,24);
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(
                        completeDraft(List.of(holiday(date,"A"),holiday(date,"B"))),
                        sourcePack()));
    }

    @Test
    void holidayOutsideCoverageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(
                        completeDraft(List.of(holiday(LocalDate.of(2027,1,1),"OUTSIDE"))),
                        sourcePack()));
    }

    @Test
    void invalidRegionIsRejected() {
        var base = completeDraft(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(
                        new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                                "RU","KYA",base.coverageFrom(),base.coverageTo(),base.legalRegime(),
                                base.legalBasis(),base.sourceRevision(),base.sourceReference(),true,List.of()),
                        sourcePack()));
    }

    @Test
    void unsupportedInstallJurisdictionIsRejected() {
        var base = completeDraft(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(
                        new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                                "DE","DE-BE",base.coverageFrom(),base.coverageTo(),base.legalRegime(),
                                base.legalBasis(),base.sourceRevision(),base.sourceReference(),true,List.of()),
                        sourcePack()));
    }

    @Test
    void reversedCoverageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(
                        new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                                "RU","RU-KYA",LocalDate.of(2026,12,31),LocalDate.of(2026,1,1),
                                "REGIME","BASIS","REVISION","SOURCE",true,List.of()),
                        sourcePack()));
    }

    @Test
    void missingSourcePackProvenanceFailsClosedBeforeDateFacts() {
        LocalDate date = LocalDate.of(2026,7,15);
        Fixture fixture = installFixture(completeDraft(List.of()));
        setField(fixture.dataset(),"sourcePackSha256",null);
        when(datasets.findCovering("RU","RU-KYA",date)).thenReturn(List.of(fixture.dataset()));

        var result = service.resolve("RU","RU-KYA",date);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                RegionalStatutoryHolidayDatasetService.DATASET_SOURCE_PACK_PROVENANCE_MISSING));
        verify(facts,never()).findByDatasetOrderByHolidayDateAscIdAsc(any());
    }

    @Test
    void sameSemanticDatasetWithDifferentSourcePackFailsClosedConflict() {
        var draft = completeDraft(List.of());
        Fixture fixture = installFixture(draft);
        reset(datasets,facts);
        when(datasets.findByFingerprint(fixture.installed().fingerprint())).thenReturn(Optional.of(fixture.dataset()));
        when(facts.findByDatasetOrderByHolidayDateAscIdAsc(fixture.dataset())).thenReturn(fixture.facts());

        var different = new RegionalStatutoryHolidayDatasetService.SourcePackProvenance(
                RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "COMPLETE LEGAL REVIEW");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.installTrusted(draft,different));
        assertTrue(ex.getMessage().startsWith(
                RegionalStatutoryHolidayDatasetService.DATASET_SOURCE_PACK_CONFLICT));
    }

    @Test
    void unsupportedSourcePackSchemaIsRejected() {
        var invalid = new RegionalStatutoryHolidayDatasetService.SourcePackProvenance(
                "UNSUPPORTED",PACK_SHA,"COMPLETE LEGAL REVIEW");
        assertThrows(IllegalArgumentException.class,
                () -> service.installTrusted(completeDraft(List.of()),invalid));
    }

    private void stubFreshInstall() {
        when(datasets.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(datasets.saveAndFlush(any(RegionalStatutoryHolidayDataset.class))).thenAnswer(inv -> {
            RegionalStatutoryHolidayDataset d = inv.getArgument(0);
            setField(d,"id",ids.getAndIncrement());
            return d;
        });
        when(facts.saveAll(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<RegionalStatutoryHolidayDateFact> rows = inv.getArgument(0);
            for (RegionalStatutoryHolidayDateFact row : rows) setField(row,"id",ids.getAndIncrement());
            return rows;
        });
    }

    private Fixture installFixture(RegionalStatutoryHolidayDatasetService.DatasetDraft draft) {
        reset(datasets,facts);
        stubFreshInstall();
        List<RegionalStatutoryHolidayDateFact> captured = new ArrayList<>();
        when(facts.saveAll(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<RegionalStatutoryHolidayDateFact> rows = inv.getArgument(0);
            for (RegionalStatutoryHolidayDateFact row : rows) {
                setField(row,"id",ids.getAndIncrement());
                captured.add(row);
            }
            return rows;
        });
        var installed = service.installTrusted(draft,sourcePack());
        ArgumentCaptor<RegionalStatutoryHolidayDataset> captor =
                ArgumentCaptor.forClass(RegionalStatutoryHolidayDataset.class);
        verify(datasets).saveAndFlush(captor.capture());
        RegionalStatutoryHolidayDataset dataset = captor.getValue();
        reset(datasets,facts);
        return new Fixture(dataset,captured,installed);
    }

    private void stubResolve(Fixture fixture, LocalDate date) {
        when(datasets.findCovering("RU","RU-KYA",date)).thenReturn(List.of(fixture.dataset()));
        when(facts.findByDatasetOrderByHolidayDateAscIdAsc(fixture.dataset())).thenReturn(fixture.facts());
    }

    private RegionalStatutoryHolidayDatasetService.DatasetDraft completeDraft(
            List<RegionalStatutoryHolidayDatasetService.HolidayDraft> holidays) {
        return draft(true,holidays);
    }

    private RegionalStatutoryHolidayDatasetService.DatasetDraft draft(
            boolean complete,
            List<RegionalStatutoryHolidayDatasetService.HolidayDraft> holidays) {
        return new RegionalStatutoryHolidayDatasetService.DatasetDraft(
                "RU","RU-KYA",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),
                "RU_KYA_2026_V1","REGIONAL LEGAL BASIS","REVISION-2026-01",
                "SOURCE-REFERENCE",complete,holidays);
    }

    private RegionalStatutoryHolidayDatasetService.HolidayDraft holiday(LocalDate date,String code) {
        return new RegionalStatutoryHolidayDatasetService.HolidayDraft(
                date,code,"Holiday label","HOLIDAY LEGAL BASIS","HOLIDAY SOURCE");
    }

    private RegionalStatutoryHolidayDatasetService.SourcePackProvenance sourcePack() {
        return new RegionalStatutoryHolidayDatasetService.SourcePackProvenance(
                RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                PACK_SHA,
                "COMPLETE LEGAL REVIEW");
    }

    private void setField(Object target,String fieldName,Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target,value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private record Fixture(
            RegionalStatutoryHolidayDataset dataset,
            List<RegionalStatutoryHolidayDateFact> facts,
            RegionalStatutoryHolidayDatasetService.InstalledDataset installed) {}
}

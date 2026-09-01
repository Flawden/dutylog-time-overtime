package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacationPayProductionAuthorityWiringContractTest {

    @Test
    void canonicalApplicationBoundaryOwnsFinalParagraph7AndParagraph8Authorities() throws Exception {
        String source = read("src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java");
        assertTrue(source.contains(
                "AverageEarningsParagraph7PreEventAccruedWageAuthorityService paragraph7Authority"
        ));
        assertTrue(source.contains(
                "AverageEarningsParagraph8TariffSalaryAuthorityService paragraph8Authority"
        ));
        assertTrue(source.contains("canonicalParagraph7Authority("));
        assertTrue(source.contains("canonicalParagraph8Authority("));
    }

    @Test
    void canonicalJ5AuthoritiesStayLazyBehindOrderedFallbackSuppliers() throws Exception {
        String source = read("src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java");
        assertTrue(source.contains("() -> canonicalParagraph7Authority("));
        assertTrue(source.contains("() -> canonicalParagraph8Authority(user, eventDate)"));
        assertTrue(source.contains("orderedFallbackResolver.resolve("));
    }

    @Test
    void explicitAuthorityAndBasisSupplierSeamsRemainAvailable() throws Exception {
        String source = read("src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java");
        assertTrue(source.contains(
                "Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>"
        ));
        assertTrue(source.contains(
                "Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>"
        ));
        assertTrue(source.contains(
                "Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>"
        ));
    }

    @Test
    void paragraph7ComposerOwnsNoSnapshotOrTotalPayMutation() throws Exception {
        String source = read(
                "src/main/java/ru/daniil/shifts/service/"
                        + "AverageEarningsParagraph7PreEventAccruedWageAuthorityService.java"
        );
        assertTrue(source.contains("@Service"));
        assertTrue(source.contains("@Transactional(readOnly = true)"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("setTotalPay"));
        assertFalse(source.contains("totalPay"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}

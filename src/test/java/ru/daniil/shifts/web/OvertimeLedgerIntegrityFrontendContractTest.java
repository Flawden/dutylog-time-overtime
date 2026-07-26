package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OvertimeLedgerIntegrityFrontendContractTest {

    private static String resource(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(relative));
    }

    @Test
    void allocationRenderingDegradesPerItemInsteadOfBreakingTheWholeLedger() throws Exception {
        String overtime = resource("js/40-overtime.js");

        assertTrue(overtime.contains("function safeAllocationRangeLabels(allocation)"));
        assertTrue(overtime.contains("Failed to render overtime allocation"));
        assertTrue(overtime.contains("allocationRenderWarning"));
    }

    @Test
    void ledgerRowsAreCommittedAtomicallyAfterTheWholeFragmentIsBuilt() throws Exception {
        String overtime = resource("js/40-overtime.js");

        assertTrue(overtime.contains("const fragment = document.createDocumentFragment()"));
        assertTrue(overtime.contains("Failed to render overtime ledger atomically"));
        assertTrue(overtime.contains("tbody.replaceChildren(fragment)"));
    }

    @Test
    void splitUsageActionsClearlyOperateOnTheWholeTimeOff() throws Exception {
        String overtime = resource("js/40-overtime.js");
        String css = resource("app.css");

        assertTrue(overtime.contains("allocationPartBadge"));
        assertTrue(overtime.contains("allocationPartCount"));
        assertTrue(overtime.contains("allocationPartIndex"));
        assertTrue(overtime.contains("Удалить весь отгул"));
        assertTrue(overtime.contains("Начисления переработки останутся"));
        assertTrue(css.contains(".allocationPartBadge"));
    }
}

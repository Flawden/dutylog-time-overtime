package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacationPayHttpEntryPointContractTest {
    @Test
    void controllerOwnsOnlyAuthenticatedNoStorePayrollHttpBoundary() throws Exception {
        String source = read("src/main/java/ru/daniil/shifts/web/VacationPayController.java");

        assertTrue(source.contains(
                "@RequestMapping({\"/api/payroll/vacation-pay\", \"/api/v1/payroll/vacation-pay\"})"
        ));
        assertTrue(source.contains("@PostMapping(\"/preview\")"));
        assertTrue(source.contains("users.requireUser(principal)"));
        assertTrue(source.contains("modules.requireEnabled(user, ModuleService.PAYROLL)"));
        assertTrue(source.contains("vacationPay.resolve("));
        assertTrue(source.contains("CacheControl.noStore()"));
    }

    @Test
    void transportDtosKeepExplicitEvidenceAndFlatStableResult() throws Exception {
        String source = read("src/main/java/ru/daniil/shifts/dto/Dtos.java");

        assertTrue(source.contains("record VacationPayPreviewRequest("));
        assertTrue(source.contains(
                "@NotNull(message = \"Список доказанных месяцев без Payroll обязателен\")"
        ));
        assertTrue(source.contains("> provenNoPayrollMonths"));
        assertTrue(source.contains("record VacationPayPreviewDto("));
        assertTrue(source.contains("String selectedBasis"));
        assertTrue(source.contains("String blockingStage"));
        assertTrue(source.contains("String upstreamBlockingReason"));
        assertTrue(source.contains("Long vacationPayMinor"));
        assertTrue(source.contains("int payableCalendarDays"));
    }

    @Test
    void openApiAndGeneratedClientExposeOneReadOnlyPreviewContract() throws Exception {
        String yaml = read("src/main/resources/static/openapi/dutylog-v1.yaml");
        String generated = read("frontend/src/generated/dutylog-api.ts");

        assertTrue(yaml.contains("/api/v1/payroll/vacation-pay/preview:"));
        assertTrue(yaml.contains("operationId: previewVacationPay"));
        assertTrue(yaml.contains("VacationPayPreviewInput:"));
        assertTrue(yaml.contains("VacationPayPreview:"));
        assertTrue(yaml.contains(
                "enum: [PRIMARY_REFERENCE_PERIOD, PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD, "
                        + "PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE, "
                        + "PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY]"
        ));
        assertTrue(generated.contains(
                "\"previewVacationPay\": { method: \"POST\", "
                        + "path: \"/api/v1/payroll/vacation-pay/preview\" }"
        ));
        assertTrue(generated.contains("export type VacationPayPreviewInput"));
        assertTrue(generated.contains("export type VacationPayPreview"));
    }

    @Test
    void httpBoundaryCannotMutatePayrollSnapshotsOrTotalPay() throws Exception {
        String controller = read("src/main/java/ru/daniil/shifts/web/VacationPayController.java");

        assertFalse(controller.contains("PayrollSnapshot"));
        assertFalse(controller.contains("PayrollService"));
        assertFalse(controller.contains("totalPay"));
        assertFalse(controller.contains(".save("));
        assertFalse(controller.contains("@PutMapping"));
        assertFalse(controller.contains("@PatchMapping"));
        assertFalse(controller.contains("@DeleteMapping"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}

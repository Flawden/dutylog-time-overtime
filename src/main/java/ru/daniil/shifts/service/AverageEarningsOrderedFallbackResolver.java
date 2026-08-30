package ru.daniil.shifts.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ordered paragraphs 6-8 fallback policy for vacation average earnings.
 *
 * <p>This layer is deliberately policy-only. It does not calculate average
 * daily earnings and does not price paragraph 8. It consumes already-proven
 * authorities and selects exactly one legal basis in strict order:</p>
 *
 * <pre>
 * PRIMARY -> PARAGRAPH 6 PRECEDING -> PARAGRAPH 7 PRE-EVENT -> PARAGRAPH 8
 * </pre>
 *
 * <p>Paragraph-7 and paragraph-8 authorities are supplied lazily so a later
 * authority cannot even be evaluated before every earlier branch is proven
 * exhausted. A blocked or identity-mismatched authority is never interpreted
 * as exhausted. Missing is not zero; blocked is not fallback.</p>
 */
public final class AverageEarningsOrderedFallbackResolver {

    public static final String AUTHORITY_EVENT_IDENTITY_MISMATCH =
            "PP_540_ORDERED_FALLBACK_AUTHORITY_EVENT_IDENTITY_MISMATCH";

    private AverageEarningsOrderedFallbackResolver() {
    }

    public static Resolution resolve(
            AverageEarningsParagraph6ReferenceResolver.Resolution paragraph6,
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>
                    paragraph7Supplier,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>
                    paragraph8Supplier
    ) {
        Objects.requireNonNull(
                paragraph6,
                "Ordered fallback resolver requires paragraph-6 authority"
        );
        Objects.requireNonNull(
                paragraph7Supplier,
                "Ordered fallback resolver requires paragraph-7 supplier"
        );
        Objects.requireNonNull(
                paragraph8Supplier,
                "Ordered fallback resolver requires paragraph-8 supplier"
        );

        LocalDate eventDate = Objects.requireNonNull(
                paragraph6.eventDate(),
                "Ordered fallback resolver requires legal event date"
        );
        YearMonth eventMonth = YearMonth.from(eventDate);
        AverageEarningsLegalPolicy.LegalRegime legalRegime =
                AverageEarningsLegalPolicy.requireRegime(eventDate);

        if (!paragraph6.ready()) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_6,
                    requireReason(paragraph6.blockingReason()),
                    "Paragraph-6 reference authority is blocked"
            );
        }

        if (!eventMonth.equals(paragraph6.eventMonth())
                || paragraph6.selection() == null
                || paragraph6.selectedEvidence() == null
                || paragraph6.selectedEvidence().window() == null
                || !eventMonth.equals(paragraph6.selectedEvidence().window().eventMonth())) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_6,
                    AUTHORITY_EVENT_IDENTITY_MISMATCH,
                    "Paragraph-6 authority does not belong to the legal event"
            );
        }

        AverageEarningsReferenceWindow primary =
                AverageEarningsReferenceWindow.primary(eventDate);

        if (paragraph6.selection()
                == AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY) {
            if (!paragraph6.selectedEvidence().window().equals(primary)) {
                return Resolution.blocked(
                        eventDate,
                        BlockingStage.PARAGRAPH_6,
                        AUTHORITY_EVENT_IDENTITY_MISMATCH,
                        "Primary paragraph-6 selection carries a non-primary reference window"
                );
            }
            return Resolution.readyReference(
                    eventDate,
                    Selection.PRIMARY_REFERENCE_PERIOD,
                    paragraph6.selection(),
                    primary
            );
        }

        if (paragraph6.selection()
                == AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING) {
            AverageEarningsReferenceWindow preceding = primary.precedingEqual();
            if (!paragraph6.selectedEvidence().window().equals(preceding)) {
                return Resolution.blocked(
                        eventDate,
                        BlockingStage.PARAGRAPH_6,
                        AUTHORITY_EVENT_IDENTITY_MISMATCH,
                        "Paragraph-6 preceding selection carries the wrong reference window"
                );
            }
            return Resolution.readyReference(
                    eventDate,
                    Selection.PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD,
                    paragraph6.selection(),
                    preceding
            );
        }

        if (paragraph6.selection()
                != AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_6,
                    AUTHORITY_EVENT_IDENTITY_MISMATCH,
                    "Paragraph-6 authority has unsupported selection state"
            );
        }

        AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7 =
                Objects.requireNonNull(
                        paragraph7Supplier.get(),
                        "Ordered fallback paragraph-7 supplier returned null"
                );

        if (!paragraph7IdentityMatches(paragraph7, eventDate)) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_7,
                    AUTHORITY_EVENT_IDENTITY_MISMATCH,
                    "Paragraph-7 authority does not belong to the legal pre-event window"
            );
        }

        if (!paragraph7.ready()) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_7,
                    requireReason(paragraph7.blockingReason()),
                    paragraph7.blockingMessage() == null
                            ? "Paragraph-7 pre-event accrued-wage authority is blocked"
                            : paragraph7.blockingMessage()
            );
        }

        boolean paragraph7WagePresent = paragraph7.accruedWagePresent();
        boolean paragraph7WorkedTimePresent = paragraph7.workedTimePresent();

        if (paragraph7WagePresent && paragraph7WorkedTimePresent) {
            return Resolution.readyParagraph7(
                    eventDate,
                    paragraph6.selection(),
                    paragraph7
            );
        }

        List<Paragraph7ExhaustionReason> paragraph7Exhaustion = new ArrayList<>(2);
        if (!paragraph7WagePresent) {
            paragraph7Exhaustion.add(
                    Paragraph7ExhaustionReason.NO_PRE_EVENT_ACCRUED_WAGE
            );
        }
        if (!paragraph7WorkedTimePresent) {
            paragraph7Exhaustion.add(
                    Paragraph7ExhaustionReason.NO_PRE_EVENT_ACTUALLY_WORKED_TIME
            );
        }

        AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8 =
                Objects.requireNonNull(
                        paragraph8Supplier.get(),
                        "Ordered fallback paragraph-8 supplier returned null"
                );

        if (!paragraph8IdentityMatches(
                paragraph8,
                eventDate,
                eventMonth,
                legalRegime
        )) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_8,
                    AUTHORITY_EVENT_IDENTITY_MISMATCH,
                    "Paragraph-8 authority does not belong to the legal event month"
            );
        }

        if (!paragraph8.ready()) {
            return Resolution.blocked(
                    eventDate,
                    BlockingStage.PARAGRAPH_8,
                    requireReason(paragraph8.blockingReason()),
                    paragraph8.blockingMessage() == null
                            ? "Paragraph-8 tariff/salary authority is blocked"
                            : paragraph8.blockingMessage()
            );
        }

        return Resolution.readyParagraph8(
                eventDate,
                paragraph6.selection(),
                paragraph7,
                paragraph8,
                paragraph7Exhaustion
        );
    }

    private static boolean paragraph7IdentityMatches(
            AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7,
            LocalDate eventDate
    ) {
        return eventDate.equals(paragraph7.eventDate())
                && YearMonth.from(eventDate).atDay(1).equals(paragraph7.periodFrom())
                && eventDate.equals(paragraph7.cutoffExclusive());
    }

    private static boolean paragraph8IdentityMatches(
            AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8,
            LocalDate eventDate,
            YearMonth eventMonth,
            AverageEarningsLegalPolicy.LegalRegime legalRegime
    ) {
        return eventDate.equals(paragraph8.eventDate())
                && eventMonth.equals(paragraph8.eventMonth())
                && eventMonth.atDay(1).equals(paragraph8.compensationBoundary())
                && legalRegime == paragraph8.legalRegime();
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return AUTHORITY_EVENT_IDENTITY_MISMATCH;
        }
        return reason;
    }

    public enum Selection {
        PRIMARY_REFERENCE_PERIOD,
        PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD,
        PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE,
        PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
    }

    public enum BlockingStage {
        PARAGRAPH_6,
        PARAGRAPH_7,
        PARAGRAPH_8
    }

    public enum Paragraph7ExhaustionReason {
        NO_PRE_EVENT_ACCRUED_WAGE,
        NO_PRE_EVENT_ACTUALLY_WORKED_TIME
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            String blockingMessage,
            Selection selection,
            AverageEarningsParagraph6ReferenceResolver.Selection paragraph6Selection,
            AverageEarningsReferenceWindow selectedReferenceWindow,
            AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7Authority,
            AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8Authority,
            List<Paragraph7ExhaustionReason> paragraph7ExhaustionReasons
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Ordered fallback result requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Ordered fallback result requires event month"
            );
            paragraph7ExhaustionReasons = List.copyOf(Objects.requireNonNull(
                    paragraph7ExhaustionReasons,
                    "Ordered fallback result requires paragraph-7 exhaustion reasons"
            ));

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Ordered fallback result event identity is invalid"
                );
            }

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Ordered fallback result state is invalid"
                );
            }

            if (!ready) {
                if (blockingStage == null
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || selection != null
                        || paragraph6Selection != null
                        || selectedReferenceWindow != null
                        || paragraph7Authority != null
                        || paragraph8Authority != null
                        || !paragraph7ExhaustionReasons.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked ordered fallback cannot expose partial selection"
                    );
                }
            } else {
                if (blockingStage != null
                        || blockingMessage != null
                        || selection == null
                        || paragraph6Selection == null) {
                    throw new IllegalArgumentException(
                            "Ready ordered fallback selection is incomplete"
                    );
                }

                AverageEarningsReferenceWindow primary =
                        AverageEarningsReferenceWindow.primary(eventDate);

                switch (selection) {
                case PRIMARY_REFERENCE_PERIOD -> {
                    if (paragraph6Selection
                                    != AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY
                            || !primary.equals(selectedReferenceWindow)
                            || paragraph7Authority != null
                            || paragraph8Authority != null
                            || !paragraph7ExhaustionReasons.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Primary ordered fallback selection is inconsistent"
                        );
                    }
                }
                case PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD -> {
                    if (paragraph6Selection
                                    != AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING
                            || !primary.precedingEqual().equals(selectedReferenceWindow)
                            || paragraph7Authority != null
                            || paragraph8Authority != null
                            || !paragraph7ExhaustionReasons.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Paragraph-6 ordered fallback selection is inconsistent"
                        );
                    }
                }
                case PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE -> {
                    if (paragraph6Selection
                                    != AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED
                            || selectedReferenceWindow != null
                            || paragraph7Authority == null
                            || !paragraph7Authority.ready()
                            || !paragraph7IdentityMatches(paragraph7Authority, eventDate)
                            || !paragraph7Authority.accruedWagePresent()
                            || !paragraph7Authority.workedTimePresent()
                            || paragraph8Authority != null
                            || !paragraph7ExhaustionReasons.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Paragraph-7 ordered fallback selection is inconsistent"
                        );
                    }
                }
                case PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY -> {
                    if (paragraph6Selection
                                    != AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED
                            || selectedReferenceWindow != null
                            || paragraph7Authority == null
                            || !paragraph7Authority.ready()
                            || !paragraph7IdentityMatches(paragraph7Authority, eventDate)
                            || paragraph8Authority == null
                            || !paragraph8Authority.ready()
                            || !paragraph8IdentityMatches(
                                    paragraph8Authority,
                                    eventDate,
                                    eventMonth,
                                    AverageEarningsLegalPolicy.requireRegime(eventDate)
                            )) {
                        throw new IllegalArgumentException(
                                "Paragraph-8 ordered fallback selection is incomplete"
                        );
                    }

                    List<Paragraph7ExhaustionReason> expected = new ArrayList<>(2);
                    if (!paragraph7Authority.accruedWagePresent()) {
                        expected.add(Paragraph7ExhaustionReason.NO_PRE_EVENT_ACCRUED_WAGE);
                    }
                    if (!paragraph7Authority.workedTimePresent()) {
                        expected.add(
                                Paragraph7ExhaustionReason.NO_PRE_EVENT_ACTUALLY_WORKED_TIME
                        );
                    }
                    if (expected.isEmpty()
                            || !paragraph7ExhaustionReasons.equals(expected)) {
                        throw new IllegalArgumentException(
                                "Paragraph-8 selection requires exact paragraph-7 exhaustion evidence"
                        );
                    }
                }
            }
        }
        }

        static Resolution readyReference(
                LocalDate eventDate,
                Selection selection,
                AverageEarningsParagraph6ReferenceResolver.Selection paragraph6Selection,
                AverageEarningsReferenceWindow selectedReferenceWindow
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    selection,
                    paragraph6Selection,
                    selectedReferenceWindow,
                    null,
                    null,
                    List.of()
            );
        }

        static Resolution readyParagraph7(
                LocalDate eventDate,
                AverageEarningsParagraph6ReferenceResolver.Selection paragraph6Selection,
                AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE,
                    paragraph6Selection,
                    null,
                    paragraph7,
                    null,
                    List.of()
            );
        }

        static Resolution readyParagraph8(
                LocalDate eventDate,
                AverageEarningsParagraph6ReferenceResolver.Selection paragraph6Selection,
                AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7,
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8,
                List<Paragraph7ExhaustionReason> paragraph7Exhaustion
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    null,
                    Selection.PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY,
                    paragraph6Selection,
                    null,
                    paragraph7,
                    paragraph8,
                    paragraph7Exhaustion
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                BlockingStage stage,
                String reason,
                String message
        ) {
            Objects.requireNonNull(stage, "Ordered fallback blocker stage is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Ordered fallback blocker reason is required"
                );
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException(
                        "Ordered fallback blocker message is required"
                );
            }
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    false,
                    stage,
                    reason,
                    message,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }
    }
}

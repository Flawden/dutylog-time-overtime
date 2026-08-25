package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacationPayrollReferenceTruthTest {

    private static final String RESOURCE =
            "/payroll/truth/vacation-pay-reference-truth-v1.json";

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void sameSalaryReferenceMonthsProrateBasePayExactly() throws Exception {
        JsonNode root = read();

        assertEquals(
                6_054_800L,
                root.path(
                        "monthlySalaryMinor"
                ).asLong()
        );

        for (JsonNode month : root.path("months")) {
            long salary =
                    root.path(
                            "monthlySalaryMinor"
                    ).asLong();

            long worked =
                    month.path(
                            "workedMinutes"
                    ).asLong();

            long norm =
                    month.path(
                            "productionNormMinutes"
                    ).asLong();

            assertEquals(
                    ratioHalfUp(
                            salary,
                            worked,
                            norm
                    ),
                    month.path(
                            "basePayMinor"
                    ).asLong(),
                    month.path(
                            "period"
                    ).asText()
            );
        }
    }

    @Test
    void vacationPayUsesObservedDailyAmountTimesPayableCalendarDays()
            throws Exception {

        JsonNode root = read();

        long averageDaily =
                root.path(
                        "observedAverageDailyEarningsMinor"
                ).asLong();

        assertEquals(
                368_642L,
                averageDaily
        );

        List<JsonNode> postings =
                postings(root);

        assertEquals(
                2,
                postings.size()
        );

        for (JsonNode posting : postings) {
            assertEquals(
                    PayrollEarningKind.VACATION_PAY.name(),
                    posting.path(
                            "semanticKey"
                    ).asText()
            );

            JsonNode qualifiedQuantity =
                    posting.path(
                            "qualifiedQuantity"
                    );

            assertEquals(
                    "CALENDAR_DAYS",
                    qualifiedQuantity.path(
                            "unit"
                    ).asText()
            );

            assertEquals(
                    posting.path(
                            "payableCalendarDays"
                    ).asLong(),
                    qualifiedQuantity.path(
                            "value"
                    ).asLong()
            );

            assertEquals(
                    Math.multiplyExact(
                            averageDaily,
                            posting.path(
                                    "payableCalendarDays"
                            ).asLong()
                    ),
                    posting.path(
                            "amountMinor"
                    ).asLong()
            );
        }
    }

    @Test
    void vacationQuantityIsCalendarDaysNotScheduledWorkMinutes()
            throws Exception {

        List<JsonNode> postings =
                postings(
                        read()
                );

        JsonNode mayCoverage =
                postings.get(0);

        JsonNode juneCoverage =
                postings.get(1);

        assertEquals(
                14,
                mayCoverage.path(
                        "payableCalendarDays"
                ).asInt()
        );

        assertEquals(
                14,
                juneCoverage.path(
                        "payableCalendarDays"
                ).asInt()
        );

        assertNotEquals(
                mayCoverage.path(
                        "scheduledWorkMinutes"
                ).asInt(),
                juneCoverage.path(
                        "scheduledWorkMinutes"
                ).asInt()
        );

        assertEquals(
                mayCoverage.path(
                        "amountMinor"
                ).asLong(),
                juneCoverage.path(
                        "amountMinor"
                ).asLong()
        );
    }

    @Test
    void calendarSpanAndPayableVacationDaysRemainDistinctConcepts()
            throws Exception {

        List<JsonNode> postings =
                postings(
                        read()
                );

        JsonNode first =
                postings.get(0);

        JsonNode second =
                postings.get(1);

        assertEquals(
                14L,
                inclusiveCalendarDays(
                        first
                )
        );

        assertEquals(
                14,
                first.path(
                        "payableCalendarDays"
                ).asInt()
        );

        assertEquals(
                15L,
                inclusiveCalendarDays(
                        second
                )
        );

        assertEquals(
                14,
                second.path(
                        "payableCalendarDays"
                ).asInt()
        );
    }

    @Test
    void vacationPayIsExcludedFromObservedCurrentMonthPercentageBases()
            throws Exception {

        JsonNode root = read();
        JsonNode may =
                month(
                        root,
                        "2026-05"
                );

        long basePay =
                may.path(
                        "basePayMinor"
                ).asLong();

        long harmful =
                may.path(
                        "harmfulConditionsMinor"
                ).asLong();

        long monthly =
                may.path(
                        "monthlyBonusMinor"
                ).asLong();

        long regional =
                may.path(
                        "regionalCoefficientMinor"
                ).asLong();

        long vacationTotal =
                postings(root)
                        .stream()
                        .mapToLong(item ->
                                item.path(
                                        "amountMinor"
                                ).asLong()
                        )
                        .sum();

        assertEquals(
                percentHalfUp(
                        basePay,
                        400
                ),
                harmful
        );

        assertEquals(
                percentHalfUp(
                        Math.addExact(
                                basePay,
                                harmful
                        ),
                        4_000
                ),
                monthly
        );

        assertEquals(
                percentHalfUp(
                        Math.addExact(
                                Math.addExact(
                                        basePay,
                                        harmful
                                ),
                                monthly
                        ),
                        1_500
                ),
                regional
        );

        /*
         * Negative evidence: including vacation pay would change every
         * observed result, therefore it cannot belong to these current-month
         * percentage bases.
         */
        assertNotEquals(
                harmful,
                percentHalfUp(
                        Math.addExact(
                                basePay,
                                vacationTotal
                        ),
                        400
                )
        );

        assertNotEquals(
                monthly,
                percentHalfUp(
                        Math.addExact(
                                Math.addExact(
                                        basePay,
                                        harmful
                                ),
                                vacationTotal
                        ),
                        4_000
                )
        );

        assertNotEquals(
                regional,
                percentHalfUp(
                        Math.addExact(
                                Math.addExact(
                                        Math.addExact(
                                                basePay,
                                                harmful
                                        ),
                                        monthly
                                ),
                                vacationTotal
                        ),
                        1_500
                )
        );
    }

    @Test
    void postingPeriodCanPrecedeVacationCoveragePeriod()
            throws Exception {

        JsonNode second =
                postings(
                        read()
                ).get(1);

        assertEquals(
                "2026-05",
                second.path(
                        "postingPeriod"
                ).asText()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        6,
                        1
                ),
                LocalDate.parse(
                        second.path(
                                "coveredFrom"
                        ).asText()
                )
        );

        assertFalse(
                second.path(
                        "coveredFrom"
                ).asText()
                        .startsWith(
                                second.path(
                                        "postingPeriod"
                                ).asText()
                        )
        );
    }

    @Test
    void postVacationJulyControlConfirmsOneTimeBonusEligibility()
            throws Exception {

        JsonNode july =
                month(
                        read(),
                        "2026-07"
                );

        long basePay =
                july.path(
                        "basePayMinor"
                ).asLong();

        long harmful =
                july.path(
                        "harmfulConditionsMinor"
                ).asLong();

        long monthly =
                july.path(
                        "monthlyBonusMinor"
                ).asLong();

        long oneTime =
                july.path(
                        "oneTimeBonusMinor"
                ).asLong();

        long regional =
                july.path(
                        "regionalCoefficientMinor"
                ).asLong();

        long gross =
                july.path(
                        "grossMinor"
                ).asLong();

        assertEquals(
                6_054_800L,
                basePay
        );

        assertEquals(
                percentHalfUp(
                        basePay,
                        400
                ),
                harmful
        );

        long monthlyBase =
                Math.addExact(
                        basePay,
                        harmful
                );

        assertEquals(
                percentHalfUp(
                        monthlyBase,
                        4_000
                ),
                monthly
        );

        /*
         * Independent real-payroll negative evidence:
         * ONE_TIME_BONUS exists in July but does not feed the 40% bonus.
         */
        assertNotEquals(
                monthly,
                percentHalfUp(
                        Math.addExact(
                                monthlyBase,
                                oneTime
                        ),
                        4_000
                )
        );

        long regionalBaseWithoutOneTime =
                Math.addExact(
                        Math.addExact(
                                basePay,
                                harmful
                        ),
                        monthly
                );

        /*
         * The same ONE_TIME_BONUS does feed the 15% regional coefficient.
         */
        assertEquals(
                percentHalfUp(
                        Math.addExact(
                                regionalBaseWithoutOneTime,
                                oneTime
                        ),
                        1_500
                ),
                regional
        );

        assertNotEquals(
                percentHalfUp(
                        regionalBaseWithoutOneTime,
                        1_500
                ),
                regional
        );

        assertEquals(
                gross,
                Math.addExact(
                        Math.addExact(
                                Math.addExact(
                                        Math.addExact(
                                                basePay,
                                                harmful
                                        ),
                                        monthly
                                ),
                                oneTime
                        ),
                        regional
                )
        );
    }

    @Test
    void historicalAverageReferenceBaseRemainsUnknown()
            throws Exception {

        JsonNode reference =
                read().path(
                        "historicalAverageReferenceBase"
                );

        assertEquals(
                "UNKNOWN",
                reference.path(
                        "status"
                ).asText()
        );

        assertTrue(
                reference.path(
                        "amountMinor"
                ).isNull()
        );

        assertNull(
                reference.path(
                        "amountMinor"
                ).isMissingNode()
                        ? null
                        : reference.get(
                                "amountMinor"
                        ).isNull()
                        ? null
                        : reference.get(
                                "amountMinor"
                        ).asLong()
        );
    }

    private JsonNode read() throws Exception {
        try (InputStream input =
                     getClass()
                             .getResourceAsStream(
                                     RESOURCE
                             )) {

            if (input == null) {
                throw new IllegalStateException(
                        "Missing vacation truth resource: "
                                + RESOURCE
                );
            }

            return objectMapper.readTree(
                    input
            );
        }
    }

    private static List<JsonNode> postings(
            JsonNode root
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        for (JsonNode month : root.path("months")) {
            for (JsonNode posting
                    : month.path(
                            "vacationPostings"
                    )) {
                result.add(
                        posting
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    private static JsonNode month(
            JsonNode root,
            String period
    ) {
        for (JsonNode month : root.path("months")) {
            if (period.equals(
                    month.path(
                            "period"
                    ).asText()
            )) {
                return month;
            }
        }

        throw new IllegalArgumentException(
                "Missing reference month "
                        + period
        );
    }

    private static long inclusiveCalendarDays(
            JsonNode posting
    ) {
        LocalDate from =
                LocalDate.parse(
                        posting.path(
                                "coveredFrom"
                        ).asText()
                );

        LocalDate to =
                LocalDate.parse(
                        posting.path(
                                "coveredTo"
                        ).asText()
                );

        return Math.addExact(
                ChronoUnit.DAYS.between(
                        from,
                        to
                ),
                1L
        );
    }

    private static long ratioHalfUp(
            long amount,
            long numerator,
            long denominator
    ) {
        return BigDecimal
                .valueOf(
                        amount
                )
                .multiply(
                        BigDecimal.valueOf(
                                numerator
                        )
                )
                .divide(
                        BigDecimal.valueOf(
                                denominator
                        ),
                        0,
                        RoundingMode.HALF_UP
                )
                .longValueExact();
    }

    private static long percentHalfUp(
            long baseMinor,
            int rateBps
    ) {
        return Math.floorDiv(
                Math.addExact(
                        Math.multiplyExact(
                                baseMinor,
                                rateBps
                        ),
                        5_000L
                ),
                10_000L
        );
    }
}

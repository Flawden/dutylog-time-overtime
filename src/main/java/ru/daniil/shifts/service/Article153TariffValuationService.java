package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.PayrollSnapshotArticle153;
import java.util.*;
import static ru.daniil.shifts.service.Article153TariffDocument.*;

/** Arithmetic references from frozen C3 evidence. Never a final payable earning. */
@Service
public class Article153TariffValuationService {
    private final PayPricingEngine pricing;
    public Article153TariffValuationService(PayPricingEngine pricing) { this.pricing = pricing; }

    public Document value(PayrollSnapshotArticle153 authority) {
        JsonNode source = Article153SnapshotCodec.read(authority);
        var snapshot = authority.getSnapshot();
        Map<Key, List<Share>> groups = new TreeMap<>(Comparator.comparing(Key::kind)
                .thenComparingLong(Key::rate).thenComparingInt(Key::bps));
        var gates = new TreeSet<String>(List.of("FINAL_PAYROLL_INTEGRATION_REQUIRED",
                "REMUNERATION_COMPLETENESS_REQUIRED", "LEGACY_HOLIDAY_RECONCILIATION_REQUIRED"));
        int index = 0;
        for (JsonNode piece : source.path("pieces")) {
            validatePiece(piece, snapshot.getCurrencyCode());
            int minutes = Math.toIntExact(number(piece.at("/norm/qualifiedPiece/sourcePiece/minutes")));
            long rate = number(piece.at("/historicalRate/baseHourlyRateMinor"));
            int floor = Math.toIntExact(number(piece.at("/statutoryFloor/additionalTariffBps")));
            int local = Math.toIntExact(number(piece.at("/localRate/configuredAdditionalTariffBps")));
            for (Kind kind : Kind.values()) {
                Key key = new Key(kind, rate, kind == Kind.STATUTORY_TARIFF_REFERENCE ? floor : local);
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Share(index, minutes));
            }
            for (JsonNode component : piece.at("/components/components")) {
                if ("INCLUDE".equals(component.path("decision").asText())) {
                    gates.add("COMPONENT_MONETARY_RULES_REQUIRED");
                }
            }
            if (local > 0 && "OTHER_REST_DAY".equals(piece.at("/statutoryFloor/compensationChoice").asText())) {
                gates.add("LOCAL_RATE_REST_DAY_APPLICABILITY_REQUIRED");
            }
            index++;
        }
        List<Line> lines = new ArrayList<>();
        long floorTotal = 0, localTotal = 0;
        for (var entry : groups.entrySet()) {
            Key key = entry.getKey();
            int minutes = 0;
            for (Share share : entry.getValue()) minutes = Math.addExact(minutes, share.minutes());
            var calculated = pricing.price(key.rate(), List.of(new PayPricingEngine.PricingSlice(minutes,
                    List.of(new PayPricingEngine.PremiumComponent(key.kind().name(), key.bps())))));
            // The engine's implicit ordinary base is a reference only and MUST NOT be added here.
            long amount = calculated.premiumAmountMinor();
            lines.add(new Line(key.kind(), key.rate(), key.bps(), minutes, amount, entry.getValue()));
            if (key.kind() == Kind.STATUTORY_TARIFF_REFERENCE) floorTotal = Math.addExact(floorTotal, amount);
            else localTotal = Math.addExact(localTotal, amount);
        }
        return new Document(SCHEMA, "TARIFF_REFERENCE_ONLY", "PAY_PRICING_ENGINE_BPS_HALF_UP_V1",
                snapshot.getId(), snapshot.getCalculationHash(), authority.getFingerprint(),
                snapshot.getCurrencyCode(), authority.getPieceCount(), authority.getQualifiedMinutes(),
                floorTotal, localTotal, false, List.copyOf(gates), lines);
    }

    static void validatePiece(JsonNode piece, String currency) {
        long rate = number(piece.at("/historicalRate/baseHourlyRateMinor"));
        check(rate > 0 && currency.equals(text(piece.at("/historicalRate/currencyCode"))), "RATE_OR_CURRENCY");
        String date = text(piece.at("/norm/qualifiedPiece/payrollDate"));
        for (String path : List.of("/historicalRate/sourceDate", "/statutoryFloor/sourceDate", "/localRate/sourceDate", "/components/sourceDate")) {
            check(date.equals(text(piece.at(path))), "SOURCE_DATE");
        }
        check(text(piece.at("/norm/payMode")).equals(text(piece.at("/statutoryFloor/payMode")))
                && text(piece.at("/norm/normPosition")).equals(text(piece.at("/statutoryFloor/normPosition"))), "POLICY_BINDING");
        check(piece.at("/localRate/ready").isBoolean() && piece.at("/localRate/ready").booleanValue()
                && piece.at("/components/ready").isBoolean() && piece.at("/components/ready").booleanValue(), "AUTHORITY_NOT_READY");
        String election = text(piece.at("/election/state"));
        String choice = text(piece.at("/statutoryFloor/compensationChoice"));
        check(("NONE".equals(election) && "ENHANCED_PAY".equals(choice))
                || ("ELECTED".equals(election) && "OTHER_REST_DAY".equals(choice)), "CHOICE_BINDING");
        check(piece.at("/components/components").isArray(), "COMPONENTS");
        for (JsonNode component : piece.at("/components/components")) {
            String decision = text(component.path("decision"));
            check("INCLUDE".equals(decision) || "EXCLUDE".equals(decision), "COMPONENT_DECISION");
        }
        check(number(piece.at("/statutoryFloor/additionalTariffBps")) >= 0
                && number(piece.at("/localRate/configuredAdditionalTariffBps")) >= 0, "NEGATIVE_BPS");
    }
    static long number(JsonNode node) {
        check(node.isIntegralNumber() && node.canConvertToLong(), "INTEGER_REQUIRED");
        return node.longValue();
    }
    static String text(JsonNode node) {
        check(node.isTextual() && !node.textValue().isBlank(), "TEXT_REQUIRED");
        return node.textValue();
    }
    private record Key(Kind kind, long rate, int bps) {}
}

package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ru.daniil.shifts.model.PayrollSnapshotArticle153Tariff;
import java.util.*;

/** Versioned monetary reference format. Historical reads validate, but never reprice. */
public final class Article153TariffDocument {
    public static final String SCHEMA = "ARTICLE153_TARIFF_REFERENCE_V1";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    private Article153TariffDocument() {}
    public enum Kind { STATUTORY_TARIFF_REFERENCE, CONFIGURED_LOCAL_REFERENCE }
    public record Share(int sourcePieceIndex, int minutes) {
        public Share { check(sourcePieceIndex >= 0 && minutes > 0, "SHARE"); }
    }
    public record Line(Kind kind, long hourlyRateMinor, int additionalBps, int minutes,
                       long amountMinor, List<Share> sources) {
        public Line {
            Objects.requireNonNull(kind);
            sources = List.copyOf(sources);
            check(hourlyRateMinor > 0 && additionalBps >= 0 && minutes > 0 && amountMinor >= 0, "LINE");
            int sum = 0;
            var indexes = new HashSet<Integer>();
            for (Share share : sources) {
                check(indexes.add(share.sourcePieceIndex()), "DUPLICATE_SHARE");
                sum = Math.addExact(sum, share.minutes());
            }
            check(sum == minutes, "LINE_MINUTES");
        }
    }
    public record Document(String schema, String scope, String roundingContract, Long snapshotId,
            String calculationHash, String authorityFingerprint, String currencyCode, int pieceCount,
            long qualifiedMinutes, long statutoryTariffReferenceMinor, long configuredLocalReferenceMinor,
            boolean finalPayableReady, List<String> activationBlockers, List<Line> lines) {
        public Document {
            check(SCHEMA.equals(schema) && "TARIFF_REFERENCE_ONLY".equals(scope)
                    && "PAY_PRICING_ENGINE_BPS_HALF_UP_V1".equals(roundingContract), "SCHEMA");
            check(snapshotId != null && snapshotId > 0 && hash(calculationHash) && hash(authorityFingerprint)
                    && currencyCode != null && currencyCode.matches("[A-Z]{3}")
                    && pieceCount >= 0 && qualifiedMinutes >= 0, "IDENTITY");
            activationBlockers = List.copyOf(activationBlockers); lines = List.copyOf(lines);
            check(!finalPayableReady && activationBlockers.containsAll(List.of("FINAL_PAYROLL_INTEGRATION_REQUIRED",
                    "REMUNERATION_COMPLETENESS_REQUIRED", "LEGACY_HOLIDAY_RECONCILIATION_REQUIRED")), "ACTIVATION");
            for (Kind kind : Kind.values()) {
                long amount = 0, minutes = 0;
                var indexes = new HashSet<Integer>();
                for (Line line : lines) if (line.kind() == kind) {
                    amount = Math.addExact(amount, line.amountMinor());
                    minutes = Math.addExact(minutes, line.minutes());
                    for (Share share : line.sources()) {
                        check(share.sourcePieceIndex() < pieceCount && indexes.add(share.sourcePieceIndex()), "SOURCE_COVERAGE");
                    }
                }
                check(indexes.size() == pieceCount && minutes == qualifiedMinutes, "QUANTITY");
                check(amount == (kind == Kind.STATUTORY_TARIFF_REFERENCE
                        ? statutoryTariffReferenceMinor : configuredLocalReferenceMinor), "TOTAL");
            }
        }
    }
    public static String encode(Document document) {
        try { return JSON.writeValueAsString(document); }
        catch (Exception e) { throw new IllegalStateException("ARTICLE153_TARIFF_ENCODING", e); }
    }
    public static Document read(PayrollSnapshotArticle153Tariff row) {
        check(SCHEMA.equals(row.getSchemaVersion()), "SCHEMA");
        check(Article153SnapshotCodec.fingerprint(row.getPayloadJson()).equals(row.getFingerprint()), "FINGERPRINT");
        var authority = row.getAuthority();
        JsonNode source = Article153SnapshotCodec.read(authority);
        try {
            Document doc = JSON.readValue(row.getPayloadJson(), Document.class);
            check(doc.snapshotId().equals(authority.getSnapshot().getId())
                    && doc.calculationHash().equals(authority.getSnapshot().getCalculationHash())
                    && doc.authorityFingerprint().equals(authority.getFingerprint())
                    && doc.currencyCode().equals(authority.getSnapshot().getCurrencyCode())
                    && doc.pieceCount() == authority.getPieceCount()
                    && doc.qualifiedMinutes() == authority.getQualifiedMinutes(), "BINDING");
            for (Line line : doc.lines()) for (Share share : line.sources()) {
                JsonNode piece = source.path("pieces").get(share.sourcePieceIndex());
                check(share.minutes() == Article153TariffValuationService.number(piece.at("/norm/qualifiedPiece/sourcePiece/minutes"))
                        && line.hourlyRateMinor() == Article153TariffValuationService.number(piece.at("/historicalRate/baseHourlyRateMinor"))
                        && line.additionalBps() == Article153TariffValuationService.number(piece.at(line.kind() == Kind.STATUTORY_TARIFF_REFERENCE
                            ? "/statutoryFloor/additionalTariffBps" : "/localRate/configuredAdditionalTariffBps")), "LINE_BINDING");
            }
            return doc;
        } catch (Exception e) { throw new IllegalStateException("ARTICLE153_TARIFF_INVALID", e); }
    }
    private static boolean hash(String text) { return text != null && text.matches("[0-9a-f]{64}"); }
    static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("ARTICLE153_TARIFF_" + reason);
    }
}

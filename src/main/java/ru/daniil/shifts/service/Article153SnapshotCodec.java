package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotArticle153;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** V1 wire format. Reads never execute current legal or pricing policy. */
public final class Article153SnapshotCodec {
    public static final String SCHEMA = "ARTICLE153_AUTHORITY_SNAPSHOT_V1";
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    private Article153SnapshotCodec() {}
    static ObjectNode object() { return JSON.createObjectNode(); }
    static JsonNode value(Object value) { return JSON.valueToTree(value); }
    static String encode(ObjectNode document) {
        try { return JSON.writeValueAsString(document); }
        catch (Exception e) { throw new IllegalStateException("ARTICLE153_SNAPSHOT_ENCODING", e); }
    }
    static JsonNode tree(String json) {
        try { return JSON.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("ARTICLE153_SNAPSHOT_INVALID", e); }
    }
    public static String fingerprint(String json) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static JsonNode read(PayrollSnapshotArticle153 row) {
        require(SCHEMA.equals(row.getSchemaVersion()), "SCHEMA");
        require(fingerprint(row.getPayloadJson()).equals(row.getFingerprint()), "FINGERPRINT");
        try {
            JsonNode doc = JSON.readTree(row.getPayloadJson());
            require(doc != null && doc.isObject() && SCHEMA.equals(doc.path("schema").asText()), "SCHEMA");
            PayrollSnapshot snapshot = row.getSnapshot();
            JsonNode header = doc.path("snapshot");
            require(header.path("snapshotId").asLong(-1) == snapshot.getId()
                    && header.path("ownerId").asLong(-1) == snapshot.getOwner().getId()
                    && header.path("revision").asInt(-1) == snapshot.getRevision()
                    && header.path("periodMonth").asText().equals(snapshot.getPeriodMonth().toString())
                    && header.path("calculationHash").asText().equals(snapshot.getCalculationHash())
                    && header.path("currencyCode").asText().equals(snapshot.getCurrencyCode())
                    && sameSqlInstant(header.path("sourcePeriodClosedAt").asText(), snapshot.getSourcePeriodClosedAt())
                    && sameSqlInstant(header.path("sourceIntegrityCheckedAt").asText(), snapshot.getSourceIntegrityCheckedAt()), "BINDING");
            require("AUTHORITY_ONLY".equals(doc.path("scope").asText()), "SCOPE");
            JsonNode pieces = doc.path("pieces");
            require(pieces.isArray() && pieces.size() == row.getPieceCount(), "COUNT");
            long sum = 0;
            for (JsonNode piece : pieces) {
                long minutes = piece.path("norm").path("qualifiedPiece").path("sourcePiece").path("minutes").asLong(-1);
                require(minutes > 0, "MINUTES");
                sum = Math.addExact(sum, minutes);
                require(piece.path("statutoryFloor").isObject() && piece.path("compensationTerm").isObject()
                        && piece.path("historicalRate").isObject() && piece.path("election").isObject() && piece.path("localRate").isObject()
                        && piece.path("components").isObject() && piece.path("pricingRules").isArray(), "CONTENT");
            }
            require(sum == row.getQualifiedMinutes() && doc.path("qualifiedMinutes").asLong(-1) == sum, "MINUTES");
            return doc;
        } catch (Exception e) { throw new IllegalStateException("ARTICLE153_SNAPSHOT_INVALID", e); }
    }
    // PostgreSQL/H2 timestamp(6) rounds Java nanoseconds to microseconds.
    // Preserve the original evidence text while comparing at database precision.
    private static boolean sameSqlInstant(String text, java.time.Instant stored) {
        return java.time.Instant.parse(text).plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                .equals(stored.plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }
    static void require(boolean test, String reason) {
        if (!test) throw new IllegalStateException("ARTICLE153_SNAPSHOT_" + reason);
    }
}

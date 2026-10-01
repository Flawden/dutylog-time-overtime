package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.Objects;

/** Reviewed classification of one exact compensation version; never inferred from its name. */
@Entity
@Immutable
@Table(name = "article153_component_authorities",
        uniqueConstraints = @UniqueConstraint(name = "uq_article153_component_version",
                columnNames = "component_version_id"))
public class Article153ComponentAuthority {
    public enum Decision { INCLUDE, EXCLUDE, UNCLASSIFIED }
    public enum SourceKind { COLLECTIVE_AGREEMENT, LOCAL_NORMATIVE_ACT, EMPLOYMENT_CONTRACT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_version_id", nullable = false, updatable = false)
    private CompensationComponentVersion componentVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Decision decision;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 40, updatable = false)
    private SourceKind sourceKind;
    @Column(name = "source_reference", nullable = false, length = 500, updatable = false)
    private String sourceReference;
    @Column(name = "source_revision", nullable = false, length = 160, updatable = false)
    private String sourceRevision;
    @Column(name = "classification_basis", nullable = false, length = 2000, updatable = false)
    private String classificationBasis;
    @Column(name = "component_fingerprint", nullable = false, length = 64, updatable = false)
    private String componentFingerprint;
    @Column(name = "certified_at", nullable = false, updatable = false)
    private Instant certifiedAt;

    protected Article153ComponentAuthority() {}

    public Article153ComponentAuthority(CompensationComponentVersion version, Decision decision,
            SourceKind sourceKind, String reference, String revision, String basis,
            String fingerprint, Instant certifiedAt) {
        this.componentVersion = Objects.requireNonNull(version, "Component version is required");
        this.decision = Objects.requireNonNull(decision, "Article 153 decision is required");
        this.sourceKind = Objects.requireNonNull(sourceKind, "Article 153 source kind is required");
        this.sourceReference = requireText(reference, 500);
        this.sourceRevision = requireText(revision, 160);
        this.classificationBasis = requireText(basis, 2000);
        this.componentFingerprint = Objects.requireNonNull(fingerprint);
        this.certifiedAt = Objects.requireNonNull(certifiedAt).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        validate();
    }

    @PrePersist
    public void validate() {
        Objects.requireNonNull(componentVersion);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(sourceKind);
        Objects.requireNonNull(certifiedAt);
        requireText(sourceReference, 500);
        requireText(sourceRevision, 160);
        requireText(classificationBasis, 2000);
        if (componentFingerprint == null || !componentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Article 153 component fingerprint must be SHA-256");
        }
    }

    public static String requireText(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException("Article 153 source text must contain 1.." + max + " characters");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public CompensationComponentVersion getComponentVersion() { return componentVersion; }
    public Decision getDecision() { return decision; }
    public SourceKind getSourceKind() { return sourceKind; }
    public String getSourceReference() { return sourceReference; }
    public String getSourceRevision() { return sourceRevision; }
    public String getClassificationBasis() { return classificationBasis; }
    public String getComponentFingerprint() { return componentFingerprint; }
    public Instant getCertifiedAt() { return certifiedAt; }
}

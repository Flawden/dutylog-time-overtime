package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Append-only monthly source review; corrections create a new revision. */
@Entity
@Immutable
@Table(name="article153_remuneration_authorities", uniqueConstraints=@UniqueConstraint(
        name="uq_article153_remuneration_revision", columnNames={"owner_id","period_month","revision"}))
public class Article153RemunerationAuthority {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="owner_id", nullable=false, updatable=false) private AppUser owner;
    @Column(name="period_month",nullable=false,updatable=false) private LocalDate periodMonth;
    @Column(nullable=false,updatable=false) private int revision;
    @Column(name="payload_json",nullable=false,updatable=false,columnDefinition="TEXT") private String payloadJson;
    @Column(nullable=false,updatable=false,length=64) private String fingerprint;
    @Column(name="certified_at",nullable=false,updatable=false) private Instant certifiedAt;
    protected Article153RemunerationAuthority() {}
    public Article153RemunerationAuthority(AppUser owner,LocalDate month,int revision,String json,String hash) {
        this.owner=owner;this.periodMonth=month;this.revision=revision;this.payloadJson=json;
        this.fingerprint=hash;this.certifiedAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);validate();
    }
    @PrePersist public void validate() {
        Objects.requireNonNull(owner);Objects.requireNonNull(periodMonth);Objects.requireNonNull(certifiedAt);
        if(owner.getId()==null || owner.getId()<=0 || periodMonth.getDayOfMonth()!=1 || revision<1
                || payloadJson==null || payloadJson.isBlank() || fingerprint==null || !fingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("ARTICLE153_REMUNERATION_ROW_INVALID");
    }
    public Long getId(){return id;} public AppUser getOwner(){return owner;}
    public LocalDate getPeriodMonth(){return periodMonth;} public int getRevision(){return revision;}
    public String getPayloadJson(){return payloadJson;} public String getFingerprint(){return fingerprint;}
    public Instant getCertifiedAt(){return certifiedAt;}
}

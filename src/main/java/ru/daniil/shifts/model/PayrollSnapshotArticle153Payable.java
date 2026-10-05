package ru.daniil.shifts.model;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.util.Objects;
@Entity @Immutable @Table(name="payroll_snapshot_article153_payable")
public class PayrollSnapshotArticle153Payable {
    @Id @Column(name="snapshot_id") private Long snapshotId;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @MapsId @JoinColumn(name="snapshot_id",nullable=false,updatable=false)
    private PayrollSnapshotArticle153Remuneration remuneration;
    @Column(name="schema_version",nullable=false,updatable=false,length=40) private String schemaVersion;
    @Column(name="payload_json",nullable=false,updatable=false,columnDefinition="text") private String payloadJson;
    @Column(nullable=false,updatable=false,length=64) private String fingerprint;
    protected PayrollSnapshotArticle153Payable(){}
    public PayrollSnapshotArticle153Payable(PayrollSnapshotArticle153Remuneration remuneration,String schema,String json,String hash){
        this.remuneration=Objects.requireNonNull(remuneration);
        if(schema==null||schema.isBlank()||schema.length()>40||json==null||json.isBlank()||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid Article 153 payable document");
        schemaVersion=schema;payloadJson=json;fingerprint=hash;
    }
    public Long getSnapshotId(){return snapshotId;}
    public PayrollSnapshotArticle153Remuneration getRemuneration(){return remuneration;}
    public String getSchemaVersion(){return schemaVersion;}
    public String getPayloadJson(){return payloadJson;}
    public String getFingerprint(){return fingerprint;}
}

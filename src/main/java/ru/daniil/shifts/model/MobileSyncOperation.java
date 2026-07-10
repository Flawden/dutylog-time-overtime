package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Durable idempotency record for Android offline queue operations.
 * Payloads and personal content are intentionally not stored here.
 */
@Entity
@Table(name = "mobile_sync_operations",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_sync_owner_operation", columnNames = {"user_id", "operation_id"}),
        indexes = {
                @Index(name = "idx_mobile_sync_owner_created", columnList = "user_id,created_at"),
                @Index(name = "idx_mobile_sync_operation_id", columnList = "operation_id")
        })
public class MobileSyncOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "operation_id", nullable = false, length = 64)
    private String operationId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_key", nullable = false, length = 120)
    private String entityKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "server_version")
    private Long serverVersion;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MobileSyncOperation() {}

    public MobileSyncOperation(AppUser owner,
                               String operationId,
                               String entityType,
                               String entityKey,
                               String status,
                               Long serverVersion,
                               String errorCode,
                               String message) {
        this.owner = owner;
        this.operationId = operationId;
        this.entityType = entityType;
        this.entityKey = entityKey;
        this.status = status;
        this.serverVersion = serverVersion;
        this.errorCode = errorCode;
        this.message = message;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getOperationId() { return operationId; }
    public String getEntityType() { return entityType; }
    public String getEntityKey() { return entityKey; }
    public String getStatus() { return status; }
    public Long getServerVersion() { return serverVersion; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}

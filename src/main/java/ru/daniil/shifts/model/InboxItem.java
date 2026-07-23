package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Unstructured quick capture. The user may write first and organise later.
 * Client operation ids make offline retries idempotent.
 */
@Entity
@Table(name = "inbox_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inbox_items_user_client_operation",
                columnNames = {"user_id", "client_operation_id"}),
        indexes = {
                @Index(name = "idx_inbox_items_user_status_created", columnList = "user_id, status, created_at")
        })
public class InboxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InboxItemStatus status = InboxItemStatus.OPEN;

    @Column(name = "client_operation_id", length = 80)
    private String clientOperationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected InboxItem() {}

    public InboxItem(AppUser owner, String text, String clientOperationId) {
        this.owner = owner;
        this.text = text;
        this.clientOperationId = clientOperationId;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public InboxItemStatus getStatus() { return status; }
    public void setStatus(InboxItemStatus status) { this.status = status; }
    public String getClientOperationId() { return clientOperationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}

package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Stable owner-scoped identity of one user-defined compensation component.
 *
 * Business meaning is intentionally absent from this row.
 * Name, formula, value and enabled state belong to effective-dated versions.
 */
@Entity
@Table(
        name = "compensation_components",
        indexes = @Index(
                name = "idx_compensation_components_owner",
                columnList = "user_id, id"
        )
)
public class CompensationComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt = Instant.now();

    protected CompensationComponent() {}

    public CompensationComponent(AppUser owner) {
        if (owner == null) {
            throw new IllegalArgumentException(
                    "Compensation component requires owner"
            );
        }

        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

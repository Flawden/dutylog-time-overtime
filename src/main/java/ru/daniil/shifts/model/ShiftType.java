package ru.daniil.shifts.model;

import jakarta.persistence.*;

/**
 * Тип смены: «Дневная», «Ночная», «12 часов» и т.д.
 * builtin = true у стартовых типов — их фронтенд не даёт удалять.
 */
@Entity
@Table(name = "shift_types")
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    /** Длительность в часах. 0 — например, для выходного. */
    @Column(nullable = false)
    private double hours;

    /** Цвет в формате #RRGGBB. */
    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private boolean builtin = false;

    /** Владелец. У каждого пользователя — свой набор типов смен. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    protected ShiftType() {} // для JPA

    public ShiftType(AppUser owner, String name, double hours, String color, boolean builtin) {
        this.owner = owner;
        this.name = name;
        this.hours = hours;
        this.color = color;
        this.builtin = builtin;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getHours() { return hours; }
    public void setHours(double hours) { this.hours = hours; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isBuiltin() { return builtin; }
    public AppUser getOwner() { return owner; }
}

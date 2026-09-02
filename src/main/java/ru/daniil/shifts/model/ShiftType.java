package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Тип смены: «Дневная», «Ночная», «Выходной» или пользовательская смена.
 *
 * hours — короткое числовое значение для старых экранов и общего подсчёта.
 * plannedHours/startTime/endTime/breakMinutes — более точная модель смены
 * для расчёта переработок, уведомлений и будущего Android-приложения.
 */
@Entity
@Table(name = "shift_types")
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    /** Длительность/план в часах. 0 — например, для выходного. */
    @Column(nullable = false)
    private double hours;

    /** Цвет в формате #RRGGBB. */
    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private boolean builtin = false;

    /** Время начала смены, если известно. Например 06:30 или 20:00. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Время конца смены, если известно. Может быть меньше startTime — значит смена через полночь. */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** Обед/перерыв по умолчанию, в минутах. */
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 0;

    /** Источник истины для положения неоплачиваемых перерывов. */
    @Enumerated(EnumType.STRING)
    @Column(name = "break_authority", nullable = false, length = 32)
    private WorkBreakAuthority breakAuthority = WorkBreakAuthority.LEGACY_EARLY_TOTAL;

    /**
     * Explicit wall-clock break windows. They belong to the template only;
     * concrete dated assignments receive immutable absolute snapshots.
     */
    @OneToMany(mappedBy = "shiftType", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<ShiftTypeBreakWindow> breakWindows = new ArrayList<>();

    /** Плановые оплачиваемые/учитываемые часы. Если null — используем hours. */
    @Column(name = "planned_hours")
    private Double plannedHours;

    /** Можно ли создавать напоминания перед этой сменой. */
    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    /** Переопределение времени напоминания именно для этой смены. null — использовать глобальную настройку. */
    @Column(name = "notification_minutes_before")
    private Integer notificationMinutesBefore;

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
        this.plannedHours = hours;
    }

    public ShiftType(AppUser owner, String name, double hours, String color, boolean builtin,
                     LocalTime startTime, LocalTime endTime, int breakMinutes, Double plannedHours) {
        this(owner, name, hours, color, builtin);
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = Math.max(0, breakMinutes);
        this.plannedHours = plannedHours != null ? plannedHours : hours;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getHours() { return hours; }
    public void setHours(double hours) { this.hours = hours; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = Math.max(0, breakMinutes); }
    public WorkBreakAuthority getBreakAuthority() {
        return breakAuthority == null
                ? WorkBreakAuthority.LEGACY_EARLY_TOTAL
                : breakAuthority;
    }
    public List<ShiftTypeBreakWindow> getBreakWindows() {
        return List.copyOf(breakWindows);
    }
    public void replaceExplicitBreakWindows(List<ShiftTypeBreakWindow> windows) {
        List<ShiftTypeBreakWindow> safe = windows == null ? List.of() : windows.stream()
                .sorted(Comparator.comparingInt(ShiftTypeBreakWindow::getPosition))
                .toList();
        for (ShiftTypeBreakWindow window : safe) {
            if (window == null || window.getShiftType() != this) {
                throw new IllegalArgumentException(
                        "Explicit break window must belong to this shift type"
                );
            }
        }
        breakWindows.clear();
        breakWindows.addAll(safe);
        breakAuthority = WorkBreakAuthority.EXPLICIT_WINDOWS;
        breakMinutes = safe.stream()
                .mapToInt(ShiftTypeBreakWindow::getDurationMinutes)
                .sum();
    }
    public void useLegacyBreakTotal(int minutes) {
        breakWindows.clear();
        breakAuthority = WorkBreakAuthority.LEGACY_EARLY_TOTAL;
        setBreakMinutes(minutes);
    }
    public Double getPlannedHours() { return plannedHours; }
    public void setPlannedHours(Double plannedHours) { this.plannedHours = plannedHours; }
    public double effectivePlannedHours() { return plannedHours != null ? plannedHours : hours; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public Integer getNotificationMinutesBefore() { return notificationMinutesBefore; }
    public void setNotificationMinutesBefore(Integer notificationMinutesBefore) {
        this.notificationMinutesBefore = notificationMinutesBefore != null ? Math.max(0, notificationMinutesBefore) : null;
    }
    public AppUser getOwner() { return owner; }
}

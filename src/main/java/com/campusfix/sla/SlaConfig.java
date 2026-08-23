package com.campusfix.sla;

import com.campusfix.common.model.Auditable;
import com.campusfix.request.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * How long a request of a given priority may take, and when to start warning.
 *
 * <p>The numbers were constants on the {@link Priority} enum until now. They
 * move here because a service target is a college's policy, not a fact about the
 * software — a hostel with two electricians cannot promise the same turnaround
 * as a campus with twenty.
 *
 * <p>Exactly one row per priority. The design document also had an {@code active}
 * flag; it was dropped, because a deactivated SLA row raises a question nobody
 * has an answer to — what is the target then?
 */
@Entity
@Table(name = "sla_configs")
public class SlaConfig extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private Priority priority;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours;

    /**
     * How far into the window a request starts showing as "due soon", as a
     * percentage. 75 means three quarters of the time is gone.
     */
    @Column(name = "warning_percentage", nullable = false)
    private int warningPercentage;

    protected SlaConfig() {
        // required by JPA
    }

    public SlaConfig(Priority priority, int durationHours, int warningPercentage) {
        this.priority = priority;
        this.durationHours = durationHours;
        this.warningPercentage = warningPercentage;
    }

    public Long getId() {
        return id;
    }

    public Priority getPriority() {
        return priority;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public int getWarningPercentage() {
        return warningPercentage;
    }

    public void change(int durationHours, int warningPercentage) {
        this.durationHours = durationHours;
        this.warningPercentage = warningPercentage;
    }
}

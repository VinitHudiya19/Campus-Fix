package com.campusfix.request;

import com.campusfix.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One period during which a technician was responsible for a request.
 *
 * <p>Reassigning does not overwrite anything: the current row is ended by
 * setting {@code unassignedAt}, and a new row begins. A department head can
 * therefore answer "who had this last week, and for how long?" — which is the
 * whole reason this table exists rather than a single column on the request.
 *
 * <p>The design document also listed an {@code active} flag. It was dropped:
 * "active" is exactly "{@code unassignedAt is null}", and storing the same fact
 * twice only creates a way for the two to disagree.
 */
@Entity
@Table(name = "assignments", indexes = {
        @Index(name = "idx_assignment_request", columnList = "request_id"),
        @Index(name = "idx_assignment_technician", columnList = "technician_id")
})
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private User technician;

    /** The department head or admin who made the decision, kept for accountability. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** Why this technician, in the assigner's words. Optional. */
    @Column(length = 255)
    private String note;

    /** Null while this technician is still the one responsible. */
    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    protected Assignment() {
        // required by JPA
    }

    public Assignment(ServiceRequest request, User technician, User assignedBy, Instant assignedAt, String note) {
        this.request = request;
        this.technician = technician;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public User getTechnician() {
        return technician;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public String getNote() {
        return note;
    }

    public Instant getUnassignedAt() {
        return unassignedAt;
    }

    public boolean isActive() {
        return unassignedAt == null;
    }

    void end(Instant at) {
        if (unassignedAt != null) {
            throw new IllegalStateException("Assignment " + id + " has already ended");
        }
        this.unassignedAt = at;
    }
}

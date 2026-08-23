package com.campusfix.request;

import com.campusfix.category.Category;
import com.campusfix.common.model.Auditable;
import com.campusfix.location.Location;
import com.campusfix.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A problem a student has reported. The centre of the whole product.
 */
@Entity
@Table(name = "service_requests", indexes = {
        // Indexes on the columns the list screens actually filter by. Not on
        // everything: each index has to be updated on every write, so an unused
        // one is a permanent cost with no benefit.
        @Index(name = "idx_request_status", columnList = "status"),
        @Index(name = "idx_request_student", columnList = "student_id"),
        @Index(name = "idx_request_category", columnList = "category_id"),
        @Index(name = "idx_request_due_at", columnList = "due_at"),
        @Index(name = "idx_request_technician", columnList = "assigned_technician_id")
})
public class ServiceRequest extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The readable reference a student quotes, e.g. CF-2026-000042. */
    @Column(name = "request_number", unique = true, length = 20)
    private String requestNumber;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Optional: some problems, such as a college-wide outage, have no one place. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    /**
     * When this request becomes late, worked out once at creation from the
     * priority. Stored rather than calculated on read so that changing the SLA
     * policy later does not silently rewrite the deadline of every old request.
     */
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /**
     * Who is responsible <em>right now</em>. The {@code assignments} table holds
     * the full history of who held it and when; this column is the answer to
     * "who has it?", which every list and detail screen asks.
     *
     * <p>It is a deliberate duplication of the newest open row in that table.
     * Deriving it instead would put a subquery into every request query, on the
     * hottest path in the application. Only {@code AssignmentService} writes
     * either, and it writes both inside one transaction, so they cannot drift.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    /** Set when a technician says it is fixed. Cleared if the student reopens it. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Set when the student confirms, or when staff reject. The end of the line. */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** What the technician actually did, in their own words. */
    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    /** Why the request was refused. Required when rejecting, so nobody is left guessing. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected ServiceRequest() {
        // required by JPA
    }

    public ServiceRequest(String title,
                          String description,
                          User student,
                          Category category,
                          Location location,
                          Priority priority,
                          Instant dueAt) {
        this.title = title;
        this.description = description;
        this.student = student;
        this.category = category;
        this.location = location;
        this.priority = priority;
        this.dueAt = dueAt;
        this.status = RequestStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getStudent() {
        return student;
    }

    public Category getCategory() {
        return category;
    }

    public Location getLocation() {
        return location;
    }

    public Priority getPriority() {
        return priority;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public User getAssignedTechnician() {
        return assignedTechnician;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    /**
     * Records the new owner and moves an untouched request to ASSIGNED.
     *
     * <p>A request already IN_PROGRESS or REOPENED keeps its status: handing the
     * work to a different technician does not undo the work already done. The
     * complete set of legal status moves is Phase 8's job; this method only
     * covers the one move that assignment itself causes.
     */
    void assignTo(User technician, Instant at) {
        this.assignedTechnician = technician;
        this.assignedAt = at;
        if (status == RequestStatus.OPEN) {
            status = RequestStatus.ASSIGNED;
        }
    }

    /**
     * Moves the request to a new status and keeps the timestamps that go with it.
     *
     * <p>Whether the move is legal at all, and whether this person may make it,
     * is decided by {@link StatusAction} before this is called. The entity's job
     * is only to stay internally consistent once the decision is made.
     */
    void applyStatus(RequestStatus next, String note, Instant at) {
        switch (next) {
            case RESOLVED -> {
                this.resolvedAt = at;
                this.resolutionNote = note;
            }
            case CLOSED -> this.closedAt = at;
            case REJECTED -> {
                this.closedAt = at;
                this.rejectionReason = note;
            }
            // Reopening undoes the resolution: the problem was never fixed, so
            // leaving resolvedAt set would make the SLA figures claim otherwise.
            case REOPENED -> {
                this.resolvedAt = null;
                this.resolutionNote = null;
            }
            default -> {
                // IN_PROGRESS, ASSIGNED and OPEN carry no extra timestamp.
            }
        }
        this.status = next;
    }

    /** Sends the request back to the unassigned queue. */
    void clearAssignment() {
        this.assignedTechnician = null;
        this.assignedAt = null;
        if (status == RequestStatus.ASSIGNED) {
            status = RequestStatus.OPEN;
        }
    }

    /**
     * Set once, immediately after the insert that produced the id. Refuses to
     * run twice, because a request number that changes is worse than none.
     */
    void assignRequestNumber(String requestNumber) {
        if (this.requestNumber != null) {
            throw new IllegalStateException("Request number is already set on request " + id);
        }
        this.requestNumber = requestNumber;
    }
}

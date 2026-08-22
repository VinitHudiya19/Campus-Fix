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
        @Index(name = "idx_request_due_at", columnList = "due_at")
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

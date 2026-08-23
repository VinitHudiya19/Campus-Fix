package com.campusfix.activity;

import com.campusfix.request.ServiceRequest;
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
 * One line of a request's history.
 *
 * <p>Append only. Nothing in the codebase updates or deletes a row here, which
 * is the point of an audit trail: it is worth nothing if it can be edited after
 * the fact.
 */
@Entity
@Table(name = "activity_logs", indexes = {
        @Index(name = "idx_activity_request", columnList = "request_id")
})
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    /** Null when the system acted on its own, such as an SLA breach. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    @Column(name = "old_value", length = 60)
    private String oldValue;

    @Column(name = "new_value", length = 60)
    private String newValue;

    /** The human sentence shown on the timeline. */
    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityLog() {
        // required by JPA
    }

    public ActivityLog(ServiceRequest request, User actor, ActivityType type,
                       String oldValue, String newValue, String message, Instant createdAt) {
        this.request = request;
        this.actor = actor;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public ActivityType getType() {
        return type;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

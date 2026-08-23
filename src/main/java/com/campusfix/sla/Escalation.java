package com.campusfix.sla;

import com.campusfix.request.ServiceRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A record that a late request was pushed up a level.
 *
 * <p>The unique constraint on (request, level) is what stops the scheduled check
 * escalating the same request every fifteen minutes forever. It is enforced in
 * the database, not only in the code, because the check could in principle run
 * on two instances at once.
 */
@Entity
@Table(name = "escalations",
        uniqueConstraints = @UniqueConstraint(name = "uk_escalation_request_level",
                columnNames = {"request_id", "level"}))
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EscalationLevel level;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Escalation() {
        // required by JPA
    }

    public Escalation(ServiceRequest request, EscalationLevel level, String reason, Instant createdAt) {
        this.request = request;
        this.level = level;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public EscalationLevel getLevel() {
        return level;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

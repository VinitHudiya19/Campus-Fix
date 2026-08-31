package com.campusfix.notification;

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
 * One thing a particular person should know about.
 *
 * <p>Distinct from {@link com.campusfix.activity.ActivityLog}, which is easy to
 * confuse it with. The activity log is the history of a <em>request</em> and
 * everyone who can see the request sees the same entries. A notification belongs
 * to a <em>person</em> and carries a read state — the same event produces one
 * activity entry and zero or more notifications.
 */
@Entity
@Table(name = "notifications", indexes = {
        // The query behind the bell icon is "unread, for me, newest first", so
        // the index covers recipient and read state together.
        @Index(name = "idx_notification_recipient", columnList = "recipient_id, read_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Every notification this application sends is about a request. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    /** Null until the recipient has seen it. */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // required by JPA
    }

    public Notification(User recipient, ServiceRequest request, NotificationType type,
                        String message, Instant createdAt) {
        this.recipient = recipient;
        this.request = request;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Marking an already-read notification again must not move its timestamp. */
    void markRead(Instant at) {
        if (readAt == null) {
            this.readAt = at;
        }
    }
}

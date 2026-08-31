package com.campusfix.notification.channel;

import com.campusfix.notification.Notification;
import com.campusfix.notification.NotificationRepository;
import com.campusfix.notification.NotificationType;
import com.campusfix.request.ServiceRequest;
import com.campusfix.user.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes the notification to the database, which is what the bell icon reads.
 *
 * <p>Always active, and ordered first: this is the channel the product actually
 * depends on. Email is a copy sent somewhere else, and its absence — or failure
 * — must never mean the recipient was not told.
 */
@Component
@Order(1)
public class InAppChannel implements NotificationChannel {

    private final NotificationRepository repository;
    private final Clock clock;

    public InAppChannel(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deliver(User recipient, ServiceRequest request, NotificationType type, String message) {
        repository.save(new Notification(recipient, request, type, message, clock.instant()));
    }

    @Override
    public String describe() {
        return "in-app";
    }
}

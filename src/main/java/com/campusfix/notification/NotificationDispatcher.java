package com.campusfix.notification;

import com.campusfix.notification.channel.NotificationChannel;
import com.campusfix.request.RequestStatus;
import com.campusfix.request.ServiceRequest;
import com.campusfix.request.ServiceRequestRepository;
import com.campusfix.request.event.RequestAssignedEvent;
import com.campusfix.request.event.RequestStatusChangedEvent;
import com.campusfix.sla.EscalationLevel;
import com.campusfix.sla.event.RequestEscalatedEvent;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * Turns something that happened into notifications for the people who care.
 *
 * <p>Two annotations do the real work here, and both are there for a reason.
 *
 * <p><b>{@code @TransactionalEventListener(AFTER_COMMIT)}</b> — the handler runs
 * only if the transaction that published the event actually committed. A plain
 * {@code @EventListener} runs inside it, so a later failure and rollback would
 * leave people told about a change that never happened. Emails cannot be
 * un-sent.
 *
 * <p><b>{@code @Async}</b> — delivery happens on a small pool rather than the
 * request thread. Without it, a slow mail server adds its latency to the API
 * response that triggered it, and the person who clicked "Resolve" waits for
 * somebody else's inbox.
 *
 * <p><b>{@code REQUIRES_NEW}</b> — a fresh transaction for the writes these
 * handlers do. Spring rejects {@code @TransactionalEventListener} combined with
 * a plain {@code @Transactional} outright, and it is right to: the original
 * transaction has already committed by now, so "join the current one" describes
 * something that no longer exists.
 *
 * <p>The consequence of all this is worth being clear about: by the time these
 * run the original transaction is over, so a failure here cannot roll anything
 * back. A lost notification is the accepted cost, and it is logged rather than
 * silent.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public NotificationDispatcher(ServiceRequestRepository requestRepository,
                                  UserRepository userRepository,
                                  List<NotificationChannel> channels) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.channels = channels;
    }

    @PostConstruct
    void logActiveChannels() {
        log.info("Notification channels active: {}",
                channels.stream().map(NotificationChannel::describe).toList());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAssigned(RequestAssignedEvent event) {
        deliver(event.requestId(), event.technicianId(), NotificationType.ASSIGNED,
                "%s assigned this request to you.".formatted(event.assignedByName()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(RequestStatusChangedEvent event) {
        ServiceRequest request = requestRepository.findByIdWithDetail(event.requestId()).orElse(null);
        if (request == null) {
            return;
        }

        // Who needs to know depends on which way the request moved. Nobody is
        // told about their own action — the person who clicked the button
        // already knows.
        switch (event.to()) {
            case RESOLVED -> notifyReporter(request, event, NotificationType.RESOLVED,
                    "%s marked your request as resolved. Please confirm it is fixed, or reopen it."
                            .formatted(event.actorName()));

            case REJECTED -> notifyReporter(request, event, NotificationType.REJECTED,
                    "%s rejected your request%s".formatted(event.actorName(), reason(event)));

            case REOPENED -> notifyAssignee(request, event, NotificationType.REOPENED,
                    "%s says this is still not fixed%s".formatted(event.actorName(), reason(event)));

            case CLOSED -> notifyAssignee(request, event, NotificationType.CLOSED,
                    "%s confirmed your fix. This request is closed.".formatted(event.actorName()));

            default -> {
                // OPEN, ASSIGNED and IN_PROGRESS produce no notification.
                // Starting work is not news to anyone, and assignment is
                // announced by its own event.
            }
        }
    }

    /**
     * An escalation has no actor — the scheduled check found it. It goes to the
     * people who can do something: the department's heads first, then the
     * administrators.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEscalated(RequestEscalatedEvent event) {
        ServiceRequest request = requestRepository.findByIdWithDetail(event.requestId()).orElse(null);
        if (request == null) {
            return;
        }

        Long departmentId = request.getCategory().getDepartment().getId();
        List<User> recipients = event.level() == EscalationLevel.DEPARTMENT_HEAD
                ? userRepository.search(Role.DEPARTMENT_HEAD, departmentId, true)
                : userRepository.search(Role.ADMIN, null, true);

        String message = "This request passed its deadline and is still unresolved. %s"
                .formatted(event.reason());

        recipients.forEach(recipient ->
                send(recipient, request, NotificationType.ESCALATED, message));
    }

    private void notifyReporter(ServiceRequest request, RequestStatusChangedEvent event,
                                NotificationType type, String message) {
        User reporter = request.getStudent();
        if (!reporter.getId().equals(event.actorId())) {
            send(reporter, request, type, message);
        }
    }

    private void notifyAssignee(ServiceRequest request, RequestStatusChangedEvent event,
                                NotificationType type, String message) {
        User assignee = request.getAssignedTechnician();
        if (assignee != null && !assignee.getId().equals(event.actorId())) {
            send(assignee, request, type, message);
        }
    }

    private void deliver(Long requestId, Long recipientId, NotificationType type, String message) {
        Optional<ServiceRequest> request = requestRepository.findByIdWithDetail(requestId);
        Optional<User> recipient = userRepository.findById(recipientId);

        if (request.isPresent() && recipient.isPresent()) {
            send(recipient.get(), request.get(), type, message);
        }
    }

    /**
     * Each channel is attempted independently. One failing — a mail server that
     * is down — must not stop the others, because the in-app record is the one
     * the product actually relies on.
     */
    private void send(User recipient, ServiceRequest request, NotificationType type, String message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(recipient, request, type, message);
            } catch (RuntimeException e) {
                log.error("Channel {} failed to notify {} about request {}",
                        channel.describe(), recipient.getEmail(), request.getRequestNumber(), e);
            }
        }
    }

    private String reason(RequestStatusChangedEvent event) {
        return event.note() == null ? "." : ": " + event.note();
    }
}

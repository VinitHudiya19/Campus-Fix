package com.campusfix.notification;

import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.notification.dto.NotificationResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Reading and clearing your own notifications.
 *
 * <p>Every method works from the signed-in user's id rather than accepting one,
 * so there is no endpoint that could be pointed at somebody else's bell.
 */
@Service
public class NotificationService {

    /** The bell shows a short list; the full history is not a feature anyone asked for. */
    private static final int RECENT_LIMIT = 20;

    private final NotificationRepository repository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, CurrentUser currentUser, Clock clock) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent() {
        return repository.findForRecipient(currentUser.require().id(), PageRequest.of(0, RECENT_LIMIT))
                .map(NotificationResponse::from)
                .getContent();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByRecipientIdAndReadAtIsNull(currentUser.require().id());
    }

    /**
     * Looked up by id <em>and</em> recipient. Fetching by id alone and then
     * checking the owner is the same thing until somebody edits it later and
     * forgets the check; this way the query cannot return someone else's row.
     */
    @Transactional
    public void markRead(Long id) {
        Long recipientId = currentUser.require().id();
        repository.findByIdAndRecipientId(id, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id))
                .markRead(clock.instant());
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUser.require().id(), clock.instant());
    }
}

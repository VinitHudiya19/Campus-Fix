package com.campusfix.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The bell icon polls this, so it has to stay cheap — a count over the
     * covering index rather than loading rows to size a list.
     */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Query("""
            select n from Notification n
            join fetch n.request r
            where n.recipient.id = :recipientId
            order by n.createdAt desc
            """)
    Page<Notification> findForRecipient(@Param("recipientId") Long recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    /**
     * "Mark all as read" as one statement. Loading every unread row to set one
     * field on each would be a query plus an update per notification.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}

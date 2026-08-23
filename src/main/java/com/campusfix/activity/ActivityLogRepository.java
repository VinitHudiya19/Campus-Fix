package com.campusfix.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Oldest first: a timeline reads like a story, from the report to now.
     * The actor is left-joined because system events have none.
     */
    @Query("""
            select a from ActivityLog a
            left join fetch a.actor
            where a.request.id = :requestId
            order by a.createdAt asc, a.id asc
            """)
    List<ActivityLog> findTimeline(@Param("requestId") Long requestId);
}

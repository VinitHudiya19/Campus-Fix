package com.campusfix.sla;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    boolean existsByRequestIdAndLevel(Long requestId, EscalationLevel level);

    @Query("""
            select e from Escalation e
            where e.request.id = :requestId
            order by e.createdAt asc, e.id asc
            """)
    List<Escalation> findForRequest(@Param("requestId") Long requestId);
}

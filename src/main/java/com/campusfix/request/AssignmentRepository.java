package com.campusfix.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** The technician currently responsible, if any. */
    @Query("""
            select a from Assignment a
            join fetch a.technician
            join fetch a.assignedBy
            where a.request.id = :requestId and a.unassignedAt is null
            """)
    Optional<Assignment> findActiveForRequest(@Param("requestId") Long requestId);

    /** Full history, newest first, for the request detail page. */
    @Query("""
            select a from Assignment a
            join fetch a.technician
            join fetch a.assignedBy
            where a.request.id = :requestId
            order by a.assignedAt desc
            """)
    List<Assignment> findHistoryForRequest(@Param("requestId") Long requestId);

    /** How much work a technician currently holds, used to inform the assigner. */
    @Query("""
            select count(a) from Assignment a
            where a.technician.id = :technicianId
              and a.unassignedAt is null
              and a.request.status not in (com.campusfix.request.RequestStatus.CLOSED,
                                           com.campusfix.request.RequestStatus.REJECTED)
            """)
    long countOpenWorkFor(@Param("technicianId") Long technicianId);
}

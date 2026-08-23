package com.campusfix.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    /**
     * One query for every list screen in the product.
     *
     * <p>{@code studentId}, {@code departmentId} and {@code technicianId} are not
     * user-supplied filters — they are how visibility is enforced. The caller's
     * own id is pushed in here so the database never returns anyone else's rows,
     * rather than fetching everything and filtering in Java where a single
     * forgotten condition leaks the whole table.
     *
     * <p>The joins are fetched because every row on screen shows the category,
     * the reporter and the assignee. Without them this would be the N+1 problem
     * again, at twenty rows a page.
     */
    @Query(value = """
            select r from ServiceRequest r
            join fetch r.category c
            join fetch c.department d
            join fetch r.student s
            left join fetch r.location l
            left join fetch r.assignedTechnician t
            where (:studentId is null or s.id = :studentId)
              and (:departmentId is null or d.id = :departmentId)
              and (:technicianId is null or t.id = :technicianId)
              and (:status is null or r.status = :status)
              and (:categoryId is null or c.id = :categoryId)
              and (:priority is null or r.priority = :priority)
              and (:unassignedOnly = false or r.assignedTechnician is null)
            """,
            countQuery = """
            select count(r) from ServiceRequest r
            where (:studentId is null or r.student.id = :studentId)
              and (:departmentId is null or r.category.department.id = :departmentId)
              and (:technicianId is null or r.assignedTechnician.id = :technicianId)
              and (:status is null or r.status = :status)
              and (:categoryId is null or r.category.id = :categoryId)
              and (:priority is null or r.priority = :priority)
              and (:unassignedOnly = false or r.assignedTechnician is null)
            """)
    Page<ServiceRequest> search(@Param("studentId") Long studentId,
                                @Param("departmentId") Long departmentId,
                                @Param("technicianId") Long technicianId,
                                @Param("status") RequestStatus status,
                                @Param("categoryId") Long categoryId,
                                @Param("priority") Priority priority,
                                @Param("unassignedOnly") boolean unassignedOnly,
                                Pageable pageable);

    @Query("""
            select r from ServiceRequest r
            join fetch r.category c
            join fetch c.department d
            join fetch r.student s
            left join fetch r.location l
            left join fetch r.assignedTechnician t
            where r.id = :id
            """)
    Optional<ServiceRequest> findByIdWithDetail(@Param("id") Long id);

    Optional<ServiceRequest> findByRequestNumber(String requestNumber);

    /**
     * Past its deadline and still genuinely unfinished.
     *
     * <p>RESOLVED is excluded as well as CLOSED and REJECTED: the technician has
     * done the work and it is the student's turn, so chasing the department
     * would be chasing the wrong people. It is still recorded as a missed target.
     */
    @Query("""
            select r from ServiceRequest r
            join fetch r.category c
            join fetch c.department d
            where r.dueAt < :now
              and r.status not in (com.campusfix.request.RequestStatus.RESOLVED,
                                   com.campusfix.request.RequestStatus.CLOSED,
                                   com.campusfix.request.RequestStatus.REJECTED)
            order by r.dueAt asc
            """)
    List<ServiceRequest> findOverdue(@Param("now") Instant now);
}

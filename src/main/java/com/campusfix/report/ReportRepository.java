package com.campusfix.report;

import com.campusfix.request.ServiceRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Read-only aggregation queries for the reports screen.
 *
 * <p>Extends {@code Repository} rather than {@code JpaRepository} on purpose:
 * nothing here should be able to save or delete a request. The interface offers
 * exactly the five questions the reports screen asks and nothing else.
 *
 * <p>Every query takes {@code departmentId}. It is not a user-supplied filter —
 * it is how a department head is confined to their own department, using the
 * same "push the scope into the WHERE clause" approach as the request list.
 */
public interface ReportRepository extends Repository<ServiceRequest, Long> {

    /**
     * How many requests each department has in each status.
     *
     * <p>Grouped in the database. Fetching every request and counting them in
     * Java would move the whole table across the wire to produce twenty numbers.
     */
    @Query("""
            select new com.campusfix.report.StatusTally(d.id, d.name, r.status, count(r))
            from ServiceRequest r
            join r.category c
            join c.department d
            where r.createdAt >= :since
              and (:departmentId is null or d.id = :departmentId)
            group by d.id, d.name, r.status
            """)
    List<StatusTally> tallyByStatus(@Param("since") Instant since,
                                    @Param("departmentId") Long departmentId);

    /**
     * The raw dates of every request that has been fixed, so the service can work
     * out whether each one met its deadline and how long it took.
     *
     * <p>The dates come back rather than a computed average because subtracting
     * two timestamps is not portable JPQL — MySQL spells it {@code TIMESTAMPDIFF}
     * and H2 spells it {@code DATEDIFF}, so a native query would tie the reports
     * to one database and break the H2-backed tests. At a college's volume this
     * is a few thousand rows of two dates; if that ever stopped being true, this
     * is the query to move into SQL.
     */
    @Query("""
            select new com.campusfix.report.ResolutionFact(d.id, r.createdAt, r.resolvedAt, r.dueAt)
            from ServiceRequest r
            join r.category c
            join c.department d
            where r.createdAt >= :since
              and r.resolvedAt is not null
              and (:departmentId is null or d.id = :departmentId)
            """)
    List<ResolutionFact> resolutionFacts(@Param("since") Instant since,
                                         @Param("departmentId") Long departmentId);

    /**
     * Requests that are late <em>right now</em> — regardless of when they were
     * reported, because a request that breached two months ago and is still open
     * is exactly the thing a head needs to see.
     */
    @Query("""
            select new com.campusfix.report.CountByDepartment(d.id, count(r))
            from ServiceRequest r
            join r.category c
            join c.department d
            where r.dueAt < :now
              and r.status not in (com.campusfix.request.RequestStatus.RESOLVED,
                                   com.campusfix.request.RequestStatus.CLOSED,
                                   com.campusfix.request.RequestStatus.REJECTED)
              and (:departmentId is null or d.id = :departmentId)
            group by d.id
            """)
    List<CountByDepartment> breachedNow(@Param("now") Instant now,
                                        @Param("departmentId") Long departmentId);

    /**
     * How many times a request came back after being called fixed.
     *
     * <p>Counted from the activity log, not from the current status: a request
     * reopened and then properly fixed reads as CLOSED today, so counting
     * statuses would report zero reopens on exactly the requests that had them.
     * This is the audit trail earning its keep.
     */
    @Query("""
            select new com.campusfix.report.CountByDepartment(d.id, count(a))
            from ActivityLog a
            join a.request r
            join r.category c
            join c.department d
            where a.newValue = 'REOPENED'
              and a.createdAt >= :since
              and (:departmentId is null or d.id = :departmentId)
            group by d.id
            """)
    List<CountByDepartment> reopenCounts(@Param("since") Instant since,
                                         @Param("departmentId") Long departmentId);

    /** Which problems actually happen most, for the admin deciding where to spend. */
    @Query("""
            select new com.campusfix.report.CategoryTally(c.name, d.name, count(r))
            from ServiceRequest r
            join r.category c
            join c.department d
            where r.createdAt >= :since
              and (:departmentId is null or d.id = :departmentId)
            group by c.name, d.name
            order by count(r) desc
            """)
    List<CategoryTally> volumeByCategory(@Param("since") Instant since,
                                         @Param("departmentId") Long departmentId);
}

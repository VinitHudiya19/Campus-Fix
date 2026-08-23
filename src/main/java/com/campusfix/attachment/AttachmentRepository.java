package com.campusfix.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("""
            select a from Attachment a
            join fetch a.uploadedBy
            where a.request.id = :requestId
            order by a.createdAt asc
            """)
    List<Attachment> findForRequest(@Param("requestId") Long requestId);

    long countByRequestId(Long requestId);

    /**
     * Loads the attachment together with its request, because every caller has
     * to check whether the person asking is allowed to see that request.
     */
    @Query("""
            select a from Attachment a
            join fetch a.request r
            join fetch r.category c
            join fetch c.department
            join fetch r.student
            left join fetch r.assignedTechnician
            where a.id = :id
            """)
    Optional<Attachment> findByIdWithRequest(@Param("id") Long id);
}

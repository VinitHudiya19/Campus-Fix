package com.campusfix.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByDepartmentIdAndActiveTrue(Long departmentId);

    Optional<Category> findByNameIgnoreCaseAndDepartmentId(String name, Long departmentId);

    /**
     * Loads the department in the same query. Without the join fetch, rendering a
     * list of categories with their department name would run one extra query per
     * row, which is the classic N+1 problem.
     */
    @Query("""
            select c from Category c
            join fetch c.department d
            where (:departmentId is null or d.id = :departmentId)
              and (:activeOnly = false or c.active = true)
            order by d.name asc, c.name asc
            """)
    List<Category> search(@Param("departmentId") Long departmentId,
                          @Param("activeOnly") boolean activeOnly);

    @Query("select c from Category c join fetch c.department where c.id = :id")
    Optional<Category> findByIdWithDepartment(@Param("id") Long id);
}

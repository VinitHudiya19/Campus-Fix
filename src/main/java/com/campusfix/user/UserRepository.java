package com.campusfix.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /** Used by login, which needs the department name for the response. */
    @Query("select u from User u left join fetch u.department where u.email = :email")
    Optional<User> findByEmailWithDepartment(@Param("email") String email);

    boolean existsByDepartmentIdAndActiveTrue(Long departmentId);

    /**
     * One flexible query instead of a method per filter combination. The
     * {@code left join fetch} loads the department in the same round trip and
     * still returns students, who have none.
     */
    @Query("""
            select u from User u
            left join fetch u.department d
            where (:role is null or u.role = :role)
              and (:departmentId is null or d.id = :departmentId)
              and (:activeOnly = false or u.active = true)
            order by u.fullName asc
            """)
    List<User> search(@Param("role") Role role,
                      @Param("departmentId") Long departmentId,
                      @Param("activeOnly") boolean activeOnly);

    @Query("select u from User u left join fetch u.department where u.id = :id")
    Optional<User> findByIdWithDepartment(@Param("id") Long id);
}

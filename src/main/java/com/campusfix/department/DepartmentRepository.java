package com.campusfix.department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();
}

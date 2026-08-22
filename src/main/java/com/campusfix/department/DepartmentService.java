package com.campusfix.department;

import com.campusfix.category.CategoryRepository;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.department.dto.DepartmentRequest;
import com.campusfix.department.dto.DepartmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             CategoryRepository categoryRepository) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> findAll(boolean activeOnly) {
        List<Department> departments = activeOnly
                ? departmentRepository.findByActiveTrueOrderByNameAsc()
                : departmentRepository.findAllByOrderByNameAsc();
        return departments.stream().map(DepartmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse findById(Long id) {
        return DepartmentResponse.from(getOrThrow(id));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A department named '" + name + "' already exists");
        }
        Department department = new Department(name, trimOrNull(request.description()));
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = getOrThrow(id);
        String name = request.name().trim();

        // A department may keep its own name; only a clash with a different row is a conflict.
        departmentRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A department named '" + name + "' already exists");
                });

        department.rename(name);
        department.describe(trimOrNull(request.description()));
        return DepartmentResponse.from(department);
    }

    /**
     * Departments are deactivated, never deleted. Service requests will point at
     * a department for years, and deleting the row would break that history.
     */
    @Transactional
    public void deactivate(Long id) {
        Department department = getOrThrow(id);
        if (categoryRepository.existsByDepartmentIdAndActiveTrue(id)) {
            throw new BusinessRuleException(
                    "Move or deactivate this department's categories before deactivating it");
        }
        department.deactivate();
    }

    @Transactional
    public void activate(Long id) {
        getOrThrow(id).activate();
    }

    private Department getOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

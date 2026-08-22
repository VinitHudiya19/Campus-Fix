package com.campusfix.category;

import com.campusfix.category.dto.CategoryRequest;
import com.campusfix.category.dto.CategoryResponse;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.department.Department;
import com.campusfix.department.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           DepartmentRepository departmentRepository) {
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> search(Long departmentId, boolean activeOnly) {
        return categoryRepository.search(departmentId, activeOnly).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(getOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Department department = activeDepartment(request.departmentId());
        String name = request.name().trim();
        requireNameFreeInDepartment(name, department.getId(), null);

        Category category = new Category(name, trimOrNull(request.description()), department);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getOrThrow(id);
        Department department = activeDepartment(request.departmentId());
        String name = request.name().trim();
        requireNameFreeInDepartment(name, department.getId(), id);

        category.rename(name);
        category.describe(trimOrNull(request.description()));
        category.moveTo(department);
        return CategoryResponse.from(category);
    }

    /**
     * Categories are deactivated, never deleted. Old service requests keep
     * pointing at the category they were reported under, so the row has to stay.
     * A deactivated category simply stops appearing in the student's dropdown.
     */
    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).deactivate();
    }

    @Transactional
    public void activate(Long id) {
        Category category = getOrThrow(id);
        if (!category.getDepartment().isActive()) {
            throw new BusinessRuleException(
                    "Activate the department '" + category.getDepartment().getName() + "' first");
        }
        category.activate();
    }

    private Department activeDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        if (!department.isActive()) {
            throw new BusinessRuleException(
                    "Department '" + department.getName() + "' is inactive and cannot take new categories");
        }
        return department;
    }

    /**
     * The same category name may exist under two different departments, so
     * uniqueness is checked per department rather than globally.
     */
    private void requireNameFreeInDepartment(String name, Long departmentId, Long allowedId) {
        categoryRepository.findByNameIgnoreCaseAndDepartmentId(name, departmentId)
                .filter(existing -> !existing.getId().equals(allowedId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "This department already has a category named '" + name + "'");
                });
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findByIdWithDepartment(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

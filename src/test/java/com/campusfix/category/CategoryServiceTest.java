package com.campusfix.category;

import com.campusfix.category.dto.CategoryRequest;
import com.campusfix.category.dto.CategoryResponse;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.department.Department;
import com.campusfix.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createLinksTheCategoryToItsDepartment() {
        Department department = departmentWithId(1L, "IT Support", true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(categoryRepository.findByNameIgnoreCaseAndDepartmentId("Wi-Fi", 1L)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("  Wi-Fi  ", null, 1L));

        assertThat(response.name()).isEqualTo("Wi-Fi");
        assertThat(response.departmentName()).isEqualTo("IT Support");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsAnUnknownDepartment() {
        when(departmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Wi-Fi", null, 404L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createRejectsAnInactiveDepartment() {
        when(departmentRepository.findById(1L))
                .thenReturn(Optional.of(departmentWithId(1L, "Old Department", false)));

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Wi-Fi", null, 1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void createRejectsADuplicateNameInsideTheSameDepartment() {
        Department department = departmentWithId(1L, "IT Support", true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(categoryRepository.findByNameIgnoreCaseAndDepartmentId("Wi-Fi", 1L))
                .thenReturn(Optional.of(categoryWithId(7L, "Wi-Fi", department)));

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Wi-Fi", null, 1L)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has a category");
    }

    @Test
    void theSameCategoryNameIsAllowedUnderADifferentDepartment() {
        Department electrical = departmentWithId(2L, "Electrical", true);
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(electrical));
        when(categoryRepository.findByNameIgnoreCaseAndDepartmentId("Wiring", 2L)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryRequest("Wiring", null, 2L));

        assertThat(response.departmentName()).isEqualTo("Electrical");
    }

    @Test
    void deactivateKeepsTheRowSoOldRequestsStayReadable() {
        Category category = categoryWithId(7L, "Wi-Fi", departmentWithId(1L, "IT Support", true));
        when(categoryRepository.findByIdWithDepartment(7L)).thenReturn(Optional.of(category));

        categoryService.deactivate(7L);

        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void activateIsBlockedWhileTheDepartmentIsInactive() {
        Category category = categoryWithId(7L, "Wi-Fi", departmentWithId(1L, "Old Department", false));
        when(categoryRepository.findByIdWithDepartment(7L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.activate(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Old Department");
    }

    private Department departmentWithId(Long id, String name, boolean active) {
        Department department = new Department(name, null);
        ReflectionTestUtils.setField(department, "id", id);
        if (!active) {
            department.deactivate();
        }
        return department;
    }

    private Category categoryWithId(Long id, String name, Department department) {
        Category category = new Category(name, null, department);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}

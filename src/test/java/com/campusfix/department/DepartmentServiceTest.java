package com.campusfix.department;

import com.campusfix.category.CategoryRepository;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.department.dto.DepartmentRequest;
import com.campusfix.department.dto.DepartmentResponse;
import com.campusfix.user.UserRepository;
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

/**
 * These tests cover the rules, not the framework. The repositories are mocked so
 * no database is involved and each rule can be checked on its own.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void createTrimsTheNameAndSavesTheDepartment() {
        when(departmentRepository.existsByNameIgnoreCase("IT Support")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(call -> call.getArgument(0));

        DepartmentResponse response = departmentService.create(
                new DepartmentRequest("  IT Support  ", "  Network and computers  "));

        assertThat(response.name()).isEqualTo("IT Support");
        assertThat(response.description()).isEqualTo("Network and computers");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsADuplicateNameRegardlessOfCase() {
        when(departmentRepository.existsByNameIgnoreCase("it support")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.create(new DepartmentRequest("it support", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateLetsADepartmentKeepItsOwnName() {
        Department existing = departmentWithId(1L, "IT Support");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByNameIgnoreCase("IT Support")).thenReturn(Optional.of(existing));

        DepartmentResponse response = departmentService.update(1L, new DepartmentRequest("IT Support", "Updated"));

        assertThat(response.description()).isEqualTo("Updated");
    }

    @Test
    void updateRejectsANameTakenByAnotherDepartment() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(departmentWithId(1L, "IT Support")));
        when(departmentRepository.findByNameIgnoreCase("Electrical"))
                .thenReturn(Optional.of(departmentWithId(2L, "Electrical")));

        assertThatThrownBy(() -> departmentService.update(1L, new DepartmentRequest("Electrical", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivateIsBlockedWhileActiveCategoriesStillPointAtTheDepartment() {
        Department department = departmentWithId(1L, "IT Support");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(categoryRepository.existsByDepartmentIdAndActiveTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categories");

        assertThat(department.isActive()).isTrue();
    }

    @Test
    void deactivateMarksTheDepartmentInactiveWhenNothingDependsOnIt() {
        Department department = departmentWithId(1L, "IT Support");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(categoryRepository.existsByDepartmentIdAndActiveTrue(1L)).thenReturn(false);
        when(userRepository.existsByDepartmentIdAndActiveTrue(1L)).thenReturn(false);

        departmentService.deactivate(1L);

        assertThat(department.isActive()).isFalse();
    }

    @Test
    void findByIdReportsAMissingDepartment() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * Ids are normally assigned by the database. In a unit test there is no
     * database, so the field is set directly to model a row that already exists.
     */
    private Department departmentWithId(Long id, String name) {
        Department department = new Department(name, null);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }
}

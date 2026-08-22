package com.campusfix.user;

import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.department.Department;
import com.campusfix.department.DepartmentRepository;
import com.campusfix.user.dto.CreateUserRequest;
import com.campusfix.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rules that decide whether a user is valid: unique email, and which
 * roles are allowed to belong to a department. The password encoder is real
 * rather than mocked so the test also proves the stored value is a hash.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService() {
        return new UserService(userRepository, departmentRepository, passwordEncoder);
    }

    @Test
    void createNormalisesTheEmailAndStoresOnlyAHashOfThePassword() {
        when(userRepository.existsByEmail("ravi@college.edu")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = userService().create(new CreateUserRequest(
                "  Ravi Kumar  ", "  Ravi@College.edu  ", "student123", Role.STUDENT, null));

        assertThat(response.fullName()).isEqualTo("Ravi Kumar");
        assertThat(response.email()).isEqualTo("ravi@college.edu");
        assertThat(response.departmentId()).isNull();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("student123");
        assertThat(passwordEncoder.matches("student123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createRejectsAnEmailThatIsAlreadyRegistered() {
        when(userRepository.existsByEmail("ravi@college.edu")).thenReturn(true);

        assertThatThrownBy(() -> userService().create(new CreateUserRequest(
                "Ravi Kumar", "Ravi@College.edu", "student123", Role.STUDENT, null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void aTechnicianMustBelongToADepartment() {
        when(userRepository.existsByEmail("tech@college.edu")).thenReturn(false);

        assertThatThrownBy(() -> userService().create(new CreateUserRequest(
                "Amit Sharma", "tech@college.edu", "tech1234", Role.TECHNICIAN, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must belong to a department");
    }

    @Test
    void aStudentCannotBelongToADepartment() {
        when(userRepository.existsByEmail("ravi@college.edu")).thenReturn(false);

        assertThatThrownBy(() -> userService().create(new CreateUserRequest(
                "Ravi Kumar", "ravi@college.edu", "student123", Role.STUDENT, 1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not attached to a department");
    }

    @Test
    void staffCannotBeAddedToAnInactiveDepartment() {
        Department closed = new Department("Old Department", null);
        ReflectionTestUtils.setField(closed, "id", 1L);
        closed.deactivate();
        when(userRepository.existsByEmail("tech@college.edu")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> userService().create(new CreateUserRequest(
                "Amit Sharma", "tech@college.edu", "tech1234", Role.TECHNICIAN, 1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }
}

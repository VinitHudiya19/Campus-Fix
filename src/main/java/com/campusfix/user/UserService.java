package com.campusfix.user;

import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.department.Department;
import com.campusfix.department.DepartmentRepository;
import com.campusfix.user.dto.ChangePasswordRequest;
import com.campusfix.user.dto.CreateUserRequest;
import com.campusfix.user.dto.UpdateUserRequest;
import com.campusfix.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(Role role, Long departmentId, boolean activeOnly) {
        return userRepository.search(role, departmentId, activeOnly).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account already exists for " + email);
        }

        Department department = resolveDepartment(request.role(), request.departmentId());
        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                department);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getOrThrow(id);

        if (isSelf(id) && request.role() != user.getRole()) {
            throw new BusinessRuleException("You cannot change your own role");
        }

        Department department = resolveDepartment(request.role(), request.departmentId());
        user.changeProfile(request.fullName().trim(), request.role(), department);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        getOrThrow(id).changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * Users are deactivated, never deleted. Every request, comment and assignment
     * they created still refers to them, so removing the row would leave the
     * history full of dangling references.
     */
    @Transactional
    public void deactivate(Long id) {
        if (isSelf(id)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        getOrThrow(id).deactivate();
    }

    @Transactional
    public void activate(Long id) {
        User user = getOrThrow(id);
        if (user.getRole().isDepartmentRequired()
                && (user.getDepartment() == null || !user.getDepartment().isActive())) {
            throw new BusinessRuleException(
                    "This user's department is inactive. Reactivate the department first.");
        }
        user.activate();
    }

    /**
     * Applies the one rule that ties users to departments: staff who actually do
     * the work must sit in a department, while students and admins are campus
     * wide and must not carry one.
     */
    private Department resolveDepartment(Role role, Long departmentId) {
        if (!role.isDepartmentRequired()) {
            if (departmentId != null) {
                throw new BusinessRuleException(
                        "A " + role.getDisplayName().toLowerCase() + " is not attached to a department");
            }
            return null;
        }

        if (departmentId == null) {
            throw new BusinessRuleException(
                    "A " + role.getDisplayName().toLowerCase() + " must belong to a department");
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        if (!department.isActive()) {
            throw new BusinessRuleException(
                    "Department '" + department.getName() + "' is inactive and cannot take new staff");
        }
        return department;
    }

    /**
     * Together, the two self-guards keep at least one working administrator alive.
     * An admin cannot deactivate themselves and cannot demote themselves, so the
     * only way to remove an admin is for a *different* admin to do it — which
     * means that other admin is still there afterwards.
     */
    private boolean isSelf(Long id) {
        return currentUser.find()
                .map(signedIn -> signedIn.id().equals(id))
                .orElse(false);
    }

    private User getOrThrow(Long id) {
        return userRepository.findByIdWithDepartment(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /** Lowercased and trimmed so that Ravi@College.edu and ravi@college.edu are one account. */
    private String normaliseEmail(String email) {
        return email.trim().toLowerCase();
    }
}

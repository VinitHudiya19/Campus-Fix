package com.campusfix.user;

import com.campusfix.common.model.Auditable;
import com.campusfix.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    /** Always stored lowercase so that logging in is not case sensitive. */
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** The BCrypt hash. The plain password is never held in a field. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * STRING, not ORDINAL. With ORDINAL the database would store 0,1,2,3 and
     * reordering the enum would silently turn every technician into an admin.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /** Null for students and admins, required for technicians and heads. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {
        // required by JPA
    }

    public User(String fullName, String email, String passwordHash, Role role, Department department) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.department = department;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Department getDepartment() {
        return department;
    }

    public boolean isActive() {
        return active;
    }

    public void changeProfile(String fullName, Role role, Department department) {
        this.fullName = fullName;
        this.role = role;
        this.department = department;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}

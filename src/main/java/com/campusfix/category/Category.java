package com.campusfix.category;

import com.campusfix.common.model.Auditable;
import com.campusfix.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The kind of problem a student reports, for example Wi-Fi or Projector.
 * Students pick a category; the category decides which department gets the work.
 */
@Entity
@Table(name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_name_department",
                columnNames = {"name", "department_id"}))
public class Category extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * LAZY so that listing categories does not always load departments. Queries
     * that need the department name join it in explicitly.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
        // required by JPA
    }

    public Category(String name, String description, Department department) {
        this.name = name;
        this.description = description;
        this.department = department;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Department getDepartment() {
        return department;
    }

    public boolean isActive() {
        return active;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void moveTo(Department department) {
        this.department = department;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}

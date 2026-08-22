package com.campusfix.location;

import com.campusfix.common.model.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A place on campus, stored in parts rather than as one free-text line.
 *
 * <p>"Block A, second floor, room 201" typed by hand arrives twenty different
 * ways and can never be grouped or reported on. Separate columns let a
 * department head ask "how many requests came from Block A this month?".
 */
@Entity
@Table(name = "locations",
        uniqueConstraints = @UniqueConstraint(name = "uk_location_place",
                columnNames = {"campus", "building", "floor", "room"}))
public class Location extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String campus;

    @Column(nullable = false, length = 80)
    private String building;

    /** Null for places without floors, such as a ground or an entrance gate. */
    @Column(length = 40)
    private String floor;

    /** Null when the whole building or floor is meant, such as a corridor. */
    @Column(length = 40)
    private String room;

    @Column(nullable = false)
    private boolean active = true;

    protected Location() {
        // required by JPA
    }

    public Location(String campus, String building, String floor, String room) {
        this.campus = campus;
        this.building = building;
        this.floor = floor;
        this.room = room;
    }

    public Long getId() {
        return id;
    }

    public String getCampus() {
        return campus;
    }

    public String getBuilding() {
        return building;
    }

    public String getFloor() {
        return floor;
    }

    public String getRoom() {
        return room;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * One readable line for dropdowns and request lists, built from whichever
     * parts are present: "Main Campus - Block A - Floor 2 - Room 201".
     */
    public String displayName() {
        StringBuilder text = new StringBuilder(campus).append(" - ").append(building);
        if (floor != null && !floor.isBlank()) {
            text.append(" - ").append(floor);
        }
        if (room != null && !room.isBlank()) {
            text.append(" - ").append(room);
        }
        return text.toString();
    }

    public void changePlace(String campus, String building, String floor, String room) {
        this.campus = campus;
        this.building = building;
        this.floor = floor;
        this.room = room;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}

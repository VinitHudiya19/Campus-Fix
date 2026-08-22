package com.campusfix.location.dto;

import com.campusfix.location.Location;

public record LocationResponse(
        Long id,
        String campus,
        String building,
        String floor,
        String room,
        String displayName,
        boolean active) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getCampus(),
                location.getBuilding(),
                location.getFloor(),
                location.getRoom(),
                location.displayName(),
                location.isActive());
    }
}

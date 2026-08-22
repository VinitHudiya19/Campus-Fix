package com.campusfix.location;

import com.campusfix.common.exception.DuplicateResourceException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.location.dto.LocationRequest;
import com.campusfix.location.dto.LocationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> search(String campus, boolean activeOnly) {
        return locationRepository.search(trimOrNull(campus), activeOnly).stream()
                .map(LocationResponse::from)
                .toList();
    }

    /** Feeds the first dropdown on the report form, before a building is picked. */
    @Transactional(readOnly = true)
    public List<String> activeCampuses() {
        return locationRepository.findActiveCampuses();
    }

    @Transactional(readOnly = true)
    public LocationResponse findById(Long id) {
        return LocationResponse.from(getOrThrow(id));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        String campus = request.campus().trim();
        String building = request.building().trim();
        String floor = trimOrNull(request.floor());
        String room = trimOrNull(request.room());

        requirePlaceFree(campus, building, floor, room, null);
        Location location = new Location(campus, building, floor, room);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse update(Long id, LocationRequest request) {
        Location location = getOrThrow(id);
        String campus = request.campus().trim();
        String building = request.building().trim();
        String floor = trimOrNull(request.floor());
        String room = trimOrNull(request.room());

        requirePlaceFree(campus, building, floor, room, id);
        location.changePlace(campus, building, floor, room);
        return LocationResponse.from(location);
    }

    /**
     * Deactivated, not deleted, for the same reason as everything else: old
     * requests record where the problem was, and that has to stay readable.
     */
    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).deactivate();
    }

    @Transactional
    public void activate(Long id) {
        getOrThrow(id).activate();
    }

    private void requirePlaceFree(String campus, String building, String floor, String room, Long allowedId) {
        locationRepository.findSamePlace(campus, building, floor, room)
                .filter(existing -> !existing.getId().equals(allowedId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "This place already exists: " + existing.displayName());
                });
    }

    private Location getOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", id));
    }

    /** Blank becomes null so "no floor" is stored one way and never two. */
    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

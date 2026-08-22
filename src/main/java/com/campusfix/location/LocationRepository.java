package com.campusfix.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("""
            select l from Location l
            where (:activeOnly = false or l.active = true)
              and (:campus is null or lower(l.campus) = lower(:campus))
            order by l.campus asc, l.building asc, l.floor asc, l.room asc
            """)
    List<Location> search(@Param("campus") String campus, @Param("activeOnly") boolean activeOnly);

    /**
     * Uniqueness has to treat null and empty as the same thing, otherwise the
     * same room could be added twice with floor stored as null one time and ""
     * the next. The service normalises blanks to null before calling this.
     */
    @Query("""
            select l from Location l
            where lower(l.campus) = lower(:campus)
              and lower(l.building) = lower(:building)
              and (:floor is null and l.floor is null or lower(l.floor) = lower(:floor))
              and (:room is null and l.room is null or lower(l.room) = lower(:room))
            """)
    Optional<Location> findSamePlace(@Param("campus") String campus,
                                     @Param("building") String building,
                                     @Param("floor") String floor,
                                     @Param("room") String room);

    @Query("select distinct l.campus from Location l where l.active = true order by l.campus asc")
    List<String> findActiveCampuses();
}

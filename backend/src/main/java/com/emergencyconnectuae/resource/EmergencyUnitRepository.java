// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmergencyUnitRepository extends JpaRepository<EmergencyUnit, UUID> {

    /**
     * PostGIS proximity search (SRS 6.2). Parameterized — no string interpolation.
     * Returns AVAILABLE units within :radius metres of (:lng,:lat), nearest first,
     * with the distance in metres as the second projection column.
     * type = 'ALL' matches every unit type.
     */
    @Query(value = """
            SELECT u.id            AS id,
                   u.type          AS type,
                   u.status        AS status,
                   u.home_station  AS home_station,
                   ST_Distance(u.location,
                       ST_MakePoint(:lng, :lat)::geography) AS distance_m
            FROM emergency_units u
            WHERE u.is_deleted = false
              AND u.status = 'AVAILABLE'
              AND u.location IS NOT NULL
              AND ST_DWithin(u.location,
                      ST_MakePoint(:lng, :lat)::geography, :radius)
              AND (:type = 'ALL' OR u.type = :type)
            ORDER BY distance_m ASC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findNearbyAvailable(@Param("lat") double lat,
                                       @Param("lng") double lng,
                                       @Param("radius") double radiusMetres,
                                       @Param("type") String type);
}

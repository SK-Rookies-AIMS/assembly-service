package com.aims.assembly.repository.car;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class CarMasterRepository {

    private final JdbcTemplate jdbcTemplate;

    public CarMasterRepository(@Qualifier("sampleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, String> findVehicleIdsByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = ids.stream()
                .map(ignored -> "?")
                .collect(Collectors.joining(", "));
        return jdbcTemplate.query(
                "SELECT id, vehicle_id FROM car_master WHERE id IN (" + placeholders + ")",
                rs -> {
                    Map<Long, String> vehicleIdsById = new LinkedHashMap<>();
                    while (rs.next()) {
                        vehicleIdsById.put(rs.getLong("id"), rs.getString("vehicle_id"));
                    }
                    return vehicleIdsById;
                },
                ids.toArray()
        );
    }
}

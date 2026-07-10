package com.aims.assembly.mapper;

import com.aims.assembly.dto.inspection.InspectionTargetDto;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InspectionTargetRowMapper
        implements RowMapper<InspectionTargetDto> {

    @Override
    public InspectionTargetDto mapRow(
            ResultSet rs,
            int rowNum
    ) throws SQLException {

        return new InspectionTargetDto(
                rs.getLong("id"),
                rs.getString("vehicle_id"),
                rs.getString("car_type"),
                rs.getString("engine_type"),
                rs.getString("car_color"),
                rs.getInt("fuel_efficiency"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
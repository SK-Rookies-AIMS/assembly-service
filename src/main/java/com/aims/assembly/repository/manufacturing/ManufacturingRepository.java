package com.aims.assembly.repository.manufacturing;

import com.aims.assembly.dto.inspection.InspectionTargetDto;
import com.aims.assembly.mapper.InspectionTargetRowMapper;
import com.aims.assembly.domain.enums.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ManufacturingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ManufacturingRepository(
            @Qualifier("sampleJdbcTemplate")
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InspectionTargetDto> findInspectionTarget(
            ProcessCode processCode,
            DispatchStatus dispatchStatus,
            AnalysisStatus analysisStatus
    ) {

        String sql = """
            SELECT
                m.id,
                c.vehicle_id,
                c.car_type,
                c.engine_type,
                c.car_color,
                c.fuel_efficiency,
                c.created_at
        
            FROM manufacturing_event_json m
        
            JOIN car_master c
              ON m.car_master_id = c.id
        
            WHERE m.process_code = ?
              AND m.dispatch_status = ?
              AND m.analysis_status = ?
        
            ORDER BY m.id
        """;

        return jdbcTemplate.query(
                sql,
                new InspectionTargetRowMapper(),
                processCode.name(),
                dispatchStatus.name(),
                analysisStatus.name()
        );
    }
}
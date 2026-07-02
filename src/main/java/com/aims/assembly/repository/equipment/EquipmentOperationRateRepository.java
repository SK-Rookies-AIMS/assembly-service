package com.aims.assembly.repository.equipment;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
public class EquipmentOperationRateRepository {

    private final JdbcTemplate jdbcTemplate;

    public EquipmentOperationRateRepository(
            @Qualifier("sampleJdbcTemplate") JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StatusCount> countByProcessAndStatus() {
        return jdbcTemplate.query(
                """
                        SELECT process_code, current_status, COUNT(*) AS count
                        FROM equipment
                        WHERE process_code IN ('PRESS', 'BODY', 'PAINT', 'ASSEMBLY')
                        GROUP BY process_code, current_status
                        """,
                (rs, rowNum) -> {
                    String processCode = rs.getString("process_code");
                    String currentStatus = rs.getString("current_status");
                    EquipmentOperationStatus status = EquipmentOperationStatus.from(currentStatus)
                            .orElse(null);
                    if (status == null) {
                        log.warn(
                                "Ignoring equipment count row with invalid current_status: processCode={}, currentStatus={}",
                                processCode,
                                currentStatus
                        );
                        return null;
                    }
                    return new StatusCount(
                            ProcessCode.valueOf(processCode),
                            status,
                            rs.getLong("count")
                    );
                }
        ).stream().filter(row -> row != null).toList();
    }

    public record StatusCount(
            ProcessCode processCode,
            EquipmentOperationStatus status,
            long count
    ) {
    }
}

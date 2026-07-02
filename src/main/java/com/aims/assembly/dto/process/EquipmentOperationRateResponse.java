package com.aims.assembly.dto.process;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;

import java.util.List;
import java.util.Map;

public record EquipmentOperationRateResponse(
        List<Item> items
) {
    public record Item(
            String processCode,
            String processName,
            long runningCount,
            long warningCount,
            long operatingCount,
            long stoppedCount,
            long faultCount,
            long totalCount,
            double operationRate,
            Map<EquipmentOperationStatus, Long> statusCounts
    ) {
    }
}

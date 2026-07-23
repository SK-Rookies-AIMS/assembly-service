package com.aims.assembly.dto.process;

import com.aims.assembly.domain.enums.EquipmentOperationStatus;

import java.util.LinkedHashMap;
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
            Map<String, Long> statusCounts
    ) {
        public Item {
            statusCounts = normalizeStatusCounts(statusCounts);
        }

        private static Map<String, Long> normalizeStatusCounts(Map<?, ?> source) {
            Map<String, Long> normalized = new LinkedHashMap<>();
            for (EquipmentOperationStatus status : EquipmentOperationStatus.values()) {
                normalized.put(status.name(), numberToLong(statusCountValue(source, status)));
            }
            return normalized;
        }

        private static Object statusCountValue(Map<?, ?> source, EquipmentOperationStatus status) {
            if (source == null) {
                return null;
            }
            Object value = source.get(status.name());
            return value != null ? value : source.get(status);
        }

        private static long numberToLong(Object value) {
            return value instanceof Number number ? number.longValue() : 0L;
        }
    }
}

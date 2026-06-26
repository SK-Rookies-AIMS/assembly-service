package com.aims.assembly.dto.kafka;

import com.aims.assembly.domain.enums.AnalysisStatus;
import com.aims.assembly.domain.enums.DispatchStatus;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;

public record StoredManufacturingEventResponse(
        ManufacturingRawEvent payload,
        String rawJson,
        String carId,
        DispatchStatus dispatchStatus,
        AnalysisStatus analysisStatus,
        boolean sent,
        long retryCount,
        String errorMessage
) {
    public static StoredManufacturingEventResponse from(StoredManufacturingEvent event) {
        return new StoredManufacturingEventResponse(event.payload(), event.rawJson(), event.carId(),
                event.dispatchStatus(), event.analysisStatus(), event.sent(), event.retryCount(),
                event.errorMessage());
    }
}

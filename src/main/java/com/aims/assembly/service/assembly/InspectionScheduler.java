package com.aims.assembly.service.assembly;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InspectionScheduler {

    private final com.aims.assembly.service.assembly.InspectionTransferService inspectionTransferService;

    @Scheduled(fixedDelay = 1000)
    public void transferInspection() {
        inspectionTransferService.transfer();
    }
}
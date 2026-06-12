package com.aims.assembly.controller.process;

import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.dto.process.ProcessEventResponse;
import com.aims.assembly.service.process.ProcessEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/process/events")
@RequiredArgsConstructor
public class ProcessEventController {

    private final ProcessEventService processEventService;

    @GetMapping("/equipments")
    public ApiResponse<List<ProcessEventResponse.EquipmentDTO>> getEquipments() {
        return ApiResponse.success(
                processEventService.getEquipments(),
                "SampleDB equipment data retrieved successfully"
        );
    }

    @GetMapping("/manufacturing-events")
    public ApiResponse<List<ProcessEventResponse.ManufacturingEventDTO>> getManufacturingEvents() {
        return ApiResponse.success(
                processEventService.getManufacturingEvents(),
                "SampleDB manufacturing event data retrieved successfully"
        );
    }

    @GetMapping("/thermal-visions")
    public ApiResponse<List<ProcessEventResponse.ThermalVisionDTO>> getThermalVisions() {
        return ApiResponse.success(
                processEventService.getThermalVisions(),
                "SampleDB thermal vision data retrieved successfully"
        );
    }

    @GetMapping("/robot-vibrations")
    public ApiResponse<List<ProcessEventResponse.RobotArmVibrationDTO>> getRobotArmVibrations() {
        return ApiResponse.success(
                processEventService.getRobotArmVibrations(),
                "SampleDB robot arm vibration data retrieved successfully"
        );
    }
}

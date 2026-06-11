package com.aims.assembly.service.process;

import com.aims.assembly.dto.process.ProcessEventResponse;
import com.aims.assembly.exception.ProcessEventException;
import com.aims.assembly.repository.process.ProcessEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessEventService {

    private final ProcessEventRepository processEventRepository;

    public List<ProcessEventResponse.EquipmentDTO> getEquipments() {
        List<ProcessEventResponse.EquipmentDTO> data = processEventRepository.findEquipments();

        if (data.isEmpty()) {
            throw new ProcessEventException("Equipment data does not exist.");
        }

        return data;
    }

    public List<ProcessEventResponse.ManufacturingEventDTO> getManufacturingEvents() {
        List<ProcessEventResponse.ManufacturingEventDTO> data = processEventRepository.findManufacturingEvents();

        if (data.isEmpty()) {
            throw new ProcessEventException("Manufacturing event data does not exist.");
        }

        return data;
    }

    public List<ProcessEventResponse.ThermalVisionDTO> getThermalVisions() {
        List<ProcessEventResponse.ThermalVisionDTO> data = processEventRepository.findThermalVisions();

        if (data.isEmpty()) {
            throw new ProcessEventException("Thermal vision data does not exist.");
        }

        return data;
    }

    public List<ProcessEventResponse.RobotArmVibrationDTO> getRobotArmVibrations() {
        List<ProcessEventResponse.RobotArmVibrationDTO> data = processEventRepository.findRobotArmVibrations();

        if (data.isEmpty()) {
            throw new ProcessEventException("Robot arm vibration data does not exist.");
        }

        return data;
    }
}

package com.aims.assembly.service.assembly;

import com.aims.assembly.domain.enums.AnalysisStatus;
import com.aims.assembly.domain.enums.DispatchStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.inspection.InspectionMasterEntity;
import com.aims.assembly.dto.inspection.InspectionTargetDto;
import com.aims.assembly.repository.inspection.InspectionRepository;
import com.aims.assembly.repository.manufacturing.ManufacturingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InspectionTransferService {

    private final ManufacturingRepository manufacturingRepository;

    private final InspectionRepository inspectionRepository;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void transfer() {

        List<InspectionTargetDto> targets =
                manufacturingRepository.findInspectionTarget(
                        ProcessCode.ASSEMBLY,
                        DispatchStatus.SENT,
                        AnalysisStatus.NORMAL
                );

        for (InspectionTargetDto dto : targets) {

            if (inspectionRepository.existsByVehicleId(dto.getVehicleId())) {
                continue;
            }

            InspectionMasterEntity entity =
                    InspectionMasterEntity.builder()
                            .vehicleId(dto.getVehicleId())
                            .carType(dto.getCarType())
                            .engineType(dto.getEngineType())
                            .carColor(dto.getCarColor())
                            .fuelEfficiency(dto.getFuelEfficiency())
                            .createdAt(LocalDateTime.now())
                            .build();

            inspectionRepository.save(entity);

        }
    }

}
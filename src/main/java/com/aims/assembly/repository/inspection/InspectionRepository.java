package com.aims.assembly.repository.inspection;

import com.aims.assembly.domain.inspection.InspectionMasterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InspectionRepository
        extends JpaRepository<InspectionMasterEntity,Long> {

    boolean existsByVehicleId(String vehicleId);
}
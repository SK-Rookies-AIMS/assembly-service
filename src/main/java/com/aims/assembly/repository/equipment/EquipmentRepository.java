package com.aims.assembly.repository.equipment;

import com.aims.assembly.domain.equipment.Equipment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EquipmentRepository {
    @PersistenceContext(unitName = "sample")
    private EntityManager entityManager;

    public Optional<Equipment> findByEquipmentCode(String equipmentCode) {
        return entityManager.createQuery(
                        "select e from Equipment e where e.equipmentCode = :code", Equipment.class)
                .setParameter("code", equipmentCode)
                .getResultStream()
                .findFirst();
    }
}

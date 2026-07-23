package com.aims.assembly.domain.car;

import com.aims.assembly.domain.commons.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "car_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CarMaster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false, length = 50)
    private String vehicleId;

    @Column(name = "car_type", nullable = false, length = 20)
    private String carType;

    @Column(name = "engine_type", nullable = false, length = 30)
    private String engineType;

    @Column(name = "car_color", nullable = false, length = 30)
    private String carColor;

    @Column(name = "fuel_efficiency")
    private Integer fuelEfficiency;
}

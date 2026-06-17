package com.aims.assembly.domain.car;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarMaster {

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

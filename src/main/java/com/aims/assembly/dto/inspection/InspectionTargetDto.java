package com.aims.assembly.dto.inspection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InspectionTargetDto {

    private Long eventId;

    private String vehicleId;

    private String carType;

    private String engineType;

    private String carColor;

    private Integer fuelEfficiency;

    private LocalDateTime createdAt;
}
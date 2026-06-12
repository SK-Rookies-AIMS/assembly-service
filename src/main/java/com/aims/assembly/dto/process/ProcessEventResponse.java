package com.aims.assembly.dto.process;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ProcessEventResponse {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "SampleDB equipment response")
    public static class EquipmentDTO {
        private Long id;
        private String processCode;
        private String equipmentCode;
        private String equipmentName;
        private String equipmentType;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "SampleDB manufacturing event response")
    public static class ManufacturingEventDTO {
        private Long id;
        private Long carMasterId;
        private Long equipmentId;
        private String sampleManufacturingEventId;
        private String equipmentCode;
        private String sourceDataset;
        private String sourceType;
        private String dataType;
        private String processCode;
        private String equipmentType;
        private String stationCode;
        private LocalDateTime eventTime;
        private String metricCode;
        private Double metricValue;
        private String unit;
        private Double processTime;
        private Double waitingTime;
        private String qualityResult;
        private String defectType;
        private Boolean expectedIsAbnormal;
        private String expectedAbnormalType;
        private String expectedSeverity;
        private String expectedLabel;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "SampleDB thermal vision response")
    public static class ThermalVisionDTO {
        private Long id;
        private Long manufacturingEventId;
        private Long carMasterId;
        private String imagePosition;
        private Double thermalAvgTemp;
        private Double thermalMaxTemp;
        private Double thermalMinTemp;
        private Double thermalStdTemp;
        private Double thicknessValue;
        private Double defectScore;
        private String expectedVisionLabel;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "SampleDB robot arm vibration response")
    public static class RobotArmVibrationDTO {
        private Long id;
        private Long manufacturingEventId;
        private Long equipmentId;
        private LocalDateTime measuredAt;

        private Float freq0100Hz;
        private Float freq101200Hz;
        private Float freq201300Hz;
        private Float freq301400Hz;
        private Float freq401500Hz;
        private Float freq501600Hz;
        private Float freq601700Hz;
        private Float freq701800Hz;
        private Float freq801900Hz;
        private Float freq9011000Hz;
        private Float freq10011100Hz;
        private Float freq11011200Hz;
        private Float freq12011300Hz;
        private Float freq13011400Hz;
        private Float freq14011500Hz;
        private Float freq15011600Hz;
    }
}

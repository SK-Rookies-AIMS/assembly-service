package com.aims.assembly.dto.body;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BodyAnomalyDetectionResponse(
        LocalDate date,
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime previousEndAt,
        List<DateOption> dateOptions,
        Metrics metrics,
        List<ChartPoint> chart,
        List<FrequencyBandPoint> frequencyChart,
        FrequencyZoneAnalysis frequencyZoneAnalysis,
        AlertPanel alert
) {
    public record FrequencyBandPoint(
            LocalDateTime timestamp,
            String band, 
            Double value,
            Double targetValue
    ) {}
    public record DateOption(
            LocalDate date,
            String sampleEventId
    ) {
    }

    public record Metrics(
            String robotMotionStatus,
            String robotOperationMode,
            Double targetVibrationScore,
            Double vibrationScore,
            Double targetVibrationPeak,
            Double vibrationPeak,
            Double vibrationRms,
            String frequencyPeakBand,
            Double frequencyPeakValue,
            Double riskScore,
            String riskScoreScale,
            String severity,
            Map<String, Double> frequencyBands
    ) {
    }

    public record ChartPoint(
            String eventId,
            String analysisId,
            LocalDateTime timestamp,
            Double targetVibrationScore,
            Double vibrationScore,
            Double targetVibrationPeak,
            Double vibrationPeak,
            Double vibrationRms,
            Double riskScore,
            Boolean isAbnormal,
            String severity
    ) {
    }

    public record FrequencyZoneAnalysis(
            ZoneStats low,
            ZoneStats main,
            ZoneStats high,
            ZoneStats ultra
    ) {
        public record ZoneStats(
                Double avg,
                Double max
        ) {}
    }

    public record AlertPanel(
            Boolean detected,
            String title,
            List<String> reasons
    ) {
    }
}

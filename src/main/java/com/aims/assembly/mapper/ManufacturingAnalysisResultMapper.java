package com.aims.assembly.mapper;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;

import java.time.LocalDateTime;

public final class ManufacturingAnalysisResultMapper {

    private ManufacturingAnalysisResultMapper() {
    }

    public static ManufacturingAnalysisResult toManufacturingAnalysisResult(
            String analysisId,
            String eventId,
            Long carMasterId,
            Long equipmentId,
            ProcessCode processCode,
            LocalDateTime eventTime,
            Boolean isAbnormal,
            String abnormalType,
            Severity severity,
            Double riskScore,
            String analysisMessage,
            LocalDateTime analyzedAt
    ) {
        return ManufacturingAnalysisResult.builder()
                .analysisId(analysisId)
                .eventId(eventId)
                .carMasterId(carMasterId)
                .equipmentId(equipmentId)
                .processCode(processCode)
                .eventTime(eventTime)
                .isAbnormal(isAbnormal)
                .abnormalType(abnormalType)
                .severity(severity)
                .riskScore(riskScore)
                .analysisMessage(analysisMessage)
                .analyzedAt(analyzedAt)
                .build();
    }

    public static PressAnalysisResult toPressAnalysisResult(
            ManufacturingAnalysisResult analysisResult,
            Boolean countIncreaseYn,
            Double targetCycleTimeSec,
            Double actualCycleTimeSec,
            Double cycleTimeGapSec,
            Double timestampDelaySec
    ) {
        return PressAnalysisResult.builder()
                .analysisResult(analysisResult)
                .countIncreaseYn(countIncreaseYn)
                .targetCycleTimeSec(targetCycleTimeSec)
                .actualCycleTimeSec(actualCycleTimeSec)
                .cycleTimeGapSec(cycleTimeGapSec)
                .timestampDelaySec(timestampDelaySec)
                .build();
    }

    public static BodyAnalysisResult toBodyAnalysisResult(
            ManufacturingAnalysisResult analysisResult,
            String robotMotionStatus,
            String robotOperationMode,
            Double robotVibrationScore,
            String frequencyPeakBand,
            Double frequencyPeakValue,
            String frequencyBandsJson
    ) {
        return BodyAnalysisResult.builder()
                .analysisResult(analysisResult)
                .robotMotionStatus(robotMotionStatus)
                .robotOperationMode(robotOperationMode)
                .robotVibrationScore(robotVibrationScore)
                .frequencyPeakBand(frequencyPeakBand)
                .frequencyPeakValue(frequencyPeakValue)
                .frequencyBandsJson(frequencyBandsJson)
                .build();
    }

    public static PaintAnalysisResult toPaintAnalysisResult(
            ManufacturingAnalysisResult analysisResult,
            Double defectScore,
            Double thermalStdTemp,
            Double surfaceQualityScore,
            String visionLabel,
            String imagePosition,
            Double thicknessValue
    ) {
        return PaintAnalysisResult.builder()
                .analysisResult(analysisResult)
                .defectScore(defectScore)
                .thermalStdTemp(thermalStdTemp)
                .surfaceQualityScore(surfaceQualityScore)
                .visionLabel(visionLabel)
                .imagePosition(imagePosition)
                .thicknessValue(thicknessValue)
                .build();
    }

    public static AssemblyAnalysisResult toAssemblyAnalysisResult(
            ManufacturingAnalysisResult analysisResult,
            String expectedSequence,
            String actualSequence,
            Integer sequenceErrorCount,
            Integer missingPartCount,
            Integer fasteningErrorCount
    ) {
        return AssemblyAnalysisResult.builder()
                .analysisResult(analysisResult)
                .expectedSequence(expectedSequence)
                .actualSequence(actualSequence)
                .sequenceErrorCount(sequenceErrorCount)
                .missingPartCount(missingPartCount)
                .fasteningErrorCount(fasteningErrorCount)
                .build();
    }
}

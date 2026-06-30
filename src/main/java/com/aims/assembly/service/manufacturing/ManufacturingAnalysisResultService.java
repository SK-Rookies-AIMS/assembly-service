package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.assembly.AssemblyAnalysisResult;
import com.aims.assembly.domain.body.BodyAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.domain.paint.PaintAnalysisResult;
import com.aims.assembly.domain.press.PressAnalysisResult;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingAnalysisResultService {
    private final ManufacturingAnalysisResultRepository repository;
    private final com.aims.assembly.repository.event.ManufacturingEventJsonRepository eventRepository;
    private final PressAnalysisResultRepository pressRepository;
    private final BodyAnalysisResultRepository bodyRepository;
    private final PaintAnalysisResultRepository paintRepository;
    private final AssemblyAnalysisResultRepository assemblyRepository;
    private final ManufacturingEventAnalyzer analyzer;

    /**
     * 분석 결과를 MainDB manufacturing_analysis_result 에 저장하고,
     * 같은 트랜잭션 내에서 공정별 결과 테이블(press/body/paint/assembly_analysis_result)에도 저장한다.
     *
     * <p>공정별 저장 실패 시 전체 트랜잭션이 롤백된다.
     * consumer 가 재처리하므로 Kafka-DB 최종 일관성이 보장된다.
     */
    @Transactional
    public void save(ManufacturingRawEvent raw, ManufacturingAnalysisEvent analysis) {
        var result = analysis.analysisResult();
        LocalDateTime eventTime = raw.eventTime();
        if (eventTime == null) {
            throw new IllegalArgumentException(
                    "manufacturing raw event eventTime must not be null. eventId="
                            + raw.eventId()
            );
        }
        var stored = eventRepository.findByEventId(raw.eventId()).orElse(null);
        Long carMasterId = raw.carMasterId() != null ? raw.carMasterId()
                : stored == null ? null : stored.payload().carMasterId();
        Long equipmentId = raw.equipmentId() != null ? raw.equipmentId()
                : stored == null ? null : stored.payload().equipmentId();
        if (stored != null && !Objects.equals(stored.payload().eventTime(), raw.eventTime())) {
            log.warn("DB eventTime differs from event.eventTime: eventId={}, db={}, json={}",
                    raw.eventId(), stored.payload().eventTime(), raw.eventTime());
        }
        ManufacturingAnalysisResult savedResult = repository.saveAndFlush(
                ManufacturingAnalysisResult.builder()
                        .analysisId(analysis.analysisId())
                        .eventId(analysis.eventId())
                        .carMasterId(carMasterId)
                        .equipmentId(equipmentId)
                        .processCode(raw.processCode())
                        .eventTime(eventTime)
                        .isAbnormal(result.isAbnormal())
                        .abnormalType(abnormalType(result, overallRiskScore(analysis)))
                        .severity(severity(analysis.riskLevel()))
                        .riskScore(overallRiskScore(analysis))
                        .analysisMessage(analysis.reason() == null ? null : analysis.reason().mainReason())
                        .analyzedAt(analysis.analyzedAt())
                        .build()
        );

        // 공정별 결과 저장 (같은 트랜잭션)
        saveProcessSpecificResult(raw, savedResult);
    }

    /**
     * 공정별 결과 테이블에 추가 데이터를 저장한다.
     * ManufacturingAnalysisResult 저장 후 동일 트랜잭션에서 실행된다.
     */
    private void saveProcessSpecificResult(
            ManufacturingRawEvent raw,
            ManufacturingAnalysisResult savedResult
    ) {
        Map<String, Object> json = raw.eventJson();
        switch (raw.processCode()) {
            case PRESS -> {
                boolean countIncrease = bool(json, "processData", "press", "countIncreaseYn");
                double targetCycleTime = number(json, "processData", "press", "targetCycleTimeSec");
                double actualCycleTime = number(json, "processMetrics", "cycleTimeSec");
                double stationDelaySec = number(json, "processMetrics", "stationDelaySec");
                pressRepository.save(
                        PressAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .countIncreaseYn(countIncrease)
                                .targetCycleTimeSec(targetCycleTime > 0 ? targetCycleTime : null)
                                .actualCycleTimeSec(actualCycleTime > 0 ? actualCycleTime : null)
                                .cycleTimeGapSec(actualCycleTime > 0 && targetCycleTime > 0
                                        ? actualCycleTime - targetCycleTime : null)
                                .timestampDelaySec(stationDelaySec > 0 ? stationDelaySec : null)
                                .build()
                );
            }
            case BODY -> {
                double robotVibrationScore =
                        number(json, "sensor", "robotArmVibration", "vibrationScore");
                double frequencyHz = number(json, "sensor", "robotArmVibration", "frequencyHz");
                String frequencyPeakBand = frequencyHz > 0
                        ? (frequencyHz < 100 ? "LOW" : frequencyHz < 500 ? "MID" : "HIGH")
                        : null;
                bodyRepository.save(
                        BodyAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .robotMotionStatus(savedResult.getIsAbnormal() ? "ABNORMAL" : "NORMAL")
                                .robotVibrationScore(robotVibrationScore > 0 ? robotVibrationScore : null)
                                .frequencyPeakBand(frequencyPeakBand)
                                .frequencyPeakValue(frequencyHz > 0 ? frequencyHz : null)
                                .build()
                );
            }
            case PAINT -> {
                double defectScore = number(json, "processData", "paint", "defectScore");
                double thermalStdTemp = number(json, "processData", "paint", "thermalStdTemp");
                double surfaceQualityScore =
                        number(json, "processData", "paint", "surfaceQualityScore");
                String visionLabel = text(json, "processData", "paint", "visionLabel");
                paintRepository.save(
                        PaintAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .defectScore(defectScore > 0 ? defectScore : null)
                                .thermalStdTemp(thermalStdTemp > 0 ? thermalStdTemp : null)
                                .surfaceQualityScore(surfaceQualityScore > 0 ? surfaceQualityScore : null)
                                .visionLabel(visionLabel)
                                .build()
                );
            }
            case ASSEMBLY -> {
                int sequenceErrorCount =
                        (int) number(json, "processData", "assembly", "sequenceErrorCount");
                int missingPartCount =
                        (int) number(json, "processData", "assembly", "missingPartCount");
                int fasteningErrorCount =
                        (int) number(json, "processData", "assembly", "fasteningErrorCount");
                assemblyRepository.save(
                        AssemblyAnalysisResult.builder()
                                .analysisResult(savedResult)
                                .sequenceErrorCount(sequenceErrorCount)
                                .missingPartCount(missingPartCount)
                                .fasteningErrorCount(fasteningErrorCount)
                                .build()
                );
            }
        }
    }

    private String abnormalType(ManufacturingAnalysisEvent.AnalysisResult result, double riskScore) {
        if (result.isEquipmentFault()) return "EQUIPMENT";
        if (result.isSequenceError() || riskScore >= 60) return "PROCESS";
        return null;
    }

    private double overallRiskScore(ManufacturingAnalysisEvent analysis) {
        if (analysis.riskScores() == null || analysis.riskScores().overallRiskScore() == null) {
            return 0.0;
        }
        return analysis.riskScores().overallRiskScore();
    }

    private Severity severity(String riskLevel) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            return Severity.CRITICAL;
        }
        if ("WARNING".equalsIgnoreCase(riskLevel) || "MEDIUM".equalsIgnoreCase(riskLevel)) {
            return Severity.WARNING;
        }
        return Severity.NORMAL;
    }

    @SuppressWarnings("unchecked")
    private double number(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return 0.0;
            current = ((Map<String, Object>) map).get(key);
        }
        return current instanceof Number n ? n.doubleValue() : 0.0;
    }

    @SuppressWarnings("unchecked")
    private boolean bool(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return false;
            current = ((Map<String, Object>) map).get(key);
        }
        return current instanceof Boolean b && b;
    }

    @SuppressWarnings("unchecked")
    private String text(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = ((Map<String, Object>) map).get(key);
        }
        return current == null ? null : current.toString();
    }
}

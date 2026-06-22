package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingAnalysisResultService {
    private final ManufacturingAnalysisResultRepository repository;
    private final com.aims.assembly.repository.event.ManufacturingEventJsonRepository eventRepository;

    @Transactional
    public void save(ManufacturingRawEvent raw, ManufacturingAnalysisEvent analysis) {
        var result = analysis.analysisResult();
        var stored = eventRepository.findByEventId(raw.eventId()).orElse(null);
        Long carMasterId = raw.carMasterId() != null ? raw.carMasterId()
                : stored == null ? null : stored.payload().carMasterId();
        Long equipmentId = raw.equipmentId() != null ? raw.equipmentId()
                : stored == null ? null : stored.payload().equipmentId();
        if (stored != null && !Objects.equals(stored.payload().eventTime(), raw.eventTime())) {
            log.warn("DB eventTime differs from event.eventTime: eventId={}, db={}, json={}",
                    raw.eventId(), stored.payload().eventTime(), raw.eventTime());
        }
        repository.save(ManufacturingAnalysisResult.builder()
                .analysisId(analysis.analysisId())
                .eventId(analysis.eventId())
                .carMasterId(carMasterId)
                .equipmentId(equipmentId)
                .processCode(raw.processCode())
                .eventTime(raw.eventTime())
                .isAbnormal(result.isAbnormal())
                .abnormalType(abnormalType(result))
                .severity(severity(analysis.riskLevel()))
                .riskScore(analysis.riskScores().overallRiskScore())
                .analysisMessage(analysis.reason() == null ? null : analysis.reason().mainReason())
                .analyzedAt(analysis.analyzedAt())
                .build());
    }

    private String abnormalType(ManufacturingAnalysisEvent.AnalysisResult result) {
        if (result.isEquipmentFault()) return "EQUIPMENT";
        if (result.isQualityDefect()) return "PRODUCT";
        if (result.isSequenceError()) return "PROCESS";
        if (result.isBottleneck()) return "BOTTLENECK";
        return null;
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
}

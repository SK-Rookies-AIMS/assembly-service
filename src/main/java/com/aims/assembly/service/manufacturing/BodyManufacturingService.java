package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BodyManufacturingService implements ManufacturingProcessHandler {

    private final ManufacturingEventAnalyzer analyzer;

    @Override
    public ProcessCode supports() {
        return ProcessCode.BODY;
    }

    @Override
    public ManufacturingAnalysisEvent process(ManufacturingRawEvent event) {
        // 로봇 암 진동, 주파수와 충돌 위험을 포함한 차체 위험도 분석
        log.debug("Processing BODY event: {}", event.eventId());
        return analyzer.analyze(event);
    }
}

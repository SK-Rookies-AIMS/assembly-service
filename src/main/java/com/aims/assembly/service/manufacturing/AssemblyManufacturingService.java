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
public class AssemblyManufacturingService implements ManufacturingProcessHandler {

    private final ManufacturingEventAnalyzer analyzer;

    @Override
    public ProcessCode supports() {
        return ProcessCode.ASSEMBLY;
    }

    @Override
    public ManufacturingAnalysisEvent process(ManufacturingRawEvent event) {
        // 조립 순서, 누락 부품과 체결 오류를 포함한 의장 위험도 분석
        log.debug("Processing ASSEMBLY event: {}", event.eventId());
        return analyzer.analyze(event);
    }
}

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
public class PaintManufacturingService implements ManufacturingProcessHandler {

    private final ManufacturingEventAnalyzer analyzer;

    @Override
    public ProcessCode supports() {
        return ProcessCode.PAINT;
    }

    @Override
    public ManufacturingAnalysisEvent process(ManufacturingRawEvent event) {
        // 열화상 온도, 도막과 비전 불량을 포함한 도장 품질 위험도 분석
        log.debug("Processing PAINT event: {}", event.eventId());
        return analyzer.analyze(event);
    }
}

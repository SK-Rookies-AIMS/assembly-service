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
public class PressManufacturingService implements ManufacturingProcessHandler {

    private final ManufacturingEventAnalyzer analyzer;

    @Override
    public ProcessCode supports() {
        return ProcessCode.PRESS;
    }

    @Override
    public ManufacturingAnalysisEvent process(ManufacturingRawEvent event) {
        // 전류 RMS, cycle time, CNT 증가 여부를 포함한 프레스 위험도 분석
        log.debug("Processing PRESS event: {}", event.eventId());
        return analyzer.analyze(event);
    }
}

package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;

/**
 * 제조 공정별 비즈니스 로직 공통 계약.
 * ProcessCode 기반 Handler 선택과 분석 결과 반환 규약.
 */
public interface ManufacturingProcessHandler {

    // Handler 지원 공정 코드
    ProcessCode supports();

    // 공정별 raw 이벤트 분석 및 analysis 이벤트 생성
    ManufacturingAnalysisEvent process(ManufacturingRawEvent event);
}

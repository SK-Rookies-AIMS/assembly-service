package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 단일 Manufacturing Consumer 수신 이벤트의 공정별 서비스 분배.
 */
@Service
public class ManufacturingProcessRouter {

    private final Map<ProcessCode, ManufacturingProcessHandler> handlers;

    public ManufacturingProcessRouter(List<ManufacturingProcessHandler> handlers) {
        // ProcessCode를 key로 사용하는 공정 Handler Map 구성
        EnumMap<ProcessCode, ManufacturingProcessHandler> handlerMap =
                new EnumMap<>(ProcessCode.class);

        for (ManufacturingProcessHandler handler : handlers) {
            // Handler가 지원하는 공정 코드 등록
            ManufacturingProcessHandler duplicate = handlerMap.put(handler.supports(), handler);

            // 동일 공정 Handler 중복 Bean 등록 방지
            if (duplicate != null) {
                throw new KafkaException(
                        KafkaErrorStatus.DUPLICATE_PROCESS_HANDLER,
                        "동일한 제조 공정 처리기가 중복 등록되었습니다. processCode="
                                + handler.supports()
                );
            }
        }

        // 런타임 Handler Map 변경 방지
        this.handlers = Map.copyOf(handlerMap);
    }

    public ManufacturingAnalysisEvent route(ManufacturingRawEvent event) {
        // DB 엔티티 process_code 기반 공정 코드 조회
        ProcessCode processCode = event.processCode();

        // PRESS/BODY/PAINT/ASSEMBLY 전용 Handler 선택
        ManufacturingProcessHandler handler = handlers.get(processCode);

        // 지원하지 않는 공정 코드의 무응답 처리 방지
        if (handler == null) {
            throw new KafkaException(
                    KafkaErrorStatus.UNSUPPORTED_PROCESS,
                    "지원하지 않는 제조 공정입니다. processCode=" + processCode
            );
        }

        // 선택된 공정 서비스의 분석 로직 실행
        return handler.process(event);
    }
}

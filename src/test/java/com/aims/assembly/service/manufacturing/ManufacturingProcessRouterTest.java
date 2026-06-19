package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka broker 연결 없이 processCode 기반 공정 서비스 선택 검증.
 */
@DisplayName("제조 공정 라우터 단위 테스트")
class ManufacturingProcessRouterTest {

    @Test
    @DisplayName("PRESS, BODY, PAINT, ASSEMBLY 이벤트를 각 전용 Handler로 전달")
    void routesEachProcessToItsDedicatedHandler() {
        // Given: 공정별 Handler Mock과 라우터 구성
        ManufacturingProcessHandler press = handler(ProcessCode.PRESS);
        ManufacturingProcessHandler body = handler(ProcessCode.BODY);
        ManufacturingProcessHandler paint = handler(ProcessCode.PAINT);
        ManufacturingProcessHandler assembly = handler(ProcessCode.ASSEMBLY);
        ManufacturingProcessRouter router =
                new ManufacturingProcessRouter(List.of(press, body, paint, assembly));

        // When & Then: 각 processCode의 전용 Handler 선택 검증
        assertRoute(router, event(ProcessCode.PRESS), press);
        assertRoute(router, event(ProcessCode.BODY), body);
        assertRoute(router, event(ProcessCode.PAINT), paint);
        assertRoute(router, event(ProcessCode.ASSEMBLY), assembly);
    }

    private ManufacturingProcessHandler handler(ProcessCode processCode) {
        ManufacturingProcessHandler handler = mock(ManufacturingProcessHandler.class);
        when(handler.supports()).thenReturn(processCode);
        return handler;
    }

    private void assertRoute(
            ManufacturingProcessRouter router,
            ManufacturingRawEvent event,
            ManufacturingProcessHandler expectedHandler
    ) {
        // Given: 선택된 Handler의 분석 결과
        ManufacturingAnalysisEvent expected = mock(ManufacturingAnalysisEvent.class);
        when(expectedHandler.process(event)).thenReturn(expected);

        // When: processCode 기반 라우팅
        ManufacturingAnalysisEvent actual = router.route(event);

        // Then: 예상 Handler 호출 및 결과 반환
        assertThat(actual).isSameAs(expected);
        verify(expectedHandler).process(event);
    }

    private ManufacturingRawEvent event(ProcessCode processCode) {
        return new ManufacturingRawEvent(
                1L, "EVT-001", LocalDateTime.of(2026, 6, 18, 10, 0),
                10L, 20L, processCode, "ST-01", "EQ-01",
                "TEST", "RUNNING", "PROCESS_STATUS", Map.of()
        );
    }
}

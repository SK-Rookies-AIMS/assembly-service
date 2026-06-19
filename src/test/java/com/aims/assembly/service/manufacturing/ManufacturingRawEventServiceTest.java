package com.aims.assembly.service.manufacturing;

import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import com.aims.assembly.domain.enums.ProcessCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SampleDB Repository와 Kafka Producer Mock을 사용한 raw 이벤트 발행 서비스 단위 테스트.
 * DB 조회, Kafka 성공 이후 상태 갱신, Kafka ErrorCode 처리 검증.
 */
@DisplayName("SampleDB raw 이벤트 발행 서비스 단위 테스트")
class ManufacturingRawEventServiceTest {

    private final ManufacturingEventJsonRepository repository =
            mock(ManufacturingEventJsonRepository.class);
    private final ManufacturingKafkaProducer producer =
            mock(ManufacturingKafkaProducer.class);
    private final ManufacturingRawEventService service =
            new ManufacturingRawEventService(repository, producer);

    @Test
    @DisplayName("Kafka 발행 성공 이후에만 SampleDB 전송 상태 갱신")
    void sendsEventJsonAndMarksRowAsSentOnlyAfterKafkaSuccess() {
        // Given: SampleDB에서 조회된 미전송 제조 이벤트
        ManufacturingRawEvent payload = new ManufacturingRawEvent(
                7L, "EVT-007", LocalDateTime.of(2026, 6, 18, 10, 0),
                10L, 20L, ProcessCode.PRESS, "PRESS-01", "EQ_PRESS_01",
                "HYDRAULIC_PRESS", "RUNNING", "PROCESS_STATUS",
                Map.of("temperature", 42.5)
        );
        StoredManufacturingEvent event = new StoredManufacturingEvent(payload, false, null);
        KafkaPublishResult publishResult = new KafkaPublishResult(
                "factory.manufacturing.raw",
                1,
                10L,
                "EQ_PRESS_01",
                "EVT-007"
        );

        // Given: DB 조회, Kafka 성공, DB 상태 갱신 결과 Mock
        when(repository.findById(7L)).thenReturn(Optional.of(event));
        when(producer.sendRaw(event))
                .thenReturn(CompletableFuture.completedFuture(publishResult));
        when(repository.markSent(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        // When: 지정 SampleDB 이벤트 발행
        KafkaPublishResult actual = service.sendById(7L).join();

        // Then: Kafka 결과 반환 및 성공 이후 markSent 호출 검증
        assertThat(actual).isSameAs(publishResult);
        verify(producer).sendRaw(event);
        verify(repository).markSent(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("SampleDB 이벤트 미존재 시 EVENT_NOT_FOUND ErrorCode 반환")
    void throwsKafkaErrorCodeWhenEventDoesNotExist() {
        // Given: 지정 PK의 SampleDB 이벤트 미존재
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When & Then: 프로젝트 표준 Kafka ErrorCode 검증
        assertThatThrownBy(() -> service.sendById(999L))
                .isInstanceOf(KafkaException.class)
                .satisfies(exception -> assertThat(((KafkaException) exception).getCode())
                        .isEqualTo(KafkaErrorStatus.EVENT_NOT_FOUND));
    }

    @Test
    @DisplayName("Kafka 성공 후 DB 상태 갱신 실패 시 EVENT_STATUS_UPDATE_FAILED 반환")
    void throwsKafkaErrorCodeWhenSentStatusUpdateFails() {
        // Given: Kafka 발행에는 성공하지만 SampleDB 갱신 건수가 0인 상황
        ManufacturingRawEvent payload = new ManufacturingRawEvent(
                7L, "EVT-007", LocalDateTime.of(2026, 6, 18, 10, 0),
                10L, 20L, ProcessCode.PRESS, "PRESS-01", "EQ_PRESS_01",
                "HYDRAULIC_PRESS", "RUNNING", "PROCESS_STATUS", Map.of()
        );
        StoredManufacturingEvent event = new StoredManufacturingEvent(payload, false, null);
        KafkaPublishResult publishResult = new KafkaPublishResult(
                "factory.manufacturing.raw", 1, 10L, "EQ_PRESS_01", "EVT-007"
        );

        when(repository.findById(7L)).thenReturn(Optional.of(event));
        when(producer.sendRaw(event))
                .thenReturn(CompletableFuture.completedFuture(publishResult));
        when(repository.markSent(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        // When & Then: CompletableFuture 내부 Kafka ErrorCode 검증
        assertThatThrownBy(() -> service.sendById(7L).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(KafkaException.class)
                .satisfies(exception -> {
                    KafkaException cause = (KafkaException) exception.getCause();
                    assertThat(cause.getCode())
                            .isEqualTo(KafkaErrorStatus.EVENT_STATUS_UPDATE_FAILED);
                });
    }
}

package com.aims.assembly.kafka;

import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import com.aims.assembly.domain.enums.ProcessCode;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * 실제 Kafka broker 대신 KafkaTemplate Mock을 사용한 Producer 단위 테스트.
 * message key, payload, broker 메타데이터 변환, Kafka ErrorCode 처리 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("제조 Kafka Producer 단위 테스트")
class ManufacturingKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SendResult<String, String> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    @Mock
    private KafkaMessageTraceStore traceStore;

    @Test
    @DisplayName("불량 전이 분석은 carId를 message key로 사용")
    void usesCarIdForDefectTransferAnalysis() {
        ManufacturingKafkaProducer producer =
                new ManufacturingKafkaProducer(
                        kafkaTemplate,
                        objectMapper,
                        new KafkaCustomProperties(),
                        traceStore
                );
        ManufacturingAnalysisEvent event = mock(ManufacturingAnalysisEvent.class);
        when(event.analysisType()).thenReturn("DEFECT_TRANSFER_PREDICTION");
        when(event.carId()).thenReturn("CAR-000001");

        assertThat(producer.analysisMessageKey(event)).isEqualTo("CAR-000001");
    }

    @Test
    @DisplayName("불량 전이 분석의 carId가 없으면 carMasterId를 message key로 사용")
    void fallsBackToCarMasterIdWhenCarIdIsMissing() {
        ManufacturingKafkaProducer producer =
                new ManufacturingKafkaProducer(
                        kafkaTemplate,
                        objectMapper,
                        new KafkaCustomProperties(),
                        traceStore
                );
        ManufacturingAnalysisEvent event = mock(ManufacturingAnalysisEvent.class);
        when(event.analysisType()).thenReturn("DEFECT_TRANSFER_PREDICTION");
        when(event.carMasterId()).thenReturn(701L);

        assertThat(producer.analysisMessageKey(event)).isEqualTo("CAR_MASTER-701");
    }

    @Test
    @DisplayName("불량 전이 분석의 차량 식별자가 모두 없으면 equipmentCode를 사용")
    void fallsBackToEquipmentCodeWhenVehicleIdentifiersAreMissing() {
        ManufacturingKafkaProducer producer =
                new ManufacturingKafkaProducer(
                        kafkaTemplate,
                        objectMapper,
                        new KafkaCustomProperties(),
                        traceStore
                );
        ManufacturingAnalysisEvent event = mock(ManufacturingAnalysisEvent.class);
        when(event.analysisType()).thenReturn("DEFECT_TRANSFER_PREDICTION");
        when(event.equipmentCode()).thenReturn("EQ_PRESS_001");

        assertThat(producer.analysisMessageKey(event)).isEqualTo("EQ_PRESS_001");
    }

    @Test
    @DisplayName("raw 이벤트 발행 시 equipmentCode를 message key로 사용")
    void sendsRawEventUsingEquipmentCodeAsMessageKey() {
        // Given: SampleDB 엔티티 컬럼 기반 raw 이벤트
        KafkaCustomProperties properties = new KafkaCustomProperties();
        ManufacturingKafkaProducer producer =
                new ManufacturingKafkaProducer(kafkaTemplate, objectMapper, properties, traceStore);
        ManufacturingRawEvent payload = new ManufacturingRawEvent(
                1L, "EVT-001", LocalDateTime.of(2026, 6, 18, 10, 0),
                10L, 20L, ProcessCode.PRESS, "PRESS-01", "EQ_PRESS_01",
                "HYDRAULIC_PRESS", "RUNNING", "PROCESS_STATUS",
                Map.of("temperature", 42.5)
        );
        StoredManufacturingEvent event = new StoredManufacturingEvent(payload, false, null);

        // Given: JSON 직렬화 및 Kafka broker 성공 응답 Mock
        when(objectMapper.writeValueAsString(payload))
                .thenReturn("{\"eventId\":\"EVT-001\"}");
        when(kafkaTemplate.send(
                "factory.manufacturing.raw",
                "EQ_PRESS_01",
                "{\"eventId\":\"EVT-001\"}"
        )).thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn("factory.manufacturing.raw");
        when(recordMetadata.partition()).thenReturn(3);
        when(recordMetadata.offset()).thenReturn(10L);

        // When: raw 토픽 발행
        KafkaPublishResult result = producer.sendRaw(event).join();

        // Then: raw 토픽, equipmentCode key, 직렬화 payload 전송 검증
        verify(kafkaTemplate).send(
                "factory.manufacturing.raw",
                "EQ_PRESS_01",
                "{\"eventId\":\"EVT-001\"}"
        );

        // Then: 현재 Pod의 발행 추적 이력 기록 검증
        verify(traceStore).recordProduced(
                "factory.manufacturing.raw",
                3,
                10L,
                "EQ_PRESS_01",
                "EVT-001",
                "{\"eventId\":\"EVT-001\"}"
        );

        // Then: broker RecordMetadata 기반 API 응답 검증
        assertThat(result.topic()).isEqualTo("factory.manufacturing.raw");
        assertThat(result.partition()).isEqualTo(3);
        assertThat(result.offset()).isEqualTo(10L);
        assertThat(result.messageKey()).isEqualTo("EQ_PRESS_01");
    }

    @Test
    @DisplayName("Kafka broker 발행 실패를 MESSAGE_PUBLISH_FAILED ErrorCode로 변환")
    void convertsBrokerPublishFailureToKafkaErrorCode() {
        // Given: 발행할 raw 이벤트
        KafkaCustomProperties properties = new KafkaCustomProperties();
        ManufacturingKafkaProducer producer =
                new ManufacturingKafkaProducer(kafkaTemplate, objectMapper, properties, traceStore);
        ManufacturingRawEvent payload = new ManufacturingRawEvent(
                1L, "EVT-001", LocalDateTime.of(2026, 6, 18, 10, 0),
                10L, 20L, ProcessCode.PRESS, "PRESS-01", "EQ_PRESS_01",
                "HYDRAULIC_PRESS", "RUNNING", "PROCESS_STATUS", Map.of()
        );
        StoredManufacturingEvent event = new StoredManufacturingEvent(payload, false, null);

        // Given: broker 연결 실패 CompletableFuture Mock
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"eventId\":\"EVT-001\"}");
        when(kafkaTemplate.send(
                "factory.manufacturing.raw",
                "EQ_PRESS_01",
                "{\"eventId\":\"EVT-001\"}"
        )).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        // When & Then: 비동기 실패 원인의 Kafka ErrorCode 변환 검증
        assertThatThrownBy(() -> producer.sendRaw(event).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(KafkaException.class)
                .satisfies(exception -> {
                    KafkaException cause = (KafkaException) exception.getCause();
                    assertThat(cause.getCode())
                            .isEqualTo(KafkaErrorStatus.MESSAGE_PUBLISH_FAILED);
                });
    }
}

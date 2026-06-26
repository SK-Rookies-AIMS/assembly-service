package com.aims.assembly.kafka;

import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 제조 파이프라인 토픽별 메시지 발행.
 * 메시지 직렬화, key 선택, broker 저장 메타데이터 반환 담당.
 */
public class ManufacturingKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaCustomProperties kafkaProperties;
    private final KafkaMessageTraceStore traceStore;

    public CompletableFuture<KafkaPublishResult> sendRaw(
            StoredManufacturingEvent event,
            LocalDateTime eventTime
    ) {
        ManufacturingRawEvent payload = withPublishedEventTime(event.payload(), eventTime);
        // Route metadata comes from table columns; eventJson remains the immutable JSON column.
        return send(
                // 원천 제조 이벤트 토픽 선택
                kafkaProperties.getTopics().getRaw().getName(),
                requiredLongKey(payload.carMasterId(), "carMasterId", payload.eventId()),
                // 전체 파이프라인 추적용 eventId
                payload.eventId(),
                payload
        );
    }


    private ManufacturingRawEvent withPublishedEventTime(
            ManufacturingRawEvent event,
            LocalDateTime eventTime
    ) {
        return new ManufacturingRawEvent(
                event.id(),
                event.eventId(),
                eventTime,
                event.carMasterId(),
                event.equipmentId(),
                event.processCode(),
                event.equipmentCode(),
                event.equipmentType(),
                event.equipmentStatus(),
                event.eventType(),
                event.eventJson()
        );
    }

    public CompletableFuture<KafkaPublishResult> sendAnalysis(ManufacturingAnalysisEvent event) {
        String key = analysisMessageKey(event);

        // 분석 결과 토픽 발행
        return send(
                kafkaProperties.getTopics().getAnalysis().getName(),
                key,
                event.eventId(),
                event
        );
    }

    String analysisMessageKey(ManufacturingAnalysisEvent event) {
        return requiredLongKey(event.carMasterId(), "carMasterId", event.eventId());
    }

    public CompletableFuture<KafkaPublishResult> sendAlert(ManufacturingAlertEvent event) {
        return send(
                kafkaProperties.getTopics().getAlert().getName(),
                requiredTextKey(event.alertId(), "alertId", event.eventId()),
                event.eventId(),
                event
        );
    }

    public CompletableFuture<KafkaPublishResult> sendEquipment(EquipmentStatusEvent event) {
        return send(
                kafkaProperties.getTopics().getEquipment().getName(),
                requiredLongKey(event.equipmentId(), "equipmentId", event.eventId()),
                event.eventId(),
                event
        );
    }

    private CompletableFuture<KafkaPublishResult> send(
            String topic,
            String key,
            String eventId,
            Object payload
    ) {
        // 문자열 payload는 원문 유지, 객체 payload는 JSON 직렬화
        String message;
        try {
            message = payload instanceof String text
                    ? text
                    : objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            throw new KafkaException(
                    KafkaErrorStatus.MESSAGE_SERIALIZATION_FAILED,
                    KafkaErrorStatus.MESSAGE_SERIALIZATION_FAILED.getMessage(),
                    exception
            );
        }

        // 비동기 Kafka 발행 및 broker 저장 결과 대기
        try {
            return kafkaTemplate.send(topic, key, message)
                .handle((result, exception) -> {
                    if (exception != null) {
                        throw new KafkaException(
                                KafkaErrorStatus.MESSAGE_PUBLISH_FAILED,
                                "Kafka 메시지 전송에 실패했습니다. topic=" + topic
                                        + ", key=" + key,
                                unwrap(exception)
                        );
                    }

                    // 현재 Pod의 개발·진단용 발행 이력 기록
                    traceStore.recordProduced(
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            key,
                            eventId,
                            message
                    );

                    // 실제 broker가 반환한 topic/partition/offset 응답 구성
                    return new KafkaPublishResult(
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            key,
                            eventId
                    );
                });
        } catch (RuntimeException exception) {
            if (exception instanceof KafkaException kafkaException) {
                throw kafkaException;
            }
            throw new KafkaException(
                    KafkaErrorStatus.MESSAGE_PUBLISH_FAILED,
                    "Kafka 메시지 전송에 실패했습니다. topic=" + topic + ", key=" + key,
                    exception
            );
        }
    }

    private Throwable unwrap(Throwable exception) {
        // CompletableFuture CompletionException 내부 원인 추출
        return exception.getCause() == null ? exception : exception.getCause();
    }

    private String requiredLongKey(Long value, String fieldName, String eventId) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Kafka message key field must not be null. field="
                            + fieldName + ", eventId=" + eventId
            );
        }
        return String.valueOf(value);
    }

    private String requiredTextKey(String value, String fieldName, String eventId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka message key field must not be blank. field="
                            + fieldName + ", eventId=" + eventId
            );
        }
        return value;
    }
}

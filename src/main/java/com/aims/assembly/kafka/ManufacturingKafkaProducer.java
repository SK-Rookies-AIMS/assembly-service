package com.aims.assembly.kafka;

import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
/**
 * 제조 파이프라인 토픽별 메시지 발행.
 * 메시지 직렬화, key 선택, broker 저장 메타데이터 반환 담당.
 */
public class ManufacturingKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaCustomProperties kafkaProperties;
    private final KafkaMessageTraceStore traceStore;

    public CompletableFuture<KafkaPublishResult> sendRaw(StoredManufacturingEvent event) {
        // SampleDB 엔티티 컬럼과 event_json 결합 payload 선택
        // equipmentCode key 사용을 통한 동일 설비 이벤트의 동일 partition 배치
        return send(
                // 원천 제조 이벤트 토픽 선택
                kafkaProperties.getTopics().getRaw().getName(),
                // 설비별 순서 보장용 message key
                event.equipmentCode(),
                // 전체 파이프라인 추적용 eventId
                event.eventId(),
                event.payload()
        );
    }

    public CompletableFuture<KafkaPublishResult> sendAnalysis(ManufacturingAnalysisEvent event) {
        // 불량 전이 분석의 차량 단위 순서 보장을 위한 carId key 선택
        // 일반 공정 분석의 설비 단위 순서 보장을 위한 equipmentCode key 선택
        String key = "DEFECT_TRANSFER_PREDICTION".equals(event.analysisType())
                ? event.carId()
                : event.equipmentCode();

        // 분석 결과 토픽 발행
        return send(
                kafkaProperties.getTopics().getAnalysis().getName(),
                key,
                event.eventId(),
                event
        );
    }

    public CompletableFuture<KafkaPublishResult> sendAlert(ManufacturingAlertEvent event) {
        // 동일 설비 알림 순서 보장을 위한 equipmentCode key 사용
        return send(
                kafkaProperties.getTopics().getAlert().getName(),
                event.equipmentCode(),
                event.eventId(),
                event
        );
    }

    public CompletableFuture<KafkaPublishResult> sendEquipment(EquipmentStatusEvent event) {
        // 동일 설비 상태 변경 순서 보장을 위한 equipmentCode key 사용
        return send(
                kafkaProperties.getTopics().getEquipment().getName(),
                event.equipmentCode(),
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
}

package com.aims.assembly.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 개발·검증용 최근 Kafka 송수신 이력 보관.
 * 현재 Pod 메모리에 최대 200건 저장, 운영 영구 저장 용도 제외.
 */
@Component
public class KafkaMessageTraceStore {

    private static final int MAX_ENTRIES = 200;

    private final ConcurrentLinkedDeque<KafkaMessageTrace> traces = new ConcurrentLinkedDeque<>();

    public void recordProduced(
            String topic,
            int partition,
            long offset,
            String key,
            String eventId,
            String payload
    ) {
        // Producer 성공 결과와 전송 payload 기록
        add(new KafkaMessageTrace(
                LocalDateTime.now(),
                "PRODUCED",
                topic,
                partition,
                offset,
                key,
                eventId,
                null,
                abbreviate(payload)
        ));
    }

    public void recordConsumed(
            ConsumerRecord<String, String> record,
            String groupId,
            String eventId
    ) {
        // Consumer 수신 위치와 Consumer Group 기록
        add(new KafkaMessageTrace(
                LocalDateTime.now(),
                "CONSUMED",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                eventId,
                groupId,
                abbreviate(record.value())
        ));
    }

    public List<KafkaMessageTrace> findRecent(String eventId) {
        // eventId 미지정 시 전체 조회, 지정 시 단일 파이프라인 이력 필터링
        return traces.stream()
                .filter(trace -> eventId == null || eventId.isBlank() || eventId.equals(trace.eventId()))
                .toList();
    }

    private void add(KafkaMessageTrace trace) {
        // 최신 이력 우선 저장
        traces.addFirst(trace);

        // 최대 200건 초과 시 가장 오래된 이력 제거
        while (traces.size() > MAX_ENTRIES) {
            traces.pollLast();
        }
    }

    private String abbreviate(String payload) {
        // Pod 메모리 과다 사용 방지를 위한 payload 길이 제한
        if (payload == null || payload.length() <= 2_000) {
            return payload;
        }
        return payload.substring(0, 2_000) + "...";
    }

    public record KafkaMessageTrace(
            LocalDateTime recordedAt,
            String direction,
            String topic,
            int partition,
            long offset,
            String messageKey,
            String eventId,
            String consumerGroup,
            String payload
    ) {
    }
}

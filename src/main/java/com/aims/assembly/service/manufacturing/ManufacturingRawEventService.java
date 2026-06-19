package com.aims.assembly.service.manufacturing;

import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SampleDB 제조 이벤트 조회, raw Kafka 발행, 전송 상태 갱신.
 */
@Service
@RequiredArgsConstructor
public class ManufacturingRawEventService {

    private final ManufacturingEventJsonRepository repository;
    private final ManufacturingKafkaProducer producer;

    public List<StoredManufacturingEvent> findEvents(int limit) {
        // 과도한 SampleDB 조회 방지를 위한 1~100건 범위 제한
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findRecent(safeLimit);
    }

    public StoredManufacturingEvent findFirstUnsent() {
        // Scheduler 및 테스트 API용 다음 미전송 이벤트 조회
        return repository.findFirstUnsent()
                .orElseThrow(() -> new KafkaException(
                        KafkaErrorStatus.UNSENT_EVENT_NOT_FOUND
                ));
    }

    public CompletableFuture<KafkaPublishResult> sendById(long id) {
        // 테스트 API에서 지정한 SampleDB 이벤트 조회
        StoredManufacturingEvent event = repository.findById(id)
                .orElseThrow(() -> new KafkaException(
                        KafkaErrorStatus.EVENT_NOT_FOUND,
                        "제조 이벤트를 찾을 수 없습니다. id=" + id
                ));
        return sendAndMark(event);
    }

    public CompletableFuture<KafkaPublishResult> sendNextUnsent() {
        // event_time 순 다음 미전송 이벤트 1건 발행
        return sendAndMark(findFirstUnsent());
    }

    public CompletableFuture<List<KafkaPublishResult>> sendNextUnsentBatch(int batchSize) {
        // PRD의 개발·시연·부하 테스트 모드에 맞게 1~1000건 범위로 제한
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1_000));
        List<StoredManufacturingEvent> events = repository.findUnsent(safeBatchSize);

        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // 조회 순서대로 발행을 시작하고 모든 broker 응답이 끝난 뒤 결과 반환
        List<CompletableFuture<KafkaPublishResult>> futures = events.stream()
                .map(this::sendAndMark)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    private CompletableFuture<KafkaPublishResult> sendAndMark(StoredManufacturingEvent event) {
        // Kafka broker 저장 요청
        return producer.sendRaw(event)
                .thenApply(result -> {
                    // broker 성공 응답 이후에만 SampleDB 전송 상태 갱신
                    int updated = repository.markSent(event.id(), LocalDateTime.now());

                    // 대상 행 미갱신 시 데이터 정합성 오류 처리
                    if (updated != 1) {
                        throw new KafkaException(
                                KafkaErrorStatus.EVENT_STATUS_UPDATE_FAILED,
                                "제조 이벤트 전송 상태 갱신에 실패했습니다. id=" + event.id()
                        );
                    }

                    // Kafka topic/partition/offset 메타데이터 반환
                    return result;
                });
    }
}

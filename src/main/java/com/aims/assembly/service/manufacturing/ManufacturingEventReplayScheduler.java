package com.aims.assembly.service.manufacturing;

import com.aims.assembly.properties.KafkaCustomProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SampleDB의 미전송 제조 이벤트를 event_time 순서로 Kafka에 재생한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.kafka.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class ManufacturingEventReplayScheduler {

    private final ManufacturingRawEventService rawEventService;
    private final KafkaCustomProperties kafkaProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.kafka.scheduler.fixed-delay-ms:5000}")
    public void replay() {
        // 이전 비동기 발행 작업이 끝나지 않았으면 중복 조회와 중복 발행을 방지한다.
        if (!running.compareAndSet(false, true)) {
            log.debug("이전 제조 이벤트 재생 작업이 진행 중이므로 이번 실행을 건너뜁니다.");
            return;
        }

        int batchSize = kafkaProperties.getScheduler().getBatchSize();
        rawEventService.sendNextUnsentBatch(batchSize)
                .whenComplete((results, exception) -> {
                    try {
                        if (exception != null) {
                            log.error("제조 이벤트 자동 재생 중 오류가 발생했습니다.", exception);
                            return;
                        }
                        if (!results.isEmpty()) {
                            log.info(
                                    "SampleDB 제조 이벤트 {}건을 Kafka raw 토픽으로 발행했습니다.",
                                    results.size()
                            );
                        }
                    } finally {
                        running.set(false);
                    }
                });
    }
}

package com.aims.assembly.service.manufacturing;

import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import com.aims.assembly.dto.kafka.StoredManufacturingEventResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ManufacturingRawEventService {

    private final ManufacturingEventJsonRepository repository;
    private final ManufacturingKafkaProducer producer;
    private final KafkaCustomProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ManufacturingRawEventService(
            ManufacturingEventJsonRepository repository,
            ManufacturingKafkaProducer producer,
            KafkaCustomProperties properties,
            @Qualifier("sampleTransactionManager") PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.producer = producer;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<StoredManufacturingEventResponse> findEvents(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 100))).stream()
                .map(StoredManufacturingEventResponse::from).toList();
    }

    public StoredManufacturingEventResponse findFirstReady() {
        return repository.findFirstReady(LocalDateTime.now(), maxRetries())
                .map(StoredManufacturingEventResponse::from)
                .orElseThrow(() -> new KafkaException(KafkaErrorStatus.UNSENT_EVENT_NOT_FOUND));
    }

    public CompletableFuture<KafkaPublishResult> sendById(long id) {
        return execute(() -> requireSuccess(transactionTemplate.execute(status -> {
            StoredManufacturingEvent event = repository.findReadyByIdForUpdate(id)
                    .orElseThrow(() -> new KafkaException(
                            KafkaErrorStatus.EVENT_NOT_FOUND,
                            "READY manufacturing event not found. id=" + id));
            return publishLocked(event);
        })));
    }

    public CompletableFuture<KafkaPublishResult> sendNextReady() {
        return execute(() -> requireSuccess(transactionTemplate.execute(status -> {
            repository.prepareDispatchablePendingEvents(LocalDateTime.now(), 1);
            StoredManufacturingEvent event = repository
                    .findReadyForUpdate(LocalDateTime.now(), 1, maxRetries()).stream().findFirst()
                    .orElseThrow(() -> new KafkaException(KafkaErrorStatus.UNSENT_EVENT_NOT_FOUND));
            return publishLocked(event);
        })));
    }

    public CompletableFuture<List<KafkaPublishResult>> sendNextReadyBatch(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 1_000));
        return execute(() -> transactionTemplate.execute(status -> {
            repository.prepareDispatchablePendingEvents(LocalDateTime.now(), limit);
            List<StoredManufacturingEvent> events = repository.findReadyForUpdate(
                    LocalDateTime.now(), limit, maxRetries());
            List<KafkaPublishResult> results = new ArrayList<>(events.size());
            for (StoredManufacturingEvent event : events) {
                PublishOutcome outcome = publishLocked(event);
                if (outcome.result() != null) results.add(outcome.result());
            }
            return List.copyOf(results);
        }));
    }

    private PublishOutcome publishLocked(StoredManufacturingEvent event) {
        try {
            // eventTime을 여기서 한 번 생성 → Kafka payload와 SampleDB event_time이 동일한 값을 가짐
            LocalDateTime eventTime = LocalDateTime.now();
            KafkaPublishResult result = producer.sendRaw(event, eventTime).join();
            if (repository.markSent(event.id(), eventTime) != 1) {
                throw new KafkaException(KafkaErrorStatus.EVENT_STATUS_UPDATE_FAILED,
                        "Failed to mark manufacturing event as SENT. id=" + event.id());
            }
            return new PublishOutcome(result, null);
        } catch (RuntimeException exception) {
            repository.markPublishFailed(event.id(), rootMessage(exception));
            return new PublishOutcome(null, exception);
        }
    }

    private KafkaPublishResult requireSuccess(PublishOutcome outcome) {
        if (outcome.failure() != null) throw outcome.failure();
        return outcome.result();
    }

    private int maxRetries() {
        return Math.max(1, properties.getScheduler().getMaxRetries());
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private <T> CompletableFuture<T> execute(java.util.concurrent.Callable<T> action) {
        try {
            return CompletableFuture.completedFuture(action.call());
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private record PublishOutcome(KafkaPublishResult result, RuntimeException failure) {}
}

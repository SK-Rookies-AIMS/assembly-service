package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.enums.*;
import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManufacturingRawEventServiceTest {
    private final ManufacturingEventJsonRepository repository = mock(ManufacturingEventJsonRepository.class);
    private final ManufacturingKafkaProducer producer = mock(ManufacturingKafkaProducer.class);
    private ManufacturingRawEventService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ManufacturingRawEventService(repository, producer,
                new KafkaCustomProperties(), tx);
    }

    @Test
    void marksSentOnlyAfterBrokerSuccess() {
        StoredManufacturingEvent event = event();
        KafkaPublishResult result = new KafkaPublishResult("factory.manufacturing.raw", 0, 1, "CAR-1", "EVT-1");
        when(repository.findReadyByIdForUpdate(7)).thenReturn(Optional.of(event));
        when(producer.sendRaw(event)).thenReturn(CompletableFuture.completedFuture(result));
        when(repository.markSent(7)).thenReturn(1);

        assertThat(service.sendById(7).join()).isSameAs(result);
        verify(repository).markSent(7);
        verify(repository, never()).markPublishFailed(anyLong(), anyString());
    }

    @Test
    void recordsRetryAndErrorAfterBrokerFailure() {
        StoredManufacturingEvent event = event();
        when(repository.findReadyByIdForUpdate(7)).thenReturn(Optional.of(event));
        when(producer.sendRaw(event)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> service.sendById(7).join()).hasRootCauseMessage("broker down");
        verify(repository).markPublishFailed(eq(7L), contains("broker down"));
        verify(repository, never()).markSent(anyLong());
    }

    @Test
    void preparesPendingEventsBeforeSchedulerSelectsReadyBatch() {
        when(repository.findReadyForUpdate(any(LocalDateTime.class), eq(1_000), eq(3)))
                .thenReturn(List.of());

        assertThat(service.sendNextReadyBatch(1_000).join()).isEmpty();

        var order = inOrder(repository);
        order.verify(repository).prepareDispatchablePendingEvents(any(LocalDateTime.class), eq(1_000));
        order.verify(repository).findReadyForUpdate(any(LocalDateTime.class), eq(1_000), eq(3));
    }

    private StoredManufacturingEvent event() {
        ManufacturingRawEvent raw = new ManufacturingRawEvent(7, "EVT-1",
                LocalDateTime.of(2026, 6, 17, 10, 0), 1L, 2L, ProcessCode.PRESS,
                "EQ-1", "HYDRAULIC_PRESS", null, null, Map.of("carId", "CAR-1"));
        return new StoredManufacturingEvent(raw, "{\"carId\":\"CAR-1\"}", "CAR-1",
                DispatchStatus.READY, AnalysisStatus.NOT_ANALYZED, false, 0, null);
    }
}

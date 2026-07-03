package com.aims.assembly.service.equipment;

import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EquipmentStateKafkaListener {

    private final ManufacturingKafkaProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(EquipmentStateService.EquipmentStateCommittedEvent committed) {
        producer.sendEquipment(committed.event()).join();
    }
}

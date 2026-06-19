package com.aims.assembly.kafka.model;

public record KafkaPublishResult(
        String topic,
        int partition,
        long offset,
        String messageKey,
        String eventId
) {
}

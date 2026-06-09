package com.aims.assembly.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaCustomProperties {

    private List<String> bootstrapServers = List.of("localhost:9092");
    private String groupId = "assembly-local";
    private String autoOffsetReset = "earliest";
}

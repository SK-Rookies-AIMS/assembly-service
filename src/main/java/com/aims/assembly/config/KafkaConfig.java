package com.aims.assembly.config;

import com.aims.assembly.properties.KafkaCustomProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Admin, Producer, Consumer 공통 설정.
 * 로컬 Kafka와 AWS MSK IAM 연결 설정을 app.kafka 속성으로 통합 관리한다.
 */
@EnableKafka
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaCustomProperties kafkaCustomProperties;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> properties = commonProperties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5_000);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        return new KafkaAdmin(properties);
    }

    @Bean
    public NewTopic manufacturingRawTopic() {
        return createTopic(kafkaCustomProperties.getTopics().getRaw());
    }

    @Bean
    public NewTopic manufacturingAnalysisTopic() {
        return createTopic(kafkaCustomProperties.getTopics().getAnalysis());
    }

    @Bean
    public NewTopic manufacturingAlertTopic() {
        return createTopic(kafkaCustomProperties.getTopics().getAlert());
    }

    @Bean
    public NewTopic manufacturingEquipmentTopic() {
        return createTopic(kafkaCustomProperties.getTopics().getEquipment());
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> properties = commonProperties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put("key.serializer.encoding", StandardCharsets.UTF_8.name());
        properties.put("value.serializer.encoding", StandardCharsets.UTF_8.name());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> properties = commonProperties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaCustomProperties.getAutoOffsetReset());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put("key.deserializer.encoding", StandardCharsets.UTF_8.name());
        properties.put("value.deserializer.encoding", StandardCharsets.UTF_8.name());
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private NewTopic createTopic(KafkaCustomProperties.Topic topic) {
        return TopicBuilder.name(topic.getName())
                .partitions(topic.getPartitions())
                .build();
    }

    private Map<String, Object> commonProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                kafkaCustomProperties.getSecurityProtocol()
        );
        putIfPresent(properties, "sasl.mechanism", kafkaCustomProperties.getSaslMechanism());
        putIfPresent(properties, "sasl.jaas.config", kafkaCustomProperties.getSaslJaasConfig());
        putIfPresent(
                properties,
                "sasl.client.callback.handler.class",
                kafkaCustomProperties.getSaslClientCallbackHandlerClass()
        );
        return properties;
    }

    private void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }
}

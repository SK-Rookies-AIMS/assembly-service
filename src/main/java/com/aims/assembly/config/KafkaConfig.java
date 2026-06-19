package com.aims.assembly.config;

import com.aims.assembly.properties.KafkaCustomProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@RequiredArgsConstructor
/**
 * Kafka Admin, Producer, Consumer 공통 설정.
 * 로컬 Kafka와 AWS MSK IAM 연결 설정을 app.kafka 속성으로 통합 관리.
 */
public class KafkaConfig {

    private final KafkaCustomProperties kafkaCustomProperties;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        // Admin/Producer/Consumer 공통 보안 속성 적용
        Map<String, Object> properties = commonProperties();

        // 토픽 생성·조회 대상 Kafka/MSK broker 지정
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());

        // broker 장애 시 애플리케이션 시작 및 진단 API의 장시간 대기 방지
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5_000);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);

        // NewTopic Bean 자동 반영용 KafkaAdmin 생성
        // 운영 IAM Role의 CreateTopic, DescribeTopic 권한 필요
        return new KafkaAdmin(properties);
    }

    @Bean
    public NewTopic manufacturingRawTopic() {
        // SampleDB 원천 제조 이벤트 토픽 생성 정보
        return createTopic(kafkaCustomProperties.getTopics().getRaw());
    }

    @Bean
    public NewTopic manufacturingAnalysisTopic() {
        // 공정 분석 결과 토픽 생성 정보
        return createTopic(kafkaCustomProperties.getTopics().getAnalysis());
    }

    @Bean
    public NewTopic manufacturingAlertTopic() {
        // 제조 위험 알림 토픽 생성 정보
        return createTopic(kafkaCustomProperties.getTopics().getAlert());
    }

    @Bean
    public NewTopic manufacturingEquipmentTopic() {
        // 설비 상태 결과 토픽 생성 정보
        return createTopic(kafkaCustomProperties.getTopics().getEquipment());
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        // MSK IAM 또는 로컬 Kafka 공통 보안 속성 적용
        Map<String, Object> properties = commonProperties();

        // Producer broker 및 문자열 직렬화 설정
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 리더와 ISR replica 기록 완료 후 전송 성공 처리
        properties.put(ProducerConfig.ACKS_CONFIG, "all");

        // 네트워크 재시도에 따른 중복 레코드 방지
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 일시적 broker 장애에 대한 Producer 내부 재시도 허용
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        // 제조 이벤트 발행용 공통 KafkaTemplate 생성
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        // MSK IAM 또는 로컬 Kafka 공통 보안 속성 적용
        Map<String, Object> properties = commonProperties();

        // Consumer broker와 초기 offset 정책 설정
        // 각 Listener의 Consumer Group은 @KafkaListener groupId에서 명시적으로 관리
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCustomProperties.getBootstrapServers());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaCustomProperties.getAutoOffsetReset());

        // Listener 처리 완료 이후 offset 커밋을 위한 자동 커밋 비활성화
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Kafka key/value 문자열 역직렬화 설정
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // 모든 @KafkaListener에 공통 ConsumerFactory 연결
        factory.setConsumerFactory(consumerFactory());

        // 레코드 단위 Listener 처리 성공 후 offset 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private NewTopic createTopic(KafkaCustomProperties.Topic topic) {
        // application.yaml의 토픽명과 파티션 수를 NewTopic으로 변환
        return TopicBuilder.name(topic.getName())
                .partitions(topic.getPartitions())
                .build();
    }

    private Map<String, Object> commonProperties() {
        Map<String, Object> properties = new HashMap<>();

        // Admin/Producer/Consumer 공통 전송 보안 프로토콜 설정
        properties.put(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                kafkaCustomProperties.getSecurityProtocol()
        );

        // AWS MSK IAM 사용 시에만 SASL 관련 속성 추가
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
        // 빈 SASL 설정의 Kafka Client 전달 방지
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }
}

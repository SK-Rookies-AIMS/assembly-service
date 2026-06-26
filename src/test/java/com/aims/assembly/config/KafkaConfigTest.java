package com.aims.assembly.config;

import com.aims.assembly.properties.KafkaCustomProperties;
import org.junit.jupiter.api.DisplayName;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Kafka broker 연결 없이 NewTopic Bean의 토픽명과 파티션 설정 검증.
 */
@DisplayName("Kafka 토픽 설정 단위 테스트")
class KafkaConfigTest {

    private final KafkaConfig kafkaConfig = new KafkaConfig(new KafkaCustomProperties());

    @Test
    @DisplayName("제조 파이프라인의 모든 토픽을 2개 파티션으로 생성")
    void createsPrdTopicsWithRecommendedPartitionCounts() {
        // Given: KafkaCustomProperties 기본 토픽 설정

        // When & Then: 토픽별 이름과 파티션 수 검증
        assertTopic(kafkaConfig.manufacturingRawTopic(), "factory.manufacturing.raw", 2);
        assertTopic(kafkaConfig.manufacturingAnalysisTopic(), "factory.manufacturing.analysis", 2);
        assertTopic(kafkaConfig.manufacturingEquipmentTopic(), "factory.equipment.status", 2);
        assertTopic(kafkaConfig.manufacturingAlertTopic(), "factory.manufacturing.alert", 2);
    }

    private void assertTopic(NewTopic topic, String expectedName, int expectedPartitions) {
        // NewTopic Bean에 설정된 논리 토픽명과 파티션 수 비교
        assertThat(topic.name()).isEqualTo(expectedName);
        assertThat(topic.numPartitions()).isEqualTo(expectedPartitions);
    }
}

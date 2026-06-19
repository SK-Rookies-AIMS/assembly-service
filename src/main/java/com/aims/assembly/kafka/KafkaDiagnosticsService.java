package com.aims.assembly.kafka;

import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 실제 Kafka/MSK 연결 및 토픽 메타데이터 조회.
 * 진단 API 호출 시 AdminClient 생성 후 즉시 종료.
 */
@Service
@RequiredArgsConstructor
public class KafkaDiagnosticsService {

    private final KafkaAdmin kafkaAdmin;
    private final KafkaCustomProperties properties;

    public KafkaBrokerStatus inspect() {
        // 진단 대상 제조 토픽 목록 구성
        List<String> topicNames = List.of(
                properties.getTopics().getRaw().getName(),
                properties.getTopics().getAnalysis().getName(),
                properties.getTopics().getEquipment().getName(),
                properties.getTopics().getAlert().getName()
        );

        // KafkaAdmin과 동일한 인증 설정을 사용하는 단기 AdminClient 생성
        try (Admin admin = KafkaAdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // 실제 연결된 Kafka cluster ID 조회
            String clusterId = admin.describeCluster().clusterId().get(5, TimeUnit.SECONDS);

            // 활성 broker node 수 조회
            int brokerCount = admin.describeCluster().nodes().get(5, TimeUnit.SECONDS).size();

            // 제조 토픽별 실제 메타데이터 조회
            Map<String, TopicDescription> descriptions =
                    admin.describeTopics(topicNames).allTopicNames().get(5, TimeUnit.SECONDS);

            // 토픽별 실제 partition 수 변환
            Map<String, Integer> partitions = new LinkedHashMap<>();
            topicNames.forEach(name ->
                    partitions.put(name, descriptions.get(name).partitions().size())
            );


            // 실제 broker 연결 및 토픽 상태 응답 구성
            return new KafkaBrokerStatus(
                    true,
                    clusterId,
                    brokerCount,
                    properties.getSecurityProtocol(),
                    partitions
            );
        } catch (Exception exception) {
            // 네트워크, IAM 인증, Describe 권한, 토픽 미존재 오류 통합 처리
            throw new KafkaException(
                    KafkaErrorStatus.BROKER_INSPECTION_FAILED,
                    KafkaErrorStatus.BROKER_INSPECTION_FAILED.getMessage(),
                    exception
            );
        }
    }

    public record KafkaBrokerStatus(
            boolean connected,
            String clusterId,
            int brokerCount,
            String securityProtocol,
            Map<String, Integer> topicPartitions
    ) {
    }
}

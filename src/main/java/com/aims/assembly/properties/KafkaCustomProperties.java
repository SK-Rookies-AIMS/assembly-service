package com.aims.assembly.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka")
/**
 * application.yaml의 app.kafka 설정 바인딩.
 */
public class KafkaCustomProperties {

    // Kafka/MSK bootstrap broker 목록
    private List<String> bootstrapServers = List.of("localhost:9092");
    // Consumer Group의 저장 offset이 없는 경우 시작 위치
    private String autoOffsetReset = "earliest";
    // 전체 Kafka Listener 시작 여부
    private boolean listenersEnabled = true;
    // 로컬 PLAINTEXT 또는 MSK SASL_SSL 설정
    private String securityProtocol = "PLAINTEXT";
    // AWS MSK IAM SASL mechanism
    private String saslMechanism;
    // AWS MSK IAM LoginModule 설정
    private String saslJaasConfig;
    // AWS MSK IAM callback handler 설정
    private String saslClientCallbackHandlerClass;
    // SampleDB 이벤트 자동 재생 Scheduler 설정
    private Scheduler scheduler = new Scheduler();
    // 제조 파이프라인 토픽별 설정
    private Topics topics = new Topics();

    @Getter
    @Setter
    public static class Scheduler {
        // 자동 재생 활성화 여부
        private boolean enabled = false;
        // 이전 작업 완료 후 다음 실행까지 대기 시간
        private long fixedDelayMs = 5_000;
        // 한 번에 조회하고 발행할 이벤트 수
        private int batchSize = 10;
    }

    @Getter
    @Setter
    public static class Topics {
        // 원천 제조 이벤트
        private Topic raw = new Topic("factory.manufacturing.raw", 2);
        // 공정 분석 결과
        private Topic analysis = new Topic("factory.manufacturing.analysis", 2);
        // 위험 알림
        private Topic alert = new Topic("factory.manufacturing.alert", 2);
        // 설비 상태
        private Topic equipment = new Topic("factory.manufacturing.equipment", 2);
    }

    @Getter
    @Setter
    public static class Topic {
        private String name;
        private int partitions;

        public Topic() {
        }

        public Topic(String name, int partitions) {
            this.name = name;
            this.partitions = partitions;
        }
    }
}

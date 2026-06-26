package com.aims.assembly.kafka;

import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.service.manufacturing.ManufacturingProcessRouter;
import com.aims.assembly.service.equipment.EquipmentStateService;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.service.manufacturing.ManufacturingAnalysisResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 제조 Kafka 파이프라인 토픽별 메시지 소비.
 * Consumer Group별 독립 처리와 후속 토픽 발행 담당.
 *
 * <p>alert 발행 경로:
 * <ul>
 *   <li>Case A: analysis topic → consumeAnalysisForAlert → riskScore WARNING/CRITICAL → alert 발행</li>
 *   <li>Case B: raw topic → consumeRaw → 설비 이상 상태(FAULT/STOPPED/ERROR/DOWN) 직접 감지 → alert 발행</li>
 * </ul>
 */
public class ManufacturingKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final ManufacturingEventAnalyzer analyzer;
    private final ManufacturingProcessRouter processRouter;
    private final ManufacturingKafkaProducer producer;
    private final KafkaMessageTraceStore traceStore;
    private final ManufacturingRawEventParser rawEventParser;
    private final EquipmentStateService equipmentStateService;
    private final ManufacturingEventJsonRepository eventRepository;
    private final ManufacturingAnalysisResultService analysisResultService;

    @KafkaListener(
            topics = "${app.kafka.topics.raw.name}",
            // 2개 partition의 병렬 처리를 위한 동일 Consumer Group 내 Consumer 2개 구성
            groupId = "manufacturing-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeRaw(ConsumerRecord<String, String> record) {
        // raw JSON을 엔티티 컬럼 기반 Kafka 모델로 역직렬화
        ManufacturingRawEvent event = rawEventParser.parse(record.value());

        // topic, partition, offset, Consumer Group 진단 이력 기록
        traceStore.recordConsumed(record, "manufacturing-consumer-group", event.eventId());

        // 수신 이벤트 식별 및 partition 위치 로그
        log.info(
                "Raw event received: process={}, equipment={}, partition={}, offset={}",
                event.processCode(),
                event.equipmentCode(),
                record.partition(),
                record.offset()
        );

        // processCode 기반 PRESS/BODY/PAINT/ASSEMBLY 서비스 선택
        // 공정 분석 결과 생성 후 analysis 토픽 발행
        try {
            // Case B: 설비 이상 상태(FAULT/STOPPED/ERROR/DOWN) 감지 시 raw 단계에서 즉시 alert 발행
            // 분석 결과(processRisk)와 무관하게 독립적으로 발행된다
            if (analyzer.isEquipmentAbnormal(event)) {
                log.warn(
                        "Equipment abnormal status detected in raw event: eventId={}, equipment={}",
                        event.eventId(), event.equipmentCode()
                );
                producer.sendAlert(analyzer.toEquipmentStatusAlert(event)).join();
            }

            ManufacturingAnalysisEvent analysis = processRouter.route(event);
            analysisResultService.save(event, analysis);
            producer.sendAnalysis(analysis).join();
            // NORMAL means analysis execution completed; defect/fault lives in the result payload.
            eventRepository.markAnalysisCompleted(
                    event.eventId(), isAbnormalAnalysis(analysis));
        } catch (RuntimeException exception) {
            eventRepository.markAnalysisFailed(event.eventId(), exception.getMessage());
            throw exception;
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.raw.name}",
            // 별도 Consumer Group을 통한 제조 분석 Group과 동일 raw 이벤트 독립 소비
            groupId = "ai-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeRawForAi(ConsumerRecord<String, String> record) {
        // AI 분석 입력용 raw 이벤트 역직렬화
        ManufacturingRawEvent event = rawEventParser.parse(record.value());

        // AI Consumer Group 수신 이력 기록
        traceStore.recordConsumed(record, "ai-consumer-group", event.eventId());
        log.info(
                "AI 분석 대상 이벤트 수신: event={}, equipment={}, partition={}",
                event.eventId(),
                event.equipmentCode(),
                record.partition()
        );

        // 병목 분석과 불량 전이 예측 결과를 각각 analysis 토픽에 발행
        producer.sendAnalysis(analyzer.analyzeBottleneck(event)).join();
        producer.sendAnalysis(analyzer.analyzeDefectTransfer(event)).join();
    }

    @KafkaListener(
            topics = "${app.kafka.topics.analysis.name}",
            groupId = "equipment-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeAnalysisForEquipment(ConsumerRecord<String, String> record) {
        // 공정 분석 결과 역직렬화
        ManufacturingAnalysisEvent analysis =
                readMessage(record, ManufacturingAnalysisEvent.class);

        // Equipment Consumer Group 수신 이력 기록
        traceStore.recordConsumed(record, "equipment-consumer-group", analysis.eventId());

        // Product/process defects do not mutate equipment state.
        if (analysis.analysisResult().isEquipmentFault()) {
            equipmentStateService.markFault(analysis);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.analysis.name}",
            groupId = "alert-analysis-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeAnalysisForAlert(ConsumerRecord<String, String> record) {
        // 알림 판정 대상 분석 결과 역직렬화
        ManufacturingAnalysisEvent analysis =
                readMessage(record, ManufacturingAnalysisEvent.class);

        // Alert Analysis Consumer Group 수신 이력 기록
        traceStore.recordConsumed(record, "alert-analysis-consumer-group", analysis.eventId());

        // Case A: processRisk 기반 riskScore가 WARNING/CRITICAL이면 alert 발행
        if (analyzer.requiresAlert(analysis)) {
            producer.sendAlert(analyzer.toAlertEvent(analysis)).join();
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.equipment.name}",
            groupId = "dashboard-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeEquipment(ConsumerRecord<String, String> record) {
        // 대시보드 반영 대상 설비 상태 이벤트 역직렬화
        EquipmentStatusEvent event = readMessage(record, EquipmentStatusEvent.class);

        // Dashboard Consumer Group 수신 이력 기록
        traceStore.recordConsumed(record, "dashboard-consumer-group", event.eventId());
        if ("RECOVERED".equals(event.changeType())) {
            eventRepository.restoreBlockedEvents(event.equipmentId(), event.equipmentCode());
        } else if ("FAULT".equals(event.changeType())) {
            eventRepository.blockReadyEvents(event.equipmentId(), event.equipmentCode());
        }
        log.info(
                "Equipment event received: equipment={}, health={}, risk={}, partition={}",
                event.equipmentCode(),
                event.healthStatus(),
                event.riskLevel(),
                record.partition()
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.alert.name}",
            groupId = "alert-notification-consumer-group",
            concurrency = "2",
            autoStartup = "${app.kafka.listeners-enabled:true}"
    )
    public void consumeAlert(ConsumerRecord<String, String> record) {
        // 실시간 알림 처리 대상 이벤트 역직렬화
        ManufacturingAlertEvent event =
                readMessage(record, ManufacturingAlertEvent.class);

        // Alert Notification Consumer Group 수신 이력 기록
        traceStore.recordConsumed(record, "alert-notification-consumer-group", event.eventId());
        log.warn(
                "Manufacturing alert received: type={}, equipment={}, level={}, score={}, partition={}",
                event.alertType(),
                event.equipmentCode(),
                event.riskLevel(),
                event.riskScore(),
                record.partition()
        );
    }

    private <T> T readMessage(ConsumerRecord<String, String> record, Class<T> messageType) {
        try {
            // Kafka 문자열 payload를 Listener별 메시지 타입으로 변환
            return objectMapper.readValue(record.value(), messageType);
        } catch (RuntimeException exception) {
            throw new KafkaException(
                    KafkaErrorStatus.MESSAGE_DESERIALIZATION_FAILED,
                    "Kafka 메시지 역직렬화에 실패했습니다. topic=" + record.topic()
                            + ", partition=" + record.partition()
                            + ", offset=" + record.offset(),
                    exception
            );
        }
    }

    static boolean isAbnormalAnalysis(ManufacturingAnalysisEvent analysis) {
        var result = analysis.analysisResult();
        return result.isAbnormal() || result.isQualityDefect()
                || result.isEquipmentFault() || result.isBottleneck()
                || result.isSequenceError();
    }
}

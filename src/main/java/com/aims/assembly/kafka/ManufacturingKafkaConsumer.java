package com.aims.assembly.kafka;

import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.service.manufacturing.ManufacturingProcessRouter;
import com.aims.assembly.service.equipment.EquipmentStateService;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository.StoredManufacturingEvent;
import com.aims.assembly.service.manufacturing.ManufacturingAnalysisResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
 *   <li>Case B: raw topic → consumeRaw → 설비 이상 상태(WARNING/STOPPED/FAULT) 직접 감지 → alert 발행</li>
 * </ul>
 */
public class ManufacturingKafkaConsumer {

    private static final Pattern OFFSET_DATE_TIME_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)(?:Z|[+-]\\d{2}:\\d{2})"
    );

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
        boolean analysisStatusUpdated = false;
        try {
            // Case B: 설비 이상 상태(WARNING/STOPPED/FAULT) 감지 시 raw 단계에서 즉시 alert 발행
            // 분석 결과(processRisk)와 무관하게 독립적으로 발행된다
            if (analyzer.isEquipmentAbnormal(event)) {
                log.warn(
                        "Equipment abnormal status detected in raw event: eventId={}, equipment={}",
                        event.eventId(), event.equipmentCode()
                );
                producer.sendEquipment(analyzer.toEquipmentStatusEvent(event)).join();
                producer.sendAlert(analyzer.toEquipmentStatusAlert(event)).join();
            }

            ManufacturingAnalysisEvent analysis = processRouter.route(event);
            analysisResultService.save(event, analysis);
            // NORMAL means analysis execution completed; defect/fault lives in the result payload.
            boolean abnormal = isAbnormalAnalysis(analysis);
            int analysisStatusUpdatedRows = eventRepository.markAnalysisCompleted(event.eventId(), abnormal);
            analysisStatusUpdated = analysisStatusUpdatedRows > 0;
            log.info(
                    "[MANUFACTURING_FLOW] analysis status update eventId={}, analysisStatus={}, updatedRows={}",
                    event.eventId(),
                    abnormal ? "ABNORMAL" : "NORMAL",
                    analysisStatusUpdatedRows
            );
            StoredManufacturingEvent storedEvent = eventRepository.findByEventId(event.eventId())
                    .orElse(null);
            Long carMasterId = storedEvent == null
                    ? event.carMasterId()
                    : storedEvent.payload().carMasterId();
            ProcessCode processCode = storedEvent == null
                    ? event.processCode()
                    : storedEvent.payload().processCode();
            log.info(
                    "[PROCESS_FLOW] current eventId={}, carMasterId={}, processCode={}, "
                            + "analysisStatus={}, dispatchStatus={}",
                    event.eventId(),
                    carMasterId,
                    processCode,
                    abnormal ? "ABNORMAL" : "NORMAL",
                    storedEvent == null ? "UNKNOWN" : storedEvent.dispatchStatus()
            );
            Long currentEventRowId = storedEvent == null ? null : storedEvent.id();

            transitionFollowingProcesses(event.eventId(), currentEventRowId, carMasterId, processCode, abnormal);
            producer.sendAnalysis(analysis).join();
            log.info(
                    "카프카 원시 이벤트 처리 완료: eventId={}, processCode={}, abnormal={}, analysisSent={}",
                    event.eventId(),
                    processCode,
                    abnormal,
                    true
            );
        } catch (RuntimeException exception) {
            if (!analysisStatusUpdated) {
                int updatedRows = eventRepository.markAnalysisFailed(event.eventId(), exception.getMessage());
                log.info(
                        "[MANUFACTURING_FLOW] analysis status failed eventId={}, updatedRows={}",
                        event.eventId(),
                        updatedRows
                );
            }
            throw exception;
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
        boolean alertSent = false;
        if (analyzer.requiresAlert(analysis)) {
            producer.sendAlert(analyzer.toAlertEvent(analysis)).join();
            alertSent = true;
        }
        log.info(
                "카프카 이상 탐지 분석 이벤트 처리 완료: eventId={}, processCode={}, riskLevel={}, alertSent={}",
                analysis.eventId(),
                analysis.processCode(),
                analysis.riskLevel(),
                alertSent
        );
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
        equipmentStateService.applyStatusEvent(event);
        if ("RECOVERED".equalsIgnoreCase(event.changeType())) {
            eventRepository.restoreBlockedEvents(event.equipmentId(), event.equipmentCode());
        } else if ("FAULT".equalsIgnoreCase(event.changeType()) && isBlockingEquipmentStatus(event.operationStatus())) {
            eventRepository.blockReadyEvents(event.equipmentId(), event.equipmentCode());
        }
        log.info(
                "Equipment event received: equipment={}, status={}, risk={}, partition={}",
                event.equipmentCode(),
                event.operationStatus(),
                event.riskLevel(),
                record.partition()
        );
        log.info(
                "카프카 설비 상태 이벤트 처리 완료: eventId={}, equipmentCode={}, changeType={}, operationStatus={}",
                event.eventId(),
                event.equipmentCode(),
                event.changeType(),
                event.operationStatus()
        );
    }

    private boolean isBlockingEquipmentStatus(String operationStatus) {
        if (operationStatus == null || operationStatus.isBlank()) {
            return false;
        }
        String normalized = operationStatus.trim().toUpperCase(Locale.ROOT);
        return "FAULT".equals(normalized) || "STOPPED".equals(normalized);
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
        log.info(
                "카프카 알림 이벤트 처리 완료: eventId={}, alertType={}, equipmentCode={}",
                event.eventId(),
                event.alertType(),
                event.equipmentCode()
        );
    }

    private <T> T readMessage(ConsumerRecord<String, String> record, Class<T> messageType) {
        try {
            // Kafka 문자열 payload를 Listener별 메시지 타입으로 변환
            return objectMapper.readValue(record.value(), messageType);
        } catch (RuntimeException exception) {
            String normalizedPayload = normalizeOffsetDateTimes(record.value());
            if (!normalizedPayload.equals(record.value())) {
                try {
                    return objectMapper.readValue(normalizedPayload, messageType);
                } catch (RuntimeException normalizedException) {
                    normalizedException.addSuppressed(exception);
                    exception = normalizedException;
                }
            }
            throw new KafkaException(
                    KafkaErrorStatus.MESSAGE_DESERIALIZATION_FAILED,
                    "Kafka 메시지 역직렬화에 실패했습니다. topic=" + record.topic()
                            + ", partition=" + record.partition()
                            + ", offset=" + record.offset(),
                    exception
            );
        }
    }

    static String normalizeOffsetDateTimes(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        return OFFSET_DATE_TIME_PATTERN.matcher(payload).replaceAll("$1");
    }

    static boolean isAbnormalAnalysis(ManufacturingAnalysisEvent analysis) {
        var result = analysis.analysisResult();
        return isNonNormalRiskLevel(analysis.riskLevel())
                || result.isAbnormal() || result.isQualityDefect()
                || result.isEquipmentFault() || result.isBottleneck()
                || result.isSequenceError();
    }

    private static boolean isNonNormalRiskLevel(String riskLevel) {
        return "WARNING".equalsIgnoreCase(riskLevel)
                || "MEDIUM".equalsIgnoreCase(riskLevel)
                || "CRITICAL".equalsIgnoreCase(riskLevel)
                || "HIGH".equalsIgnoreCase(riskLevel);
    }

    private void transitionFollowingProcesses(
            String eventId,
            Long currentEventRowId,
            Long carMasterId,
            ProcessCode currentProcessCode,
            boolean abnormal
    ) {
        if (abnormal) {
            int updatedRows = eventRepository.blockFollowingProcesses(
                    carMasterId,
                    followingProcesses(currentProcessCode)
            );
            log.info(
                    "[PROCESS_FLOW] followingProcesses={} block result updatedRows={}",
                    followingProcesses(currentProcessCode),
                    updatedRows
            );
            return;
        }

        ProcessCode nextProcess = nextProcess(currentProcessCode);
        String nextProcessCode = nextProcess == null ? null : nextProcess.name();

        log.info(
                "[PROCESS_FLOW] releaseNextProcessByCurrentRowId called eventId={}, currentEventRowId={}, carMasterId={}, "
                        + "currentProcess={}, nextProcess={}",
                eventId,
                currentEventRowId,
                carMasterId,
                currentProcessCode,
                nextProcessCode
        );

        // 정상이면 아무것도 하지 않음.
        // 다음 공정 READY는 AGV 도착 API에서 처리.
        log.info(
                "[PROCESS_FLOW] analysis completed. Waiting for AGV arrival. eventId={}",
                eventId
        );


        //int updatedRows = eventRepository.releaseNextProcessByCurrentRowId(currentEventRowId);
    }

    private ProcessCode nextProcess(ProcessCode currentProcessCode) {
        return switch (currentProcessCode) {
            case PRESS -> ProcessCode.BODY;
            case BODY -> ProcessCode.PAINT;
            case PAINT -> ProcessCode.ASSEMBLY;
            case ASSEMBLY -> null;
        };
    }

    private List<ProcessCode> followingProcesses(ProcessCode currentProcessCode) {
        return switch (currentProcessCode) {
            case PRESS -> List.of(ProcessCode.BODY, ProcessCode.PAINT, ProcessCode.ASSEMBLY);
            case BODY -> List.of(ProcessCode.PAINT, ProcessCode.ASSEMBLY);
            case PAINT -> List.of(ProcessCode.ASSEMBLY);
            case ASSEMBLY -> List.of();
        };
    }
}

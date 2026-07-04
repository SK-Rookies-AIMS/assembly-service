package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisDetailResponse;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisResultResponse;
import com.aims.assembly.dto.kafka.ManufacturingCarCompletionResponse;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.mapper.ManufacturingQueryResponseMapper;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisQueryRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturingAnalysisResultQueryService {
    private final ManufacturingAnalysisQueryRepository queryRepository;
    private final ManufacturingEventJsonRepository eventRepository;
    private final ManufacturingEventAnalyzer analyzer;
    private final KafkaCustomProperties kafkaProperties;

    public ManufacturingAnalysisResultResponse findLatestByEventId(String eventId) {
        ManufacturingAnalysisResultResponse response = queryRepository.findLatestByEventId(eventId)
                .map(ManufacturingAnalysisResultResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manufacturing analysis result not found. eventId=" + eventId
                ));
        log.info("제조 이상 탐지 결과 조회 완료: eventId={}", eventId);
        return response;
    }

    public List<ManufacturingAnalysisResultResponse> findRecent(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        List<ManufacturingAnalysisResultResponse> responses = queryRepository.findRecent(size)
                .stream()
                .map(ManufacturingAnalysisResultResponse::from)
                .toList();
        log.info("최근 제조 이상 탐지 결과 조회 완료: 건수={}", responses.size());
        return responses;
    }

    public ManufacturingCarCompletionResponse findCarCompletion(long carMasterId) {
        List<ManufacturingAnalysisResult> results = queryRepository.findByCarMasterId(carMasterId);
        Map<ProcessCode, ManufacturingAnalysisResult> latestByProcess = new EnumMap<>(ProcessCode.class);
        results.forEach(result -> latestByProcess.put(result.getProcessCode(), result));

        List<ManufacturingCarCompletionResponse.ProcessCompletion> processes =
                List.of(ProcessCode.PRESS, ProcessCode.BODY, ProcessCode.PAINT, ProcessCode.ASSEMBLY)
                        .stream()
                        .map(process -> processCompletion(process, latestByProcess.get(process)))
                        .toList();

        int normalCompleted = (int) processes.stream()
                .filter(ManufacturingCarCompletionResponse.ProcessCompletion::normalCompleted)
                .count();
        boolean hasAbnormal = processes.stream()
                .anyMatch(process -> Boolean.TRUE.equals(process.isAbnormal())
                        || process.severity() != null && !Severity.NORMAL.name().equals(process.severity()));
        boolean finalCompleted = normalCompleted == 4;
        boolean blocked = eventRepository.hasBlockedEventsByCarMasterId(carMasterId);

        String status;
        if (finalCompleted) {
            status = "FINAL_COMPLETED";
        } else if (hasAbnormal) {
            status = "ABNORMAL";
        } else if (blocked) {
            status = "BLOCKED";
        } else if (normalCompleted == 0) {
            status = "WAITING";
        } else {
            status = "IN_PROGRESS";
        }

        ManufacturingCarCompletionResponse response = ManufacturingQueryResponseMapper.toCarCompletionResponse(
                carMasterId,
                status,
                finalCompleted,
                normalCompleted,
                4,
                processes
        );
        log.info(
                "차량 공정 완료 상태 조회 완료: carMasterId={}, status={}, completed={}/{}",
                carMasterId,
                status,
                normalCompleted,
                4
        );
        return response;
    }

    private ManufacturingCarCompletionResponse.ProcessCompletion processCompletion(
            ProcessCode process,
            ManufacturingAnalysisResult result
    ) {
        if (result == null) {
            return new ManufacturingCarCompletionResponse.ProcessCompletion(
                    process, false, false, null, null, null, null
            );
        }
        boolean normalCompleted = Boolean.FALSE.equals(result.getIsAbnormal())
                && result.getSeverity() == Severity.NORMAL;
        return new ManufacturingCarCompletionResponse.ProcessCompletion(
                process,
                true,
                normalCompleted,
                result.getIsAbnormal(),
                result.getSeverity() == null ? null : result.getSeverity().name(),
                result.getEventId(),
                result.getAnalysisId()
        );
    }

    public ManufacturingAnalysisDetailResponse findDetailByEventId(String eventId) {
        ManufacturingAnalysisResult result = queryRepository.findLatestByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manufacturing analysis result not found. eventId=" + eventId
                ));
        var raw = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manufacturing raw event not found. eventId=" + eventId
                ));
        var detail = analyzer.analyzeDetail(raw.payload());
        String alertTopicName = kafkaProperties.getTopics().getAlert().getName();

        ManufacturingAnalysisDetailResponse.ProcessSpecificResult processSpecificResult =
                resolveProcessSpecificResult(result);

        ManufacturingAnalysisDetailResponse response = ManufacturingAnalysisDetailResponse.from(
                eventId,
                result.getProcessCode(),
                result.getSeverity() == null ? null : result.getSeverity().name(),
                result.getIsAbnormal(),
                result.getAbnormalType(),
                result.getAnalysisMessage(),
                detail,
                alertTopicName,
                processSpecificResult
        );
        log.info("제조 이상 탐지 상세 조회 완료: eventId={}, processCode={}", eventId, result.getProcessCode());
        return response;
    }

    private ManufacturingAnalysisDetailResponse.ProcessSpecificResult resolveProcessSpecificResult(
            ManufacturingAnalysisResult result
    ) {
        Long resultId = result.getId();
        String analysisId = result.getAnalysisId();
        return switch (result.getProcessCode()) {
            case PRESS -> ManufacturingQueryResponseMapper.toProcessSpecificResult(
                    queryRepository.hasPressDetail(analysisId), "press_analysis_result", resultId);
            case BODY -> ManufacturingQueryResponseMapper.toProcessSpecificResult(
                    queryRepository.hasBodyDetail(analysisId), "body_analysis_result", resultId);
            case PAINT -> ManufacturingQueryResponseMapper.toProcessSpecificResult(
                    queryRepository.hasPaintDetail(analysisId), "paint_analysis_result", resultId);
            case ASSEMBLY -> ManufacturingQueryResponseMapper.toProcessSpecificResult(
                    queryRepository.hasAssemblyDetail(analysisId), "assembly_analysis_result", resultId);
        };
    }
}

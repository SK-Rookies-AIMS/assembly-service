package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.domain.enums.Severity;
import com.aims.assembly.dto.kafka.ManufacturingCarCompletionResponse;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisDetailResponse;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisResultResponse;
import com.aims.assembly.kafka.ManufacturingEventAnalyzer;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.repository.analysis.AssemblyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.BodyAnalysisResultRepository;
import com.aims.assembly.repository.analysis.ManufacturingAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PaintAnalysisResultRepository;
import com.aims.assembly.repository.analysis.PressAnalysisResultRepository;
import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturingAnalysisResultQueryService {
    private final ManufacturingAnalysisResultRepository repository;
    private final ManufacturingEventJsonRepository eventRepository;
    private final ManufacturingEventAnalyzer analyzer;
    private final KafkaCustomProperties kafkaProperties;
    private final PressAnalysisResultRepository pressRepository;
    private final BodyAnalysisResultRepository bodyRepository;
    private final PaintAnalysisResultRepository paintRepository;
    private final AssemblyAnalysisResultRepository assemblyRepository;

    public ManufacturingAnalysisResultResponse findLatestByEventId(String eventId) {
        return repository.findFirstByEventIdOrderByAnalyzedAtDescCreatedAtDesc(eventId)
                .map(ManufacturingAnalysisResultResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Manufacturing analysis result not found. eventId=" + eventId
                ));
    }

    public List<ManufacturingAnalysisResultResponse> findRecent(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return repository.findAllByOrderByAnalyzedAtDescCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(ManufacturingAnalysisResultResponse::from)
                .toList();
    }

    public ManufacturingCarCompletionResponse findCarCompletion(long carMasterId) {
        List<ManufacturingAnalysisResult> results =
                repository.findByCarMasterIdOrderByEventTimeAscAnalyzedAtAsc(carMasterId);
        Map<ProcessCode, ManufacturingAnalysisResult> latestByProcess =
                new EnumMap<>(ProcessCode.class);
        results.stream()
                .sorted(Comparator
                        .comparing(
                                ManufacturingAnalysisResult::getEventTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                ManufacturingAnalysisResult::getAnalyzedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(result -> latestByProcess.put(result.getProcessCode(), result));

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
        return new ManufacturingCarCompletionResponse(
                carMasterId,
                status,
                finalCompleted,
                normalCompleted,
                4,
                processes
        );
    }

    private ManufacturingCarCompletionResponse.ProcessCompletion processCompletion(
            ProcessCode process,
            ManufacturingAnalysisResult result
    ) {
        if (result == null) {
            return new ManufacturingCarCompletionResponse.ProcessCompletion(
                    process, false, false, null, null, null, null);
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
        ManufacturingAnalysisResult result =
                repository.findFirstByEventIdOrderByAnalyzedAtDescCreatedAtDesc(eventId)
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

        return ManufacturingAnalysisDetailResponse.from(
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
    }

    /**
     * 공정별 결과 테이블 저장 여부 조회.
     * analysisId 기준으로 공정별 repository를 조회해 processSpecificResult 정보를 반환한다.
     */
    private ManufacturingAnalysisDetailResponse.ProcessSpecificResult resolveProcessSpecificResult(
            ManufacturingAnalysisResult result
    ) {
        Long resultId = result.getId();
        String analysisId = result.getAnalysisId();
        return switch (result.getProcessCode()) {
            case PRESS -> pressRepository.findByAnalysisResult_AnalysisId(analysisId)
                    .map(r -> new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            true, "press_analysis_result", resultId))
                    .orElse(new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            false, "press_analysis_result", null));
            case BODY -> bodyRepository.findByAnalysisResult_AnalysisId(analysisId)
                    .map(r -> new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            true, "body_analysis_result", resultId))
                    .orElse(new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            false, "body_analysis_result", null));
            case PAINT -> paintRepository.findByAnalysisResult_AnalysisId(analysisId)
                    .map(r -> new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            true, "paint_analysis_result", resultId))
                    .orElse(new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            false, "paint_analysis_result", null));
            case ASSEMBLY -> assemblyRepository.findByAnalysisResult_AnalysisId(analysisId)
                    .map(r -> new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            true, "assembly_analysis_result", resultId))
                    .orElse(new ManufacturingAnalysisDetailResponse.ProcessSpecificResult(
                            false, "assembly_analysis_result", null));
        };
    }
}

package com.aims.assembly.service.manufacturing;

import com.aims.assembly.domain.analysis.ManufacturingAnalysisResult;
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

package com.aims.assembly.controller.kafka;

import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.kafka.KafkaDiagnosticsService;
import com.aims.assembly.kafka.KafkaMessageTraceStore;
import com.aims.assembly.kafka.model.KafkaPublishResult;
import com.aims.assembly.properties.KafkaCustomProperties;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisDetailResponse;
import com.aims.assembly.dto.kafka.ManufacturingAnalysisResultResponse;
import com.aims.assembly.dto.kafka.StoredManufacturingEventResponse;
import com.aims.assembly.service.manufacturing.ManufacturingAnalysisResultQueryService;
import com.aims.assembly.service.manufacturing.ManufacturingRawEventService;
import com.aims.assembly.service.equipment.EquipmentStateService;
import com.aims.assembly.kafka.model.EquipmentStatusEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/kafka/manufacturing")
@RequiredArgsConstructor
@Tag(
        name = "Manufacturing Kafka Test",
        description = """
                제조 이벤트 Kafka 파이프라인을 검증하는 개발·운영 진단 API입니다.
                raw 이벤트 발행, 실제 MSK 연결 확인, 토픽/파티션 확인,
                현재 Pod가 송수신한 최근 메시지 추적 기능을 제공합니다.

                테스트 흐름:
                factory.manufacturing.raw
                → factory.manufacturing.analysis
                → factory.equipment.status / factory.manufacturing.alert
                """
)
public class ManufacturingKafkaTestController {

    private final ManufacturingRawEventService rawEventService;
    private final KafkaCustomProperties kafkaProperties;
    private final KafkaMessageTraceStore traceStore;
    private final KafkaDiagnosticsService diagnosticsService;
    private final EquipmentStateService equipmentStateService;
    private final ManufacturingAnalysisResultQueryService analysisResultQueryService;

    @Operation(
            summary = "SampleDB의 다음 미전송 raw 이벤트 조회",
            description = """
                    SampleDB `manufacturing_event_json` 테이블에서 `is_sent=0`인 가장 오래된 행을 조회합니다.
                    Kafka 메시지는 엔티티 컬럼의 식별·라우팅 정보와 `eventJson` 세부 데이터를 결합해 생성됩니다.
                    이 API는 조회만 수행하며 Kafka 전송 상태를 변경하지 않습니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "다음 미전송 제조 이벤트 조회 성공"
            )
    })
    @GetMapping("/sample")
    public ApiResponse<StoredManufacturingEventResponse> sample() {
        // SampleDB 기준 다음 미전송 제조 이벤트 조회
        return ApiResponse.success(
                rawEventService.findFirstReady(),
                "Next unsent SampleDB manufacturing event"
        );
    }

    @Operation(
            summary = "SampleDB의 지정 raw 이벤트를 Kafka로 전송",
            description = """
                    경로의 ID에 해당하는 SampleDB `manufacturing_event_json` 행을 조회하고,
                    엔티티 컬럼과 `event_json`을 결합한 메시지를 `factory.manufacturing.raw`로 전송합니다.

                    `car_master_id`를 Kafka message key로 사용하므로 같은 차량/제품의 제조 이벤트는
                    동일한 파티션에 저장되어 차량/제품 기준 이벤트 순서가 보장됩니다.

                    eventId, eventTime, carMasterId, equipmentId, processCode, equipmentCode, equipmentType,
                    equipmentStatus, eventType은 테이블 컬럼을 사용하고 센서·공정 상세 데이터는
                    eventJson 필드에 포함합니다.

                    응답의 partition과 offset은 MSK broker가 실제 저장 후 반환한 메타데이터입니다.
                    Kafka 전송 성공 후에만 해당 행의 `is_sent=1`, `sent_at=현재 시각`으로 갱신합니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "MSK raw 토픽 전송 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "MSK 연결, IAM 인증 또는 메시지 직렬화 실패"
            )
    })
    @PostMapping("/send/{id}")
    public CompletableFuture<ApiResponse<KafkaPublishResult>> send(
            @Parameter(
                    name = "id",
                    description = "SampleDB manufacturing_event_json 테이블의 PK",
                    required = true,
                    example = "1"
            )
            @PathVariable long id
    ) {
        // 지정 PK의 SampleDB 이벤트 조회 및 raw 토픽 발행
        return rawEventService.sendById(id)
                .thenApply(result -> ApiResponse.success(
                        result,
                        "SampleDB event_json sent to Kafka raw topic"
                ));
    }

    @Operation(
            summary = "SampleDB의 다음 미전송 이벤트를 raw 토픽으로 전송",
            description = """
                    SampleDB `manufacturing_event_json`에서 `is_sent=0`인 가장 오래된 행 1건을 조회하여
                    엔티티 컬럼과 `event_json`을 결합한 raw 메시지를 실제 토픽으로 전송합니다.

                    응답의 eventId를 `/messages?eventId={eventId}`에 전달하면
                    raw → analysis → equipment/alert 처리 이력을 현재 Pod에서 조회할 수 있습니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "샘플 이벤트가 실제 MSK raw 토픽에 저장됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "MSK 연결 또는 IAM 쓰기 권한 실패"
            )
    })
    @PostMapping("/send-sample")
    public CompletableFuture<ApiResponse<KafkaPublishResult>> sendSample() {
        // event_time 기준 다음 미전송 SampleDB 이벤트 1건 발행
        return rawEventService.sendNextReady()
                .thenApply(result -> ApiResponse.success(
                        result,
                        "Next SampleDB event_json sent to Kafka raw topic"
                ));
    }

    @Operation(
            summary = "SampleDB raw 이벤트 목록 조회",
            description = """
                    SampleDB `manufacturing_event_json` 테이블을 이벤트 시간 순으로 조회합니다.
                    각 행의 `eventJson`, `isSent`, `sentAt`을 함께 확인할 수 있어
                    Kafka 전송 전후 상태를 검증할 수 있습니다. 최대 100건까지 조회합니다.
                    """
    )
    @GetMapping("/events")
    public ApiResponse<List<StoredManufacturingEventResponse>> events(
            @Parameter(
                    description = "조회 건수. 1~100 범위로 제한됩니다.",
                    example = "20"
            )
            @RequestParam(defaultValue = "20") int limit
    ) {
        // 전송 상태와 eventJson 확인용 SampleDB 이벤트 목록 조회
        return ApiResponse.success(
                rawEventService.findEvents(limit),
                "SampleDB manufacturing_event_json rows"
        );
    }

    @Operation(
            summary = "eventId 기준 제조 분석 결과 조회",
            description = """
                    Kafka raw consumer가 처리한 뒤 MainDB `manufacturing_analysis_result`에 저장한
                    최신 분석 결과 1건을 eventId 기준으로 조회합니다.

                    Swagger에서 raw 발행 후 consumer 처리 및 DB 저장 여부를 확인할 때 사용합니다.
                    """
    )
    @GetMapping("/analysis-results/{eventId}")
    public ApiResponse<ManufacturingAnalysisResultResponse> analysisResult(
            @Parameter(
                    name = "eventId",
                    description = "제조 이벤트 ID. 예: EVT-20260601-000013",
                    required = true
            )
            @PathVariable String eventId
    ) {
        return ApiResponse.success(
                analysisResultQueryService.findLatestByEventId(eventId),
                "Manufacturing analysis result"
        );
    }

    @Operation(
            summary = "eventId 기준 제조 분석 계산 상세 조회",
            description = """
                    특정 eventId의 raw payload를 기준으로 bottleneck/equipment/defect/process risk를
                    어떤 입력값과 계산식으로 산출했는지 조회합니다.

                    riskScore는 0~100점 만점 기준이며 각 세부 점수도 clamp로 0~100 범위로 제한됩니다.
                    """
    )
    @GetMapping("/analysis-results/{eventId}/detail")
    public ApiResponse<ManufacturingAnalysisDetailResponse> analysisResultDetail(
            @Parameter(
                    name = "eventId",
                    description = "제조 이벤트 ID. 예: EVT-20260601-000013",
                    required = true
            )
            @PathVariable String eventId
    ) {
        return ApiResponse.success(
                analysisResultQueryService.findDetailByEventId(eventId),
                "Manufacturing analysis calculation detail"
        );
    }

    @Operation(
            summary = "최근 제조 분석 결과 목록 조회",
            description = """
                    MainDB `manufacturing_analysis_result`에 저장된 최근 분석 결과를
                    analyzedAt, createdAt 최신순으로 조회합니다.

                    방금 Kafka로 발행한 이벤트가 분석 결과로 저장됐는지 Swagger에서 확인할 때 사용합니다.
                    """
    )
    @GetMapping("/analysis-results")
    public ApiResponse<List<ManufacturingAnalysisResultResponse>> analysisResults(
            @Parameter(
                    description = "조회 건수. 1~100 범위로 제한됩니다.",
                    example = "20"
            )
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(
                analysisResultQueryService.findRecent(limit),
                "Recent manufacturing analysis results"
        );
    }

    @PostMapping("/equipment/{equipmentCode}/recover")
    public ApiResponse<EquipmentStatusEvent> recoverEquipment(
            @PathVariable String equipmentCode,
            @RequestParam(defaultValue = "Administrator recovery") String reason
    ) {
        return ApiResponse.success(
                equipmentStateService.recover(equipmentCode, reason),
                "Equipment recovery committed; status event will be published after commit"
        );
    }

    /**
     * 실제 broker 접속, broker 수, 토픽별 실제 partition 수 조회.
     * MSK IAM, VPC, 토픽 조회 권한 통합 검증.
     */
    @Operation(
            summary = "실제 MSK 연결 및 토픽 파티션 조회",
            description = """
                    AdminClient로 실제 Kafka/MSK broker에 접속하여 clusterId, broker 수,
                    각 제조 토픽의 실제 파티션 수를 조회합니다.

                    `/topics`가 애플리케이션 설정값을 반환하는 것과 달리 이 API는 실제 broker 상태를 반환합니다.
                    이 호출이 성공하면 VPC 네트워크, MSK IAM 인증, DescribeCluster 및 DescribeTopic 권한이
                    정상적으로 구성된 것입니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "실제 MSK 연결 및 토픽 메타데이터 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "브로커 접근, IAM 인증, Describe 권한 또는 토픽 조회 실패"
            )
    })
    @GetMapping("/broker")
    public ApiResponse<KafkaDiagnosticsService.KafkaBrokerStatus> broker() {
        // AdminClient 기반 실제 MSK cluster 및 토픽 메타데이터 조회
        return ApiResponse.success(
                diagnosticsService.inspect(),
                "Kafka/MSK broker connection verified"
        );
    }

    /**
     * 현재 Pod의 최근 Kafka 송수신 메시지 조회.
     * eventId 기준 raw→analysis→equipment/alert 흐름 필터링.
     */
    @Operation(
            summary = "현재 Pod의 최근 Kafka 송수신 이력 조회",
            description = """
                    현재 애플리케이션 Pod가 Producer로 전송하거나 Consumer로 수신한 최근 메시지를 조회합니다.
                    최대 200건을 Pod 메모리에만 저장하며 애플리케이션 재시작 시 초기화됩니다.
                    여러 Pod로 실행 중이면 호출된 Pod의 이력만 반환하므로 영구 메시지 조회 API가 아닙니다.

                    eventId를 전달하면 하나의 제조 이벤트가 raw, analysis, equipment, alert 토픽을
                    거치는 처리 흐름만 필터링할 수 있습니다. 전체 Kafka 메시지 탐색은 Kafka UI를 사용합니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "현재 Pod의 최근 Kafka 메시지 추적 이력 반환"
            )
    })
    @GetMapping("/messages")
    public ApiResponse<java.util.List<KafkaMessageTraceStore.KafkaMessageTrace>> messages(
            @Parameter(
                    name = "eventId",
                    description = """
                            조회할 제조 원천 이벤트 ID입니다.
                            생략하면 현재 Pod의 최근 송수신 이력을 모두 반환합니다.
                            예: EVT-550e8400-e29b-41d4-a716-446655440000
                            """,
                    in = ParameterIn.QUERY,
                    example = "EVT-550e8400-e29b-41d4-a716-446655440000"
            )
            @RequestParam(required = false) String eventId
    ) {
        // 현재 Pod 메모리의 최근 송수신 이력 조회
        return ApiResponse.success(
                traceStore.findRecent(eventId),
                "Recent in-memory Kafka message traces"
        );
    }
}

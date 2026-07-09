package com.aims.assembly.controller.body;

import com.aims.assembly.dto.body.BodyAnomalyDetectionResponse;
import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.service.body.BodyAnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/process/body")
@RequiredArgsConstructor
@Tag(
        name = "차체 이상 탐지",
        description = "차체 로봇 진동 이상 탐지 대시보드 API"
)
public class BodyAnomalyDetectionController {
    private final BodyAnomalyDetectionService service;

    @Operation(
            summary = "차체 이상 탐지 대시보드 조회",
            description = """
                    차체 분석 결과(로봇 진동, 주파수 이상 등)를 날짜 또는 시간 범위 기준으로 조회합니다.
                    응답에는 로봇 모션 상태, 운전 모드, 진동 점수, 피크 진동값, 위험도 지표와
                    그래프 시계열(진동 점수, 위험도, 피크 진동값), 이상 탐지 알림 패널 정보가 포함됩니다.
                    date는 yyyy-MM-dd, from/to/endAt은 ISO LocalDateTime 형식을 사용합니다.
                    """
    )
    @GetMapping("/analysis")
    public ApiResponse<BodyAnomalyDetectionResponse> dashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endAt,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.success(
                service.findDashboard(date, from, to, endAt, limit),
                "차체 이상 탐지 API 조회 성공"
        );
    }
}

package com.aims.assembly.controller.press;

import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.dto.press.PressAnomalyDetectionResponse;
import com.aims.assembly.service.press.PressAnomalyDetectionService;
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
@RequestMapping("/api/process/press")
@RequiredArgsConstructor
@Tag(
        name = "프레스 이상 탐지",
        description = "프레스 이상 정지 탐지 대시보드 API"
)
public class PressAnomalyDetectionController {
    private final PressAnomalyDetectionService service;

    @Operation(
            summary = "프레스 이상 정지 탐지 대시보드 조회",
            description = """
                    프레스 분석 결과를 날짜 또는 시간 범위 기준으로 조회합니다.
                    응답에는 상단 지표, 그래프 시계열, 이상 탐지 알림 패널 정보가 포함됩니다.
                    그래프를 과거 시간대로 드래그할 때는 이전 응답의 previousEndAt 값을 다음 요청의 endAt으로 전달하면 됩니다.
                    date는 yyyy-MM-dd, from/to/endAt은 ISO LocalDateTime 형식을 사용합니다.
                    """
    )
    @GetMapping("/analysis")
    public ApiResponse<PressAnomalyDetectionResponse> dashboard(
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
                "Press anomaly detection dashboard"
        );
    }
}

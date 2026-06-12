package com.aims.assembly.controller.process;

import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.dto.process.ProcessSampleResponse;
import com.aims.assembly.service.process.ProcessSampleLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/process/sample")
@RequiredArgsConstructor
public class ProcessSampleController {

    private final ProcessSampleLoadService processSampleLoadService;

    @PostMapping
    public ApiResponse<ProcessSampleResponse> loadSampleData(
            @RequestParam(defaultValue = "false") boolean reset,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDate
    ) {
        return ApiResponse.success(
                processSampleLoadService.load(reset, fromDate),
                "Sample DB 기반 제조 분석 결과 적재 완료"
        );
    }
}

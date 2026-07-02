package com.aims.assembly.controller.process;

import com.aims.assembly.dto.common.ApiResponse;
import com.aims.assembly.dto.process.AssemblyDashboardResponse;
import com.aims.assembly.dto.process.PaintDashboardResponse;
import com.aims.assembly.dto.process.ProcessAvailableDatesResponse;
import com.aims.assembly.service.process.ProcessDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
public class ProcessDashboardController {
    private final ProcessDashboardService dashboardService;

    @GetMapping("/paint")
    public ApiResponse<PaintDashboardResponse> paint(
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
            Integer limit
    ) {
        return ApiResponse.success(
                dashboardService.getPaintDashboard(date, from, to, limit),
                "Paint process dashboard"
        );
    }

    @GetMapping("/paint/dates")
    public ApiResponse<ProcessAvailableDatesResponse> paintDates() {
        return ApiResponse.success(
                dashboardService.getPaintDates(),
                "Paint process dashboard available dates"
        );
    }

    @GetMapping("/assembly")
    public ApiResponse<AssemblyDashboardResponse> assembly(
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
            Integer limit
    ) {
        return ApiResponse.success(
                dashboardService.getAssemblyDashboard(date, from, to, limit),
                "Assembly process dashboard"
        );
    }

    @GetMapping("/assembly/dates")
    public ApiResponse<ProcessAvailableDatesResponse> assemblyDates() {
        return ApiResponse.success(
                dashboardService.getAssemblyDates(),
                "Assembly process dashboard available dates"
        );
    }
}

package com.aims.assembly.dto.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessSampleResponse {
    private boolean reset;
    private LocalDateTime fromDate;
    private int pressCount;
    private int bodyCount;
    private int paintCount;
    private int assemblyCount;
    private int processHistoryCount;
    private int equipmentStatusCount;
}

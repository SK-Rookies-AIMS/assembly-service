package com.aims.assembly.dto.manufacturing;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgvArrivalRequest {

    /**
     * AGV가 운반 완료한 제조 이벤트
     */
    private String eventId;
}
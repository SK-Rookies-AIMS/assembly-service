package com.aims.assembly.domain.enums;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum EquipmentHealthStatus {

    NORMAL("정상"),
    WARNING("경고"),
    CRITICAL("위험");

    private final String description;
}

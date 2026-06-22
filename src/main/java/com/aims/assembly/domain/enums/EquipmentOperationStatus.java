package com.aims.assembly.domain.enums;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum EquipmentOperationStatus {

    RUNNING("가동 중", true),
    IDLE("대기 중", false),
    WARNING("경고", true),
    STOPPED("정지", false),
    FAULT("고장", false),
    MAINTENANCE("점검 중", false);

    private final String description;
    private final boolean running;
}

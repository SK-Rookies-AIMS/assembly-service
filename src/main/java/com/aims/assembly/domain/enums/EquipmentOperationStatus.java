package com.aims.assembly.domain.enums;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum EquipmentOperationStatus {

    RUNNING("가동 중", true),
    WARNING("경고", true),
    STOPPED("정지", false),
    FAULT("고장", false);

    private final String description;
    private final boolean running;
}

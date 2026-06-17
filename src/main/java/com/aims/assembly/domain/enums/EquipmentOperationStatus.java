package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EquipmentOperationStatus {

    RUNNING("가동 중", true),
    IDLE("대기 중", false),
    STOPPED("정지", false),
    ERROR("오류", false),
    MAINTENANCE("점검 중", false),
    UNKNOWN("상태 불명", false);

    private final String description;
    private final boolean running;
}

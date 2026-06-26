package com.aims.assembly.domain.enums;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum EquipmentHealthStatus {

    NORMAL("정상"),
    ABNORMAL("이상");

    private final String description;
}

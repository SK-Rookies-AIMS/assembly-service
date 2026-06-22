package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DispatchStatus {

    PENDING("전송 대기"),
    READY("전송 준비"),
    SENT("전송 완료"),
    BLOCKED("전송 차단");

    private final String description;
}

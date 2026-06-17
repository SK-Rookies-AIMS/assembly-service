package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Severity {

    NORMAL("정상"),
    WARNING("경고"),
    CRITICAL("위험");

    private final String description;
}

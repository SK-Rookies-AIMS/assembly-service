package com.aims.assembly.domain.alert;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Severity {
    DANGER("위험"),
    CAUTION("주의");

    private final String displayName;
}
package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisStatus {

    NOT_ANALYZED("미분석"),
    NORMAL("정상"),
    ABNORMAL("이상");

    private final String description;
}

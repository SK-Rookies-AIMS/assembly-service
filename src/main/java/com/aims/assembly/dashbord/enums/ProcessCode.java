package com.aims.assembly.dashbord.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProcessCode {
    ASSEMBLY("조립"),
    BODY("차체"),
    PAINT("도장"),
    PRESS("프레스"),
    INSPECTION("검사");

    private final String displayName;
}

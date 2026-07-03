package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProcessCode {
    PRESS("프레스"),
    BODY("차체"),
    PAINT("도장"),
    ASSEMBLY("의장");

    private final String description;
}

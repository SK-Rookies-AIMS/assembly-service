package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EquipmentType {

    HYDRAULIC_PRESS("유압 프레스"),
    ROBOT_ARM("로봇 팔"),
    CAMERA("열화상 카메라"),
    CONVEYOR("컨베이어");

    private final String description;
}

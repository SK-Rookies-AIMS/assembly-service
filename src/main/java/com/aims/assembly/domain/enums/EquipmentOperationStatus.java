package com.aims.assembly.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum EquipmentOperationStatus {

    RUNNING("가동 중", true),
    WARNING("경고", true),
    STOPPED("정지", false),
    FAULT("고장", false);

    private final String description;
    private final boolean running;

    public static Optional<EquipmentOperationStatus> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EquipmentOperationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

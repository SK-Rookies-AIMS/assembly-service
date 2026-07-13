package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AlertImageUrlResolverTest {
    private static final String BASE_URI =
            "s3://event-image-858507113889-ap-northeast-2-an/";

    @ParameterizedTest
    @MethodSource("mappedImages")
    void resolvesImageUrl(
            ProcessCode processCode,
            String alertType,
            String riskLevel,
            String fileName
    ) {
        assertThat(AlertImageUrlResolver.resolve(processCode, alertType, riskLevel))
                .isEqualTo(BASE_URI + fileName);
    }

    static Stream<Arguments> mappedImages() {
        return Stream.of(
                mapping(ProcessCode.PRESS, "press"),
                mapping(ProcessCode.BODY, "body"),
                mapping(ProcessCode.PAINT, "paint"),
                mapping(ProcessCode.ASSEMBLY, "assamble")
        ).flatMap(stream -> stream);
    }

    private static Stream<Arguments> mapping(ProcessCode processCode, String filePrefix) {
        return Stream.of(
                Arguments.of(
                        processCode,
                        ManufacturingAlertEvent.TYPE_EQUIPMENT_ABNORMAL,
                        "CRITICAL",
                        filePrefix + "_1.png"
                ),
                Arguments.of(
                        processCode,
                        ManufacturingAlertEvent.TYPE_MANUFACTURING_ABNORMAL,
                        "CRITICAL",
                        filePrefix + "_2.png"
                ),
                Arguments.of(
                        processCode,
                        ManufacturingAlertEvent.TYPE_EQUIPMENT_ABNORMAL,
                        "WARNING",
                        filePrefix + "_3.png"
                ),
                Arguments.of(
                        processCode,
                        ManufacturingAlertEvent.TYPE_MANUFACTURING_ABNORMAL,
                        "WARNING",
                        filePrefix + "_3.png"
                )
        );
    }

    @Test
    void returnsNullForUnmappedValues() {
        assertThat(AlertImageUrlResolver.resolve(null, "EQUIPMENT_ABNORMAL", "CRITICAL")).isNull();
        assertThat(AlertImageUrlResolver.resolve(ProcessCode.PRESS, null, "CRITICAL")).isNull();
        assertThat(AlertImageUrlResolver.resolve(ProcessCode.PRESS, "UNKNOWN", "CRITICAL")).isNull();
        assertThat(AlertImageUrlResolver.resolve(ProcessCode.PRESS, "EQUIPMENT_ABNORMAL", null)).isNull();
        assertThat(AlertImageUrlResolver.resolve(ProcessCode.PRESS, "EQUIPMENT_ABNORMAL", "LOW")).isNull();
    }
}

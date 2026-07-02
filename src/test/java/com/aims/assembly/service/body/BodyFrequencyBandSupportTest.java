package com.aims.assembly.service.body;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BodyFrequencyBandSupportTest {

    @Test
    void aggregatesDetailedFrequencyBandsIntoLowMediumHigh() {
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("freq_0_100_hz", 0.0026);
        raw.put("freq_101_200_hz", 0.0042);
        raw.put("freq_501_600_hz", 0.0062);

        Map<String, Double> summary = BodyFrequencyBandSupport.toSummaryBands(raw);

        assertThat(summary).containsEntry("LOW", 0.0026);
        assertThat(summary).containsEntry("MEDIUM", 0.0042);
        assertThat(summary).containsEntry("HIGH", 0.0062);
    }

    @Test
    void resolvesPeakValueFromFrequencyPeakBandKey() {
        Map<String, Double> raw = Map.of(
                "freq_501_600_hz", 0.002907113,
                "freq_0_100_hz", 0.001193284
        );

        Double peak = BodyFrequencyBandSupport.resolvePeakValue("501_600_HZ", raw, null);

        assertThat(peak).isEqualTo(0.002907113);
    }
}

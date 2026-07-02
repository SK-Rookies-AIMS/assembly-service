package com.aims.assembly.service.body;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * processData.body.frequencyBands / frequencyPeakBand 해석 유틸.
 */
public final class BodyFrequencyBandSupport {
    private static final Pattern DETAILED_BAND_PATTERN =
            Pattern.compile("freq_(\\d+)_(\\d+)_hz", Pattern.CASE_INSENSITIVE);

    private BodyFrequencyBandSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Double> toDoubleMap(Object frequencyBandsObj) {
        if (!(frequencyBandsObj instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> bands = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Number number)) {
                continue;
            }
            bands.put(entry.getKey().toString(), number.doubleValue());
        }
        return bands;
    }

    public static Map<String, Double> toSummaryBands(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (hasSummaryKeys(raw)) {
            Map<String, Double> summary = new LinkedHashMap<>();
            putSummaryValue(summary, "LOW", raw);
            putSummaryValue(summary, "MEDIUM", raw, "MID");
            putSummaryValue(summary, "HIGH", raw);
            return summary.isEmpty() ? null : summary;
        }
        return aggregateDetailedBands(raw);
    }

    public static Double resolvePeakValue(String peakBand, Map<String, Double> bands, Double fallback) {
        if (bands != null && !bands.isEmpty()) {
            if (peakBand != null && !peakBand.isBlank()) {
                Double matched = lookupBandValue(peakBand, bands);
                if (matched != null) {
                    return matched;
                }
            }
            return bands.values().stream().max(Double::compareTo).orElse(null);
        }
        return fallback;
    }

    public static Double lookupBandValue(String peakBand, Map<String, Double> bands) {
        if (peakBand == null || bands == null || bands.isEmpty()) {
            return null;
        }
        if (bands.containsKey(peakBand)) {
            return bands.get(peakBand);
        }
        String normalizedPeak = normalizeKey(peakBand);
        for (Map.Entry<String, Double> entry : bands.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalizedPeak)) {
                return entry.getValue();
            }
        }
        String peakToken = extractBandToken(normalizedPeak);
        if (peakToken != null) {
            for (Map.Entry<String, Double> entry : bands.entrySet()) {
                if (normalizeKey(entry.getKey()).contains(peakToken)) {
                    return entry.getValue();
                }
            }
        }
        Map<String, Double> summary = toSummaryBands(bands);
        if (summary != null) {
            String summaryKey = switch (peakBand.toUpperCase(Locale.ROOT)) {
                case "MID" -> "MEDIUM";
                default -> peakBand.toUpperCase(Locale.ROOT);
            };
            if (summary.containsKey(summaryKey)) {
                return summary.get(summaryKey);
            }
        }
        return null;
    }

    private static boolean hasSummaryKeys(Map<String, Double> raw) {
        return raw.keySet().stream()
                .map(key -> key.toUpperCase(Locale.ROOT))
                .anyMatch(key -> "LOW".equals(key) || "MID".equals(key) || "MEDIUM".equals(key) || "HIGH".equals(key));
    }

    private static void putSummaryValue(Map<String, Double> target, String key, Map<String, Double> raw) {
        putSummaryValue(target, key, raw, key);
    }

    private static void putSummaryValue(
            Map<String, Double> target,
            String targetKey,
            Map<String, Double> raw,
            String... sourceKeys
    ) {
        for (String sourceKey : sourceKeys) {
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                if (sourceKey.equalsIgnoreCase(entry.getKey())) {
                    target.put(targetKey, entry.getValue());
                    return;
                }
            }
        }
    }

    private static Map<String, Double> aggregateDetailedBands(Map<String, Double> raw) {
        double low = 0.0;
        double medium = 0.0;
        double high = 0.0;
        boolean found = false;

        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            Matcher matcher = DETAILED_BAND_PATTERN.matcher(normalizeKey(entry.getKey()));
            if (!matcher.find()) {
                continue;
            }
            found = true;
            int upperHz = Integer.parseInt(matcher.group(2));
            double value = entry.getValue();
            if (upperHz <= 100) {
                low = Math.max(low, value);
            } else if (upperHz <= 500) {
                medium = Math.max(medium, value);
            } else {
                high = Math.max(high, value);
            }
        }

        if (!found) {
            return null;
        }
        Map<String, Double> summary = new LinkedHashMap<>();
        summary.put("LOW", low);
        summary.put("MEDIUM", medium);
        summary.put("HIGH", high);
        return summary;
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "_");
    }

    private static String extractBandToken(String normalizedPeak) {
        Matcher matcher = DETAILED_BAND_PATTERN.matcher(normalizedPeak);
        if (matcher.find()) {
            return matcher.group(1) + "_" + matcher.group(2);
        }
        if (normalizedPeak.endsWith("_hz")) {
            return normalizedPeak.replace("freq_", "").replace("_hz", "");
        }
        return null;
    }
}

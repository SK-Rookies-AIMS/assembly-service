package com.aims.assembly.kafka;

import com.aims.assembly.common.status.KafkaErrorStatus;
import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.exception.KafkaException;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class ManufacturingRawEventParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public ManufacturingRawEvent parse(String json) {
        try {
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> eventJson = eventJson(root);
            return new ManufacturingRawEvent(
                    longValue(root, "id"), directText(root, "eventId"),
                    time(directText(root, "eventTime", "event_time")),
                    nullableLong(root, "carMasterId"), nullableLong(root, "equipmentId"),
                    ProcessCode.valueOf(directText(root, "processCode")),
                    directText(root, "equipmentCode"), directText(root, "equipmentType"),
                    directText(root, "equipmentStatus"), directText(root, "eventType"), eventJson);
        } catch (RuntimeException | java.io.IOException exception) {
            throw new KafkaException(KafkaErrorStatus.MESSAGE_DESERIALIZATION_FAILED,
                    "Cannot parse manufacturing raw event envelope", exception);
        }
    }

    private String directText(Map<String, Object> value, String... fields) {
        for (String field : fields) {
            Object found = value.get(field);
            if (found != null) return found.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventJson(Map<String, Object> root) {
        Object value = root.get("eventJson");
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("eventJson must be an object");
        }
        return (Map<String, Object>) value;
    }
    private long longValue(Map<String, Object> root, String field) {
        Long value = nullableLong(root, field);
        return value == null ? 0 : value;
    }
    private Long nullableLong(Map<String, Object> root, String field) {
        Object value = root.get(field);
        return value instanceof Number n ? n.longValue() : null;
    }
    private LocalDateTime time(String value) {
        if (value == null) return null;
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException ignored) { return OffsetDateTime.parse(value).toLocalDateTime(); }
    }
}

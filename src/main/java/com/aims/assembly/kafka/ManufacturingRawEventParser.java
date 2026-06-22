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
            return new ManufacturingRawEvent(
                    longValue(root, "id"), text(root, "eventId"), time(text(root, "eventTime")),
                    nullableLong(root, "carMasterId"), nullableLong(root, "equipmentId"),
                    ProcessCode.valueOf(text(root, "processCode")),
                    text(root, "equipmentCode"), text(root, "equipmentType"),
                    text(root, "equipmentStatus"), text(root, "eventType"), root);
        } catch (RuntimeException | java.io.IOException exception) {
            throw new KafkaException(KafkaErrorStatus.MESSAGE_DESERIALIZATION_FAILED,
                    "Cannot parse immutable manufacturing event_json", exception);
        }
    }

    private String text(Object value, String field) {
        Object found = find(value, field);
        return found == null ? null : found.toString();
    }
    private Object find(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(field)) return map.get(field);
            for (Object nested : map.values()) {
                Object found = find(nested, field);
                if (found != null) return found;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object nested : values) {
                Object found = find(nested, field);
                if (found != null) return found;
            }
        }
        return null;
    }
    private long longValue(Map<String, Object> root, String field) {
        Long value = nullableLong(root, field);
        return value == null ? 0 : value;
    }
    private Long nullableLong(Map<String, Object> root, String field) {
        Object value = find(root, field);
        return value instanceof Number n ? n.longValue() : null;
    }
    private LocalDateTime time(String value) {
        if (value == null) return null;
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException ignored) { return OffsetDateTime.parse(value).toLocalDateTime(); }
    }
}

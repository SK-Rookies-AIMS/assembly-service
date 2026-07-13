package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AlertImageUrlResolver {
    private static final String IMAGE_BASE_URI =
            "s3://event-image-858507113889-ap-northeast-2-an/";

    private AlertImageUrlResolver() {
    }

    public static String resolve(
            ProcessCode processCode,
            String alertType,
            String riskLevel
    ) {
        if (processCode == null || alertType == null || riskLevel == null) {
            return mappingNotFound(processCode, alertType, riskLevel);
        }

        String filePrefix = switch (processCode) {
            case PRESS -> "press";
            case BODY -> "body";
            case PAINT -> "paint";
            case ASSEMBLY -> "assamble";
        };

        String fileSuffix = switch (riskLevel) {
            case "CRITICAL" -> switch (alertType) {
                case ManufacturingAlertEvent.TYPE_EQUIPMENT_ABNORMAL -> "1";
                case ManufacturingAlertEvent.TYPE_MANUFACTURING_ABNORMAL -> "2";
                default -> null;
            };
            case "WARNING" -> isSupportedAlertType(alertType) ? "3" : null;
            default -> null;
        };

        if (fileSuffix == null) {
            return mappingNotFound(processCode, alertType, riskLevel);
        }
        return IMAGE_BASE_URI + filePrefix + "_" + fileSuffix + ".png";
    }

    private static boolean isSupportedAlertType(String alertType) {
        return ManufacturingAlertEvent.TYPE_EQUIPMENT_ABNORMAL.equals(alertType)
                || ManufacturingAlertEvent.TYPE_MANUFACTURING_ABNORMAL.equals(alertType);
    }

    private static String mappingNotFound(
            ProcessCode processCode,
            String alertType,
            String riskLevel
    ) {
        log.warn(
                "Alert image mapping not found. processCode={}, alertType={}, riskLevel={}",
                processCode,
                alertType,
                riskLevel
        );
        return null;
    }
}

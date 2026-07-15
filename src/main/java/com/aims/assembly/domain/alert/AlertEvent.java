package com.aims.assembly.domain.alert;

import com.aims.assembly.dashbord.enums.ProcessCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "alert_event")
public class AlertEvent {

    @Id
    @Column(name = "log_no", nullable = false, length = 20)
    private String logNo;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", nullable = false, length = 20)
    private ProcessCode processCode;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "event_key", nullable = false, length = 150)
    private String eventKey;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "occurrence_score", precision = 6, scale = 4)
    private BigDecimal occurrenceScore;

    @Column(name = "detection_score", precision = 6, scale = 4)
    private BigDecimal detectionScore;

    @Column(name = "priority_score", precision = 10, scale = 2)
    private BigDecimal priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private AlertSeverity severity;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "contents", nullable = false, length = 500)
    private String contents;

    @Column(name = "action_by", length = 50)
    private String actionBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_status", length = 20)
    private AlertActionStatus actionStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "score_calculated_at")
    private LocalDateTime scoreCalculatedAt;

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public void updateAction(
            String actionBy,
            AlertActionStatus actionStatus,
            String reason
    ) {

        if (actionBy != null) {
            this.actionBy = actionBy;
        }

        if (actionStatus != null) {
            this.actionStatus = actionStatus;

            if (actionStatus == AlertActionStatus.COMPLETED
                    || actionStatus == AlertActionStatus.NOT_NEEDED) {
                this.resolvedAt = LocalDateTime.now();
            } else if (actionStatus == AlertActionStatus.INCOMPLETE) {
                this.resolvedAt = null;
            }
        }

        if (reason != null) {
            this.reason = reason;
        }
    }
}

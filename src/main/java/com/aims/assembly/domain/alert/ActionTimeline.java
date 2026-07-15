package com.aims.assembly.domain.alert;

import com.aims.assembly.domain.user.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "action_timeline")
public class ActionTimeline {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long actionId;

    @Column(name = "log_no", nullable = false, length = 20)
    private String logNo;

    @Column(name = "emp_no", nullable = false, length = 20)
    private String empNo;

    @Column(name = "emp_name", nullable = false, length = 20)
    private String empName;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_role", nullable = false, length = 20)
    private UserRole empRole;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    @Column(name = "action_content", nullable = false, length = 20)
    private String actionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_category", nullable = false, length = 20)
    private ActionCategory actionCategory;

    @Column(name = "action_result", nullable = false, length = 20)
    private String actionResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
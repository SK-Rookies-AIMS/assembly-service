package com.aims.assembly.kafka;

import com.aims.assembly.domain.enums.ProcessCode;
import com.aims.assembly.kafka.model.ManufacturingAlertEvent;
import com.aims.assembly.kafka.model.ManufacturingAnalysisEvent;
import com.aims.assembly.kafka.model.ManufacturingRawEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 AI 모델이나 Kafka broker 연결 없이 예시 위험도 계산과 후속 이벤트 변환 검증.
 */
@DisplayName("제조 이벤트 분석기 단위 테스트")
class ManufacturingEventAnalyzerTest {

    private final ManufacturingEventAnalyzer analyzer = new ManufacturingEventAnalyzer();

    @Test
    @DisplayName("raw 이벤트를 분석 결과와 알림 이벤트로 변환")
    void analyzesSampleRawEventAndCreatesDownstreamEvents() {
        // Given: SampleDB 엔티티 컬럼과 eventJson 구조를 모방한 raw 이벤트
        ManufacturingRawEvent rawEvent = createRawEvent();

        // When: 위험도 분석 및 알림 이벤트 변환
        ManufacturingAnalysisEvent analysis = analyzer.analyze(rawEvent);
        ManufacturingAlertEvent alert = analyzer.toAlertEvent(analysis);

        // Then: 원본 eventId와 equipmentCode 추적 유지
        assertThat(analysis.eventId()).isEqualTo(rawEvent.eventId());
        assertThat(analysis.equipmentCode()).isEqualTo("EQ_PRESS_01");
        assertThat(analysis.riskScores().overallRiskScore()).isBetween(0.0, 100.0);
        assertThat(alert.equipmentCode()).isEqualTo(analysis.equipmentCode());
        assertThat(alert.analysisId()).isEqualTo(analysis.analysisId());
    }

    @Test
    void classifiesMissingPartsAsQualityDefectWithoutEquipmentFault() {
        ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                "processData", Map.of("assembly", Map.of(
                        "missingPartCount", 1, "fasteningErrorCount", 0))));

        ManufacturingAnalysisEvent result = analyzer.analyze(raw);

        assertThat(result.analysisResult().isQualityDefect()).isTrue();
        assertThat(result.analysisResult().isEquipmentFault()).isFalse();
        assertThat(analyzer.requiresAlert(result)).isTrue();
        assertThat(ManufacturingKafkaConsumer.isAbnormalAnalysis(result)).isTrue();
    }

    @Test
    void classifiesFaultOperationStatusAsEquipmentFault() {
        ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                "equipmentStatus", Map.of("operationStatus", "FAULT")));

        assertThat(analyzer.analyze(raw).analysisResult().isEquipmentFault()).isTrue();
    }

    @Test
    void exposesCalculationDetailOnZeroToOneHundredScale() {
        ManufacturingRawEvent raw = createRawEvent();

        ManufacturingAnalysisEvent analysis = analyzer.analyze(raw);
        ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

        assertThat(detail.riskScoreScale()).isEqualTo("0-100");
        assertThat(detail.riskScore()).isEqualTo(analysis.riskScores().overallRiskScore());
        assertThat(detail.riskScore()).isBetween(0.0, 100.0);
        assertThat(detail.processRisk().score()).isBetween(0.0, 100.0);
    }

    @Test
    void clampsExtremeRiskScoresToOneHundred() {
        ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                "processMetrics", Map.of(
                        "cycleTimeSec", 500,
                        "stationDelaySec", 500,
                        "waitingTimeSec", 500,
                        "queueLength", 500,
                        "wipCount", 500,
                        "equipmentIdleTimeSec", 500
                ),
                "sensor", Map.of(
                        "current", Map.of("rmsAmpere", 500),
                        "vibration", Map.of("vibrationScore", 500),
                        "robotArmVibration", Map.of("vibrationScore", 500),
                        "thermal", Map.of("maxTemperature", 500)
                ),
                "processData", Map.of(
                        "press", Map.of("targetCycleTimeSec", 40, "countIncreaseYn", false)
                )
        ));

        ManufacturingAnalysisEvent result = analyzer.analyze(raw);
        ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

        assertThat(result.riskScores().overallRiskScore()).isEqualTo(100.0);
        assertThat(detail.riskScore()).isEqualTo(100.0);
        assertThat(result.riskLevel()).isEqualTo("CRITICAL");
        assertThat(result.analysisResult().isAbnormal()).isTrue();
    }

    @Test
    void classifiesNormalAndAbnormalEventsByRiskThresholds() {
        ManufacturingAnalysisEvent normal = analyzer.analyze(raw(ProcessCode.PRESS, Map.of(
                "processMetrics", Map.of("cycleTimeSec", 40, "stationDelaySec", 0),
                "processData", Map.of("press", Map.of("targetCycleTimeSec", 40, "countIncreaseYn", true))
        )));
        ManufacturingAnalysisEvent abnormal = analyzer.analyze(raw(ProcessCode.ASSEMBLY, Map.of(
                "processData", Map.of("assembly", Map.of(
                        "sequenceErrorCount", 2,
                        "missingPartCount", 1,
                        "fasteningErrorCount", 1
                ))
        )));

        assertThat(normal.riskScores().overallRiskScore()).isLessThan(60.0);
        assertThat(normal.riskLevel()).isEqualTo("LOW");
        assertThat(normal.analysisResult().isAbnormal()).isFalse();
        assertThat(abnormal.analysisResult().isAbnormal()).isTrue();
        assertThat(abnormal.analysisResult().isSequenceError()).isTrue();
        assertThat(ManufacturingKafkaConsumer.isAbnormalAnalysis(abnormal)).isTrue();
    }

    @Test
    void pressMissingCountIncreaseIsFlaggedAsWarning() {
        Map<String, Object> pressData = new java.util.HashMap<>();
        pressData.put("targetCycleTimeSec", 40);
        pressData.put("countIncreaseYn", null);
        ManufacturingAnalysisEvent result = analyzer.analyze(raw(ProcessCode.PRESS, Map.of(
                "processMetrics", Map.of("cycleTimeSec", 40, "stationDelaySec", 0),
                "processData", Map.of("press", pressData)
        )));

        assertThat(result.riskLevel()).isEqualTo("WARNING");
        assertThat(result.analysisResult().isAbnormal()).isTrue();
    }

    @Test
    void bodyCollisionRiskIsFlaggedAsCritical() {
        ManufacturingAnalysisEvent result = analyzer.analyze(raw(ProcessCode.BODY, Map.of(
                "processData", Map.of("body", Map.of(
                        "robotMotionStatus", "COLLISION_RISK",
                        "robotOperationMode", "AUTO",
                        "frequencyPeakBand", "HIGH",
                        "frequencyBands", Map.of("LOW", 0.001, "MEDIUM", 0.002, "HIGH", 0.003)
                )),
                "sensor", Map.of(
                        "robotArmVibration", Map.of(
                                "vibrationScore", 0.1,
                                "vibrationPeak", 0.001,
                                "vibrationRms", 0.001
                        )
                )
        )));

        assertThat(result.riskLevel()).isEqualTo("CRITICAL");
        assertThat(result.analysisResult().isAbnormal()).isTrue();
    }

    // ==============================
    // 신규 테스트: riskScore = processRisk
    // ==============================

    @Nested
    @DisplayName("riskScore = processRisk 단독 계산 검증")
    class RiskScoreEqualsProcessRisk {

        @Test
        @DisplayName("1. riskScore는 processRisk와 동일해야 한다")
        void riskScoreEqualsProcessRisk() {
            // ASSEMBLY: sequenceError 없이 정상 처리
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 1,
                            "missingPartCount", 0,
                            "fasteningErrorCount", 0
                    ))
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            double overallRiskScore = result.riskScores().overallRiskScore();
            // processRisk = 4.0 with the current assembly weighting
            // riskScore should equal processRisk = 4.0
            assertThat(overallRiskScore).isEqualTo(4.0);
        }

        @Test
        @DisplayName("2. bottleneckRisk/defectTransferRisk가 높아도 processRisk가 낮으면 riskScore는 낮다")
        void highBottleneckAndDefectDoNotRaiseRiskScoreWhenProcessRiskIsLow() {
            // PRESS: countIncreaseYn=true, stationDelaySec=0, rmsAmpere=0, cycleTime=targetTime
            // → processRisk = 0 (모든 항목 0)
            // bottleneckRisk, defectTransferRisk 는 이벤트에 sensor 데이터가 없으면 0이지만
            // 아래에서 sensor 값을 높게 주면 bottleneckRisk/defectTransferRisk는 높아지나
            // processRisk(PRESS)는 stationDelaySec/cycleOverTarget/rmsAmpere/countIncrease 기반이므로 낮을 수 있음
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "processMetrics", Map.of(
                            "cycleTimeSec", 40,
                            "stationDelaySec", 0,
                            "waitingTimeSec", 20,   // bottleneckRisk 상승
                            "queueLength", 10,       // bottleneckRisk 상승
                            "wipCount", 10           // bottleneckRisk 상승
                    ),
                    "sensor", Map.of(
                            "current", Map.of("rmsAmpere", 0),
                            "vibration", Map.of("vibrationScore", 1.0),   // defectTransferRisk 상승
                            "robotArmVibration", Map.of("vibrationScore", 1.0),
                            "thermal", Map.of("maxTemperature", 80)        // defectTransferRisk 상승
                    ),
                    "processData", Map.of(
                            "press", Map.of("targetCycleTimeSec", 40, "countIncreaseYn", true)
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            // processRisk(PRESS) = clamp(0*6 + 0*5 + 0*8 + 0) = 0
            assertThat(result.riskScores().overallRiskScore()).isEqualTo(0.0);
            assertThat(result.riskLevel()).isEqualTo("LOW");
        }

        @Test
        @DisplayName("3. processRisk >= 60이면 severity WARNING, isAbnormal = true")
        void processRiskAbove60TriggersWarning() {
            // ASSEMBLY: min(40, 2*25) + min(35, 1*30) = 40 + 30 = 70 >= 60
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 2,
                            "missingPartCount", 1,
                            "fasteningErrorCount", 0
                    ))
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);

            assertThat(result.riskScores().overallRiskScore()).isEqualTo(11.0);
            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(result.analysisResult().isAbnormal()).isTrue();
            assertThat(analyzer.requiresAlert(result)).isTrue();
        }

        @Test
        @DisplayName("4. processRisk >= 80이면 severity CRITICAL, isAbnormal = true")
        void processRiskAbove80TriggersCritical() {
            // ASSEMBLY: min(40, 2*25) + min(35, 1*30) + min(25, 1*20) = 40 + 30 + 20 = 90 >= 80
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 2,
                            "missingPartCount", 1,
                            "fasteningErrorCount", 1
                    ))
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);

            assertThat(result.riskScores().overallRiskScore()).isEqualTo(13.0);
            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(result.analysisResult().isAbnormal()).isTrue();
        }

        @Test
        @DisplayName("4b. PRESS 공정에서 riskScore=100일 때 메시지와 abnormalType이 올바르게 생성되는지 확인")
        void pressProcessRisk100GeneratesAbnormalTypeProcessAndCorrectMessage() {
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "processMetrics", Map.of(
                            "cycleTimeSec", 100.0,
                            "stationDelaySec", 100.0
                    ),
                    "sensor", Map.of(
                            "current", Map.of("rmsAmpere", 5.0)
                    ),
                    "processData", Map.of(
                            "press", Map.of(
                                    "targetCycleTimeSec", 40.0,
                                    "countIncreaseYn", false
                             )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

            assertThat(result.riskScores().overallRiskScore()).isEqualTo(100.0);
            assertThat(result.reason().mainReason()).isEqualTo("프레스 공정 위험이 감지되었습니다.");

            assertThat(detail.abnormalType()).isEqualTo("PROCESS");
            assertThat(detail.analysisMessageReason()).isEqualTo("프레스 공정 위험이 감지되었습니다.");
        }

        @Test
        @DisplayName("4c. 설비 상태 이상이면서 riskScore가 60 이상인 경우 abnormalType=EQUIPMENT 확인")
        void equipmentAbnormalTakesPrecedenceOverProcessRiskForAbnormalType() {
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "equipmentStatus", Map.of("operationStatus", "FAULT"),
                    "processMetrics", Map.of(
                            "cycleTimeSec", 100.0,
                            "stationDelaySec", 100.0
                    ),
                    "sensor", Map.of(
                            "current", Map.of("rmsAmpere", 5.0)
                    ),
                    "processData", Map.of(
                            "press", Map.of(
                                    "targetCycleTimeSec", 40.0,
                                    "countIncreaseYn", false
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

            assertThat(result.riskScores().overallRiskScore()).isEqualTo(100.0);
            assertThat(result.analysisResult().isEquipmentFault()).isTrue();
            assertThat(result.reason().mainReason()).isEqualTo("설비 상태값에서 이상(WARNING/STOPPED/FAULT)이 감지되었습니다.");

            assertThat(detail.abnormalType()).isEqualTo("EQUIPMENT");
            assertThat(detail.analysisMessageReason()).isEqualTo("설비 상태값에서 이상(WARNING/STOPPED/FAULT)이 감지되었습니다.");
        }

        @Test
        @DisplayName("4d. bottleneck 위험도가 높으나 processRisk가 낮고 설비가 정상인 경우 isAbnormal=false 확인")
        void bottleneckRiskDoesNotMakeStandardAnalysisAbnormal() {
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "processMetrics", Map.of(
                            "cycleTimeSec", 40.0,
                            "stationDelaySec", 0.0,
                            "waitingTimeSec", 100.0
                    ),
                    "processData", Map.of(
                            "press", Map.of(
                                    "targetCycleTimeSec", 40.0,
                                    "countIncreaseYn", true
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

            assertThat(result.riskScores().overallRiskScore()).isEqualTo(0.0);
            assertThat(result.riskScores().bottleneckRisk()).isGreaterThanOrEqualTo(60.0);

            assertThat(result.analysisResult().isAbnormal()).isFalse();
            assertThat(detail.isAbnormal()).isFalse();
            assertThat(result.reason().mainReason()).isEqualTo("주요 공정 지표가 정상 범위입니다.");
            assertThat(detail.abnormalType()).isNull();
        }
    }


    @Nested
    @DisplayName("equipmentStatus 상태값 기반 설비 이상 판단 검증")
    class EquipmentStatusBasedFaultDetection {

        @Test
        @DisplayName("5a. equipmentStatus=FAULT → isEquipmentFault=true, isEquipmentAbnormal=true")
        void equipmentStatusFaultTriggersFault() {
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "FAULT");

            assertThat(analyzer.analyze(raw).analysisResult().isEquipmentFault()).isTrue();
            assertThat(analyzer.isEquipmentAbnormal(raw)).isTrue();
        }

        @Test
        @DisplayName("5b. equipmentStatus=STOPPED → isEquipmentFault=true (수치 계산 무관)")
        void equipmentStatusStoppedTriggersFaultRegardlessOfNumerics() {
            // sensor 값이 모두 0이어서 equipmentRisk(수치)는 0이지만, 상태값으로 감지
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "STOPPED");

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            assertThat(result.analysisResult().isEquipmentFault()).isTrue();
            assertThat(analyzer.isEquipmentAbnormal(raw)).isTrue();
        }



        @Test
        @DisplayName("5d. equipmentStatus=WARNING → isEquipmentFault=true")
        void equipmentStatusWarningTriggersFault() {
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "WARNING");

            assertThat(analyzer.analyze(raw).analysisResult().isEquipmentFault()).isTrue();
            assertThat(analyzer.isEquipmentAbnormal(raw)).isTrue();
        }

        @Test
        @DisplayName("5e. equipmentStatus=RUNNING (정상) → isEquipmentFault=false")
        void normalEquipmentStatusDoesNotTriggerFault() {
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "RUNNING");

            assertThat(analyzer.analyze(raw).analysisResult().isEquipmentFault()).isFalse();
            assertThat(analyzer.isEquipmentAbnormal(raw)).isFalse();
        }

        @Test
        @DisplayName("5f. operationStatus=STOPPED (event_json 내부) → isEquipmentFault=true")
        void innerOperationStatusStoppedTriggersFault() {
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "equipmentStatus", Map.of("operationStatus", "STOPPED")
            ));

            assertThat(analyzer.analyze(raw).analysisResult().isEquipmentFault()).isTrue();
            assertThat(analyzer.isEquipmentAbnormal(raw)).isTrue();
        }
    }

    @Nested
    @DisplayName("alert topic 발행 조건 검증")
    class AlertPublishingConditions {

        @Test
        @DisplayName("6a. riskScore >= 60 (WARNING) → requiresAlert=true (Case A)")
        void warningRiskScoreTriggersAlert() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 2,
                            "missingPartCount", 1,
                            "fasteningErrorCount", 0
                    ))
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);

            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(analyzer.requiresAlert(result)).isTrue();
        }

        @Test
        @DisplayName("6b. riskScore >= 80 (CRITICAL) → requiresAlert=true (Case A)")
        void criticalRiskScoreTriggersAlert() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 2,
                            "missingPartCount", 1,
                            "fasteningErrorCount", 1
                    ))
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);

            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(analyzer.requiresAlert(result)).isTrue();
        }

        @Test
        @DisplayName("7. 설비 이상 상태 → isEquipmentAbnormal=true (Case B)")
        void equipmentAbnormalStatusTriggersEquipmentAlert() {
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "FAULT");

            assertThat(analyzer.isEquipmentAbnormal(raw)).isTrue();

            ManufacturingAlertEvent alert = analyzer.toEquipmentStatusAlert(raw);
            assertThat(alert.alertType()).isEqualTo("EQUIPMENT_ABNORMAL");
            assertThat(alert.riskLevel()).isEqualTo("CRITICAL");
            assertThat(analyzer.toEquipmentStatusEvent(raw).operationStatus()).isEqualTo("FAULT");
            assertThat(analyzer.toEquipmentStatusEvent(raw).riskLevel()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("8. alert topic message key는 alertId (PROCESS_RISK 케이스)")
        void alertEventAlertIdIsNotBlank() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of("assembly", Map.of(
                            "sequenceErrorCount", 2,
                            "missingPartCount", 0,
                            "fasteningErrorCount", 0
                    ))
            ));
            ManufacturingAnalysisEvent analysis = analyzer.analyze(raw);
            ManufacturingAlertEvent alert = analyzer.toAlertEvent(analysis);

            // alertId가 null/blank 이면 sendAlert()에서 requiredTextKey 예외 발생 → 검증
            assertThat(alert.alertId()).isNotBlank();
            assertThat(alert.alertType()).isEqualTo("MANUFACTURING_ABNORMAL");
        }

        @Test
        @DisplayName("8b. alert topic message key는 alertId (EQUIPMENT_STATUS 케이스)")
        void equipmentStatusAlertEventAlertIdIsNotBlank() {
            ManufacturingRawEvent raw = rawWithEquipmentStatus(ProcessCode.PRESS, "STOPPED");
            ManufacturingAlertEvent alert = analyzer.toEquipmentStatusAlert(raw);

            assertThat(alert.alertId()).isNotBlank();
            assertThat(alert.alertType()).isEqualTo("EQUIPMENT_ABNORMAL");
        }

        @Test
        @DisplayName("9. overallFormula 는 'riskScore = processRisk' 이어야 한다")
        void overallFormulaReflectsProcessRiskOnly() {
            ManufacturingRawEvent raw = createRawEvent();
            ManufacturingEventAnalyzer.AnalysisDetail detail = analyzer.analyzeDetail(raw);

            assertThat(detail.overallFormula()).isEqualTo("riskScore = processRisk");
        }

        @Test
        @DisplayName("10. PRESS 중간 점수대 검증 - 100점으로 포화되지 않는지 확인")
        void pressIntermediateRiskScore() {
            ManufacturingRawEvent raw = raw(ProcessCode.PRESS, Map.of(
                    "processMetrics", Map.of(
                            "cycleTimeSec", 45.0,
                            "stationDelaySec", 5.0
                    ),
                    "sensor", Map.of(
                            "current", Map.of("rmsAmpere", 2.5)
                    ),
                    "processData", Map.of(
                            "press", Map.of(
                                    "targetCycleTimeSec", 40.0,
                                    "countIncreaseYn", true
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            double score = result.riskScores().overallRiskScore();

            // current press weighting yields 15.0 for this input
            assertThat(score).isEqualTo(15.0);
            assertThat(score).isBetween(0.0, 20.0);
        }

        @Test
        @DisplayName("11. ASSEMBLY 단일 경미/중대 이상 점수 검증")
        void assemblySingleAnomalyRiskScore() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of(
                            "assembly", Map.of(
                                    "sequenceErrorCount", 1,
                                    "missingPartCount", 0,
                                    "fasteningErrorCount", 0
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            double score = result.riskScores().overallRiskScore();

            // min(45, 1 * 4) = 4.0
            assertThat(score).isEqualTo(4.0);
            assertThat(score).isBetween(0.0, 20.0);
        }

        @Test
        @DisplayName("12. ASSEMBLY 복합 이상 점수 검증 (90점)")
        void assemblyCompoundAnomalyRiskScore() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of(
                            "assembly", Map.of(
                                    "sequenceErrorCount", 1,
                                    "missingPartCount", 1,
                                    "fasteningErrorCount", 1
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            double score = result.riskScores().overallRiskScore();

            // min(45, 4) + min(35, 3) + min(20, 2) = 4 + 3 + 2 = 9.0
            assertThat(score).isEqualTo(9.0);
            assertThat(result.riskLevel()).isEqualTo("LOW");
        }

        @Test
        @DisplayName("13. ASSEMBLY 심각 이상 점수 검증 (100점)")
        void assemblySevereAnomalyRiskScore() {
            ManufacturingRawEvent raw = raw(ProcessCode.ASSEMBLY, Map.of(
                    "processData", Map.of(
                            "assembly", Map.of(
                                    "sequenceErrorCount", 3,
                                    "missingPartCount", 2,
                                    "fasteningErrorCount", 2
                            )
                    )
            ));

            ManufacturingAnalysisEvent result = analyzer.analyze(raw);
            double score = result.riskScores().overallRiskScore();

            // min(45, 12) + min(35, 6) + min(20, 4) = 12 + 6 + 4 = 22.0
            assertThat(score).isEqualTo(22.0);
            assertThat(result.riskLevel()).isEqualTo("LOW");
        }

    }

    // ==============================
    // 공통 helper 메서드
    // ==============================

    private ManufacturingRawEvent raw(ProcessCode processCode, Map<String, Object> json) {
        return new ManufacturingRawEvent(1, "EVT-X", LocalDateTime.now(), 1L, 2L,
                processCode, "EQ-1", "TYPE", "RUNNING", "EVENT", json);
    }

    private ManufacturingRawEvent rawWithEquipmentStatus(ProcessCode processCode, String status) {
        return new ManufacturingRawEvent(1, "EVT-X", LocalDateTime.now(), 1L, 2L,
                processCode, "EQ-1", "TYPE", status, "EVENT", Map.of());
    }

    private ManufacturingRawEvent createRawEvent() {
        // PRD 구조의 processMetrics, sensor, product 상세 데이터 구성
        return new ManufacturingRawEvent(
                1L,
                "EVT-20260618-001",
                LocalDateTime.of(2026, 6, 18, 10, 0),
                100L,
                200L,
                ProcessCode.PRESS,
                "EQ_PRESS_01",
                "HYDRAULIC_PRESS",
                "WARNING",
                "PROCESS_STATUS",
                Map.of(
                        "location", Map.of(
                                "factoryCode", "AIMS_FACTORY_01",
                                "lineCode", "PRESS_LINE_01"
                        ),
                        "product", Map.of(
                                "productId", "PRODUCT-000001",
                                "carId", "CAR-000001"
                        ),
                        "processMetrics", Map.of(
                                "cycleTimeSec", 42.5,
                                "waitingTimeSec", 8.3,
                                "stationDelaySec", 5.7,
                                "queueLength", 7,
                                "equipmentIdleTimeSec", 12.1
                        ),
                        "sensor", Map.of(
                                "vibration", Map.of("vibrationScore", 0.21),
                                "thermal", Map.of("maxTemperature", 52.9)
                        )
                )
        );
    }
}

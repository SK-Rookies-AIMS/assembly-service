# 프레스 이상 탐지 API 프론트 연동 가이드

## 변경 요약

프레스 이상 탐지 화면은 아래 API를 사용합니다.

```http
GET /api/process/press/analysis
```

이번 변경으로 프론트에서 반드시 반영해야 하는 사항은 두 가지입니다.

1. 그래프와 날짜 필터의 기준 시간은 `manufacturing_event_json.event_json` 안의 `eventTime`입니다.
2. 오늘 날짜 데이터가 없을 수 있으므로, 응답의 `dateOptions`로 날짜 select box를 구성해야 합니다.

원천 이벤트 JSON 예시:

```json
{
  "eventTime": "2026-06-02T13:00:00"
}
```

## Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `date` | `yyyy-MM-dd` | 아니오 | 최신 이벤트 날짜 | 특정 날짜 기준 조회 |
| `from` | `yyyy-MM-ddTHH:mm:ss` | 아니오 | 해당 날짜 00:00:00 | 조회 시작 시간 |
| `to` | `yyyy-MM-ddTHH:mm:ss` | 아니오 | 해당 날짜 23:59:59.999999999 | 조회 종료 시간 |
| `endAt` | `yyyy-MM-ddTHH:mm:ss` | 아니오 | 없음 | 그래프 드래그 시 이전 시간대 조회용 종료 시간 |
| `limit` | number | 아니오 | `30` | 조회 개수. 서버에서 `1~200` 범위로 제한 |

파라미터 없이 호출하면 기본으로 `manufacturing_event_json` 테이블에서 `eventJson`의 `eventTime`이 가장 최근인 날짜의 데이터를 조회합니다. (데이터가 전혀 없는 경우에만 오늘 날짜가 기본값으로 동작합니다.)

```http
GET /api/process/press/analysis
```

## 응답 구조

```json
{
  "success": true,
  "data": {
    "date": "2026-07-01",
    "from": "2026-07-01T00:00:00",
    "to": "2026-07-01T23:59:59.999999999",
    "previousEndAt": null,
    "dateOptions": [
      {
        "date": "2026-06-02",
        "sampleEventId": "EVT-20260602-000017"
      }
    ],
    "metrics": {
      "targetCycleTimeSec": null,
      "actualCycleTimeSec": null,
      "cycleTimeGapSec": null,
      "timestampDelaySec": null,
      "riskScore": null,
      "riskScoreScale": "0-100",
      "severity": null
    },
    "chart": [],
    "alert": {
      "detected": false,
      "title": "프레스 이상 정지 미탐지",
      "reasons": []
    }
  },
  "message": "Press anomaly detection dashboard",
  "timestamp": "2026-07-01T10:00:00"
}
```

## 현재 데이터가 없는 경우

기본 조회는 최신 프레스 이벤트 날짜 기준입니다. 최신 이벤트 날짜에 분석 데이터가 존재하지 않거나, 전체 프레스 데이터가 아예 없다면 `data.chart`가 빈 배열로 내려올 수 있습니다.

이 경우 프론트는 `data.dateOptions`를 사용해 날짜 select box를 보여줘야 합니다.

```ts
type PressDateOption = {
  date: string;
  sampleEventId: string;
};

const response = await api.get("/api/process/press/analysis");
const dashboard = response.data.data;

if (dashboard.chart.length === 0 && dashboard.dateOptions.length > 0) {
  setDateOptions(
    dashboard.dateOptions.map((option: PressDateOption) => ({
      label: option.date,
      value: option.date,
      sampleEventId: option.sampleEventId,
    }))
  );
}
```

사용자가 `2026-06-02`를 선택하면 아래처럼 다시 조회합니다.

```http
GET /api/process/press/analysis?date=2026-06-02&limit=30
```

## 화면 필드 매핑

| 화면 영역 | 표시값 | 응답 필드 |
| --- | --- | --- |
| 날짜 select box | 선택 가능한 날짜 | `data.dateOptions[].date` |
| 날짜 select box | 날짜 대표 이벤트 ID | `data.dateOptions[].sampleEventId` |
| 상단 지표 | 기준 사이클 타임 | `data.metrics.targetCycleTimeSec` |
| 상단 지표 | 실제 사이클 타임 | `data.metrics.actualCycleTimeSec` |
| 상단 지표 | 사이클 지연 | `data.metrics.cycleTimeGapSec` |
| 상단 지표 | Timestamp 지연 | `data.metrics.timestampDelaySec` |
| 상단 지표 | 위험도 | `data.metrics.riskScore` |
| 상단 지표 | 전체 위험도 | `data.metrics.severity` |
| 그래프 | X축 시간 | `data.chart[].timestamp` |
| 그래프 | 실제 사이클 타임 | `data.chart[].actualCycleTimeSec` |
| 그래프 | 기준 사이클 타임 | `data.chart[].targetCycleTimeSec` |
| 그래프 | Timestamp 지연 | `data.chart[].timestampDelaySec` |
| 우측 알림 | 알림 표시 여부 | `data.alert.detected` |
| 우측 알림 | 제목 | `data.alert.title` |
| 우측 알림 | 사유 목록 | `data.alert.reasons` |

## 그래프 시간 기준

`data.chart[].timestamp`는 MainDB 분석 결과 시간이 아닙니다.

아래 원천 이벤트 시간입니다.

```text
manufacturing_event_json.event_json.eventTime
```

따라서 프론트에서는 `timestamp`를 그대로 X축 기준으로 사용하면 됩니다.

```ts
const chartData = dashboard.chart.map((point) => ({
  x: point.timestamp,
  actualCycleTimeSec: point.actualCycleTimeSec,
  targetCycleTimeSec: point.targetCycleTimeSec,
  timestampDelaySec: point.timestampDelaySec,
}));
```

## 그래프 드래그 이전 조회

`data.chart`는 이벤트 JSON의 `eventTime` 기준 시간 오름차순으로 반환됩니다.

그래프를 과거 시간대로 드래그하면 다음 순서로 추가 조회합니다.

1. 현재 응답의 `data.previousEndAt` 값을 저장합니다.
2. 다음 요청에 `endAt={previousEndAt}`을 전달합니다.
3. 새 응답의 `data.chart`를 기존 데이터 앞쪽에 병합합니다.
4. 새 응답의 `data.previousEndAt`으로 다음 이전 구간 조회 커서를 갱신합니다.

예시:

```http
GET /api/process/press/analysis?date=2026-06-02&endAt=2026-06-02T12:59:59.999999999&limit=30
```

`previousEndAt`이 `null`이면 더 이전에 조회할 데이터가 없다고 처리하면 됩니다.

## 정상 데이터 응답 예시

```json
{
  "success": true,
  "data": {
    "date": "2026-06-02",
    "from": "2026-06-02T00:00:00",
    "to": "2026-06-02T23:59:59.999999999",
    "previousEndAt": "2026-06-02T12:59:59.999999999",
    "dateOptions": [
      {
        "date": "2026-06-02",
        "sampleEventId": "EVT-20260602-000017"
      }
    ],
    "metrics": {
      "targetCycleTimeSec": 12.0,
      "actualCycleTimeSec": 14.6,
      "cycleTimeGapSec": 2.6,
      "timestampDelaySec": 4.2,
      "riskScore": 78.0,
      "riskScoreScale": "0-100",
      "severity": "CRITICAL"
    },
    "chart": [
      {
        "eventId": "EVT-20260602-000017",
        "analysisId": "ANL-20260602-000017",
        "timestamp": "2026-06-02T13:00:00",
        "targetCycleTimeSec": 12.0,
        "actualCycleTimeSec": 14.6,
        "cycleTimeGapSec": 2.6,
        "timestampDelaySec": 4.2,
        "riskScore": 78.0,
        "countIncreaseYn": true,
        "isAbnormal": true,
        "severity": "CRITICAL"
      }
    ],
    "alert": {
      "detected": true,
      "title": "프레스 이상 정지 탐지",
      "reasons": [
        "count_increase_yn = true",
        "실제 사이클 타임이 기준보다 초과",
        "Timestamp 지연 발생",
        "press_analysis_result 기준 이상 정지 의심"
      ]
    }
  },
  "message": "Press anomaly detection dashboard",
  "timestamp": "2026-07-01T10:00:00"
}
```

## 프론트 수정 체크리스트

- 기본 진입 시 `GET /api/process/press/analysis`를 호출합니다.
- `data.chart`가 비어 있고 `data.dateOptions`가 있으면 날짜 select box를 노출합니다.
- 날짜 select box의 `value`는 `data.dateOptions[].date`로 둡니다.
- 사용자가 날짜를 선택하면 `?date=선택날짜`로 재조회합니다.
- 그래프 X축은 `data.chart[].timestamp`를 사용합니다.
- `timestamp`는 원천 이벤트 JSON의 `eventTime`이라는 점을 전제로 표시합니다.
- 이전 시간대 드래그 조회는 `data.previousEndAt`을 다음 요청의 `endAt`으로 전달합니다.

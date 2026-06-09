# Assembly Service

`assembly-service`는 모빌리티 스마트팩토리 관제 시스템에서 제조 공정 이벤트를 수집하고, 공정/설비/품질 분석 결과를 대시보드에 제공하는 Spring Boot 기반 백엔드 서비스입니다.

이 서비스는 프레스, 차체, 도장, 의장 조립 공정에서 발생하는 생산 이벤트와 센서 데이터를 기반으로 제조 병목 탐지, 공정별 이상 분석, 불량 전이 예측 결과를 생성하거나 조회하는 역할을 담당합니다.

## 주요 역할

- 제조 공정 이벤트 수집 및 조회
- 차량별 공정 이동 이력 관리
- 프레스, 차체, 도장, 의장 공정별 분석 결과 관리
  - 프레스 이상 정지 탐지
  - 차체 로봇 이상 동작 및 충돌 위험 탐지
  - 도장 품질 이상 탐지
  - 의장 조립 순서 오류 탐지
- 실시간 병목 분석 결과 제공
- 공정 간 불량 전이 예측 결과 제공
- Kafka 기반 제조 이벤트 스트리밍 연동
- Redis 기반 실시간 대시보드 캐시 연동
- OpenSearch 기반 제조 이벤트/분석 로그 검색 연동

## MVP 기능

### 제조 병목 탐지

Bosch Production Line Performance Dataset의 Station 통과 시간, 공정 처리 시간, 대기 시간을 기반으로 병목 공정을 탐지합니다.

판단 예시:

- 평균 처리 시간 대비 30% 이상 증가
- 특정 Station 체류 시간 급증
- 생산 대기열 증가
- 공정별 지연 위험도 증가

### 공정 간 불량 전이 예측

공정별 센서 데이터, 공정 이동 이력, 품질 검사 결과를 기반으로 특정 공정의 이상이 후속 공정의 불량으로 이어질 가능성을 예측합니다.

예시:

- 차체 공정 이상이 도장 공정 불량으로 전이될 확률
- 도장 공정 불량 위험도
- 주요 원인 Station 및 Sensor Feature
- 위험도 등급: `LOW`, `MEDIUM`, `HIGH`

### 공정별 분석

- 프레스: 전류 RMS, 진동, 생산 카운트, Timestamp 지연 기반 이상 정지 탐지
- 차체: 로봇 전류, 진동, 충돌 위험, 로봇 동작 이상 탐지
- 도장: 열화상 온도, 표면 품질 점수, 불량률 기반 품질 이상 탐지
- 의장: 조립 순서 오류, 부품 누락, 체결 오류 탐지

### 알림

공정별 분석 결과, 병목 분석 결과, 불량 전이 예측 결과에서 이상이 탐지되면 알림 데이터를 생성하고 프론트엔드 알림 화면에서 조회할 수 있도록 구성합니다.

## 활용 데이터셋

### Ford Engine Dataset

엔진 진동 시계열 데이터 기반 정상/이상 분류 데이터셋입니다.

- 데이터 형태: 시계열
- 샘플 길이: 500
- 라벨: `1` 정상, `-1` 이상
- 활용: 프레스/로봇 진동 이상 탐지, 예지보전

### 소성가공 자원최적화 AI 데이터셋

프레스 유압모터 및 로봇 전류 데이터를 포함합니다.

- 주요 컬럼: `Time_s[s]`, `RMS[A]`, `Acceleration[g]`
- 활용: 전류 Peak 탐지, 모터 과부하 탐지, 설비 정지 상태 분석

### 머신비전 AI 데이터셋

열화상 기반 품질 검사 데이터셋입니다.

- 데이터: 센서/전류 시계열 CSV, 라벨 JSON
- 라벨: `0` 정상, `1` 이상/불량
- 활용: 도장 품질 이상 탐지, 품질 검사 이벤트 생성

### Bosch Production Line Performance Dataset

대규모 제조 공정 데이터셋입니다.

- Numeric: 센서/측정값
- Date: 공정 시간 정보
- Categorical: 공정 상태 정보
- Response: 정상/불량 라벨
- 활용: 제조 병목 탐지, 공정 간 불량 전이 예측, 생산라인 통합 분석

## DB 구조

DB는 `sampledb`와 `maindb`로 분리합니다. 두 DB는 같은 MySQL 서버와 포트를 사용하지만 schema를 분리합니다.

### sampledb

관제 시스템으로 유입되는 샘플 원천 데이터를 저장합니다.

주요 테이블:

- `car_master`: 차량 기준 정보
- `equipment`: 샘플 설비 정보
- `manufacturing_event`: 공정/센서/품질 공통 이벤트
- `thermal_vision`: 열화상 품질 샘플 데이터
- `robot_arm_vibration`: 로봇팔 진동 원본 데이터

### maindb

대시보드 조회와 분석 결과 데이터를 저장합니다.

주요 테이블:

- `product_process_history`: 차량별 공정 이동 이력
- `press_analysis_result`: 프레스 공정 분석 결과
- `body_analysis_result`: 차체 공정 분석 결과
- `paint_analysis_result`: 도장 공정 분석 결과
- `assembly_analysis_result`: 의장 공정 분석 결과
- `bottleneck_analysis_result`: 병목 분석 결과
- `defect_transfer_prediction_result`: 불량 전이 예측 결과
- `notification`: 실시간 알림

### DB 간 참조 방식

`sampledb`와 `maindb` 사이에는 물리 FK를 사용하지 않고 논리 참조를 사용합니다.

예시:

- `maindb.body_analysis_result.manufacturing_event_id`
- `sampledb.manufacturing_event.id`
- `sampledb.robot_arm_vibration.manufacturing_event_id`

## 인프라 연동

### Kafka

제조 이벤트 스트리밍에 사용합니다.

예시 Topic:

- `raw-current-topic`
- `virtual-acceleration-topic`
- `manufacturing-event-topic`
- `analysis-result-topic`

### Redis

실시간 대시보드 조회 성능을 위해 캐시로 사용합니다.

예시 캐시 데이터:

- 설비별 Safe Score
- 병목 위험도
- RUL 예측치
- 최근 알림 수

### OpenSearch

제조 이벤트 로그와 분석 결과 검색에 사용합니다.

활용 예시:

- 병목 공정 검색
- 불량 이력 검색
- 차량별 제조 이력 검색
- 센서 트렌드 집계

## 패키지 구조

현재 프로젝트는 계층형 패키지 구조를 사용합니다.

```text
com.aims.assembly
 ├── common
 │   ├── code          # 공통 성공/에러 코드 DTO 및 인터페이스
 │   ├── response      # 공통 API 응답
 │   └── status        # 공통 성공/에러 상태 enum
 ├── config            # Spring 설정
 │   ├── DataSourceConfig
 │   ├── KafkaConfig
 │   ├── OpenSearchConfig
 │   ├── QueryDSLConfig
 │   ├── RedisCacheConfig
 │   ├── SwaggerConfig
 │   └── WebConfig
 ├── controller        # API Controller
 ├── domain            # 공통 Entity 기반 클래스 및 향후 JPA Entity
 ├── dto               # 요청/응답 DTO
 ├── exception         # 전역 예외 처리 및 비즈니스 예외
 ├── mapper            # Entity/Model -> DTO 변환
 ├── properties        # application.yaml 바인딩 설정 클래스
 ├── repository        # 데이터 접근 계층
 └── service           # 비즈니스 로직 계층
```

## 현재 기본 설정

### Profile

- `local`: 로컬 개발용, 컬러 콘솔 로그
- `dev`: 개발 서버용, 콘솔 + 파일 로그
- `prod`: 운영 서버용, 파일 로그 중심

### DB

환경변수 기반으로 `maindb`, `sampledb`를 설정합니다.

- `MAIN_DB_JDBC_URL`
- `MAIN_DB_USERNAME`
- `MAIN_DB_PASSWORD`
- `SAMPLE_DB_JDBC_URL`
- `SAMPLE_DB_USERNAME`
- `SAMPLE_DB_PASSWORD`

### 환경변수

`.env.example`에 필요한 환경변수 예시가 정의되어 있습니다.

Spring Boot는 `.env` 파일을 자동으로 읽지 않으므로 IDE 실행 설정, Docker Compose, 쉘 환경변수 등을 통해 주입해야 합니다.

## API 응답 구조

모든 API는 공통 응답 구조를 사용합니다.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com"
  },
  "message": "요청이 성공했습니다.",
  "timestamp": "2026-06-08T16:00:00"
}
```

## 로컬 실행

```bash
./gradlew bootRun
```

Windows:

```bash
./gradlew.bat bootRun
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## 테스트

```bash
./gradlew test
```

Windows:

```bash
./gradlew.bat test
```

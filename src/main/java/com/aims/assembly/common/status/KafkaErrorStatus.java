package com.aims.assembly.common.status;

import com.aims.assembly.common.code.BaseErrorCode;
import com.aims.assembly.common.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum KafkaErrorStatus implements BaseErrorCode {

    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "KAFKA404_1",
            "제조 이벤트를 찾을 수 없습니다."
    ),
    UNSENT_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "KAFKA404_2",
            "전송 대기 중인 제조 이벤트가 없습니다."
    ),
    INVALID_EVENT_JSON(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA500_1",
            "제조 이벤트 JSON 데이터가 올바르지 않습니다."
    ),
    DUPLICATE_PROCESS_HANDLER(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA500_2",
            "동일한 제조 공정 처리기가 중복 등록되었습니다."
    ),
    UNSUPPORTED_PROCESS(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "KAFKA422_1",
            "지원하지 않는 제조 공정입니다."
    ),
    MESSAGE_SERIALIZATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA500_3",
            "Kafka 메시지 직렬화에 실패했습니다."
    ),
    MESSAGE_DESERIALIZATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA500_4",
            "Kafka 메시지 역직렬화에 실패했습니다."
    ),
    MESSAGE_PUBLISH_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "KAFKA503_1",
            "Kafka 메시지 전송에 실패했습니다."
    ),
    EVENT_STATUS_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA500_5",
            "제조 이벤트 전송 상태 갱신에 실패했습니다."
    ),
    BROKER_INSPECTION_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "KAFKA503_2",
            "Kafka/MSK 브로커 상태 조회에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .success(false)
                .code(code)
                .message(message)
                .httpStatus(httpStatus)
                .build();
    }
}

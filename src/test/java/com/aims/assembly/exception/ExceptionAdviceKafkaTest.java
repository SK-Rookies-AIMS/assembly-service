package com.aims.assembly.exception;

import com.aims.assembly.common.response.ApiResponse;
import com.aims.assembly.common.status.KafkaErrorStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompletableFuture에 감싸진 KafkaException의 공통 API 오류 응답 변환 검증.
 */
@DisplayName("Kafka 비동기 예외 응답 단위 테스트")
class ExceptionAdviceKafkaTest {

    private final ExceptionAdvice exceptionAdvice = new ExceptionAdvice();

    @Test
    @DisplayName("비동기 Kafka 발행 실패를 ErrorCode의 HTTP 상태와 메시지로 반환")
    void mapsAsyncKafkaExceptionToConfiguredHttpStatusAndMessage() {
        // Given: Kafka 메시지 발행 실패 예외
        KafkaException kafkaException = new KafkaException(
                KafkaErrorStatus.MESSAGE_PUBLISH_FAILED
        );

        // When: CompletableFuture 예외 처리
        ResponseEntity<Object> response = exceptionAdvice.handleAsyncException(
                new CompletionException(kafkaException)
        );

        // Then: Kafka ErrorCode의 HTTP 상태 및 공통 응답 메시지 검증
        assertThat(response.getStatusCode())
                .isEqualTo(KafkaErrorStatus.MESSAGE_PUBLISH_FAILED.getHttpStatus());
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);

        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assert body != null;
        assertThat(body.getSuccess()).isFalse();
        assertThat(body.getMessage())
                .isEqualTo(KafkaErrorStatus.MESSAGE_PUBLISH_FAILED.getMessage());
    }
}

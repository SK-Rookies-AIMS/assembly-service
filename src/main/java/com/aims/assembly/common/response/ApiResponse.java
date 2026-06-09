package com.aims.assembly.common.response;

import com.aims.assembly.common.code.BaseCode;
import com.aims.assembly.common.code.BaseErrorCode;
import com.aims.assembly.common.status.SuccessStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "data", "message", "timestamp"})
@Schema(description = "공통 API 응답")
public class ApiResponse<T> {

    @Schema(description = "요청 성공 여부", example = "true")
    private final Boolean success;

    @Schema(description = "응답 데이터")
    private final T data;

    @Schema(description = "응답 메시지", example = "로그인 성공")
    private final String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "응답 생성 시각", example = "2026-06-08T16:00:00")
    private final LocalDateTime timestamp;

    private ApiResponse(Boolean success, T data, String message, LocalDateTime timestamp) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, SuccessStatus.OK.getMessage());
    }

    public static ApiResponse<Void> success(String message) {
        return success(null, message);
    }

    public static <T> ApiResponse<T> of(BaseCode code, T data) {
        return success(data, code.getReasonHttpStatus().getMessage());
    }

    public static <T> ApiResponse<T> failure(String message, T data) {
        return new ApiResponse<>(false, data, message, LocalDateTime.now());
    }

    public static ApiResponse<Void> failure(String message) {
        return failure(message, null);
    }

    public static <T> ApiResponse<T> failure(BaseErrorCode code, T data) {
        return failure(code.getReasonHttpStatus().getMessage(), data);
    }

    public static ApiResponse<Void> failure(BaseErrorCode code) {
        return failure(code.getReasonHttpStatus().getMessage());
    }
}

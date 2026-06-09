package com.aims.assembly.common.status;

import com.aims.assembly.common.code.BaseCode;
import com.aims.assembly.common.code.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

    OK(HttpStatus.OK, "COMMON200", "요청이 성공했습니다."),
    CREATED(HttpStatus.CREATED, "COMMON201", "요청이 성공적으로 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getReason() {
        return ReasonDTO.builder()
                .success(true)
                .code(code)
                .message(message)
                .build();
    }

    @Override
    public ReasonDTO getReasonHttpStatus() {
        return ReasonDTO.builder()
                .success(true)
                .code(code)
                .message(message)
                .httpStatus(httpStatus)
                .build();
    }
}

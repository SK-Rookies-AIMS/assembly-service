package com.aims.assembly.common.code;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
public class ErrorReasonDTO {

    private final HttpStatus httpStatus;
    private final boolean success;
    private final String code;
    private final String message;
}

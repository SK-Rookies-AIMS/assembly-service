package com.aims.assembly.exception;

import com.aims.assembly.common.code.BaseErrorCode;
import com.aims.assembly.common.code.ErrorReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;

    public GeneralException(BaseErrorCode code) {
        super(code.getReasonHttpStatus().getMessage());
        this.code = code;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public GeneralException(BaseErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorReasonDTO getErrorReason() {
        return this.code.getReason();
    }

    public ErrorReasonDTO getErrorReasonHttpStatus() {
        return this.code.getReasonHttpStatus();
    }
}

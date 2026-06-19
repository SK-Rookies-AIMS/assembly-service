package com.aims.assembly.exception;

import com.aims.assembly.common.code.BaseErrorCode;
import com.aims.assembly.exception.handler.ErrorHandler;

public class KafkaException extends ErrorHandler {

    public KafkaException(BaseErrorCode code) {
        super(code);
    }

    public KafkaException(BaseErrorCode code, String message) {
        super(code, message);
    }

    public KafkaException(BaseErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

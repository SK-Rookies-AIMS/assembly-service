package com.aims.assembly.exception.handler;

import com.aims.assembly.common.code.BaseErrorCode;
import com.aims.assembly.exception.GeneralException;

public class ErrorHandler extends GeneralException {

    public ErrorHandler(BaseErrorCode code) {
        super(code);
    }

    public ErrorHandler(BaseErrorCode code, String message) {
        super(code, message);
    }
}

package com.aims.assembly.exception;

import com.aims.assembly.common.code.ErrorReasonDTO;
import com.aims.assembly.common.response.ApiResponse;
import com.aims.assembly.common.status.ErrorStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.nio.file.AccessDeniedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<Object> handleGeneralException(GeneralException exception) {
        ErrorReasonDTO reason = exception.getErrorReasonHttpStatus();
        ApiResponse<Object> body = ApiResponse.failure(exception.getMessage(), null);
        return ResponseEntity.status(reason.getHttpStatus()).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.merge(fieldName, message, (existingMessage, newMessage) -> existingMessage + ", " + newMessage);
        });

        return handleError(ErrorStatus.BAD_REQUEST, errors);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String fieldName = fieldError.getField();
            String message = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            errors.merge(fieldName, message, (existingMessage, newMessage) -> existingMessage + ", " + newMessage);
        });

        return handleError(ErrorStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return handleError(ErrorStatus.BAD_REQUEST, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException exception) {
        return handleError(ErrorStatus.FORBIDDEN, null);
    }

    @ExceptionHandler(ProcessEventException.class)
    public ResponseEntity<Object> handleProcessEventException(ProcessEventException exception) {
        ApiResponse<Object> body = ApiResponse.failure(exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return handleError(ErrorStatus.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<Object> handleError(ErrorStatus errorStatus, Object data) {
        ApiResponse<Object> body = ApiResponse.failure(errorStatus, data);
        return ResponseEntity.status(errorStatus.getHttpStatus()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ErrorStatus errorStatus = resolveErrorStatus(statusCode);
        ApiResponse<Object> response = ApiResponse.failure(errorStatus, null);
        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    private ErrorStatus resolveErrorStatus(HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return ErrorStatus.BAD_REQUEST;
        }
        if (statusCode.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return ErrorStatus.UNAUTHORIZED;
        }
        if (statusCode.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return ErrorStatus.FORBIDDEN;
        }
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorStatus.NOT_FOUND;
        }
        if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorStatus.METHOD_NOT_ALLOWED;
        }
        return ErrorStatus.INTERNAL_SERVER_ERROR;
    }
}

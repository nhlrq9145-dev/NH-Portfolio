package com.nh.customermanager.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String EMAIL_UNIQUE_CONSTRAINT =
            "uk_customers_email";
    private static final String PHONE_UNIQUE_CONSTRAINT =
            "uk_customers_phone";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 400);
        response.put("message", "提交的数据不符合要求");
        response.put("errors", errors);

        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "status",
                exception.getStatusCode().value()
        );
        response.put(
                "message",
                exception.getReason() == null
                        ? "请求处理失败"
                        : exception.getReason()
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>>
            handleDataIntegrityViolationException(
                    DataIntegrityViolationException exception
            ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("message", databaseConflictMessage(exception));

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    private String databaseConflictMessage(Throwable exception) {
        if (containsConstraint(exception, EMAIL_UNIQUE_CONSTRAINT)) {
            return "邮箱已存在";
        }

        if (containsConstraint(exception, PHONE_UNIQUE_CONSTRAINT)) {
            return "电话已存在";
        }

        return "数据冲突，请检查邮箱或电话是否已存在";
    }

    private boolean containsConstraint(
            Throwable exception,
            String constraintName
    ) {
        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();

            if (
                    message != null
                    && message.toLowerCase(Locale.ROOT)
                            .contains(constraintName)
            ) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}

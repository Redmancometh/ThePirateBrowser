package com.thepiratebrowser.web.config;

import com.thepiratebrowser.web.putio.PutIoService.PutIoNotConfiguredException;
import com.thepiratebrowser.web.putio.CastGrantService.InvalidCastGrantException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(Exception error) {
        String message = error instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getAllErrors().stream()
                        .findFirst().map(item -> item.getDefaultMessage())
                        .orElse("The request is invalid.")
                : error.getMessage();
        return Map.of("error", safe(message, "The request is invalid."));
    }

    @ExceptionHandler(PutIoNotConfiguredException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> putIoUnavailable(PutIoNotConfiguredException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(InvalidCastGrantException.class)
    @ResponseStatus(HttpStatus.GONE)
    Map<String, String> invalidCastGrant(InvalidCastGrantException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(IllegalStateException error) {
        return Map.of("error", safe(error.getMessage(), "The operation could not be completed."));
    }

    private static String safe(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}

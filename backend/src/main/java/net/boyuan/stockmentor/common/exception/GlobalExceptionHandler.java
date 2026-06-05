package net.boyuan.stockmentor.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException exception) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", 404);
        error.put("message", exception.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", exception.getStatusCode().value());
        error.put("message", exception.getReason() == null ? exception.getMessage() : exception.getReason());
        return new ResponseEntity<>(error, exception.getStatusCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", 400);
        error.put("message", exception.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}

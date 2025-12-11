package com.example.ticketero.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    String message,
    int status,
    LocalDateTime timestamp,
    List<String> errors
) {
    // Constructor simple
    public ErrorResponse(String message, int status) {
        this(message, status, LocalDateTime.now(), List.of());
    }

    // Constructor con errores múltiples
    public ErrorResponse(String message, int status, List<String> errors) {
        this(message, status, LocalDateTime.now(), errors);
    }
}
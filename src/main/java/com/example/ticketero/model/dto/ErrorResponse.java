package com.example.ticketero.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO para errores
 */
public record ErrorResponse(
    String message,
    int status,
    LocalDateTime timestamp,
    List<String> errors
) {
    /**
     * Constructor simple para errores sin detalles
     */
    public ErrorResponse(String message, int status) {
        this(message, status, LocalDateTime.now(), List.of());
    }

    /**
     * Constructor con lista de errores de validación
     */
    public ErrorResponse(String message, int status, List<String> errors) {
        this(message, status, LocalDateTime.now(), errors);
    }
}
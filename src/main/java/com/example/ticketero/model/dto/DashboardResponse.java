package com.example.ticketero.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO para dashboard administrativo
 */
public record DashboardResponse(
    Integer totalTicketsToday,
    Integer ticketsInQueue,
    Integer ticketsCompleted,
    Integer availableAdvisors,
    Integer busyAdvisors,
    Double averageWaitTime,
    List<QueueStatusResponse> queueStatus,
    LocalDateTime lastUpdated
) {
    /**
     * Factory method para crear dashboard vacío
     */
    public static DashboardResponse empty() {
        return new DashboardResponse(
            0, 0, 0, 0, 0, 0.0,
            List.of(),
            LocalDateTime.now()
        );
    }
}
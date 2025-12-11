package com.example.ticketero.model.dto;

import java.util.List;

public record DashboardResponse(
    Integer totalTicketsToday,
    Integer ticketsInQueue,
    Integer ticketsBeingAttended,
    Integer ticketsCompleted,
    List<QueueStatusResponse> queueStatus,
    List<AdvisorStatusResponse> advisorStatus
) {}

record AdvisorStatusResponse(
    Long id,
    String name,
    String status,
    Integer moduleNumber,
    Integer assignedTicketsCount
) {}
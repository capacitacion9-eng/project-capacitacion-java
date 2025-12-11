package com.example.ticketero.model.dto;

public record QueueStatusResponse(
    String queueType,
    String displayName,
    Integer totalInQueue,
    Integer avgWaitMinutes,
    Integer nextTicketNumber
) {}
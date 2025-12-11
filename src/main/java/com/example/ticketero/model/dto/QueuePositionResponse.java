package com.example.ticketero.model.dto;

public record QueuePositionResponse(
    String numero,
    String queueType,
    String status,
    Integer positionInQueue,
    Integer estimatedWaitMinutes,
    Integer totalInQueue
) {}
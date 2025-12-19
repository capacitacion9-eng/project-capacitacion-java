package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;

/**
 * Response DTO para estado de una cola específica
 */
public record QueueStatusResponse(
    QueueType queueType,
    String displayName,
    Integer ticketsInQueue,
    Integer averageWaitMinutes,
    Integer nextTicketNumber
) {
    /**
     * Factory method desde QueueType
     */
    public static QueueStatusResponse fromQueueType(QueueType queueType, 
                                                   Integer ticketsInQueue, 
                                                   Integer nextNumber) {
        return new QueueStatusResponse(
            queueType,
            queueType.getDisplayName(),
            ticketsInQueue,
            queueType.getAvgTimeMinutes() * ticketsInQueue,
            nextNumber
        );
    }
}
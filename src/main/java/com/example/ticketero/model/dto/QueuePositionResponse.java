package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;

/**
 * Response DTO para consulta de posición en cola
 */
public record QueuePositionResponse(
    String numero,
    QueueType queueType,
    TicketStatus status,
    Integer positionInQueue,
    Integer estimatedWaitMinutes,
    String message
) {
    /**
     * Factory method para ticket en espera
     */
    public static QueuePositionResponse waiting(String numero, QueueType queueType, 
                                              Integer position, Integer waitMinutes) {
        return new QueuePositionResponse(
            numero,
            queueType,
            TicketStatus.EN_ESPERA,
            position,
            waitMinutes,
            "Su ticket está en cola. Posición: " + position
        );
    }

    /**
     * Factory method para ticket próximo
     */
    public static QueuePositionResponse next(String numero, QueueType queueType) {
        return new QueuePositionResponse(
            numero,
            queueType,
            TicketStatus.PROXIMO,
            1,
            0,
            "¡Su turno está próximo! Prepárese para ser atendido."
        );
    }

    /**
     * Factory method para ticket siendo atendido
     */
    public static QueuePositionResponse attending(String numero, QueueType queueType, 
                                                Integer moduleNumber) {
        return new QueuePositionResponse(
            numero,
            queueType,
            TicketStatus.ATENDIENDO,
            0,
            0,
            "Está siendo atendido en el módulo " + moduleNumber
        );
    }
}
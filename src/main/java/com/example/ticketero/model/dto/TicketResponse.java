package com.example.ticketero.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResponse(
    Long id,
    UUID codigoReferencia,
    String numero,
    String nationalId,
    String telefono,
    String branchOffice,
    String queueType,
    String status,
    Integer positionInQueue,
    Integer estimatedWaitMinutes,
    Long assignedAdvisorId,
    Integer assignedModuleNumber,
    LocalDateTime createdAt
) {}
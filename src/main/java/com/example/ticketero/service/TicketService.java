package com.example.ticketero.service;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QueueManagementService queueManagementService;
    private final NotificationService notificationService;

    @Transactional
    public TicketResponse createTicket(TicketCreateRequest request) {
        log.info("Creating ticket for nationalId: {}, queueType: {}", 
                request.nationalId(), request.queueType());

        // Verificar si ya tiene ticket activo
        List<TicketStatus> activeStatuses = TicketStatus.getActiveStatuses();
        List<Ticket> activeTickets = ticketRepository.findByNationalIdAndStatusIn(
                request.nationalId(), activeStatuses);
        
        if (!activeTickets.isEmpty()) {
            throw new RuntimeException("Ya tiene un ticket activo");
        }

        // Generar número de ticket
        String numero = queueManagementService.generateTicketNumber(request.queueType());
        
        // Calcular posición y tiempo estimado
        int position = queueManagementService.calculateQueuePosition(request.queueType());
        int estimatedWait = queueManagementService.calculateEstimatedWait(request.queueType(), position);

        Ticket ticket = Ticket.builder()
                .numero(numero)
                .nationalId(request.nationalId())
                .telefono(request.telefono())
                .branchOffice(request.branchOffice())
                .queueType(request.queueType())
                .status(TicketStatus.EN_ESPERA)
                .positionInQueue(position)
                .estimatedWaitMinutes(estimatedWait)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created: {}", saved.getNumero());

        // Programar notificación
        notificationService.scheduleTicketCreatedNotification(saved);

        return toResponse(saved);
    }

    public Optional<QueuePositionResponse> getQueuePosition(String numero) {
        return ticketRepository.findByNumero(numero)
                .map(ticket -> {
                    int totalInQueue = queueManagementService.getTotalInQueue(ticket.getQueueType());
                    return new QueuePositionResponse(
                            ticket.getNumero(),
                            ticket.getQueueType().name(),
                            ticket.getStatus().name(),
                            ticket.getPositionInQueue(),
                            ticket.getEstimatedWaitMinutes(),
                            totalInQueue
                    );
                });
    }

    public Optional<TicketResponse> findByCodigoReferencia(UUID codigoReferencia) {
        return ticketRepository.findByCodigoReferencia(codigoReferencia)
                .map(this::toResponse);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getCodigoReferencia(),
                ticket.getNumero(),
                ticket.getNationalId(),
                ticket.getTelefono(),
                ticket.getBranchOffice(),
                ticket.getQueueType().name(),
                ticket.getStatus().name(),
                ticket.getPositionInQueue(),
                ticket.getEstimatedWaitMinutes(),
                ticket.getAssignedAdvisorId(),
                ticket.getAssignedModuleNumber(),
                ticket.getCreatedAt()
        );
    }
}
package com.example.ticketero.service;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QueueManagementService {

    private final TicketRepository ticketRepository;
    private final AdvisorService advisorService;
    private final NotificationService notificationService;

    @Transactional
    public void processQueues() {
        log.debug("Starting queue processing");
        
        for (QueueType queueType : QueueType.values()) {
            processQueueByType(queueType);
        }
        
        log.debug("Queue processing completed");
    }

    @Transactional
    public void processQueueByType(QueueType queueType) {
        List<Ticket> activeTickets = ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(
            queueType, 
            TicketStatus.getActiveStatuses()
        );

        if (activeTickets.isEmpty()) {
            return;
        }

        log.debug("Processing {} tickets in {} queue", activeTickets.size(), queueType);

        // Actualizar posiciones
        updateQueuePositions(activeTickets);

        // Procesar tickets según estado
        for (Ticket ticket : activeTickets) {
            processTicketByStatus(ticket);
        }
    }

    @Transactional
    public void completeTicket(Long ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            // Liberar asesor si está asignado
            if (ticket.getAssignedAdvisor() != null) {
                advisorService.completeTicketAssignment(ticket);
            }

            // Cambiar estado a completado
            ticket.setStatus(TicketStatus.COMPLETADO);
            ticket.setPositionInQueue(0);
            ticket.setEstimatedWaitMinutes(0);

            ticketRepository.save(ticket);

            // Cancelar notificaciones pendientes
            notificationService.cancelPendingNotifications(ticket);

            log.info("Ticket {} completed", ticket.getNumero());
        });
    }

    private void updateQueuePositions(List<Ticket> tickets) {
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            int newPosition = i + 1;
            int newEstimatedWait = ticket.getQueueType().getAvgTimeMinutes() * newPosition;

            if (ticket.getPositionInQueue() != newPosition) {
                ticket.setPositionInQueue(newPosition);
                ticket.setEstimatedWaitMinutes(newEstimatedWait);
                ticketRepository.save(ticket);
            }
        }
    }

    private void processTicketByStatus(Ticket ticket) {
        switch (ticket.getStatus()) {
            case EN_ESPERA -> processWaitingTicket(ticket);
            case PROXIMO -> processNextTicket(ticket);
            case ATENDIENDO -> {
                // Ticket ya está siendo atendido, no hacer nada
            }
        }
    }

    private void processWaitingTicket(Ticket ticket) {
        // Si está en posición 1-3, cambiar a PROXIMO
        if (ticket.getPositionInQueue() <= 3) {
            ticket.setStatus(TicketStatus.PROXIMO);
            ticketRepository.save(ticket);
            
            notificationService.scheduleProximoTurnoNotification(ticket);
            log.info("Ticket {} moved to PROXIMO status", ticket.getNumero());
        }
    }

    private void processNextTicket(Ticket ticket) {
        // Intentar asignar a un asesor disponible
        if (advisorService.assignTicketToAdvisor(ticket)) {
            ticketRepository.save(ticket);
            
            notificationService.scheduleEsTuTurnoNotification(ticket);
            log.info("Ticket {} assigned and moved to ATENDIENDO", ticket.getNumero());
        }
    }
}
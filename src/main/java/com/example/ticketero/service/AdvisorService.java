package com.example.ticketero.service;

import com.example.ticketero.model.dto.DashboardResponse;
import com.example.ticketero.model.dto.QueueStatusResponse;
import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdvisorService {

    private final AdvisorRepository advisorRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    @Transactional
    public Optional<Ticket> assignNextTicket() {
        // Buscar asesor disponible
        Optional<Advisor> availableAdvisor = advisorRepository.findAvailableAdvisorWithLeastLoad();
        
        if (availableAdvisor.isEmpty()) {
            log.debug("No available advisors found");
            return Optional.empty();
        }

        // Buscar próximo ticket en espera (por prioridad de cola)
        Optional<Ticket> nextTicket = findNextTicketToAssign();
        
        if (nextTicket.isEmpty()) {
            log.debug("No tickets waiting for assignment");
            return Optional.empty();
        }

        Advisor advisor = availableAdvisor.get();
        Ticket ticket = nextTicket.get();

        // Asignar ticket
        ticket.setAssignedAdvisorId(advisor.getId());
        ticket.setAssignedModuleNumber(advisor.getModuleNumber());
        ticket.setStatus(TicketStatus.ATENDIENDO);

        // Actualizar contador del asesor
        advisor.setAssignedTicketsCount(advisor.getAssignedTicketsCount() + 1);
        if (advisor.getAssignedTicketsCount() >= 1) {
            advisor.setStatus(AdvisorStatus.BUSY);
        }

        log.info("Assigned ticket {} to advisor {} (module {})", 
                ticket.getNumero(), advisor.getName(), advisor.getModuleNumber());

        // Programar notificación "es tu turno"
        notificationService.scheduleEsTuTurnoNotification(ticket);

        return Optional.of(ticket);
    }

    private Optional<Ticket> findNextTicketToAssign() {
        // Buscar por prioridad de cola (CAJA tiene prioridad 1, más alta)
        for (QueueType queueType : QueueType.values()) {
            List<Ticket> tickets = ticketRepository.findByQueueTypeAndStatusOrderByCreatedAtAsc(
                    queueType, TicketStatus.PROXIMO);
            
            if (!tickets.isEmpty()) {
                return Optional.of(tickets.get(0));
            }
        }

        // Si no hay tickets PROXIMO, buscar EN_ESPERA
        for (QueueType queueType : QueueType.values()) {
            List<Ticket> tickets = ticketRepository.findByQueueTypeAndStatusOrderByCreatedAtAsc(
                    queueType, TicketStatus.EN_ESPERA);
            
            if (!tickets.isEmpty()) {
                return Optional.of(tickets.get(0));
            }
        }

        return Optional.empty();
    }

    @Transactional
    public void completeTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        if (ticket.getAssignedAdvisorId() != null) {
            Advisor advisor = advisorRepository.findById(ticket.getAssignedAdvisorId())
                    .orElseThrow(() -> new RuntimeException("Advisor not found"));

            // Liberar asesor
            advisor.setAssignedTicketsCount(Math.max(0, advisor.getAssignedTicketsCount() - 1));
            if (advisor.getAssignedTicketsCount() == 0) {
                advisor.setStatus(AdvisorStatus.AVAILABLE);
            }
        }

        ticket.setStatus(TicketStatus.COMPLETADO);
        log.info("Completed ticket: {}", ticket.getNumero());
    }

    public DashboardResponse getDashboardMetrics() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        int totalToday = (int) ticketRepository.countByCreatedAtAfter(startOfDay);
        int inQueue = (int) ticketRepository.countByStatusIn(List.of(TicketStatus.EN_ESPERA, TicketStatus.PROXIMO));
        int beingAttended = (int) ticketRepository.countByStatusIn(List.of(TicketStatus.ATENDIENDO));
        int completed = (int) ticketRepository.countByStatusIn(List.of(TicketStatus.COMPLETADO));

        List<QueueStatusResponse> queueStatus = Arrays.stream(QueueType.values())
                .map(this::buildQueueStatus)
                .toList();

        List<DashboardResponse.AdvisorStatusResponse> advisorStatus = advisorRepository.findAll()
                .stream()
                .map(advisor -> new DashboardResponse.AdvisorStatusResponse(
                        advisor.getId(),
                        advisor.getName(),
                        advisor.getStatus().name(),
                        advisor.getModuleNumber(),
                        advisor.getAssignedTicketsCount()
                ))
                .toList();

        return new DashboardResponse(
                totalToday,
                inQueue,
                beingAttended,
                completed,
                queueStatus,
                advisorStatus
        );
    }

    private QueueStatusResponse buildQueueStatus(QueueType queueType) {
        List<TicketStatus> activeStatuses = TicketStatus.getActiveStatuses();
        int totalInQueue = (int) ticketRepository.countByQueueTypeAndStatuses(queueType, activeStatuses);
        
        return new QueueStatusResponse(
                queueType.name(),
                queueType.getDisplayName(),
                totalInQueue,
                queueType.getAvgTimeMinutes(),
                getNextTicketNumber(queueType)
        );
    }

    private Integer getNextTicketNumber(QueueType queueType) {
        List<Ticket> nextTickets = ticketRepository.findByQueueTypeAndStatusOrderByCreatedAtAsc(
                queueType, TicketStatus.PROXIMO);
        
        if (!nextTickets.isEmpty()) {
            String numero = nextTickets.get(0).getNumero();
            return Integer.parseInt(numero.substring(1)); // Remove prefix
        }
        
        return null;
    }
}
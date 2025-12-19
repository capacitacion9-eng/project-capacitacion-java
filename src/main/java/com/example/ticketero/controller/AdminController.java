package com.example.ticketero.controller;

import com.example.ticketero.model.dto.DashboardResponse;
import com.example.ticketero.model.dto.QueueStatusResponse;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import com.example.ticketero.service.AdvisorService;
import com.example.ticketero.service.QueueManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TicketRepository ticketRepository;
    private final AdvisorService advisorService;
    private final QueueManagementService queueManagementService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("Getting admin dashboard metrics");

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        // Métricas básicas
        int totalTicketsToday = (int) ticketRepository.countByCreatedAtAfter(startOfDay);
        int ticketsInQueue = (int) ticketRepository.countByStatusIn(TicketStatus.getActiveStatuses());
        int ticketsCompleted = (int) ticketRepository.countByStatus(TicketStatus.COMPLETADO);
        
        // Métricas de asesores
        int availableAdvisors = (int) advisorService.countByStatus(AdvisorStatus.AVAILABLE);
        int busyAdvisors = (int) advisorService.countByStatus(AdvisorStatus.BUSY);
        
        // Tiempo promedio (simplificado)
        double averageWaitTime = calculateAverageWaitTime();
        
        // Estado por cola
        List<QueueStatusResponse> queueStatus = Arrays.stream(QueueType.values())
            .map(this::buildQueueStatus)
            .toList();

        DashboardResponse dashboard = new DashboardResponse(
            totalTicketsToday,
            ticketsInQueue,
            ticketsCompleted,
            availableAdvisors,
            busyAdvisors,
            averageWaitTime,
            queueStatus,
            LocalDateTime.now()
        );

        return ResponseEntity.ok(dashboard);
    }

    @PostMapping("/tickets/{id}/complete")
    public ResponseEntity<Void> completeTicket(@PathVariable Long id) {
        log.info("Completing ticket with ID: {}", id);
        
        queueManagementService.completeTicket(id);
        
        return ResponseEntity.noContent().build();
    }

    private double calculateAverageWaitTime() {
        // Cálculo simplificado basado en tickets activos
        List<TicketStatus> activeStatuses = TicketStatus.getActiveStatuses();
        long activeTickets = ticketRepository.countByStatusIn(activeStatuses);
        
        if (activeTickets == 0) {
            return 0.0;
        }
        
        // Promedio simple basado en tipos de cola
        double totalEstimatedTime = Arrays.stream(QueueType.values())
            .mapToDouble(queueType -> {
                long queueTickets = ticketRepository.countTicketsAheadInQueue(
                    queueType, activeStatuses, LocalDateTime.now()
                );
                return queueTickets * queueType.getAvgTimeMinutes();
            })
            .sum();
            
        return totalEstimatedTime / activeTickets;
    }

    private QueueStatusResponse buildQueueStatus(QueueType queueType) {
        List<TicketStatus> activeStatuses = TicketStatus.getActiveStatuses();
        int ticketsInQueue = (int) ticketRepository.countTicketsAheadInQueue(
            queueType, activeStatuses, LocalDateTime.now()
        );
        
        // Próximo número (simplificado)
        int nextNumber = ticketsInQueue + 1;
        
        return QueueStatusResponse.fromQueueType(queueType, ticketsInQueue, nextNumber);
    }
}
package com.example.ticketero.scheduler;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.service.AdvisorService;
import com.example.ticketero.service.NotificationService;
import com.example.ticketero.service.QueueManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueProcessorScheduler {

    private final AdvisorService advisorService;
    private final QueueManagementService queueManagementService;
    private final NotificationService notificationService;

    /**
     * Procesa colas y asigna tickets cada 5 segundos
     */
    @Scheduled(fixedRate = 5000) // 5 segundos
    public void processQueues() {
        log.debug("Processing queues and assigning tickets...");
        
        try {
            // Actualizar posiciones en todas las colas
            for (QueueType queueType : QueueType.values()) {
                queueManagementService.updateQueuePositions(queueType);
            }
            
            // Intentar asignar próximo ticket
            Optional<Ticket> assignedTicket = advisorService.assignNextTicket();
            
            if (assignedTicket.isPresent()) {
                Ticket ticket = assignedTicket.get();
                log.info("Assigned ticket {} to module {}", 
                        ticket.getNumero(), ticket.getAssignedModuleNumber());
            }
            
        } catch (Exception e) {
            log.error("Error processing queues: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía notificaciones de "próximo turno" cada 30 segundos
     */
    @Scheduled(fixedRate = 30000) // 30 segundos
    public void processProximoTurnoNotifications() {
        log.debug("Processing proximo turno notifications...");
        
        try {
            // Esta lógica se podría expandir para encontrar tickets que están próximos
            // y programar notificaciones automáticamente
            
        } catch (Exception e) {
            log.error("Error processing proximo turno notifications: {}", e.getMessage(), e);
        }
    }
}
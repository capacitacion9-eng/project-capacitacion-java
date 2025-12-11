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
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QueueManagementService {

    private final TicketRepository ticketRepository;
    private final AtomicInteger cajaCounter = new AtomicInteger(1);
    private final AtomicInteger personalBankerCounter = new AtomicInteger(1);
    private final AtomicInteger empresasCounter = new AtomicInteger(1);
    private final AtomicInteger gerenciaCounter = new AtomicInteger(1);

    public String generateTicketNumber(QueueType queueType) {
        char prefix = queueType.getPrefix();
        int number = switch (queueType) {
            case CAJA -> cajaCounter.getAndIncrement();
            case PERSONAL_BANKER -> personalBankerCounter.getAndIncrement();
            case EMPRESAS -> empresasCounter.getAndIncrement();
            case GERENCIA -> gerenciaCounter.getAndIncrement();
        };
        return String.format("%c%02d", prefix, number);
    }

    public int calculateQueuePosition(QueueType queueType) {
        List<TicketStatus> activeStatuses = List.of(TicketStatus.EN_ESPERA, TicketStatus.PROXIMO);
        long count = ticketRepository.countByQueueTypeAndStatuses(queueType, activeStatuses);
        return (int) count + 1;
    }

    public int calculateEstimatedWait(QueueType queueType, int position) {
        int avgTimePerTicket = queueType.getAvgTimeMinutes();
        return position * avgTimePerTicket;
    }

    public int getTotalInQueue(QueueType queueType) {
        List<TicketStatus> activeStatuses = TicketStatus.getActiveStatuses();
        return (int) ticketRepository.countByQueueTypeAndStatuses(queueType, activeStatuses);
    }

    @Transactional
    public void updateQueuePositions(QueueType queueType) {
        List<Ticket> tickets = ticketRepository.findByQueueTypeAndStatusesOrderByCreated(
                queueType, List.of(TicketStatus.EN_ESPERA, TicketStatus.PROXIMO));
        
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            int newPosition = i + 1;
            ticket.setPositionInQueue(newPosition);
            ticket.setEstimatedWaitMinutes(calculateEstimatedWait(queueType, newPosition));
            
            // Actualizar estado si está próximo
            if (newPosition <= 3 && ticket.getStatus() == TicketStatus.EN_ESPERA) {
                ticket.setStatus(TicketStatus.PROXIMO);
                log.info("Ticket {} is now PROXIMO (position {})", ticket.getNumero(), newPosition);
            }
        }
    }
}
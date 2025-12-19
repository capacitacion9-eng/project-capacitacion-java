package com.example.ticketero.service;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    
    // Contadores por tipo de cola
    private final AtomicInteger cajaCounter = new AtomicInteger(1);
    private final AtomicInteger personalBankerCounter = new AtomicInteger(1);
    private final AtomicInteger empresasCounter = new AtomicInteger(1);
    private final AtomicInteger gerenciaCounter = new AtomicInteger(1);

    @Transactional
    public TicketResponse create(TicketCreateRequest request) {
        log.info("Creating ticket for nationalId: {}, queueType: {}", 
                request.nationalId(), request.queueType());

        // Generar número de ticket
        String numero = generateTicketNumber(request.queueType());
        
        // Calcular posición en cola
        long ticketsAhead = ticketRepository.countByQueueTypeAndStatusInOrderByCreatedAtAsc(
            request.queueType(), 
            TicketStatus.getActiveStatuses()
        ).size();
        
        int position = (int) ticketsAhead + 1;
        int estimatedWait = request.queueType().getAvgTimeMinutes() * position;

        // Crear ticket
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
        
        // Programar notificación
        notificationService.scheduleTicketCreatedNotification(saved);
        
        log.info("Ticket created: {} at position {}", numero, position);
        return new TicketResponse(saved);
    }

    public Optional<QueuePositionResponse> getPosition(String numero) {
        return ticketRepository.findByNumero(numero)
            .map(this::buildPositionResponse);
    }

    public Optional<TicketResponse> findByReference(UUID codigoReferencia) {
        return ticketRepository.findByCodigoReferencia(codigoReferencia)
            .map(TicketResponse::new);
    }

    private String generateTicketNumber(QueueType queueType) {
        char prefix = queueType.getPrefix();
        int number = switch (queueType) {
            case CAJA -> cajaCounter.getAndIncrement();
            case PERSONAL_BANKER -> personalBankerCounter.getAndIncrement();
            case EMPRESAS -> empresasCounter.getAndIncrement();
            case GERENCIA -> gerenciaCounter.getAndIncrement();
        };
        return String.format("%c%02d", prefix, number);
    }

    private QueuePositionResponse buildPositionResponse(Ticket ticket) {
        return switch (ticket.getStatus()) {
            case EN_ESPERA -> QueuePositionResponse.waiting(
                ticket.getNumero(),
                ticket.getQueueType(),
                ticket.getPositionInQueue(),
                ticket.getEstimatedWaitMinutes()
            );
            case PROXIMO -> QueuePositionResponse.next(
                ticket.getNumero(),
                ticket.getQueueType()
            );
            case ATENDIENDO -> QueuePositionResponse.attending(
                ticket.getNumero(),
                ticket.getQueueType(),
                ticket.getAssignedModuleNumber()
            );
            default -> QueuePositionResponse.waiting(
                ticket.getNumero(),
                ticket.getQueueType(),
                0,
                0
            );
        };
    }
}
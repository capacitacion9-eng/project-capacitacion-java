package com.example.ticketero.service;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.MessageTemplate;
import com.example.ticketero.repository.MensajeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {

    private final MensajeRepository mensajeRepository;

    @Transactional
    public void scheduleTicketCreatedNotification(Ticket ticket) {
        Mensaje mensaje = Mensaje.builder()
            .ticket(ticket)
            .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
            .fechaProgramada(LocalDateTime.now().plusMinutes(1))
            .build();

        mensajeRepository.save(mensaje);
        
        log.info("Scheduled ticket created notification for ticket: {}", ticket.getNumero());
    }

    @Transactional
    public void scheduleProximoTurnoNotification(Ticket ticket) {
        // Solo programar si el ticket tiene teléfono
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            log.debug("Skipping próximo turno notification - no phone number for ticket: {}", 
                     ticket.getNumero());
            return;
        }

        Mensaje mensaje = Mensaje.builder()
            .ticket(ticket)
            .plantilla(MessageTemplate.TOTEM_PROXIMO_TURNO)
            .fechaProgramada(LocalDateTime.now().plusMinutes(2))
            .build();

        mensajeRepository.save(mensaje);
        
        log.info("Scheduled próximo turno notification for ticket: {}", ticket.getNumero());
    }

    @Transactional
    public void scheduleEsTuTurnoNotification(Ticket ticket) {
        // Solo programar si el ticket tiene teléfono
        if (ticket.getTelefono() == null || ticket.getTelefono().isBlank()) {
            log.debug("Skipping es tu turno notification - no phone number for ticket: {}", 
                     ticket.getNumero());
            return;
        }

        Mensaje mensaje = Mensaje.builder()
            .ticket(ticket)
            .plantilla(MessageTemplate.TOTEM_ES_TU_TURNO)
            .fechaProgramada(LocalDateTime.now())
            .build();

        mensajeRepository.save(mensaje);
        
        log.info("Scheduled es tu turno notification for ticket: {}", ticket.getNumero());
    }

    @Transactional
    public void cancelPendingNotifications(Ticket ticket) {
        mensajeRepository.findByTicketId(ticket.getId())
            .stream()
            .filter(mensaje -> mensaje.getEstadoEnvio() == Mensaje.EstadoEnvio.PENDIENTE)
            .forEach(mensaje -> {
                mensaje.setEstadoEnvio(Mensaje.EstadoEnvio.FALLIDO);
                mensajeRepository.save(mensaje);
            });
        
        log.info("Cancelled pending notifications for ticket: {}", ticket.getNumero());
    }
}
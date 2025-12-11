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
        if (ticket.getTelefono() == null || ticket.getTelefono().isEmpty()) {
            log.info("No phone number for ticket {}, skipping notification", ticket.getNumero());
            return;
        }

        Mensaje mensaje = Mensaje.builder()
                .ticketId(ticket.getId())
                .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
                .estadoEnvio("PENDIENTE")
                .fechaProgramada(LocalDateTime.now().plusMinutes(1))
                .build();

        mensajeRepository.save(mensaje);
        log.info("Scheduled ticket created notification for ticket: {}", ticket.getNumero());
    }

    @Transactional
    public void scheduleProximoTurnoNotification(Ticket ticket) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isEmpty()) {
            return;
        }

        // Verificar que no existe ya este tipo de mensaje
        boolean exists = mensajeRepository.existsByTicketIdAndPlantilla(
                ticket.getId(), MessageTemplate.TOTEM_PROXIMO_TURNO);
        
        if (exists) {
            return;
        }

        Mensaje mensaje = Mensaje.builder()
                .ticketId(ticket.getId())
                .plantilla(MessageTemplate.TOTEM_PROXIMO_TURNO)
                .estadoEnvio("PENDIENTE")
                .fechaProgramada(LocalDateTime.now().plusMinutes(2))
                .build();

        mensajeRepository.save(mensaje);
        log.info("Scheduled proximo turno notification for ticket: {}", ticket.getNumero());
    }

    @Transactional
    public void scheduleEsTuTurnoNotification(Ticket ticket) {
        if (ticket.getTelefono() == null || ticket.getTelefono().isEmpty()) {
            return;
        }

        Mensaje mensaje = Mensaje.builder()
                .ticketId(ticket.getId())
                .plantilla(MessageTemplate.TOTEM_ES_TU_TURNO)
                .estadoEnvio("PENDIENTE")
                .fechaProgramada(LocalDateTime.now())
                .build();

        mensajeRepository.save(mensaje);
        log.info("Scheduled es tu turno notification for ticket: {}", ticket.getNumero());
    }
}
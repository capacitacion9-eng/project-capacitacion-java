package com.example.ticketero.scheduler;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.repository.MensajeRepository;
import com.example.ticketero.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageScheduler {

    private final MensajeRepository mensajeRepository;
    private final TelegramService telegramService;

    @Scheduled(fixedRate = 60000) // Cada 60 segundos
    @Transactional
    public void processPendingMessages() {
        LocalDateTime now = LocalDateTime.now();
        
        // Obtener mensajes pendientes listos para enviar
        List<Mensaje> pendingMessages = mensajeRepository.findPendingMessagesReadyToSend(now);
        
        if (pendingMessages.isEmpty()) {
            log.debug("No pending messages to process");
            return;
        }

        log.info("Processing {} pending messages", pendingMessages.size());

        for (Mensaje mensaje : pendingMessages) {
            processSingleMessage(mensaje);
        }

        // Procesar mensajes fallidos para reintento
        processFailedMessages(now);
    }

    private void processSingleMessage(Mensaje mensaje) {
        try {
            log.debug("Processing message {} for ticket {}", 
                     mensaje.getId(), mensaje.getTicket().getNumero());

            boolean sent = telegramService.sendMessage(mensaje);
            
            if (sent) {
                log.info("Message sent successfully for ticket: {}", 
                        mensaje.getTicket().getNumero());
            } else {
                log.warn("Failed to send message for ticket: {}", 
                        mensaje.getTicket().getNumero());
            }

            // El TelegramService ya actualiza el estado del mensaje
            mensajeRepository.save(mensaje);

        } catch (Exception e) {
            log.error("Error processing message {} for ticket {}: {}", 
                     mensaje.getId(), mensaje.getTicket().getNumero(), e.getMessage());
            
            mensaje.setEstadoEnvio(Mensaje.EstadoEnvio.FALLIDO);
            mensaje.setIntentos(mensaje.getIntentos() + 1);
            mensajeRepository.save(mensaje);
        }
    }

    private void processFailedMessages(LocalDateTime now) {
        List<Mensaje> failedMessages = mensajeRepository.findFailedMessagesForRetry(now);
        
        if (failedMessages.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed messages", failedMessages.size());

        for (Mensaje mensaje : failedMessages) {
            // Cambiar estado a pendiente para reintento
            mensaje.setEstadoEnvio(Mensaje.EstadoEnvio.PENDIENTE);
            mensajeRepository.save(mensaje);
            
            // Procesar inmediatamente
            processSingleMessage(mensaje);
        }
    }
}
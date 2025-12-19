package com.example.ticketero.service;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.MessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final RestTemplate restTemplate;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.api-url:https://api.telegram.org/bot}")
    private String apiUrl;

    public boolean sendMessage(Mensaje mensaje) {
        if (botToken.isBlank()) {
            log.warn("Telegram bot token not configured, skipping message send");
            return false;
        }

        try {
            String messageText = buildMessageText(mensaje);
            String chatId = extractChatIdFromPhone(mensaje.getTicket().getTelefono());
            
            if (chatId == null) {
                log.warn("Could not extract chat ID from phone: {}", mensaje.getTicket().getTelefono());
                return false;
            }

            // Simular envío (en implementación real haría HTTP call)
            log.info("Sending Telegram message to {}: {}", chatId, messageText);
            
            // Simular respuesta exitosa
            mensaje.setFechaEnvio(LocalDateTime.now());
            mensaje.setTelegramMessageId("msg_" + System.currentTimeMillis());
            mensaje.setEstadoEnvio(Mensaje.EstadoEnvio.ENVIADO);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error sending Telegram message for ticket {}: {}", 
                     mensaje.getTicket().getNumero(), e.getMessage());
            
            mensaje.setEstadoEnvio(Mensaje.EstadoEnvio.FALLIDO);
            mensaje.setIntentos(mensaje.getIntentos() + 1);
            
            return false;
        }
    }

    private String buildMessageText(Mensaje mensaje) {
        Ticket ticket = mensaje.getTicket();
        
        return switch (mensaje.getPlantilla()) {
            case TOTEM_TICKET_CREADO -> String.format("""
                🎫 *Ticket Creado*
                
                Número: *%s*
                Cola: %s
                Posición: %d
                Tiempo estimado: %d minutos
                Sucursal: %s
                
                Fecha: %s
                """,
                ticket.getNumero(),
                ticket.getQueueType().getDisplayName(),
                ticket.getPositionInQueue(),
                ticket.getEstimatedWaitMinutes(),
                ticket.getBranchOffice(),
                ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            );
            
            case TOTEM_PROXIMO_TURNO -> String.format("""
                ⏰ *¡Su turno está próximo!*
                
                Ticket: *%s*
                Cola: %s
                
                Por favor prepárese para ser atendido.
                Faltan aproximadamente %d minutos.
                """,
                ticket.getNumero(),
                ticket.getQueueType().getDisplayName(),
                ticket.getEstimatedWaitMinutes()
            );
            
            case TOTEM_ES_TU_TURNO -> String.format("""
                🔔 *¡Es su turno!*
                
                Ticket: *%s*
                Módulo: *%d*
                
                Diríjase al módulo %d para ser atendido.
                """,
                ticket.getNumero(),
                ticket.getAssignedModuleNumber(),
                ticket.getAssignedModuleNumber()
            );
        };
    }

    private String extractChatIdFromPhone(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        
        // En implementación real, aquí se haría lookup en base de datos
        // para obtener el chat_id de Telegram asociado al teléfono
        // Por ahora simulamos con el teléfono mismo
        return telefono.replaceAll("[^0-9]", "");
    }
}
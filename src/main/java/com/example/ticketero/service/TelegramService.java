package com.example.ticketero.service;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.MessageTemplate;
import com.example.ticketero.repository.MensajeRepository;
import com.example.ticketero.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TelegramService {

    private final MensajeRepository mensajeRepository;
    private final TicketRepository ticketRepository;
    private final RestTemplate restTemplate;

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.api-url}")
    private String apiUrl;

    @Transactional
    public void processPendingMessages() {
        List<Mensaje> pendingMessages = mensajeRepository.findPendingMessages(LocalDateTime.now());
        
        log.info("Processing {} pending messages", pendingMessages.size());
        
        for (Mensaje mensaje : pendingMessages) {
            try {
                sendMessage(mensaje);
            } catch (Exception e) {
                log.error("Error sending message {}: {}", mensaje.getId(), e.getMessage());
                handleMessageError(mensaje);
            }
        }
    }

    @Transactional
    public void sendMessage(Mensaje mensaje) {
        Ticket ticket = ticketRepository.findById(mensaje.getTicketId())
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + mensaje.getTicketId()));

        String messageText = buildMessageText(mensaje.getPlantilla(), ticket);
        String chatId = extractChatIdFromPhone(ticket.getTelefono());

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", messageText);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        
        String url = apiUrl + botToken + "/sendMessage";
        
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            
            mensaje.setEstadoEnvio("ENVIADO");
            mensaje.setFechaEnvio(LocalDateTime.now());
            mensaje.setTelegramMessageId(extractMessageId(response));
            
            log.info("Message sent successfully for ticket: {}", ticket.getNumero());
            
        } catch (Exception e) {
            log.error("Failed to send Telegram message: {}", e.getMessage());
            throw e;
        }
    }

    private String buildMessageText(MessageTemplate template, Ticket ticket) {
        return switch (template) {
            case TOTEM_TICKET_CREADO -> String.format("""
                🎫 *Ticket Creado*
                
                Número: *%s*
                Cola: %s
                Posición: %d
                Tiempo estimado: %d minutos
                
                Te notificaremos cuando sea tu turno.
                """, 
                ticket.getNumero(),
                ticket.getQueueType().getDisplayName(),
                ticket.getPositionInQueue(),
                ticket.getEstimatedWaitMinutes());
                
            case TOTEM_PROXIMO_TURNO -> String.format("""
                ⏰ *Próximo Turno*
                
                Ticket: *%s*
                
                Estás próximo a ser atendido.
                Por favor acércate al área de espera.
                """, ticket.getNumero());
                
            case TOTEM_ES_TU_TURNO -> String.format("""
                🔔 *Es Tu Turno*
                
                Ticket: *%s*
                Módulo: *%d*
                
                Dirígete al módulo de atención.
                """, 
                ticket.getNumero(),
                ticket.getAssignedModuleNumber());
        };
    }

    private String extractChatIdFromPhone(String telefono) {
        // Simulación: en producción esto vendría de una base de datos
        // que mapee teléfonos a chat_ids de Telegram
        return telefono.replaceAll("[^0-9]", "");
    }

    private String extractMessageId(Map<String, Object> response) {
        if (response != null && response.containsKey("result")) {
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            return String.valueOf(result.get("message_id"));
        }
        return null;
    }

    @Transactional
    public void handleMessageError(Mensaje mensaje) {
        mensaje.setIntentos(mensaje.getIntentos() + 1);
        
        if (mensaje.getIntentos() >= 3) {
            mensaje.setEstadoEnvio("FALLIDO");
            log.error("Message {} failed after 3 attempts", mensaje.getId());
        } else {
            // Reprogramar para reintento en 5 minutos
            mensaje.setFechaProgramada(LocalDateTime.now().plusMinutes(5));
            log.warn("Message {} failed, rescheduled for retry (attempt {})", 
                    mensaje.getId(), mensaje.getIntentos());
        }
    }
}
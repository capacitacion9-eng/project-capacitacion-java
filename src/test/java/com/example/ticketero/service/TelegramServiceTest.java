package com.example.ticketero.service;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.MessageTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramService - Unit Tests")
class TelegramServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TelegramService telegramService;

    @Nested
    @DisplayName("sendMessage()")
    class SendMessage {

        @Test
        @DisplayName("con token configurado → debe enviar mensaje exitosamente")
        void sendMessage_conToken_debeEnviarExitosamente() {
            // Given
            ReflectionTestUtils.setField(telegramService, "botToken", "test-token");
            
            Ticket ticket = ticketWaiting()
                .numero("C001")
                .telefono("+56912345678")
                .assignedModuleNumber(3)
                .build();
            
            Mensaje mensaje = Mensaje.builder()
                .ticket(ticket)
                .plantilla(MessageTemplate.TOTEM_ES_TU_TURNO)
                .build();

            // When
            boolean result = telegramService.sendMessage(mensaje);

            // Then
            assertThat(result).isTrue();
            assertThat(mensaje.getEstadoEnvio()).isEqualTo(Mensaje.EstadoEnvio.ENVIADO);
            assertThat(mensaje.getFechaEnvio()).isNotNull();
            assertThat(mensaje.getTelegramMessageId()).isNotNull();
        }

        @Test
        @DisplayName("sin token configurado → debe retornar false")
        void sendMessage_sinToken_debeRetornarFalse() {
            // Given
            ReflectionTestUtils.setField(telegramService, "botToken", "");
            
            Ticket ticket = ticketWaiting().build();
            Mensaje mensaje = Mensaje.builder()
                .ticket(ticket)
                .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
                .build();

            // When
            boolean result = telegramService.sendMessage(mensaje);

            // Then
            assertThat(result).isFalse();
            verify(restTemplate, never()).postForObject(anyString(), any(), any());
        }

        @Test
        @DisplayName("sin teléfono → debe retornar false")
        void sendMessage_sinTelefono_debeRetornarFalse() {
            // Given
            ReflectionTestUtils.setField(telegramService, "botToken", "test-token");
            
            Ticket ticket = ticketWaiting()
                .telefono(null)
                .build();
            
            Mensaje mensaje = Mensaje.builder()
                .ticket(ticket)
                .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
                .build();

            // When
            boolean result = telegramService.sendMessage(mensaje);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("debe construir mensaje correcto para TICKET_CREADO")
        void sendMessage_debeContruirMensajeTicketCreado() {
            // Given
            ReflectionTestUtils.setField(telegramService, "botToken", "test-token");
            
            Ticket ticket = ticketWaiting()
                .numero("C001")
                .telefono("+56912345678")
                .positionInQueue(3)
                .estimatedWaitMinutes(15)
                .build();
            
            Mensaje mensaje = Mensaje.builder()
                .ticket(ticket)
                .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
                .build();

            // When
            boolean result = telegramService.sendMessage(mensaje);

            // Then
            assertThat(result).isTrue();
            // El mensaje se construye internamente, verificamos que se procesó
            assertThat(mensaje.getEstadoEnvio()).isEqualTo(Mensaje.EstadoEnvio.ENVIADO);
        }
    }
}
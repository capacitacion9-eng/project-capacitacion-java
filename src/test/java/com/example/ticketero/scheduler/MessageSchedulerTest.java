package com.example.ticketero.scheduler;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.repository.MensajeRepository;
import com.example.ticketero.service.TelegramService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageScheduler - Unit Tests")
class MessageSchedulerTest {

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private TelegramService telegramService;

    @InjectMocks
    private MessageScheduler messageScheduler;

    @Nested
    @DisplayName("processPendingMessages()")
    class ProcessPendingMessages {

        @Test
        @DisplayName("sin mensajes pendientes → no debe hacer nada")
        void process_sinMensajes_noDebeHacerNada() {
            // Given
            when(mensajeRepository.findPendingMessagesReadyToSend(any()))
                .thenReturn(Collections.emptyList());

            // When
            messageScheduler.processPendingMessages();

            // Then
            verify(telegramService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("con mensaje pendiente exitoso → debe enviar y guardar")
        void process_mensajeExitoso_debeEnviarYGuardar() {
            // Given
            Mensaje mensaje = mensajePendiente()
                .ticket(ticketWaiting().build())
                .build();
            
            when(mensajeRepository.findPendingMessagesReadyToSend(any()))
                .thenReturn(List.of(mensaje));
            when(mensajeRepository.findFailedMessagesForRetry(any()))
                .thenReturn(Collections.emptyList());
            when(telegramService.sendMessage(mensaje)).thenReturn(true);

            // When
            messageScheduler.processPendingMessages();

            // Then
            verify(telegramService).sendMessage(mensaje);
            verify(mensajeRepository).save(mensaje);
        }

        @Test
        @DisplayName("con excepción → debe marcar como fallido e incrementar intentos")
        void process_conExcepcion_debeMarcarFallidoEIncrementarIntentos() {
            // Given
            Mensaje mensaje = mensajePendiente()
                .ticket(ticketWaiting().build())
                .intentos(1)
                .build();
            
            when(mensajeRepository.findPendingMessagesReadyToSend(any()))
                .thenReturn(List.of(mensaje));
            when(mensajeRepository.findFailedMessagesForRetry(any()))
                .thenReturn(Collections.emptyList());
            when(telegramService.sendMessage(mensaje))
                .thenThrow(new RuntimeException("Telegram error"));

            // When
            messageScheduler.processPendingMessages();

            // Then
            assertThat(mensaje.getEstadoEnvio()).isEqualTo(Mensaje.EstadoEnvio.FALLIDO);
            assertThat(mensaje.getIntentos()).isEqualTo(2);
            verify(mensajeRepository).save(mensaje);
        }
    }
}
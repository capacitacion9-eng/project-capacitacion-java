package com.example.ticketero.service;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.MessageTemplate;
import com.example.ticketero.repository.MensajeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - Unit Tests")
class NotificationServiceTest {

    @Mock
    private MensajeRepository mensajeRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Nested
    @DisplayName("scheduleTicketCreatedNotification()")
    class ScheduleTicketCreated {

        @Test
        @DisplayName("debe programar notificación de ticket creado")
        void schedule_debeCrearMensaje() {
            // Given
            Ticket ticket = ticketWaiting().build();

            // When
            notificationService.scheduleTicketCreatedNotification(ticket);

            // Then
            ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
            verify(mensajeRepository).save(captor.capture());

            Mensaje mensaje = captor.getValue();
            assertThat(mensaje.getTicket()).isEqualTo(ticket);
            assertThat(mensaje.getPlantilla()).isEqualTo(MessageTemplate.TOTEM_TICKET_CREADO);
            assertThat(mensaje.getFechaProgramada()).isNotNull();
        }
    }

    @Nested
    @DisplayName("scheduleProximoTurnoNotification()")
    class ScheduleProximoTurno {

        @Test
        @DisplayName("con teléfono → debe programar notificación")
        void scheduleProximo_conTelefono_debeProgramar() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono("+56912345678")
                .build();

            // When
            notificationService.scheduleProximoTurnoNotification(ticket);

            // Then
            ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
            verify(mensajeRepository).save(captor.capture());
            assertThat(captor.getValue().getPlantilla()).isEqualTo(MessageTemplate.TOTEM_PROXIMO_TURNO);
        }

        @Test
        @DisplayName("sin teléfono → no debe programar")
        void scheduleProximo_sinTelefono_nDebeProgramar() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono(null)
                .build();

            // When
            notificationService.scheduleProximoTurnoNotification(ticket);

            // Then
            verify(mensajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("con teléfono vacío → no debe programar")
        void scheduleProximo_telefonoVacio_noDebeProgramar() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono("   ")
                .build();

            // When
            notificationService.scheduleProximoTurnoNotification(ticket);

            // Then
            verify(mensajeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("scheduleEsTuTurnoNotification()")
    class ScheduleEsTuTurno {

        @Test
        @DisplayName("con teléfono → debe programar notificación inmediata")
        void scheduleEsTurno_conTelefono_debeProgramarInmediata() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono("+56912345678")
                .build();

            // When
            notificationService.scheduleEsTuTurnoNotification(ticket);

            // Then
            ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
            verify(mensajeRepository).save(captor.capture());
            
            Mensaje mensaje = captor.getValue();
            assertThat(mensaje.getPlantilla()).isEqualTo(MessageTemplate.TOTEM_ES_TU_TURNO);
            assertThat(mensaje.getFechaProgramada()).isNotNull();
        }

        @Test
        @DisplayName("sin teléfono → no debe programar")
        void scheduleEsTurno_sinTelefono_noDebeProgramar() {
            // Given
            Ticket ticket = ticketWaiting()
                .telefono(null)
                .build();

            // When
            notificationService.scheduleEsTuTurnoNotification(ticket);

            // Then
            verify(mensajeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cancelPendingNotifications()")
    class CancelPendingNotifications {

        @Test
        @DisplayName("debe cancelar notificaciones pendientes")
        void cancel_debeCancelarPendientes() {
            // Given
            Ticket ticket = ticketWaiting().id(1L).build();
            Mensaje mensajePendiente = Mensaje.builder()
                .ticket(ticket)
                .estadoEnvio(Mensaje.EstadoEnvio.PENDIENTE)
                .build();
            
            when(mensajeRepository.findByTicketId(1L))
                .thenReturn(List.of(mensajePendiente));

            // When
            notificationService.cancelPendingNotifications(ticket);

            // Then
            assertThat(mensajePendiente.getEstadoEnvio()).isEqualTo(Mensaje.EstadoEnvio.FALLIDO);
            verify(mensajeRepository).save(mensajePendiente);
        }

        @Test
        @DisplayName("sin mensajes pendientes → no debe hacer nada")
        void cancel_sinMensajesPendientes_noDebeHacerNada() {
            // Given
            Ticket ticket = ticketWaiting().id(1L).build();
            when(mensajeRepository.findByTicketId(1L))
                .thenReturn(List.of());

            // When
            notificationService.cancelPendingNotifications(ticket);

            // Then
            verify(mensajeRepository, never()).save(any());
        }

        @Test
        @DisplayName("con mensajes ya enviados → no debe cancelarlos")
        void cancel_mensajesYaEnviados_noDebeCancelar() {
            // Given
            Ticket ticket = ticketWaiting().id(1L).build();
            Mensaje mensajeEnviado = Mensaje.builder()
                .ticket(ticket)
                .estadoEnvio(Mensaje.EstadoEnvio.ENVIADO)
                .build();
            
            when(mensajeRepository.findByTicketId(1L))
                .thenReturn(List.of(mensajeEnviado));

            // When
            notificationService.cancelPendingNotifications(ticket);

            // Then
            assertThat(mensajeEnviado.getEstadoEnvio()).isEqualTo(Mensaje.EstadoEnvio.ENVIADO);
            verify(mensajeRepository, never()).save(any());
        }
    }
}
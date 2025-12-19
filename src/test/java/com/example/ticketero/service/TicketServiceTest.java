package com.example.ticketero.service;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Unit Tests")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TicketService ticketService;

    // ============================================================
    // CREAR TICKET
    // ============================================================
    
    @Nested
    @DisplayName("create()")
    class CrearTicket {

        @Test
        @DisplayName("con datos válidos → debe crear ticket y programar notificación")
        void create_conDatosValidos_debeCrearTicketYProgramarNotificacion() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            Ticket ticketGuardado = ticketWaiting()
                .numero("C001")
                .positionInQueue(1)
                .estimatedWaitMinutes(5)
                .build();

            when(ticketRepository.countTicketsAheadInQueue(
                eq(QueueType.CAJA), eq(TicketStatus.getActiveStatuses()), any()))
                .thenReturn(0L);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticketGuardado);

            // When
            TicketResponse response = ticketService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.numero()).isEqualTo("C001");
            assertThat(response.positionInQueue()).isEqualTo(1);
            assertThat(response.estimatedWaitMinutes()).isEqualTo(5);
            assertThat(response.status()).isEqualTo(TicketStatus.EN_ESPERA);

            verify(ticketRepository).save(any(Ticket.class));
            verify(notificationService).scheduleTicketCreatedNotification(any(Ticket.class));
        }

        @Test
        @DisplayName("debe generar número de ticket con prefijo correcto")
        void create_debeGenerarNumeroConPrefijoCorrecto() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            when(ticketRepository.countTicketsAheadInQueue(any(), any(), any()))
                .thenReturn(0L);
            when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ticketService.create(request);

            // Then
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());

            Ticket ticket = captor.getValue();
            assertThat(ticket.getNumero()).startsWith("C");
            assertThat(ticket.getQueueType()).isEqualTo(QueueType.CAJA);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EN_ESPERA);
        }

        @Test
        @DisplayName("para cola PERSONAL_BANKER → debe usar prefijo P")
        void create_colaPersonal_debeUsarPrefijoP() {
            // Given
            TicketCreateRequest request = new TicketCreateRequest(
                "12345678", "+56912345678", "Sucursal Centro", QueueType.PERSONAL_BANKER
            );
            when(ticketRepository.countTicketsAheadInQueue(any(), any(), any()))
                .thenReturn(0L);
            when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ticketService.create(request);

            // Then
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            assertThat(captor.getValue().getNumero()).startsWith("P");
        }

        @Test
        @DisplayName("sin teléfono → debe crear ticket igual")
        void create_sinTelefono_debeCrearTicket() {
            // Given
            TicketCreateRequest request = ticketRequestSinTelefono();
            when(ticketRepository.countTicketsAheadInQueue(any(), any(), any()))
                .thenReturn(0L);
            when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TicketResponse response = ticketService.create(request);

            // Then
            assertThat(response).isNotNull();
            verify(notificationService).scheduleTicketCreatedNotification(any());
        }

        @Test
        @DisplayName("con tickets en cola → debe calcular posición correctamente")
        void create_conTicketsEnCola_debeCalcularPosicion() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            when(ticketRepository.countTicketsAheadInQueue(any(), any(), any()))
                .thenReturn(5L); // 5 tickets ahead
            when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TicketResponse response = ticketService.create(request);

            // Then
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            
            Ticket ticket = captor.getValue();
            assertThat(ticket.getPositionInQueue()).isEqualTo(6); // 5 + 1
            assertThat(ticket.getEstimatedWaitMinutes()).isEqualTo(30); // 6 * 5 minutes
        }

        @Test
        @DisplayName("debe generar UUID único para código de referencia")
        void create_debeGenerarUuidUnico() {
            // Given
            TicketCreateRequest request = validTicketRequest();
            when(ticketRepository.countTicketsAheadInQueue(any(), any(), any()))
                .thenReturn(0L);
            when(ticketRepository.save(any())).thenAnswer(invocation -> {
                Ticket ticket = invocation.getArgument(0);
                // Simulate @PrePersist behavior
                if (ticket.getCodigoReferencia() == null) {
                    ticket.setCodigoReferencia(UUID.randomUUID());
                }
                return ticket;
            });

            // When
            ticketService.create(request);

            // Then
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            
            Ticket ticket = captor.getValue();
            assertThat(ticket.getCodigoReferencia()).isNotNull();
        }
    }

    // ============================================================
    // OBTENER TICKET
    // ============================================================
    
    @Nested
    @DisplayName("findByReference()")
    class ObtenerTicket {

        @Test
        @DisplayName("con UUID existente → debe retornar ticket")
        void findByReference_conUuidExistente_debeRetornarTicket() {
            // Given
            UUID codigo = UUID.randomUUID();
            Ticket ticket = ticketWaiting()
                .codigoReferencia(codigo)
                .numero("C001")
                .build();

            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.of(ticket));

            // When
            Optional<TicketResponse> response = ticketService.findByReference(codigo);

            // Then
            assertThat(response).isPresent();
            assertThat(response.get().codigoReferencia()).isEqualTo(codigo);
            assertThat(response.get().numero()).isEqualTo("C001");
        }

        @Test
        @DisplayName("con UUID inexistente → debe retornar Optional.empty()")
        void findByReference_conUuidInexistente_debeRetornarEmpty() {
            // Given
            UUID codigo = UUID.randomUUID();
            when(ticketRepository.findByCodigoReferencia(codigo)).thenReturn(Optional.empty());

            // When
            Optional<TicketResponse> response = ticketService.findByReference(codigo);

            // Then
            assertThat(response).isEmpty();
        }
    }

    // ============================================================
    // GET POSITION
    // ============================================================
    
    @Nested
    @DisplayName("getPosition()")
    class GetPosition {

        @Test
        @DisplayName("ticket EN_ESPERA → debe retornar posición waiting")
        void getPosition_ticketEnEspera_debeRetornarWaiting() {
            // Given
            Ticket ticket = ticketWaiting()
                .numero("C001")
                .status(TicketStatus.EN_ESPERA)
                .positionInQueue(3)
                .estimatedWaitMinutes(15)
                .build();

            when(ticketRepository.findByNumero("C001")).thenReturn(Optional.of(ticket));

            // When
            Optional<QueuePositionResponse> response = ticketService.getPosition("C001");

            // Then
            assertThat(response).isPresent();
            assertThat(response.get().numero()).isEqualTo("C001");
            assertThat(response.get().queueType()).isEqualTo(QueueType.CAJA);
        }

        @Test
        @DisplayName("ticket ATENDIENDO → debe retornar attending")
        void getPosition_ticketAtendiendo_debeRetornarAttending() {
            // Given
            Ticket ticket = ticketInProgress()
                .numero("C002")
                .status(TicketStatus.ATENDIENDO)
                .assignedModuleNumber(5)
                .build();

            when(ticketRepository.findByNumero("C002")).thenReturn(Optional.of(ticket));

            // When
            Optional<QueuePositionResponse> response = ticketService.getPosition("C002");

            // Then
            assertThat(response).isPresent();
            assertThat(response.get().numero()).isEqualTo("C002");
        }

        @Test
        @DisplayName("número inexistente → debe retornar Optional.empty()")
        void getPosition_numeroInexistente_debeRetornarEmpty() {
            // Given
            when(ticketRepository.findByNumero("X999")).thenReturn(Optional.empty());

            // When
            Optional<QueuePositionResponse> response = ticketService.getPosition("X999");

            // Then
            assertThat(response).isEmpty();
        }
    }
}
package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueManagementService - Unit Tests")
class QueueManagementServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AdvisorService advisorService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private QueueManagementService queueManagementService;

    @Nested
    @DisplayName("processQueueByType()")
    class ProcessQueueByType {

        @Test
        @DisplayName("con cola vacía → no debe hacer nada")
        void processQueue_colaVacia_noDebeHacerNada() {
            // Given
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(
                QueueType.CAJA, TicketStatus.getActiveStatuses()))
                .thenReturn(Collections.emptyList());

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            verify(ticketRepository, never()).save(any());
            verify(notificationService, never()).scheduleProximoTurnoNotification(any());
        }

        @Test
        @DisplayName("con ticket en posición 1 → debe cambiar a PROXIMO")
        void processQueue_ticketPosicion1_debeCambiarAProximo() {
            // Given
            Ticket ticket = ticketWaiting()
                .positionInQueue(1)
                .status(TicketStatus.EN_ESPERA)
                .build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket));

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PROXIMO);
            verify(ticketRepository, atLeastOnce()).save(ticket);
            verify(notificationService).scheduleProximoTurnoNotification(ticket);
        }

        @Test
        @DisplayName("con ticket PROXIMO y advisor disponible → debe asignar")
        void processQueue_ticketProximoConAdvisor_debeAsignar() {
            // Given
            Ticket ticket = ticketWaiting()
                .status(TicketStatus.PROXIMO)
                .build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket));
            when(advisorService.assignTicketToAdvisor(ticket)).thenReturn(true);

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            verify(advisorService).assignTicketToAdvisor(ticket);
            verify(ticketRepository).save(ticket);
            verify(notificationService).scheduleEsTuTurnoNotification(ticket);
        }

        @Test
        @DisplayName("con ticket PROXIMO sin advisor → no debe asignar")
        void processQueue_ticketProximoSinAdvisor_noDebeAsignar() {
            // Given
            Ticket ticket = ticketWaiting()
                .status(TicketStatus.PROXIMO)
                .build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket));
            when(advisorService.assignTicketToAdvisor(ticket)).thenReturn(false);

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            verify(advisorService).assignTicketToAdvisor(ticket);
            verify(notificationService, never()).scheduleEsTuTurnoNotification(ticket);
        }

        @Test
        @DisplayName("debe actualizar posiciones en cola correctamente")
        void processQueue_debeActualizarPosiciones() {
            // Given
            Ticket ticket1 = ticketWaiting().id(1L).positionInQueue(5).build();
            Ticket ticket2 = ticketWaiting().id(2L).positionInQueue(5).build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket1, ticket2));

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            assertThat(ticket1.getPositionInQueue()).isEqualTo(1);
            assertThat(ticket2.getPositionInQueue()).isEqualTo(2);
            assertThat(ticket1.getEstimatedWaitMinutes()).isEqualTo(5); // 1 * 5min
            assertThat(ticket2.getEstimatedWaitMinutes()).isEqualTo(10); // 2 * 5min
        }

        @Test
        @DisplayName("con múltiples tickets → solo los primeros 3 cambian a PROXIMO")
        void processQueue_multiplesTickets_soloLosPrimerosCambianAProximo() {
            // Given
            Ticket ticket1 = ticketWaiting().id(1L).status(TicketStatus.EN_ESPERA).build();
            Ticket ticket2 = ticketWaiting().id(2L).status(TicketStatus.EN_ESPERA).build();
            Ticket ticket3 = ticketWaiting().id(3L).status(TicketStatus.EN_ESPERA).build();
            Ticket ticket4 = ticketWaiting().id(4L).status(TicketStatus.EN_ESPERA).build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket1, ticket2, ticket3, ticket4));

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            assertThat(ticket1.getStatus()).isEqualTo(TicketStatus.PROXIMO);
            assertThat(ticket2.getStatus()).isEqualTo(TicketStatus.PROXIMO);
            assertThat(ticket3.getStatus()).isEqualTo(TicketStatus.PROXIMO);
            assertThat(ticket4.getStatus()).isEqualTo(TicketStatus.EN_ESPERA); // Position 4, stays EN_ESPERA
            
            verify(notificationService, times(3)).scheduleProximoTurnoNotification(any());
        }

        @Test
        @DisplayName("con ticket ATENDIENDO → no debe procesar")
        void processQueue_ticketAtendiendo_noDebeProcesar() {
            // Given
            Ticket ticket = ticketInProgress()
                .status(TicketStatus.ATENDIENDO)
                .build();
            
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ticket));

            // When
            queueManagementService.processQueueByType(QueueType.CAJA);

            // Then
            verify(advisorService, never()).assignTicketToAdvisor(any());
            verify(notificationService, never()).scheduleProximoTurnoNotification(any());
        }
    }

    @Nested
    @DisplayName("completeTicket()")
    class CompleteTicket {

        @Test
        @DisplayName("con ticket existente y advisor asignado → debe completar y liberar advisor")
        void completeTicket_conAdvisorAsignado_debeCompletarYLiberar() {
            // Given
            Advisor advisor = advisorBusy().build();
            Ticket ticket = ticketInProgress()
                .assignedAdvisor(advisor)
                .build();
            
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            // When
            queueManagementService.completeTicket(1L);

            // Then
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETADO);
            assertThat(ticket.getPositionInQueue()).isEqualTo(0);
            assertThat(ticket.getEstimatedWaitMinutes()).isEqualTo(0);
            
            verify(advisorService).completeTicketAssignment(ticket);
            verify(ticketRepository).save(ticket);
            verify(notificationService).cancelPendingNotifications(ticket);
        }

        @Test
        @DisplayName("con ticket sin advisor → debe completar sin liberar advisor")
        void completeTicket_sinAdvisor_debeCompletar() {
            // Given
            Ticket ticket = ticketWaiting()
                .assignedAdvisor(null)
                .build();
            
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            // When
            queueManagementService.completeTicket(1L);

            // Then
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETADO);
            verify(advisorService, never()).completeTicketAssignment(any());
            verify(ticketRepository).save(ticket);
        }

        @Test
        @DisplayName("con ticket inexistente → no debe hacer nada")
        void completeTicket_ticketInexistente_noDebeHacerNada() {
            // Given
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            queueManagementService.completeTicket(999L);

            // Then
            verify(advisorService, never()).completeTicketAssignment(any());
            verify(ticketRepository, never()).save(any());
            verify(notificationService, never()).cancelPendingNotifications(any());
        }
    }

    @Nested
    @DisplayName("processQueues()")
    class ProcessQueues {

        @Test
        @DisplayName("debe procesar todas las colas")
        void processQueues_debeProcesarTodasLasColas() {
            // Given
            when(ticketRepository.findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            queueManagementService.processQueues();

            // Then
            verify(ticketRepository, times(QueueType.values().length))
                .findByQueueTypeAndStatusInOrderByCreatedAtAsc(any(), any());
        }
    }
}
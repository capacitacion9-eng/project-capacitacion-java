package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.example.ticketero.testutil.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvisorService - Unit Tests")
class AdvisorServiceTest {

    @Mock
    private AdvisorRepository advisorRepository;

    @InjectMocks
    private AdvisorService advisorService;

    @Nested
    @DisplayName("assignTicketToAdvisor()")
    class AssignTicketToAdvisor {

        @Test
        @DisplayName("con advisor disponible → debe asignar ticket correctamente")
        void assignTicket_conAdvisorDisponible_debeAsignar() {
            // Given
            Advisor advisor = advisorAvailable()
                .assignedTicketsCount(1)
                .moduleNumber(3)
                .build();
            Ticket ticket = ticketWaiting().build();
            
            when(advisorRepository.findAvailableAdvisorWithLeastLoad())
                .thenReturn(Optional.of(advisor));

            // When
            boolean result = advisorService.assignTicketToAdvisor(ticket);

            // Then
            assertThat(result).isTrue();
            assertThat(ticket.getAssignedAdvisor()).isEqualTo(advisor);
            assertThat(ticket.getAssignedModuleNumber()).isEqualTo(3);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ATENDIENDO);
            assertThat(advisor.getAssignedTicketsCount()).isEqualTo(2);
            
            verify(advisorRepository).save(advisor);
        }

        @Test
        @DisplayName("advisor alcanza límite → debe cambiar a BUSY")
        void assignTicket_advisorAlcanzaLimite_debeCambiarABusy() {
            // Given
            Advisor advisor = advisorAvailable()
                .assignedTicketsCount(2)
                .build();
            Ticket ticket = ticketWaiting().build();
            
            when(advisorRepository.findAvailableAdvisorWithLeastLoad())
                .thenReturn(Optional.of(advisor));

            // When
            advisorService.assignTicketToAdvisor(ticket);

            // Then
            assertThat(advisor.getAssignedTicketsCount()).isEqualTo(3);
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.BUSY);
        }

        @Test
        @DisplayName("sin advisors disponibles → debe retornar false")
        void assignTicket_sinAdvisors_debeRetornarFalse() {
            // Given
            Ticket ticket = ticketWaiting().build();
            when(advisorRepository.findAvailableAdvisorWithLeastLoad())
                .thenReturn(Optional.empty());

            // When
            boolean result = advisorService.assignTicketToAdvisor(ticket);

            // Then
            assertThat(result).isFalse();
            assertThat(ticket.getAssignedAdvisor()).isNull();
            verify(advisorRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("completeTicketAssignment()")
    class CompleteTicketAssignment {

        @Test
        @DisplayName("debe decrementar contador y liberar advisor")
        void completeAssignment_debeDecrementarYLiberar() {
            // Given
            Advisor advisor = advisorBusy()
                .assignedTicketsCount(3)
                .build();
            Ticket ticket = ticketInProgress()
                .assignedAdvisor(advisor)
                .build();

            // When
            advisorService.completeTicketAssignment(ticket);

            // Then
            assertThat(advisor.getAssignedTicketsCount()).isEqualTo(2);
            assertThat(advisor.getStatus()).isEqualTo(AdvisorStatus.AVAILABLE);
            verify(advisorRepository).save(advisor);
        }

        @Test
        @DisplayName("con ticket sin advisor → no debe hacer nada")
        void completeAssignment_sinAdvisor_noDebeHacerNada() {
            // Given
            Ticket ticket = ticketWaiting()
                .assignedAdvisor(null)
                .build();

            // When
            advisorService.completeTicketAssignment(ticket);

            // Then
            verify(advisorRepository, never()).save(any());
        }

        @Test
        @DisplayName("contador no debe ser negativo")
        void completeAssignment_contadorNoDebeSerNegativo() {
            // Given
            Advisor advisor = advisorAvailable()
                .assignedTicketsCount(0)
                .build();
            Ticket ticket = ticketInProgress()
                .assignedAdvisor(advisor)
                .build();

            // When
            advisorService.completeTicketAssignment(ticket);

            // Then
            assertThat(advisor.getAssignedTicketsCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findAvailableAdvisorWithLeastLoad()")
    class FindAvailableAdvisor {

        @Test
        @DisplayName("debe retornar advisor con menor carga")
        void findAdvisor_debeRetornarMenorCarga() {
            // Given
            Advisor advisor = advisorAvailable().build();
            when(advisorRepository.findAvailableAdvisorWithLeastLoad())
                .thenReturn(Optional.of(advisor));

            // When
            Optional<Advisor> result = advisorService.findAvailableAdvisorWithLeastLoad();

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(advisor);
        }

        @Test
        @DisplayName("sin advisors disponibles → debe retornar empty")
        void findAdvisor_sinDisponibles_debeRetornarEmpty() {
            // Given
            when(advisorRepository.findAvailableAdvisorWithLeastLoad())
                .thenReturn(Optional.empty());

            // When
            Optional<Advisor> result = advisorService.findAvailableAdvisorWithLeastLoad();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAvailableAdvisors()")
    class FindAvailableAdvisors {

        @Test
        @DisplayName("debe retornar lista de advisors disponibles")
        void findAvailable_debeRetornarLista() {
            // Given
            List<Advisor> advisors = List.of(
                advisorAvailable().id(1L).build(),
                advisorAvailable().id(2L).build()
            );
            when(advisorRepository.findByStatus(AdvisorStatus.AVAILABLE))
                .thenReturn(advisors);

            // When
            List<Advisor> result = advisorService.findAvailableAdvisors();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyElementsOf(advisors);
        }

        @Test
        @DisplayName("sin advisors disponibles → debe retornar lista vacía")
        void findAvailable_sinDisponibles_debeRetornarListaVacia() {
            // Given
            when(advisorRepository.findByStatus(AdvisorStatus.AVAILABLE))
                .thenReturn(List.of());

            // When
            List<Advisor> result = advisorService.findAvailableAdvisors();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByStatus()")
    class CountByStatus {

        @Test
        @DisplayName("debe contar advisors por estado")
        void countByStatus_debeContarCorrectamente() {
            // Given
            when(advisorRepository.countByStatus(AdvisorStatus.AVAILABLE))
                .thenReturn(5L);
            when(advisorRepository.countByStatus(AdvisorStatus.BUSY))
                .thenReturn(3L);

            // When
            long availableCount = advisorService.countByStatus(AdvisorStatus.AVAILABLE);
            long busyCount = advisorService.countByStatus(AdvisorStatus.BUSY);

            // Then
            assertThat(availableCount).isEqualTo(5L);
            assertThat(busyCount).isEqualTo(3L);
        }
    }
}
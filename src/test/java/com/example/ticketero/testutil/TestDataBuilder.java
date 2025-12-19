package com.example.ticketero.testutil;

import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.entity.*;
import com.example.ticketero.model.enums.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builder para crear datos de prueba consistentes.
 */
public class TestDataBuilder {

    // ============================================================
    // TICKETS
    // ============================================================
    
    public static Ticket.TicketBuilder ticketWaiting() {
        return Ticket.builder()
            .id(1L)
            .codigoReferencia(UUID.randomUUID())
            .numero("C001")
            .nationalId("12345678")
            .telefono("+56912345678")
            .branchOffice("Sucursal Centro")
            .queueType(QueueType.CAJA)
            .status(TicketStatus.EN_ESPERA)
            .positionInQueue(1)
            .estimatedWaitMinutes(5)
            .createdAt(LocalDateTime.now());
    }
    
    public static Ticket.TicketBuilder ticketInProgress() {
        return ticketWaiting()
            .status(TicketStatus.ATENDIENDO)
            .createdAt(LocalDateTime.now().minusMinutes(1));
    }
    
    public static Ticket.TicketBuilder ticketCompleted() {
        return ticketInProgress()
            .status(TicketStatus.COMPLETADO)
            .createdAt(LocalDateTime.now().minusMinutes(5));
    }

    // ============================================================
    // ADVISORS
    // ============================================================
    
    public static Advisor.AdvisorBuilder advisorAvailable() {
        return Advisor.builder()
            .id(1L)
            .name("María López")
            .email("maria.lopez@banco.com")
            .moduleNumber(1)
            .status(AdvisorStatus.AVAILABLE)
            .assignedTicketsCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now());
    }
    
    public static Advisor.AdvisorBuilder advisorBusy() {
        return advisorAvailable()
            .status(AdvisorStatus.BUSY)
            .assignedTicketsCount(3);
    }

    public static Advisor.AdvisorBuilder advisorWithLoad(int ticketCount) {
        return advisorAvailable()
            .assignedTicketsCount(ticketCount)
            .status(ticketCount >= 3 ? AdvisorStatus.BUSY : AdvisorStatus.AVAILABLE);
    }

    // ============================================================
    // REQUESTS
    // ============================================================
    
    public static TicketCreateRequest validTicketRequest() {
        return new TicketCreateRequest(
            "12345678",
            "+56912345678",
            "Sucursal Centro",
            QueueType.CAJA
        );
    }
    
    public static TicketCreateRequest ticketRequestSinTelefono() {
        return new TicketCreateRequest(
            "12345678",
            null,
            "Sucursal Centro",
            QueueType.CAJA
        );
    }

    public static TicketCreateRequest ticketRequestPersonal() {
        return new TicketCreateRequest(
            "87654321",
            "+56987654321",
            "Sucursal Norte",
            QueueType.PERSONAL_BANKER
        );
    }

    public static TicketCreateRequest ticketRequestEmpresas() {
        return new TicketCreateRequest(
            "11223344",
            "+56911223344",
            "Sucursal Empresas",
            QueueType.EMPRESAS
        );
    }

    // ============================================================
    // MENSAJES
    // ============================================================
    
    public static Mensaje.MensajeBuilder mensajePendiente() {
        return Mensaje.builder()
            .id(1L)
            .plantilla(MessageTemplate.TOTEM_TICKET_CREADO)
            .fechaProgramada(LocalDateTime.now().plusMinutes(1))
            .estadoEnvio(Mensaje.EstadoEnvio.PENDIENTE)
            .intentos(0)
            .createdAt(LocalDateTime.now());
    }
}
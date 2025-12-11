package com.example.ticketero.controller;

import com.example.ticketero.model.dto.QueuePositionResponse;
import com.example.ticketero.model.dto.TicketCreateRequest;
import com.example.ticketero.model.dto.TicketResponse;
import com.example.ticketero.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody TicketCreateRequest request
    ) {
        log.info("Creating ticket for nationalId: {}, queueType: {}", 
                request.nationalId(), request.queueType());
        
        TicketResponse response = ticketService.createTicket(request);
        
        log.info("Ticket created successfully: {}", response.numero());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{numero}/position")
    public ResponseEntity<QueuePositionResponse> getQueuePosition(
            @PathVariable String numero
    ) {
        log.info("Getting queue position for ticket: {}", numero);
        
        return ticketService.getQueuePosition(numero)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reference/{codigoReferencia}")
    public ResponseEntity<TicketResponse> getByCodigoReferencia(
            @PathVariable UUID codigoReferencia
    ) {
        log.info("Getting ticket by reference code: {}", codigoReferencia);
        
        return ticketService.findByCodigoReferencia(codigoReferencia)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
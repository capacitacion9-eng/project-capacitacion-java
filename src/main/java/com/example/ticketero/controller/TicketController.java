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
            @Valid @RequestBody TicketCreateRequest request) {
        
        log.info("Creating ticket for nationalId: {}, queueType: {}", 
                request.nationalId(), request.queueType());
        
        TicketResponse response = ticketService.create(request);
        
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{numero}/position")
    public ResponseEntity<QueuePositionResponse> getPosition(@PathVariable String numero) {
        log.info("Getting position for ticket: {}", numero);
        
        return ticketService.getPosition(numero)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reference/{uuid}")
    public ResponseEntity<TicketResponse> getByReference(@PathVariable UUID uuid) {
        log.info("Getting ticket by reference: {}", uuid);
        
        return ticketService.findByReference(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
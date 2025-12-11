package com.example.ticketero.controller;

import com.example.ticketero.model.dto.DashboardResponse;
import com.example.ticketero.service.AdvisorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdvisorService advisorService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("Getting dashboard metrics");
        
        DashboardResponse dashboard = advisorService.getDashboardMetrics();
        
        log.info("Dashboard metrics retrieved: {} tickets today", 
                dashboard.totalTicketsToday());
        
        return ResponseEntity.ok(dashboard);
    }

    @PostMapping("/tickets/{ticketId}/complete")
    public ResponseEntity<Void> completeTicket(@PathVariable Long ticketId) {
        log.info("Completing ticket: {}", ticketId);
        
        advisorService.completeTicket(ticketId);
        
        log.info("Ticket {} completed successfully", ticketId);
        return ResponseEntity.noContent().build();
    }
}
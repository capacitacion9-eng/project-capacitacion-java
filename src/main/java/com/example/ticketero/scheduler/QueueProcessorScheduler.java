package com.example.ticketero.scheduler;

import com.example.ticketero.service.QueueManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueProcessorScheduler {

    private final QueueManagementService queueManagementService;

    @Scheduled(fixedRate = 5000) // Cada 5 segundos
    public void processQueues() {
        try {
            log.debug("Starting automatic queue processing");
            
            queueManagementService.processQueues();
            
            log.debug("Queue processing completed successfully");
            
        } catch (Exception e) {
            log.error("Error during queue processing: {}", e.getMessage(), e);
        }
    }
}
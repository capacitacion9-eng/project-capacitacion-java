package com.example.ticketero.scheduler;

import com.example.ticketero.service.QueueManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueProcessorScheduler - Unit Tests")
class QueueProcessorSchedulerTest {

    @Mock
    private QueueManagementService queueManagementService;

    @InjectMocks
    private QueueProcessorScheduler queueProcessorScheduler;

    @Test
    @DisplayName("debe procesar colas exitosamente")
    void processQueues_debeProceserExitosamente() {
        // When
        queueProcessorScheduler.processQueues();

        // Then
        verify(queueManagementService).processQueues();
    }

    @Test
    @DisplayName("con excepción → debe manejar error sin propagar")
    void processQueues_conExcepcion_debeManearError() {
        // Given
        doThrow(new RuntimeException("Queue error"))
            .when(queueManagementService).processQueues();

        // When - no debe lanzar excepción
        queueProcessorScheduler.processQueues();

        // Then
        verify(queueManagementService).processQueues();
    }
}
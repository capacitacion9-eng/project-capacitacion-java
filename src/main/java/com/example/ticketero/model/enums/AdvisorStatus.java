package com.example.ticketero.model.enums;

/**
 * Estados posibles de un asesor
 */
public enum AdvisorStatus {
    AVAILABLE,
    BUSY,
    OFFLINE;

    /**
     * Verifica si el asesor puede recibir asignaciones
     */
    public boolean canReceiveAssignments() {
        return this == AVAILABLE;
    }
}
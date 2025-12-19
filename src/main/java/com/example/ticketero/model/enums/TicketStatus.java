package com.example.ticketero.model.enums;

import java.util.List;

/**
 * Estados posibles de un ticket
 */
public enum TicketStatus {
    EN_ESPERA,
    PROXIMO,
    ATENDIENDO,
    COMPLETADO,
    CANCELADO,
    NO_ATENDIDO;

    /**
     * Estados considerados "activos" (cliente aún no ha sido atendido completamente)
     */
    public static List<TicketStatus> getActiveStatuses() {
        return List.of(EN_ESPERA, PROXIMO, ATENDIENDO);
    }

    /**
     * Verifica si este estado es considerado activo
     */
    public boolean isActive() {
        return getActiveStatuses().contains(this);
    }
}
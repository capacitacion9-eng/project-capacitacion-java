package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.entity.Mensaje.EstadoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    // Query derivadas
    List<Mensaje> findByEstadoEnvio(EstadoEnvio estadoEnvio);
    
    List<Mensaje> findByTicketId(Long ticketId);

    // Query para scheduler - mensajes pendientes listos para enviar
    @Query("""
        SELECT m FROM Mensaje m 
        WHERE m.estadoEnvio = 'PENDIENTE' 
        AND m.fechaProgramada <= :now 
        AND m.intentos < 3
        ORDER BY m.fechaProgramada ASC
        """)
    List<Mensaje> findPendingMessagesReadyToSend(@Param("now") LocalDateTime now);

    // Query para mensajes fallidos que pueden reintentarse
    @Query("""
        SELECT m FROM Mensaje m 
        WHERE m.estadoEnvio = 'FALLIDO' 
        AND m.intentos < 3 
        AND m.fechaProgramada <= :now
        ORDER BY m.fechaProgramada ASC
        """)
    List<Mensaje> findFailedMessagesForRetry(@Param("now") LocalDateTime now);
}
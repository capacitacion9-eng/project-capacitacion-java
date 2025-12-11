package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Mensaje;
import com.example.ticketero.model.enums.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    // Query derivadas
    List<Mensaje> findByEstadoEnvioAndFechaProgramadaBefore(String estadoEnvio, LocalDateTime fecha);
    
    List<Mensaje> findByTicketId(Long ticketId);
    
    boolean existsByTicketIdAndPlantilla(Long ticketId, MessageTemplate plantilla);

    // Query para el scheduler
    @Query("""
        SELECT m FROM Mensaje m 
        WHERE m.estadoEnvio = 'PENDIENTE' 
        AND m.fechaProgramada <= :now 
        AND m.intentos < 3
        ORDER BY m.fechaProgramada ASC
        """)
    List<Mensaje> findPendingMessages(@Param("now") LocalDateTime now);
}
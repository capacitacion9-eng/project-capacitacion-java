package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.QueueType;
import com.example.ticketero.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Query derivadas
    Optional<Ticket> findByNumero(String numero);
    
    Optional<Ticket> findByCodigoReferencia(UUID codigoReferencia);
    
    List<Ticket> findByNationalIdAndStatusIn(String nationalId, List<TicketStatus> statuses);
    
    List<Ticket> findByQueueTypeAndStatusOrderByCreatedAtAsc(QueueType queueType, TicketStatus status);
    
    long countByStatusIn(List<TicketStatus> statuses);
    
    long countByCreatedAtAfter(LocalDateTime date);

    // Queries custom
    @Query("""
        SELECT t FROM Ticket t 
        WHERE t.queueType = :queueType 
        AND t.status IN :statuses 
        ORDER BY t.createdAt ASC
        """)
    List<Ticket> findByQueueTypeAndStatusesOrderByCreated(
        @Param("queueType") QueueType queueType,
        @Param("statuses") List<TicketStatus> statuses
    );

    @Query("""
        SELECT COUNT(t) FROM Ticket t 
        WHERE t.queueType = :queueType 
        AND t.status IN :statuses
        """)
    long countByQueueTypeAndStatuses(
        @Param("queueType") QueueType queueType,
        @Param("statuses") List<TicketStatus> statuses
    );
}
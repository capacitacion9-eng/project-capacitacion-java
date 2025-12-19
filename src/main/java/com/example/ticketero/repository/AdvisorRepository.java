package com.example.ticketero.repository;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.enums.AdvisorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdvisorRepository extends JpaRepository<Advisor, Long> {

    // Query derivadas
    List<Advisor> findByStatus(AdvisorStatus status);
    
    long countByStatus(AdvisorStatus status);
    
    Optional<Advisor> findByEmail(String email);

    // Query para encontrar asesor disponible con menos carga
    @Query("""
        SELECT a FROM Advisor a 
        WHERE a.status = 'AVAILABLE' 
        ORDER BY a.assignedTicketsCount ASC, a.id ASC
        """)
    Optional<Advisor> findAvailableAdvisorWithLeastLoad();

    // Query para asesores disponibles ordenados por carga
    @Query("""
        SELECT a FROM Advisor a 
        WHERE a.status = 'AVAILABLE' 
        ORDER BY a.assignedTicketsCount ASC
        """)
    List<Advisor> findAvailableAdvisorsOrderByLoad();
}
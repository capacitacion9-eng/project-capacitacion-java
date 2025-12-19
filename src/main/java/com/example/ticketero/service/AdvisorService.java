package com.example.ticketero.service;

import com.example.ticketero.model.entity.Advisor;
import com.example.ticketero.model.entity.Ticket;
import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.TicketStatus;
import com.example.ticketero.repository.AdvisorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdvisorService {

    private final AdvisorRepository advisorRepository;

    public List<Advisor> findAvailableAdvisors() {
        return advisorRepository.findByStatus(AdvisorStatus.AVAILABLE);
    }

    public Optional<Advisor> findAvailableAdvisorWithLeastLoad() {
        return advisorRepository.findAvailableAdvisorWithLeastLoad();
    }

    @Transactional
    public boolean assignTicketToAdvisor(Ticket ticket) {
        Optional<Advisor> advisorOpt = findAvailableAdvisorWithLeastLoad();
        
        if (advisorOpt.isEmpty()) {
            log.warn("No available advisors to assign ticket: {}", ticket.getNumero());
            return false;
        }

        Advisor advisor = advisorOpt.get();
        
        // Asignar ticket al asesor
        ticket.setAssignedAdvisor(advisor);
        ticket.setAssignedModuleNumber(advisor.getModuleNumber());
        ticket.setStatus(TicketStatus.ATENDIENDO);
        
        // Actualizar contador del asesor
        advisor.setAssignedTicketsCount(advisor.getAssignedTicketsCount() + 1);
        
        // Cambiar estado del asesor si alcanza el límite
        if (advisor.getAssignedTicketsCount() >= 3) {
            advisor.setStatus(AdvisorStatus.BUSY);
        }
        
        advisorRepository.save(advisor);
        
        log.info("Ticket {} assigned to advisor {} at module {}", 
                ticket.getNumero(), advisor.getName(), advisor.getModuleNumber());
        
        return true;
    }

    @Transactional
    public void completeTicketAssignment(Ticket ticket) {
        if (ticket.getAssignedAdvisor() == null) {
            return;
        }

        Advisor advisor = ticket.getAssignedAdvisor();
        
        // Decrementar contador
        advisor.setAssignedTicketsCount(Math.max(0, advisor.getAssignedTicketsCount() - 1));
        
        // Cambiar estado a disponible si tiene capacidad
        if (advisor.getAssignedTicketsCount() < 3 && advisor.getStatus() == AdvisorStatus.BUSY) {
            advisor.setStatus(AdvisorStatus.AVAILABLE);
        }
        
        advisorRepository.save(advisor);
        
        log.info("Completed assignment for advisor {} (current load: {})", 
                advisor.getName(), advisor.getAssignedTicketsCount());
    }

    public long countByStatus(AdvisorStatus status) {
        return advisorRepository.countByStatus(status);
    }
}
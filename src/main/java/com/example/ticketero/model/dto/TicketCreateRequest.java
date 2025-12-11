package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TicketCreateRequest(
    @NotBlank(message = "National ID is required")
    @Size(min = 7, max = 20, message = "National ID must be 7-20 characters")
    String nationalId,

    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Invalid phone format")
    String telefono,

    @NotBlank(message = "Branch office is required")
    @Size(max = 100, message = "Branch office max 100 characters")
    String branchOffice,

    @NotNull(message = "Queue type is required")
    QueueType queueType
) {}
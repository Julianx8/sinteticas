package com.sinteticas.api.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Horario semanal completo. Reemplaza el anterior; una lista vacía deja la cancha sin horario (cerrada).
 */
public record WeeklyScheduleRequest(
        @NotNull @Size(max = 100) List<@Valid @NotNull ScheduleSlotRequest> slots
) {
}

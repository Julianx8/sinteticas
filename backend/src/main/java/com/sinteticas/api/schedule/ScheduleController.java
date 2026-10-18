package com.sinteticas.api.schedule;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.schedule.dto.BlockRequest;
import com.sinteticas.api.schedule.dto.BlockResponse;
import com.sinteticas.api.schedule.dto.ScheduleSlotResponse;
import com.sinteticas.api.schedule.dto.WeeklyScheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courts/{courtId}")
@Tag(name = "Horarios y bloqueos")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final BlockService blockService;
    private final BusinessTime businessTime;

    public ScheduleController(ScheduleService scheduleService, BlockService blockService, BusinessTime businessTime) {
        this.scheduleService = scheduleService;
        this.blockService = blockService;
        this.businessTime = businessTime;
    }

    @GetMapping("/schedules")
    @Operation(summary = "Horario semanal y precios de la cancha (dayOfWeek: 1 = lunes ... 7 = domingo)")
    public List<ScheduleSlotResponse> getSchedule(@PathVariable UUID courtId, @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.getWeekly(courtId, AuthenticatedUser.fromNullable(jwt)).stream()
                .map(ScheduleSlotResponse::from)
                .toList();
    }

    @PutMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Reemplazar el horario semanal completo de la cancha")
    public List<ScheduleSlotResponse> replaceSchedule(AuthenticatedUser currentUser, @PathVariable UUID courtId,
                                                      @Valid @RequestBody WeeklyScheduleRequest request) {
        return scheduleService.replaceWeekly(currentUser, courtId, request.slots()).stream()
                .map(ScheduleSlotResponse::from)
                .toList();
    }

    @GetMapping("/blocks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Bloqueos de la cancha entre dos fechas (inclusive, hora de Colombia)")
    public List<BlockResponse> listBlocks(AuthenticatedUser currentUser, @PathVariable UUID courtId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return blockService.list(currentUser, courtId, businessTime.atHour(from, 0), businessTime.atHour(to, 24))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Bloquear un periodo (no se puede si ya hay reservas en ese periodo)")
    public BlockResponse createBlock(AuthenticatedUser currentUser, @PathVariable UUID courtId,
                                     @Valid @RequestBody BlockRequest request) {
        return toResponse(blockService.create(currentUser, courtId, request));
    }

    @DeleteMapping("/blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Eliminar un bloqueo")
    public void deleteBlock(AuthenticatedUser currentUser, @PathVariable UUID courtId, @PathVariable UUID blockId) {
        blockService.delete(currentUser, courtId, blockId);
    }

    private BlockResponse toResponse(ScheduleBlock block) {
        return new BlockResponse(block.getId(), block.getCourtId(),
                businessTime.toOffset(block.getStartAt()), businessTime.toOffset(block.getEndAt()),
                block.getReason());
    }

}

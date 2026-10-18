package com.sinteticas.api.schedule;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtService;
import com.sinteticas.api.schedule.dto.BlockRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BlockService {

    /** Evita consultas gigantes: los listados de bloqueos abarcan como máximo un año. */
    private static final Duration MAX_QUERY_RANGE = Duration.ofDays(366);

    private final ScheduleBlockRepository blockRepository;
    private final CourtService courtService;
    private final List<BlockGuard> guards;

    public BlockService(ScheduleBlockRepository blockRepository, CourtService courtService, List<BlockGuard> guards) {
        this.blockRepository = blockRepository;
        this.courtService = courtService;
        this.guards = guards;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlock> list(AuthenticatedUser user, UUID courtId, Instant from, Instant to) {
        courtService.getManageable(courtId, user);
        validateRange(from, to);
        if (Duration.between(from, to).compareTo(MAX_QUERY_RANGE) > 0) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "El rango de consulta no puede superar un año");
        }
        return blockRepository.findOverlapping(courtId, from, to);
    }

    @Transactional
    public ScheduleBlock create(AuthenticatedUser user, UUID courtId, BlockRequest request) {
        Court court = courtService.getManageable(courtId, user);
        Instant start = request.startAt().toInstant();
        Instant end = request.endAt().toInstant();
        validateRange(start, end);

        guards.forEach(guard -> guard.beforeBlock(court.getId(), start, end));

        return blockRepository.save(new ScheduleBlock(court.getId(), start, end, request.reason().trim(), user.id()));
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID courtId, UUID blockId) {
        courtService.getManageable(courtId, user);
        ScheduleBlock block = blockRepository.findByIdAndCourtId(blockId, courtId)
                .orElseThrow(() -> ApiException.notFound("BLOCK_NOT_FOUND", "Bloqueo no encontrado"));
        blockRepository.delete(block);
    }

    /** Bloqueos de la cancha que se cruzan con [from, to). Lo usan disponibilidad y reservas. */
    @Transactional(readOnly = true)
    public List<ScheduleBlock> findOverlapping(UUID courtId, Instant from, Instant to) {
        return blockRepository.findOverlapping(courtId, from, to);
    }

    private static void validateRange(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "La fecha final debe ser posterior a la inicial");
        }
    }

    /**
     * Regla que se verifica antes de crear un bloqueo. La implementa el módulo de reservas
     * (no se puede bloquear un horario que ya tiene reservas) sin que este módulo dependa de él.
     */
    public interface BlockGuard {

        void beforeBlock(UUID courtId, Instant start, Instant end);

    }

}

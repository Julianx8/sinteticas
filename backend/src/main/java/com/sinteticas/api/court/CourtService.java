package com.sinteticas.api.court;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.dto.CourtRequest;
import com.sinteticas.api.venue.Venue;
import com.sinteticas.api.venue.VenueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourtService {

    private final CourtRepository courtRepository;
    private final VenueService venueService;

    public CourtService(CourtRepository courtRepository, VenueService venueService) {
        this.courtRepository = courtRepository;
        this.venueService = venueService;
    }

    /** Canchas de una sede: el público ve solo las activas; quien la administra ve todas. */
    @Transactional(readOnly = true)
    public List<Court> listForVenue(UUID venueId, Optional<AuthenticatedUser> viewer) {
        Venue venue = venueService.getVisible(venueId, viewer);
        boolean canManage = viewer.map(venue::canBeManagedBy).orElse(false);
        return canManage
                ? courtRepository.findByVenueIdOrderByName(venueId)
                : courtRepository.findByVenueIdAndActiveTrueOrderByName(venueId);
    }

    @Transactional(readOnly = true)
    public Court getVisible(UUID courtId, Optional<AuthenticatedUser> viewer) {
        Court court = find(courtId);
        boolean canManage = viewer.map(court::canBeManagedBy).orElse(false);
        if (!court.isBookable() && !canManage) {
            throw courtNotFound();
        }
        return court;
    }

    @Transactional(readOnly = true)
    public Court getManageable(UUID courtId, AuthenticatedUser user) {
        Court court = find(courtId);
        if (!court.canBeManagedBy(user)) {
            throw ApiException.forbidden("COURT_NOT_OWNED", "No administras esta cancha");
        }
        return court;
    }

    @Transactional
    public Court create(AuthenticatedUser user, UUID venueId, CourtRequest request) {
        Venue venue = venueService.getManageable(venueId, user);
        ensureUniqueName(venueId, request.name(), null);
        Court court = new Court(venue);
        apply(court, request);
        return courtRepository.saveAndFlush(court);
    }

    @Transactional
    public Court update(AuthenticatedUser user, UUID courtId, CourtRequest request) {
        Court court = getManageable(courtId, user);
        ensureUniqueName(court.getVenue().getId(), request.name(), courtId);
        apply(court, request);
        return court;
    }

    Court find(UUID courtId) {
        return courtRepository.findById(courtId).orElseThrow(CourtService::courtNotFound);
    }

    private void ensureUniqueName(UUID venueId, String name, UUID excludeId) {
        if (courtRepository.existsByVenueAndName(venueId, name.trim(), excludeId)) {
            throw ApiException.conflict("COURT_NAME_TAKEN", "Ya existe una cancha con ese nombre en la sede");
        }
    }

    private void apply(Court court, CourtRequest request) {
        court.setName(request.name());
        court.setCourtType(request.courtType());
        court.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        if (request.active() != null) {
            court.setActive(request.active());
        }
    }

    private static ApiException courtNotFound() {
        return ApiException.notFound("COURT_NOT_FOUND", "Cancha no encontrada");
    }

}

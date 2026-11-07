package com.sinteticas.api.venue;

import com.sinteticas.api.court.CourtRepository;
import com.sinteticas.api.court.CourtType;
import com.sinteticas.api.venue.dto.VenueResponse;
import com.sinteticas.api.venue.dto.VenueResponse.CourtSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Arma las respuestas de sedes con el resumen de sus canchas (tipos y precio mínimo por hora),
 * para que el catálogo muestre "Fútbol 5 · 7 — desde $70.000/h" sin una consulta por sede:
 * son siempre dos consultas para toda la página.
 */
@Service
public class VenueCatalog {

    private final CourtRepository courtRepository;

    public VenueCatalog(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> toResponses(Collection<Venue> venues) {
        Map<UUID, CourtSummary> summaries = summaries(venues.stream().map(Venue::getId).toList());
        return venues.stream()
                .map(venue -> VenueResponse.from(venue, summaries.getOrDefault(venue.getId(), CourtSummary.EMPTY)))
                .toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse toResponse(Venue venue) {
        return toResponses(List.of(venue)).get(0);
    }

    private Map<UUID, CourtSummary> summaries(List<UUID> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TreeSet<CourtType>> types = new HashMap<>();
        for (Object[] row : courtRepository.findActiveTypesByVenue(venueIds)) {
            types.computeIfAbsent((UUID) row[0], id -> new TreeSet<>()).add((CourtType) row[1]);
        }
        Map<UUID, BigDecimal> minPrices = new HashMap<>();
        for (Object[] row : courtRepository.findMinPriceByVenue(venueIds)) {
            minPrices.put((UUID) row[0], (BigDecimal) row[1]);
        }

        Map<UUID, CourtSummary> result = new HashMap<>();
        for (UUID id : venueIds) {
            result.put(id, new CourtSummary(List.copyOf(types.getOrDefault(id, new TreeSet<>())), minPrices.get(id)));
        }
        return result;
    }

}

package com.sinteticas.api.report;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtRepository;
import com.sinteticas.api.report.dto.Metrics;
import com.sinteticas.api.report.dto.VenueDailyReport;
import com.sinteticas.api.report.dto.VenueDemandReport;
import com.sinteticas.api.report.dto.VenueSummaryReport;
import com.sinteticas.api.venue.Venue;
import com.sinteticas.api.venue.VenueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reportes de una sede (solo su dueño o un SUPER_ADMIN).
 */
@Service
public class VenueReportService {

    private static final int PEAK_HOURS = 5;

    private final VenueService venueService;
    private final CourtRepository courtRepository;
    private final ReportRepository reportRepository;
    private final AvailableHoursCalculator availableHours;
    private final BusinessTime time;

    public VenueReportService(VenueService venueService, CourtRepository courtRepository,
                              ReportRepository reportRepository, AvailableHoursCalculator availableHours,
                              BusinessTime time) {
        this.venueService = venueService;
        this.courtRepository = courtRepository;
        this.reportRepository = reportRepository;
        this.availableHours = availableHours;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public VenueSummaryReport summary(AuthenticatedUser user, UUID venueId, LocalDate from, LocalDate to) {
        Venue venue = venueService.getManageable(venueId, user);
        ReportRange range = ReportRange.of(from, to, time);
        List<Court> courts = courtRepository.findByVenueIdOrderByName(venueId);

        Map<UUID, ReservationStats> byCourt = new LinkedHashMap<>();
        for (Court court : courts) {
            ReservationStats stats = new ReservationStats();
            stats.addAvailableHours(availableHours.perDay(court, range).values().stream().mapToLong(Long::longValue).sum());
            byCourt.put(court.getId(), stats);
        }

        ReservationStats totals = new ReservationStats();
        totals.addAvailableHours(0); // una sede sin canchas tiene 0 horas disponibles, no "sin dato"
        for (ReportRepository.CourtStatusRow row : reportRepository.venueByCourtAndStatus(venueId, range.start(), range.end())) {
            byCourt.get(row.getCourtId()).add(row.getStatus(), row.getReservations(), row.getHours(), row.getAmount());
            totals.add(row.getStatus(), row.getReservations(), row.getHours(), row.getAmount());
        }
        byCourt.values().forEach(stats -> totals.addAvailableHours(stats.availableHours()));

        List<VenueSummaryReport.CourtMetrics> courtMetrics = courts.stream()
                .map(c -> new VenueSummaryReport.CourtMetrics(c.getId(), c.getName(), c.isActive(),
                        Metrics.from(byCourt.get(c.getId()))))
                .toList();
        return new VenueSummaryReport(venue.getId(), venue.getName(), range.from(), range.to(),
                Metrics.from(totals), courtMetrics);
    }

    @Transactional(readOnly = true)
    public VenueDailyReport daily(AuthenticatedUser user, UUID venueId, LocalDate from, LocalDate to) {
        venueService.getManageable(venueId, user);
        ReportRange range = ReportRange.of(from, to, time);

        Map<LocalDate, ReservationStats> byDay = new LinkedHashMap<>();
        for (LocalDate date : range.dates()) {
            ReservationStats stats = new ReservationStats();
            stats.addAvailableHours(0);
            byDay.put(date, stats);
        }

        for (Court court : courtRepository.findByVenueIdOrderByName(venueId)) {
            availableHours.perDay(court, range).forEach((date, hours) -> byDay.get(date).addAvailableHours(hours));
        }
        for (ReportRepository.DayStatusRow row : reportRepository.venueByDayAndStatus(
                venueId, range.start(), range.end(), time.zone().getId())) {
            byDay.get(LocalDate.parse(row.getDay()))
                    .add(row.getStatus(), row.getReservations(), row.getHours(), row.getAmount());
        }

        List<VenueDailyReport.DayMetrics> days = byDay.entrySet().stream()
                .map(e -> new VenueDailyReport.DayMetrics(e.getKey(), Metrics.from(e.getValue())))
                .toList();
        return new VenueDailyReport(venueId, range.from(), range.to(), days);
    }

    @Transactional(readOnly = true)
    public VenueDemandReport demand(AuthenticatedUser user, UUID venueId, LocalDate from, LocalDate to) {
        venueService.getManageable(venueId, user);
        ReportRange range = ReportRange.of(from, to, time);

        List<VenueDemandReport.Cell> cells = reportRepository
                .venueDemand(venueId, range.start(), range.end(), time.zone().getId()).stream()
                .map(r -> new VenueDemandReport.Cell(r.getDayOfWeek(), r.getHour(), r.getBookedHours()))
                .toList();
        List<VenueDemandReport.Cell> peak = cells.stream()
                .sorted(Comparator.comparingLong(VenueDemandReport.Cell::bookedHours).reversed()
                        .thenComparingInt(VenueDemandReport.Cell::dayOfWeek)
                        .thenComparingInt(VenueDemandReport.Cell::hour))
                .limit(PEAK_HOURS)
                .toList();
        return new VenueDemandReport(venueId, range.from(), range.to(), cells, peak);
    }

}

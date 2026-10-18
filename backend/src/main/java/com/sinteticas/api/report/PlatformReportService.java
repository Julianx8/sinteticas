package com.sinteticas.api.report;

import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.report.dto.Metrics;
import com.sinteticas.api.report.dto.PlatformSummaryReport;
import com.sinteticas.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resumen de toda la plataforma (solo SUPER_ADMIN).
 */
@Service
public class PlatformReportService {

    private static final int TOP_VENUES = 5;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final BusinessTime time;

    public PlatformReportService(ReportRepository reportRepository, UserRepository userRepository,
                                 BusinessTime time) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PlatformSummaryReport summary(LocalDate from, LocalDate to) {
        ReportRange range = ReportRange.of(from, to, time);

        // Sin ocupación a nivel plataforma: es una métrica por sede (availableHours y occupancyRate se omiten)
        ReservationStats totals = new ReservationStats();
        for (ReportRepository.StatusRow row : reportRepository.platformByStatus(range.start(), range.end())) {
            totals.add(row.getStatus(), row.getReservations(), row.getHours(), row.getAmount());
        }

        Map<String, ReservationStats> byMonth = new TreeMap<>();
        for (ReportRepository.MonthStatusRow row : reportRepository.platformByMonthAndStatus(
                range.start(), range.end(), time.zone().getId())) {
            byMonth.computeIfAbsent(row.getMonth(), m -> new ReservationStats())
                    .add(row.getStatus(), row.getReservations(), BigDecimal.ZERO, row.getAmount());
        }
        List<PlatformSummaryReport.MonthMetrics> months = byMonth.entrySet().stream()
                .map(e -> new PlatformSummaryReport.MonthMetrics(e.getKey(), e.getValue().total(),
                        e.getValue().cancelled(), e.getValue().realizedRevenue(), e.getValue().projectedRevenue()))
                .toList();

        List<PlatformSummaryReport.TopVenue> topVenues = reportRepository
                .platformTopVenues(range.start(), range.end(), TOP_VENUES).stream()
                .map(r -> new PlatformSummaryReport.TopVenue(r.getVenueId(), r.getVenueName(), r.getReservations(),
                        r.getRealizedRevenue()))
                .toList();

        return new PlatformSummaryReport(
                range.from(),
                range.to(),
                userRepository.count(),
                reportRepository.countUsersCreatedBetween(range.start(), range.end()),
                reportRepository.countActiveVenues(),
                reportRepository.countActiveCourts(),
                Metrics.from(totals),
                months,
                topVenues);
    }

}

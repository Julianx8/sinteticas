package com.sinteticas.api.report;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.report.dto.PlatformSummaryReport;
import com.sinteticas.api.report.dto.VenueDailyReport;
import com.sinteticas.api.report.dto.VenueDemandReport;
import com.sinteticas.api.report.dto.VenueSummaryReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes. Fechas {@code from}/{@code to} inclusive, en hora de Colombia, máximo 366 días.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reportes")
public class ReportController {

    private final VenueReportService venueReportService;
    private final PlatformReportService platformReportService;

    public ReportController(VenueReportService venueReportService, PlatformReportService platformReportService) {
        this.venueReportService = venueReportService;
        this.platformReportService = platformReportService;
    }

    @GetMapping("/venues/{venueId}/reports/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Resumen de la sede: reservas, ocupación, ingresos y tasas, total y por cancha")
    public VenueSummaryReport venueSummary(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return venueReportService.summary(currentUser, venueId, from, to);
    }

    @GetMapping("/venues/{venueId}/reports/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Serie diaria de la sede (un punto por día, también los días sin reservas)")
    public VenueDailyReport venueDaily(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return venueReportService.daily(currentUser, venueId, from, to);
    }

    @GetMapping("/venues/{venueId}/reports/demand")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Demanda por día de la semana y hora, con las horas pico")
    public VenueDemandReport venueDemand(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return venueReportService.demand(currentUser, venueId, from, to);
    }

    @GetMapping("/admin/reports/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Resumen de la plataforma: usuarios, sedes, reservas por mes y sedes con más reservas")
    public PlatformSummaryReport platformSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return platformReportService.summary(from, to);
    }

}

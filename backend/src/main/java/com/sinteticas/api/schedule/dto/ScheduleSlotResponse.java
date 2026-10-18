package com.sinteticas.api.schedule.dto;

import com.sinteticas.api.schedule.CourtSchedule;

import java.math.BigDecimal;

public record ScheduleSlotResponse(
        int dayOfWeek,
        int startHour,
        int endHour,
        BigDecimal pricePerHour
) {

    public static ScheduleSlotResponse from(CourtSchedule schedule) {
        return new ScheduleSlotResponse(schedule.getDayOfWeek(), schedule.getStartHour(),
                schedule.getEndHour(), schedule.getPricePerHour());
    }

}

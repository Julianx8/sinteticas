package com.sinteticas.api.holiday;

import com.sinteticas.api.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Festivos")
public class HolidayController {

    @GetMapping
    @Operation(summary = "Festivos de Colombia de un año (público)",
            description = "En un festivo las canchas usan el horario y los precios del domingo")
    public List<ColombianHolidays.Holiday> forYear(@RequestParam int year) {
        if (year < 1984 || year > 2200) {
            throw ApiException.badRequest("INVALID_YEAR", "El año debe estar entre 1984 y 2200");
        }
        return ColombianHolidays.forYear(year);
    }

}

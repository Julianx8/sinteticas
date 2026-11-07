package com.sinteticas.api.notification;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos en español de Colombia para los correos: "martes 21 de octubre de 2026" y "$160.000".
 */
public final class EmailFormat {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_CO);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES_CO);

    private EmailFormat() {
    }

    /** "martes 21 de octubre de 2026, de 19:00 a 21:00" (las horas ya vienen en hora de Colombia). */
    public static String slot(OffsetDateTime start, OffsetDateTime end) {
        return DATE.format(start) + ", de " + TIME.format(start) + " a " + TIME.format(end);
    }

    /** "19:00" */
    public static String time(OffsetDateTime at) {
        return TIME.format(at);
    }

    /** Enlace de Google Maps a la dirección (en Girardot). */
    public static String mapsUrl(String address) {
        return "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(address + ", Girardot, Cundinamarca", StandardCharsets.UTF_8);
    }

    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(ES_CO);
        format.setMaximumFractionDigits(0);
        return "$" + format.format(amount);
    }

}

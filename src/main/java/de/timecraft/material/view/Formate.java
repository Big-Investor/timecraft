package de.timecraft.material.view;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deutsche Anzeige von Betraegen, Mengen, Daten und Stunden (nur Darstellung, keine Rechnung). */
final class Formate {

    private static final DecimalFormatSymbols DEUTSCH = DecimalFormatSymbols.getInstance(Locale.GERMANY);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");

    private Formate() {
    }

    /** z. B. "1.074,00 €" */
    static String euro(BigDecimal betrag) {
        return new DecimalFormat("#,##0.00", DEUTSCH).format(betrag) + " €";
    }

    /** Mit Vorzeichen, z. B. "+3.228,70 €" */
    static String euroMitVorzeichen(BigDecimal betrag) {
        return (betrag.signum() > 0 ? "+" : "") + euro(betrag);
    }

    /** z. B. "12,0 m²" oder "36 Stk." */
    static String menge(BigDecimal menge, Einheit einheit) {
        return zahl(menge, einheit) + " " + einheit.kuerzel();
    }

    /** Menge ohne Einheit: Stueck ganzzahlig, sonst mindestens eine Nachkommastelle. */
    static String zahl(BigDecimal menge, Einheit einheit) {
        String muster = einheit == Einheit.STUECK ? "#,##0.##" : "#,##0.0#";
        return new DecimalFormat(muster, DEUTSCH).format(menge);
    }

    static String datum(Instant zeitpunkt) {
        return DATUM.format(zeitpunkt.atZone(BERLIN));
    }

    static String tagMonat(LocalDateTime zeitpunkt) {
        return TAG_MONAT.format(zeitpunkt);
    }

    /** Minuten als Stunden, z. B. "147:15 h" */
    static String stunden(int minuten) {
        return String.format(Locale.GERMANY, "%d:%02d h", minuten / 60, minuten % 60);
    }
}

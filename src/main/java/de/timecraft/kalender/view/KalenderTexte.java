package de.timecraft.kalender.view;

import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.Locale;

/** Texte fuer die Anzeige im Kalender (nur Formatierung, keine Fachregeln). */
final class KalenderTexte {

    private static final Locale DEUTSCH = Locale.GERMAN;
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATUM_UHRZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");

    private KalenderTexte() {
    }

    /** Minuten als Stunden und Minuten, z. B. 496 → "8:16 h". */
    static String dauer(int minuten) {
        return minuten / 60 + ":" + String.format("%02d", minuten % 60) + " h";
    }

    /** Standort wie im Dokument: Büro, Homeoffice, unterwegs. */
    static String standort(Standort standort) {
        return switch (standort) {
            case BUERO -> "Büro";
            case HOMEOFFICE -> "Homeoffice";
            case UNTERWEGS -> "unterwegs";
        };
    }

    /** Projekt-ID wie in den Mockups, z. B. "P-1024"; ohne Projekt "–". */
    static String projektKennung(Long projektId) {
        return projektId == null ? "–" : "P-" + projektId;
    }

    static String uhrzeit(LocalDateTime zeitpunkt) {
        return UHRZEIT.format(zeitpunkt);
    }

    static String datumUhrzeit(LocalDateTime zeitpunkt) {
        return DATUM_UHRZEIT.format(zeitpunkt) + " Uhr";
    }

    /** Spaltenkopf eines Tages, z. B. "Mo 19.10.". */
    static String tagKopf(LocalDate tag) {
        return wochentagKurz(tag) + " " + TAG_MONAT.format(tag);
    }

    static String wochentagKurz(LocalDate tag) {
        return tag.getDayOfWeek().getDisplayName(TextStyle.SHORT, DEUTSCH).substring(0, 2);
    }

    /**
     * Ueberschrift des Zeitraums: Tag "Donnerstag, 22. Oktober 2026", Woche "KW 43 · 19.–25. Oktober 2026"
     * (ueber Monats- oder Jahresgrenzen mit beiden Monaten bzw. Jahren), Monat "Oktober 2026".
     */
    static String zeitraum(Kalenderansicht ansicht, LocalDate ersterTag, LocalDate letzterTag) {
        return switch (ansicht) {
            case TAG -> ersterTag.getDayOfWeek().getDisplayName(TextStyle.FULL, DEUTSCH) + ", " + datumLang(ersterTag);
            case WOCHE -> "KW " + ersterTag.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " · "
                    + wochenspanne(ersterTag, letzterTag);
            case MONAT -> monat(ersterTag) + " " + ersterTag.getYear();
        };
    }

    private static String wochenspanne(LocalDate von, LocalDate bis) {
        if (von.getYear() != bis.getYear()) {
            return datumLang(von) + " – " + datumLang(bis);
        }
        if (von.getMonth() != bis.getMonth()) {
            return von.getDayOfMonth() + ". " + monat(von) + " – " + datumLang(bis);
        }
        return von.getDayOfMonth() + ".–" + datumLang(bis);
    }

    private static String datumLang(LocalDate tag) {
        return tag.getDayOfMonth() + ". " + monat(tag) + " " + tag.getYear();
    }

    private static String monat(LocalDate tag) {
        return tag.getMonth().getDisplayName(TextStyle.FULL, DEUTSCH);
    }
}

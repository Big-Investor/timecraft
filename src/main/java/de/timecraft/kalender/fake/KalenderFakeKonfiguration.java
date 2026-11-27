package de.timecraft.kalender.fake;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * Beispieldaten fuer Tests (keine Beans: Die laufende Anwendung nutzt M1, M2 und M3).
 * Die Buchungen liegen relativ zum heutigen Tag, damit die aktuelle Woche immer gefuellt ist.
 *
 * @author Bennet Antonio Brinkmeier
 */
public class KalenderFakeKonfiguration {

    static final long JONAS = 1;
    static final long LENA_PL = 2;
    static final long SABINE_ADMIN = 3;
    static final long MEHMET = 4;
    static final long KARL_EHEMALIG = 7;
    static final long MIA = 6;
    static final long OTTO_PL = 5;

    static final long KUECHE = 1;
    static final long TREPPE = 2;
    static final long DACHSTUHL = 3;
    static final long ALTBAU_ARCHIV = 5;

    private static final int WOCHEN_ZURUECK = 5;
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Keine Bean: M1 liefert die echte Auskunft, nur fuer Tests. */
    public FakeMitarbeiterAuskunft fakeMitarbeiterAuskunft() {
        FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
        personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, true);
        personen.hinzufuegen(LENA_PL, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true);
        personen.hinzufuegen(SABINE_ADMIN, "Sabine", "Hartmann", Rolle.ADMIN, true);
        personen.hinzufuegen(MEHMET, "Mehmet", "Yilmaz", Rolle.MITARBEITER, true);
        personen.hinzufuegen(KARL_EHEMALIG, "Karl", "Engel", Rolle.MITARBEITER, false);
        personen.hinzufuegen(MIA, "Mia", "Fischer", Rolle.MITARBEITER, true);
        personen.hinzufuegen(OTTO_PL, "Otto", "Graf", Rolle.PROJEKTLEITUNG, true);
        return personen;
    }

    /** Keine Bean: M3 liefert die echte Auskunft, nur fuer Tests. */
    public FakeProjektAuskunft fakeProjektAuskunft() {
        FakeProjektAuskunft projekte = new FakeProjektAuskunft();
        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", LENA_PL, true), JONAS, MEHMET, KARL_EHEMALIG, LENA_PL);
        projekte.hinzufuegen(projekt(TREPPE, "Treppe Lindenstraße", LENA_PL, true), JONAS, MEHMET);
        projekte.hinzufuegen(projekt(DACHSTUHL, "Dachstuhl Meier", OTTO_PL, true), JONAS, MIA, OTTO_PL);
        projekte.hinzufuegen(projekt(ALTBAU_ARCHIV, "Altbau Sanierung", LENA_PL, false), JONAS, MEHMET);
        return projekte;
    }

    /** Keine Bean: M2 liefert die echte Auskunft, nur fuer Tests. */
    public FakeBuchungsAuskunft fakeBuchungsAuskunft(Clock clock) {
        return new Demodaten(clock).buchungen();
    }

    private static ProjektInfo projekt(long id, String name, long projektleitung, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, projektleitung, aktiv, BigDecimal.ZERO);
    }

    /** Erzeugt die Beispielbuchungen der letzten Wochen bis heute. */
    private static final class Demodaten {

        private final Clock clock;
        private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
        private long naechsteId = 1;

        Demodaten(Clock clock) {
            this.clock = clock;
        }

        FakeBuchungsAuskunft buchungen() {
            LocalDate heute = LocalDate.now(clock);
            LocalDate start = heute.with(DayOfWeek.MONDAY).minusWeeks(WOCHEN_ZURUECK);
            LocalDate geaenderterTag = heute.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
            for (LocalDate tag = start; tag.isBefore(heute); tag = tag.plusDays(1)) {
                if (tag.getDayOfWeek().getValue() <= 5) {
                    boolean frueher = tag.isBefore(heute.minusWeeks(3));
                    arbeitstag(tag, frueher, tag.equals(geaenderterTag));
                }
            }
            nachtUndZeitumstellung(heute);
            laufendeBuchung(heute);
            return buchungen;
        }

        /** Jonas: Mo und Fr Kueche, Di Treppe (frueher Altbau), Mi Kueche, Do Dachstuhl und Treppe. */
        private void arbeitstag(LocalDate tag, boolean frueher, boolean geaendert) {
            long treppeOderArchiv = frueher ? ALTBAU_ARCHIV : TREPPE;
            switch (tag.getDayOfWeek()) {
                case TUESDAY -> {
                    long id = buchung(JONAS, treppeOderArchiv, Standort.BUERO, tag, "07:15", tag, "16:30", 45, geaendert);
                    if (geaendert) {
                        String datum = tag.format(DATUM);
                        buchungen.historieHinzufuegen(id, new AenderungInfo(
                                LocalDateTime.of(tag, LocalTime.of(17, 5)).atZone(clock.getZone()).toInstant(),
                                LENA_PL, AenderungsArt.AENDERUNG, "Ende", datum + " 17:30", datum + " 16:30",
                                "Ausstempeln vergessen, laut Baustellenbericht um 16:30 Feierabend"));
                    }
                }
                case WEDNESDAY -> buchung(JONAS, KUECHE, Standort.UNTERWEGS, tag, "06:55", tag, "15:40", 45, false);
                case THURSDAY -> {
                    buchung(JONAS, DACHSTUHL, Standort.HOMEOFFICE, tag, "08:00", tag, "12:00", 0, false);
                    buchung(JONAS, TREPPE, Standort.BUERO, tag, "12:30", tag, "16:00", 0, false);
                }
                default -> buchung(JONAS, KUECHE, Standort.UNTERWEGS, tag, "07:00", tag, "15:45", 45, false);
            }
            buchung(LENA_PL, KUECHE, Standort.BUERO, tag, "08:00", tag, "16:00", 30, false);
            buchung(MEHMET, tag.getDayOfWeek().getValue() % 2 == 1 ? KUECHE : treppeOderArchiv, Standort.UNTERWEGS,
                    tag, "07:30", tag, "16:00", 30, false);
            buchung(MIA, DACHSTUHL, Standort.UNTERWEGS, tag, "07:00", tag, "15:30", 30, false);
            buchung(OTTO_PL, DACHSTUHL, Standort.BUERO, tag, "08:00", tag, "16:30", 30, false);
            buchung(SABINE_ADMIN, null, Standort.BUERO, tag, "09:00", tag, "17:00", 30, false);
            if (frueher) {
                buchung(KARL_EHEMALIG, KUECHE, Standort.UNTERWEGS, tag, "07:00", tag, "15:00", 30, false);
            }
        }

        /** Buchung ueber Mitternacht (Freitag der Vorwoche) und durch die Umstellungsnacht im Oktober. */
        private void nachtUndZeitumstellung(LocalDate heute) {
            LocalDate freitag = heute.with(DayOfWeek.MONDAY).minusDays(3);
            buchung(JONAS, KUECHE, Standort.UNTERWEGS, freitag, "20:00", freitag.plusDays(1), "01:30", 0, false);
            LocalDate umstellung = LocalDate.of(heute.getYear(), 10, 31)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            if (umstellung.isBefore(heute)) {
                buchung(MEHMET, KUECHE, Standort.UNTERWEGS, umstellung.minusDays(1), "22:00", umstellung, "06:00", 0,
                        false);
            }
        }

        /** Jonas ist heute seit 07:02 eingestempelt (frueh am Morgen: seit wenigen Minuten). */
        private void laufendeBuchung(LocalDate heute) {
            Instant jetzt = clock.instant();
            Instant beginn = LocalDateTime.of(heute, LocalTime.of(7, 2)).atZone(clock.getZone()).toInstant();
            if (!beginn.isBefore(jetzt)) {
                beginn = jetzt.minus(Duration.ofMinutes(5));
            }
            buchungen.hinzufuegen(new BuchungInfo(naechsteId++, JONAS, KUECHE, Standort.UNTERWEGS, beginn, null,
                    0, 0, false));
        }

        private long buchung(long person, Long projektId, Standort standort, LocalDate vonTag, String von,
                LocalDate bisTag, String bis, int pause, boolean geaendert) {
            Instant beginn = LocalDateTime.of(vonTag, LocalTime.parse(von)).atZone(clock.getZone()).toInstant();
            Instant ende = LocalDateTime.of(bisTag, LocalTime.parse(bis)).atZone(clock.getZone()).toInstant();
            int netto = (int) Duration.between(beginn, ende).toMinutes() - pause;
            long id = naechsteId++;
            buchungen.hinzufuegen(new BuchungInfo(id, person, projektId, standort, beginn, ende, pause, netto,
                    geaendert));
            return id;
        }
    }
}

package de.timecraft.antraege;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Stornoantrag;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Testdaten fuer M5. Die feste Uhr steht auf Freitag, 27.11.2026, 09:00 Uhr in Berlin. */
public final class Beispiele {

    public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    public static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-11-27T08:00:00Z"), BERLIN);
    public static final Instant EINGEREICHT = Instant.parse("2026-11-20T07:00:00Z");

    private Beispiele() {
    }

    public static Instant zeit(LocalDate tag, int stunde, int minute) {
        return tag.atTime(LocalTime.of(stunde, minute)).atZone(BERLIN).toInstant();
    }

    public static Abwesenheitsantrag abwesenheit(long mitarbeiterId, AbwesenheitsArt art, LocalDate von, LocalDate bis) {
        Abwesenheitsantrag antrag = new Abwesenheitsantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setArt(art);
        antrag.setVon(von);
        antrag.setBis(bis);
        return antrag;
    }

    public static Abwesenheitsantrag urlaub(long mitarbeiterId, Urlaubsart urlaubsart, LocalDate von, LocalDate bis) {
        Abwesenheitsantrag antrag = abwesenheit(mitarbeiterId, AbwesenheitsArt.URLAUB, von, bis);
        antrag.setUrlaubsart(urlaubsart);
        return antrag;
    }

    /** Abgeschlossene Buchung mit 30 Minuten Pause. */
    public static BuchungInfo buchung(long id, long mitarbeiterId, LocalDate tag, LocalTime beginn, LocalTime ende,
            Long projektId) {
        Instant start = tag.atTime(beginn).atZone(BERLIN).toInstant();
        Instant schluss = tag.atTime(ende).atZone(BERLIN).toInstant();
        int netto = (int) Duration.between(start, schluss).toMinutes() - 30;
        return new BuchungInfo(id, mitarbeiterId, projektId, Standort.BUERO, start, schluss, 30, netto, false);
    }

    /** Zeitkorrektur mit den Werten der Buchung als alte Werte und neuem Ende. */
    public static Zeitkorrekturantrag zeitkorrektur(BuchungInfo buchung, Instant neuesEnde) {
        Zeitkorrekturantrag antrag = new Zeitkorrekturantrag();
        antrag.setAntragstellerId(buchung.mitarbeiterId());
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setZeiteintragId(buchung.zeiteintragId());
        antrag.setAlterBeginn(buchung.beginn());
        antrag.setAltesEnde(buchung.ende());
        antrag.setAlterStandort(buchung.standort());
        antrag.setNeuerBeginn(buchung.beginn());
        antrag.setNeuesEnde(neuesEnde);
        antrag.setNeuerStandort(buchung.standort());
        antrag.setNeuesProjektId(buchung.projektId());
        return antrag;
    }

    public static Stornoantrag storno(long mitarbeiterId, Long bezugsantragId, String grund) {
        Stornoantrag antrag = new Stornoantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setBezugsantragId(bezugsantragId);
        antrag.setBegruendung(grund);
        return antrag;
    }

    public static <T extends Antrag> T mitStatus(T antrag, Antragsstatus status) {
        antrag.setStatus(status);
        return antrag;
    }
}

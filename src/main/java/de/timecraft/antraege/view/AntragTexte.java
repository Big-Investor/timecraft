package de.timecraft.antraege.view;

import com.vaadin.flow.component.html.Span;
import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.service.AntragAnsicht;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Deutsche Anzeigetexte und Status-Kennzeichen fuer die Views von M5 (nur Darstellung, keine Fachregeln). */
final class AntragTexte {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");
    static final DateTimeFormatter WOCHENTAG_DATUM = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", java.util.Locale.GERMAN);
    static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DATUM_UHRZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private AntragTexte() {
    }

    /** Typ fuer die Auswahl im Formular; Storno steht dort fuer "Aenderung / Storno". */
    static String auswahlname(Antragstyp typ) {
        return typ == Antragstyp.STORNO ? "Änderung / Storno" : typ.bezeichnung();
    }

    /** Kurztitel wie "Urlaub · 26.–30.10." oder "Zeitkorrektur · 19.10.". */
    static String titel(AntragAnsicht ansicht) {
        String typ = ansicht.typ().bezeichnung();
        if (ansicht.antrag().getBezugsantragId() != null && ansicht.typ() != Antragstyp.STORNO) {
            typ += " (Änderung)";
        }
        String zeitraum = zeitraum(ansicht.antrag(), ansicht.bezugsantrag());
        return zeitraum.isEmpty() ? typ : typ + " · " + zeitraum;
    }

    static String zeitraum(Antrag antrag, Antrag bezug) {
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            return zeitraum(abwesenheit.getVon(), abwesenheit.getBis());
        }
        if (antrag instanceof Zeitkorrekturantrag korrektur) {
            return tag(korrektur.getAlterBeginn()).format(TAG_MONAT);
        }
        return bezug == null ? "" : zeitraum(bezug, null);
    }

    /** Wichtigste Aenderung einer Zeitkorrektur, z. B. "Ende 15:15 → 15:45" (Mockup "Meine Anträge"). */
    static String korrektur(Zeitkorrekturantrag korrektur) {
        if (!korrektur.getNeuesEnde().equals(korrektur.getAltesEnde())) {
            return "Ende " + uhrzeit(korrektur.getAltesEnde()) + " → " + uhrzeit(korrektur.getNeuesEnde());
        }
        if (!korrektur.getNeuerBeginn().equals(korrektur.getAlterBeginn())) {
            return "Beginn " + uhrzeit(korrektur.getAlterBeginn()) + " → " + uhrzeit(korrektur.getNeuerBeginn());
        }
        if (korrektur.getNeuerStandort() != korrektur.getAlterStandort()) {
            return "Standort " + standort(korrektur.getAlterStandort()) + " → " + standort(korrektur.getNeuerStandort());
        }
        return "Projekt geändert";
    }

    static String zeitraum(LocalDate von, LocalDate bis) {
        if (von.equals(bis)) {
            return von.format(TAG_MONAT);
        }
        if (von.getMonth() == bis.getMonth() && von.getYear() == bis.getYear()) {
            return String.format("%02d.–", von.getDayOfMonth()) + bis.format(TAG_MONAT);
        }
        return von.format(TAG_MONAT) + "–" + bis.format(TAG_MONAT);
    }

    /** Status als farbiges Kennzeichen (Lumo-Badge). Fuer den Antragsteller heisst OFFEN "beantragt". */
    static Span statusKennzeichen(AntragAnsicht ansicht, boolean sichtDesAntragstellers) {
        Antragsstatus status = ansicht.antrag().getStatus();
        Span kennzeichen = new Span(statustext(ansicht, sichtDesAntragstellers));
        kennzeichen.getElement().getThemeList().add("badge");
        switch (status) {
            case OFFEN -> kennzeichen.getElement().getThemeList().add("warning");
            case GENEHMIGT -> kennzeichen.getElement().getThemeList().add("success");
            case ABGELEHNT -> kennzeichen.getElement().getThemeList().add("error");
            case ZURUECKGEZOGEN -> kennzeichen.getElement().getThemeList().add("contrast");
        }
        return kennzeichen;
    }

    static String statustext(AntragAnsicht ansicht, boolean sichtDesAntragstellers) {
        return switch (ansicht.antrag().getStatus()) {
            case OFFEN -> sichtDesAntragstellers ? "beantragt" : "offen";
            case GENEHMIGT -> ansicht.typ() == Antragstyp.KRANKHEIT ? "bestätigt" : "genehmigt";
            case ABGELEHNT -> "abgelehnt";
            case ZURUECKGEZOGEN -> "zurückgezogen";
        };
    }

    static String standort(Standort standort) {
        if (standort == null) {
            return "–";
        }
        return switch (standort) {
            case BUERO -> "Büro";
            case HOMEOFFICE -> "Homeoffice";
            case UNTERWEGS -> "Unterwegs";
        };
    }

    static String dauer(Integer minuten) {
        if (minuten == null) {
            return "–";
        }
        return minuten / 60 + ":" + String.format("%02d", Math.abs(minuten % 60));
    }

    static LocalDate tag(Instant zeitpunkt) {
        return LocalDate.ofInstant(zeitpunkt, ZONE);
    }

    static String uhrzeit(Instant zeitpunkt) {
        return zeitpunkt.atZone(ZONE).format(UHRZEIT);
    }

    static String datumUhrzeit(Instant zeitpunkt) {
        return zeitpunkt.atZone(ZONE).format(DATUM_UHRZEIT);
    }
}

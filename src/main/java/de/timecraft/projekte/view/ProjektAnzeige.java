package de.timecraft.projekte.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Texte und kleine Anzeigeelemente für Projekte und Team, damit Liste, Detailfenster und Team-Reiter
 * dasselbe zeigen: Projekt-ID wie im Mockup ("P-1024"), Datum als TT.MM.JJJJ, Status als farbige Marke.
 * Reine Darstellung, keine Fachregeln.
 *
 * @author Tim Stukenbröker
 */
final class ProjektAnzeige {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");

    private ProjektAnzeige() {
    }

    static String id(long projektId) {
        return "P-" + projektId;
    }

    static String datum(LocalDate datum) {
        return datum == null ? "" : DATUM.format(datum);
    }

    /** Zeitraum wie im Mockup: im selben Jahr "05.10. – 13.11.2026", sonst mit beiden Jahren. */
    static String zeitraum(LocalDate start, LocalDate ziel) {
        if (start.getYear() == ziel.getYear()) {
            return TAG_MONAT.format(start) + " – " + DATUM.format(ziel);
        }
        return DATUM.format(start) + " – " + DATUM.format(ziel);
    }

    /** Kleingeschriebener Statustext für die Marke in der Liste ("aktiv", "archiviert"). */
    static String status(ProjektStatus status) {
        return status == ProjektStatus.AKTIV ? "aktiv" : "archiviert";
    }

    /** Grüne Marke für aktiv, graue für archiviert. */
    static Span statusMarke(ProjektStatus status) {
        return marke(status(status), status == ProjektStatus.AKTIV);
    }

    /** Grüne Marke "eingestempelt" oder graue Marke "nicht eingestempelt" im Reiter Team. */
    static Span eingestempeltMarke(boolean eingestempelt) {
        return marke(eingestempelt ? "eingestempelt" : "nicht eingestempelt", eingestempelt);
    }

    private static Span marke(String text, boolean gruen) {
        Span marke = new Span(text);
        marke.getElement().getThemeList().add("badge");
        marke.getElement().getThemeList().add(gruen ? "success" : "contrast");
        return marke;
    }

    static String standort(Standort standort) {
        return switch (standort) {
            case BUERO -> "Büro";
            case HOMEOFFICE -> "Homeoffice";
            case UNTERWEGS -> "unterwegs";
        };
    }

    /** Uhrzeit "HH:mm" eines Zeitpunkts in der Zeitzone der Anwendung (Europe/Berlin aus der Uhr des Gerüsts). */
    static String uhrzeit(Instant zeitpunkt, ZoneId zeitzone) {
        return UHRZEIT.format(zeitpunkt.atZone(zeitzone));
    }

    /** Adresse des Detailfensters eines Projekts, z. B. "projekt/5". */
    static String detailRoute(long projektId) {
        return "projekt/" + projektId;
    }

    /** Ein Link auf eine Seite der Anwendung, der ohne Neuladen der Seite navigiert (wie ein RouterLink). */
    static Anchor link(String route, String text) {
        Anchor link = new Anchor(route, text);
        link.getElement().setAttribute("router-link", true);
        return link;
    }

    /** Fette Überschrift über einem Filterfeld, wie die Beschriftung des Feldes Projektname. */
    static Div feldMitTitel(String ueberschrift, Component feld) {
        Span beschriftung = new Span(ueberschrift);
        beschriftung.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "600");
        return new Div(beschriftung, feld);
    }
}

package de.timecraft.projekte.view;

import com.vaadin.flow.component.html.Span;
import de.timecraft.projekte.model.ProjektStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Texte und kleine Anzeigeelemente für Projekte, damit Liste und Detailfenster dasselbe zeigen:
 * Projekt-ID wie im Mockup ("P-1024"), Datum als TT.MM.JJJJ und der Status als farbige Marke.
 * Reine Darstellung, keine Fachregeln.
 */
final class ProjektAnzeige {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ProjektAnzeige() {
    }

    static String id(long projektId) {
        return "P-" + projektId;
    }

    static String datum(LocalDate datum) {
        return datum == null ? "" : DATUM.format(datum);
    }

    /** Kleingeschriebener Statustext für die Marke in der Liste ("aktiv", "archiviert"). */
    static String status(ProjektStatus status) {
        return status == ProjektStatus.AKTIV ? "aktiv" : "archiviert";
    }

    /** Grüne Marke für aktiv, graue für archiviert. */
    static Span statusMarke(ProjektStatus status) {
        Span marke = new Span(status(status));
        marke.getElement().getThemeList().add("badge");
        marke.getElement().getThemeList().add(status == ProjektStatus.AKTIV ? "success" : "contrast");
        return marke;
    }
}

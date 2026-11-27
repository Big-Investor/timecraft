package de.timecraft.benutzer.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Dunkler Bereich links auf der Anmeldeseite (Mockup „M1 · Anmeldung“): Logo, Leitsatz und Fußzeile.
 * Die Seite „Eigenes Passwort festlegen“ benutzt ihn auch, damit beide Seiten gleich aussehen.
 * Die Gestaltung kommt aus dem gemeinsamen Theme (Klassen {@code login-...}).
 *
 * @author Leon-Selim Kannenberg
 */
class Markenbereich extends Div {

    Markenbereich() {
        addClassName("login-marke");

        Span logo = new Span(VaadinIcon.CLOCK.create(), new Span("TimeCraft"));
        logo.addClassName("login-logo");

        H1 titel = new H1("Zeiterfassung für die Tischlerei.");
        Paragraph untertitel = new Paragraph(
                "Stempeln, Projekte buchen, Urlaub beantragen – in der Werkstatt am Tablet oder im Büro.");
        Div titelbereich = new Div(titel, untertitel);
        titelbereich.addClassName("login-titel");

        Span fusszeile = new Span("Tischlerei Hartmann · Demo-Umgebung");
        fusszeile.addClassName("login-fusszeile");

        add(logo, titelbereich, fusszeile);
    }
}

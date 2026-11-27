package de.timecraft.zeiterfassung.view;

import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Kleine Bausteine, die mehrere Seiten von M2 gleich aussehen lassen. Klassen: styles/zeiterfassung.css.
 *
 * @author Noah Cipa
 */
final class Bausteine {

    enum Farbe { GRUEN, ORANGE, BLAU, GRAU }

    private Bausteine() {
        // Nur statische Methoden
    }

    static Div karte(Component... inhalt) {
        Div karte = new Div(inhalt);
        karte.addClassNames("karte", "m2-karte");
        return karte;
    }

    /** Kachel mit Beschriftung und grossem Wert. */
    static Div kachel(String beschriftung, Span wert) {
        wert.addClassName("m2-kachelwert");
        return karte(text(beschriftung, "m2-kacheltitel"), wert);
    }

    static Div bereich(String klasse, Component... inhalt) {
        Div bereich = new Div(inhalt);
        bereich.addClassName(klasse);
        return bereich;
    }

    static Span text(String text, String... klassen) {
        Span span = new Span(text);
        span.addClassNames(klassen);
        return span;
    }

    static Span titel(String text) {
        return text(text, "m2-titel");
    }

    static Span gedaempft(String text) {
        return text(text, "m2-status-seit");
    }

    static Span fett(String text) {
        return text(text, "m2-fett");
    }

    static Span schild(String text, Farbe farbe) {
        return text(text, "m2-schild", "m2-" + farbe.name().toLowerCase(Locale.ROOT));
    }
}

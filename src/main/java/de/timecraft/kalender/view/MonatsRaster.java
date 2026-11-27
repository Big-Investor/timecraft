package de.timecraft.kalender.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import de.timecraft.kalender.model.KalenderInhalt;
import java.time.LocalDate;

/**
 * Monatsansicht: je Tag nur die Summe der gebuchten Stunden (FA-M6-03), Wochen von Montag bis Sonntag.
 * Gestaltet wie die Wochenansicht (kein eigenes Mockup, Entscheidung Bennet, 2026-11-18).
 *
 * @author Bennet Antonio Brinkmeier
 */
class MonatsRaster extends Div {

    private static final String RAHMEN = "1px solid #e3e7ec";

    MonatsRaster(KalenderInhalt inhalt, LocalDate heute) {
        getStyle().set("display", "grid").set("grid-template-columns", "repeat(7, 1fr)").set("background", "#ffffff")
                .set("border", RAHMEN).set("border-radius", "0.75rem").set("overflow", "hidden");
        LocalDate montag = inhalt.ersterTag().minusDays(inhalt.ersterTag().getDayOfWeek().getValue() - 1L);
        for (int i = 0; i < 7; i++) {
            Div kopf = new Div(new Span(KalenderTexte.wochentagKurz(montag.plusDays(i))));
            kopf.getStyle().set("padding", "0.75rem").set("font-weight", "700").set("font-size", "0.875rem")
                    .set("border-bottom", RAHMEN);
            add(kopf);
        }
        LocalDate sonntag = inhalt.letzterTag().plusDays(7L - inhalt.letzterTag().getDayOfWeek().getValue());
        for (LocalDate tag = montag; !tag.isAfter(sonntag); tag = tag.plusDays(1)) {
            add(zelle(inhalt, tag, heute));
        }
    }

    private static Div zelle(KalenderInhalt inhalt, LocalDate tag, LocalDate heute) {
        Div zelle = new Div();
        zelle.getStyle().set("min-height", "4.5rem").set("padding", "0.5rem 0.75rem").set("font-size", "0.875rem")
                .set("border-bottom", RAHMEN).set("border-left", RAHMEN);
        Integer summe = inhalt.tagessummen().get(tag);
        if (summe == null) {
            zelle.getStyle().set("background", "#f9fafb");
            return zelle;
        }
        if (tag.equals(heute)) {
            zelle.getStyle().set("background", "#eaf0f9");
        }
        Span nummer = new Span(String.valueOf(tag.getDayOfMonth()));
        nummer.getStyle().set("display", "block").set("font-weight", "700");
        zelle.add(nummer);
        if (summe > 0) {
            boolean laeuft = inhalt.eintraege().stream().anyMatch(e -> e.tag().equals(tag) && e.laeuft());
            Span stunden = new Span(KalenderTexte.dauer(summe) + (laeuft ? " (läuft)" : ""));
            stunden.getStyle().set("color", "var(--tc-text-gedaempft)");
            zelle.add(stunden);
        }
        return zelle;
    }
}

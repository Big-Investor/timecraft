package de.timecraft.kalender.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import de.timecraft.kalender.model.KalenderEintrag;
import de.timecraft.kalender.model.KalenderInhalt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Tages- und Wochenansicht nach dem Mockup „Kalender – Wochenansicht“: Spalte je Tag mit Kopf
 * (Datum, Tagessumme), Stundenleiste links, jede Buchung als Block auf ihrer Uhrzeit (FA-M6-03, 04).
 * Gestaltung per Inline-Styles (Entscheidung Bennet, 2026-11-18).
 */
class KalenderRaster extends Div {

    private static final int PIXEL_JE_STUNDE = 40;
    private static final int MIN_BLOCKHOEHE = 22;
    private static final int ERSTE_STUNDE = 6;
    private static final int LETZTE_STUNDE = 18;
    private static final String RAHMEN = "1px solid #e3e7ec";
    private static final String BLAU = "#1f5fae";
    private static final String HEUTE_KOPF = "#eaf0f9";
    private static final String HEUTE_SPALTE = "#f5f8fd";

    private final int vonStunde;
    private final int bisStunde;
    private final LongConsumer beiKlick;

    /** beiKlick bekommt die Buchungs-ID eines angeklickten Blocks (FA-M6-05). */
    KalenderRaster(KalenderInhalt inhalt, LocalDate heute, LongConsumer beiKlick) {
        this.beiKlick = beiKlick;
        this.vonStunde = Math.min(ERSTE_STUNDE, inhalt.eintraege().stream()
                .mapToInt(e -> minuteImTag(e.tag(), e.beginn()) / 60).min().orElse(ERSTE_STUNDE));
        this.bisStunde = Math.max(LETZTE_STUNDE, inhalt.eintraege().stream()
                .mapToInt(e -> (minuteImTag(e.tag(), e.ende()) + 59) / 60).max().orElse(LETZTE_STUNDE));
        getStyle().set("background", "#ffffff").set("border", RAHMEN).set("border-radius", "0.75rem")
                .set("overflow", "hidden");
        List<LocalDate> tage = inhalt.ersterTag().datesUntil(inhalt.letzterTag().plusDays(1)).toList();
        add(kopfzeile(inhalt, tage, heute), koerper(inhalt, tage, heute));
    }

    private Div kopfzeile(KalenderInhalt inhalt, List<LocalDate> tage, LocalDate heute) {
        Div zeile = zeile();
        zeile.getStyle().set("border-bottom", RAHMEN);
        zeile.add(stundenleisteBreite(new Div()));
        for (LocalDate tag : tage) {
            boolean laeuft = inhalt.eintraege().stream().anyMatch(e -> e.tag().equals(tag) && e.laeuft());
            Span datum = new Span(KalenderTexte.tagKopf(tag));
            datum.getStyle().set("font-weight", "700").set("display", "block");
            Span summe = new Span(KalenderTexte.dauer(inhalt.tagessummen().get(tag)) + (laeuft ? " (läuft)" : ""));
            summe.getStyle().set("color", "var(--tc-text-gedaempft)").set("font-size", "0.875rem");
            Div kopf = new Div(datum, summe);
            kopf.getStyle().set("flex", "1").set("padding", "0.75rem").set("border-left", RAHMEN)
                    .set("font-size", "0.875rem");
            if (tag.equals(heute)) {
                kopf.getStyle().set("background", HEUTE_KOPF);
            }
            zeile.add(kopf);
        }
        return zeile;
    }

    private Div koerper(KalenderInhalt inhalt, List<LocalDate> tage, LocalDate heute) {
        Div zeile = zeile();
        zeile.add(stundenleiste());
        for (LocalDate tag : tage) {
            zeile.add(tagesspalte(tag, inhalt.eintraege().stream().filter(e -> e.tag().equals(tag)).toList(),
                    tag.equals(heute)));
        }
        return zeile;
    }

    private Div stundenleiste() {
        Div leiste = stundenleisteBreite(new Div());
        leiste.getStyle().set("position", "relative").set("height", hoehe() + "px");
        for (int stunde = vonStunde; stunde < bisStunde; stunde += 2) {
            Span text = new Span(String.format("%02d", stunde));
            text.getStyle().set("position", "absolute").set("top", ((stunde - vonStunde) * PIXEL_JE_STUNDE + 4) + "px")
                    .set("right", "0.75rem").set("font-size", "0.75rem").set("color", "var(--tc-text-gedaempft)");
            leiste.add(text);
        }
        return leiste;
    }

    private Div tagesspalte(LocalDate tag, List<KalenderEintrag> eintraege, boolean heute) {
        Div spalte = new Div();
        spalte.getStyle().set("flex", "1").set("position", "relative").set("height", hoehe() + "px")
                .set("border-left", RAHMEN);
        if (heute) {
            spalte.getStyle().set("background", HEUTE_SPALTE);
        }
        for (KalenderEintrag eintrag : eintraege) {
            spalte.add(block(eintrag));
            if (eintrag.laeuft() && !eintrag.ende().toLocalDate().isAfter(tag)) {
                spalte.add(jetztLinie(position(tag, eintrag.ende())));
            }
        }
        return spalte;
    }

    /** Ein Block mit Projektname, Projekt-ID, Standort, Beginn und Ende bzw. „läuft“ und „geändert“. */
    private Div block(KalenderEintrag eintrag) {
        Span projekt = new Span(eintrag.projektName());
        projekt.getStyle().set("font-weight", "700").set("display", "block");
        Span kennung = new Span(KalenderTexte.projektKennung(eintrag.projektId()) + " · "
                + KalenderTexte.standort(eintrag.standort()));
        kennung.getStyle().set("display", "block");
        Div block = new Div(projekt, kennung, zeiten(eintrag));
        if (eintrag.geaendert()) {
            Span marke = new Span("geändert");
            marke.getStyle().set("display", "inline-block").set("margin-top", "0.375rem").set("padding", "0 0.375rem")
                    .set("background", "#ffffff").set("border-radius", "0.25rem").set("font-weight", "700")
                    .set("color", "#6b3000");
            block.add(marke);
        }
        int oben = position(eintrag.tag(), eintrag.beginn());
        int hoehe = Math.max(MIN_BLOCKHOEHE, position(eintrag.tag(), eintrag.ende()) - oben);
        block.getStyle().set("position", "absolute").set("top", oben + "px").set("height", hoehe + "px")
                .set("left", "0.375rem").set("right", "0.375rem").set("box-sizing", "border-box")
                .set("padding", "0.375rem 0.5rem").set("border-radius", "0.25rem").set("overflow", "hidden")
                .set("font-size", "0.8125rem").set("line-height", "1.4").set("cursor", "pointer");
        farben(block, eintrag);
        block.getElement().setAttribute("title", "Details anzeigen");
        block.addClickListener(klick -> beiKlick.accept(eintrag.zeiteintragId()));
        return block;
    }

    private static Span zeiten(KalenderEintrag eintrag) {
        Span zeiten = new Span();
        if (eintrag.laeuft()) {
            Span laeuft = new Span("läuft");
            laeuft.getStyle().set("font-weight", "700");
            zeiten.add(new Span("seit " + KalenderTexte.uhrzeit(eintrag.beginn()) + " · "), laeuft);
        } else {
            zeiten.setText(KalenderTexte.uhrzeit(eintrag.beginn()) + " – " + KalenderTexte.uhrzeit(eintrag.ende()));
        }
        zeiten.getStyle().set("display", "block");
        return zeiten;
    }

    private static void farben(Div block, KalenderEintrag eintrag) {
        if (eintrag.laeuft()) {
            block.getStyle().set("background", "#dde6f3").set("border", "2px dashed " + BLAU);
        } else if (eintrag.geaendert()) {
            block.getStyle().set("background", "#f3e2d3").set("border-left", "4px solid #c0712f");
        } else {
            block.getStyle().set("background", "#dde6f3").set("border-left", "4px solid " + BLAU);
        }
    }

    private static Div jetztLinie(int oben) {
        Div linie = new Div();
        linie.getStyle().set("position", "absolute").set("top", oben + "px").set("left", "0").set("right", "0")
                .set("border-top", "2px solid #c62828");
        return linie;
    }

    /** Pixel ab oberem Rand fuer eine Uhrzeit des Tages. */
    private int position(LocalDate tag, LocalDateTime zeitpunkt) {
        return (minuteImTag(tag, zeitpunkt) - vonStunde * 60) * PIXEL_JE_STUNDE / 60;
    }

    /** Minute des Tages; ein Ende um Mitternacht des Folgetags zaehlt als 24:00. */
    private static int minuteImTag(LocalDate tag, LocalDateTime zeitpunkt) {
        if (zeitpunkt.toLocalDate().isAfter(tag)) {
            return 24 * 60;
        }
        return zeitpunkt.getHour() * 60 + zeitpunkt.getMinute();
    }

    private int hoehe() {
        return (bisStunde - vonStunde) * PIXEL_JE_STUNDE;
    }

    private static Div zeile() {
        Div zeile = new Div();
        zeile.getStyle().set("display", "flex");
        return zeile;
    }

    private static Div stundenleisteBreite(Div div) {
        div.getStyle().set("flex", "0 0 3.5rem");
        return div;
    }
}

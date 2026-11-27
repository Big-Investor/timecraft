package de.timecraft.antraege.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.timepicker.TimePicker;
import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.service.AbwesenheitsDaten;
import de.timecraft.antraege.service.AntragAnsicht;
import de.timecraft.antraege.service.AntragService;
import de.timecraft.antraege.service.Urlaubsvorschau;
import de.timecraft.antraege.service.ZeitkorrekturDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Formular "Neuer Antrag" (UC-M5-02 bis UC-M5-05, UC-M5-10): Typ waehlen, passende Felder
 * erfassen, absenden. Ruft nur den AntragService auf; alle Pruefungen macht der Service.
 */
public class AntragFormular extends Div {

    private static final String AENDERN = "Ändern";
    private static final String STORNIEREN = "Stornieren";

    private final AntragService service;
    private final AktuelleSitzung sitzung;
    private final Runnable nachSpeichern;

    private final RadioButtonGroup<Antragstyp> typ = new RadioButtonGroup<>();
    private final H3 angabenTitel = new H3();
    private final Div felder = new Div();

    private final Select<Urlaubsart> urlaubsart = new Select<>();
    private final DatePicker von = Datumsfelder.datum("Von *");
    private final DatePicker bis = Datumsfelder.datum("Bis *");
    private final Span vorschau = new Span();

    private final DatePicker tag = Datumsfelder.datum("Tag der Buchung *");
    private final Select<BuchungInfo> buchung = new Select<>();
    private final Span bisher = new Span();
    private final TimePicker beginn = Datumsfelder.uhrzeit("Beginn *");
    private final TimePicker ende = Datumsfelder.uhrzeit("Ende *");
    private final Select<Standort> standort = new Select<>();
    private final Select<ProjektInfo> projekt = new Select<>();

    private final Select<AntragAnsicht> bezug = new Select<>();
    private final RadioButtonGroup<String> aktion = new RadioButtonGroup<>();

    private final TextArea begruendung = new TextArea();
    private final Button absenden = new Button("Antrag absenden", ereignis -> absenden());

    /** Antrag, der gerade bearbeitet wird; null bei einem neuen Antrag. */
    private AntragAnsicht inBearbeitung;

    /** Aktive Projekte der Person fuer die Auswahl "Projekt" (einmal beim Oeffnen geladen). */
    private List<ProjektInfo> projekte = List.of();

    public AntragFormular(AntragService service, AktuelleSitzung sitzung, Runnable nachSpeichern) {
        this.service = service;
        this.sitzung = sitzung;
        this.nachSpeichern = nachSpeichern;
        addClassName("karte");
        projekteLaden(null);
        felderEinrichten();
        Button abbrechen = new Button("Abbrechen", ereignis -> zuruecksetzen());
        absenden.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout knoepfe = new HorizontalLayout(abbrechen, absenden);
        knoepfe.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        knoepfe.setWidthFull();
        add(new H3("1. Antragstyp wählen"), typ, new Hr(), angabenTitel, felder, begruendung, knoepfe);
        zuruecksetzen();
    }

    /** Laedt einen eigenen offenen Antrag zum Bearbeiten; der Typ bleibt dabei fest. */
    public void bearbeiten(AntragAnsicht ansicht) {
        inBearbeitung = ansicht;
        typ.setValue(ansicht.typ());
        typ.setReadOnly(true);
        begruendung.setValue(Objects.requireNonNullElse(ansicht.antrag().getBegruendung(), ""));
        if (ansicht.antrag() instanceof Abwesenheitsantrag abwesenheit) {
            urlaubsart.setValue(abwesenheit.getUrlaubsart());
            von.setValue(abwesenheit.getVon());
            bis.setValue(abwesenheit.getBis());
        } else if (ansicht.antrag() instanceof Zeitkorrekturantrag korrektur) {
            zeitkorrekturLaden(korrektur, ansicht.buchung());
        }
        absenden.setText("Änderungen speichern");
        felderZeigen();
    }

    // ---------- Aufbau ----------

    private void felderEinrichten() {
        typ.setItems(Antragstyp.values());
        typ.setItemLabelGenerator(AntragTexte::auswahlname);
        typ.addValueChangeListener(ereignis -> typGewechselt());

        urlaubsart.setLabel("Urlaubsart *");
        urlaubsart.setItems(Urlaubsart.values());
        urlaubsart.setItemLabelGenerator(Urlaubsart::bezeichnung);
        urlaubsart.addValueChangeListener(ereignis -> vorschauAktualisieren());
        von.addValueChangeListener(ereignis -> vorschauAktualisieren());
        bis.addValueChangeListener(ereignis -> vorschauAktualisieren());

        tag.addValueChangeListener(ereignis -> buchungenLaden());
        buchung.setLabel("Buchung *");
        buchung.setItemLabelGenerator(this::buchungstext);
        buchung.addValueChangeListener(ereignis -> buchungUebernehmen(ereignis.getValue()));
        standort.setLabel("Standort *");
        standort.setItems(Standort.values());
        standort.setItemLabelGenerator(AntragTexte::standort);
        projekt.setLabel("Projekt");
        projekt.setEmptySelectionAllowed(true);
        projekt.setEmptySelectionCaption("ohne Projekt");
        projekt.setItemLabelGenerator(info -> info == null ? "ohne Projekt" : info.name());

        bezug.setLabel("Genehmigter Antrag *");
        bezug.setItemLabelGenerator(AntragTexte::titel);
        bezug.addValueChangeListener(ereignis -> bezugUebernehmen(ereignis.getValue()));
        aktion.setLabel("Aktion");
        aktion.setItems(AENDERN, STORNIEREN);
        aktion.addValueChangeListener(ereignis -> felderZeigen());

        begruendung.setWidthFull();
        begruendung.setMaxLength(255);
        vorschau.getElement().getThemeList().add("badge");
    }

    private void zuruecksetzen() {
        inBearbeitung = null;
        typ.setReadOnly(false);
        typ.setValue(Antragstyp.URLAUB);
        urlaubsart.setValue(Urlaubsart.ERHOLUNGSURLAUB);
        von.clear();
        bis.clear();
        tag.clear();
        buchung.setItems(List.<BuchungInfo>of());
        beginn.clear();
        ende.clear();
        standort.clear();
        projekt.clear();
        bezug.clear();
        aktion.clear();
        begruendung.clear();
        absenden.setText("Antrag absenden");
        felderZeigen();
    }

    /** Zeigt je nach Typ (und bei Aenderung/Storno je nach Bezug und Aktion) die passenden Felder (FA-M5-02). */
    private void felderZeigen() {
        felder.removeAll();
        Antragstyp gewaehlt = typ.getValue();
        if (gewaehlt == null) {
            return;
        }
        angabenTitel.setText("2. Angaben " + switch (gewaehlt) {
            case ZEITKORREKTUR -> "zur Zeitkorrektur";
            case URLAUB -> "zum Urlaub";
            case KRANKHEIT -> "zur Krankheit";
            case SONSTIGE_ABWESENHEIT -> "zur sonstigen Abwesenheit";
            case STORNO -> "zur Änderung / Storno";
        });
        begruendung.setLabel("Begründung (optional)");
        absenden.setEnabled(gewaehlt != Antragstyp.STORNO || inBearbeitung != null || bezug.getValue() != null);
        switch (gewaehlt) {
            case URLAUB -> abwesenheitsfelderZeigen(true);
            case KRANKHEIT, SONSTIGE_ABWESENHEIT -> abwesenheitsfelderZeigen(false);
            case ZEITKORREKTUR -> zeitkorrekturfelderZeigen(true);
            case STORNO -> aenderungsfelderZeigen();
        }
    }

    private void abwesenheitsfelderZeigen(boolean urlaub) {
        if (urlaub) {
            felder.add(zeile(urlaubsart, von, bis), vorschau);
            vorschauAktualisieren();
        } else {
            felder.add(zeile(von, bis));
        }
    }

    private void zeitkorrekturfelderZeigen(boolean buchungWaehlbar) {
        if (buchungWaehlbar && inBearbeitung == null) {
            felder.add(zeile(tag, buchung));
        }
        felder.add(bisher, zeile(beginn, ende), zeile(standort, projekt));
    }

    private void aenderungsfelderZeigen() {
        if (inBearbeitung != null) {
            felder.add(new Span("Storno zu: " + AntragTexte.zeitraum(inBearbeitung.bezugsantrag(), null)));
            begruendung.setLabel("Grund der Stornierung *");
            return;
        }
        felder.add(bezug);
        AntragAnsicht gewaehlt = bezug.getValue();
        if (gewaehlt == null) {
            return;
        }
        felder.add(aktion);
        if (STORNIEREN.equals(aktion.getValue())) {
            begruendung.setLabel("Grund der Stornierung *");
        } else if (AENDERN.equals(aktion.getValue())) {
            if (gewaehlt.antrag() instanceof Abwesenheitsantrag abwesenheit) {
                abwesenheitsfelderZeigen(abwesenheit.getArt() == AbwesenheitsArt.URLAUB);
            } else {
                zeitkorrekturfelderZeigen(false);
            }
        }
    }

    // ---------- Reaktionen auf Eingaben ----------

    /** Beim Wechsel auf "Aenderung / Storno" die genehmigten Antraege laden, sonst die Auswahl leeren. */
    private void typGewechselt() {
        if (typ.getValue() == Antragstyp.STORNO && inBearbeitung == null) {
            try {
                bezug.setItems(service.aenderbareAntraege(sitzung.mitarbeiterId(), sitzung.rolle()));
            } catch (RuntimeException fehler) {
                Meldungen.fehler(fehler);
            }
        } else {
            bezug.clear();
        }
        felderZeigen();
    }

    /** Projektauswahl: aktive Projekte und das bisherige Projekt der Buchung (auch wenn archiviert). */
    private void projekteLaden(Long bisherigesProjektId) {
        try {
            projekte = service.projektauswahl(sitzung.mitarbeiterId(), sitzung.rolle(), bisherigesProjektId);
            projekt.setItems(projekte);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /**
     * Stornieren gibt es nur fuer Abwesenheiten (Entscheidung Alper Sayar, 27.11.2026). Die Aktion ist mit
     * "Ändern" vorbelegt, damit nie unbeabsichtigt ein Storno entsteht.
     */
    private void bezugUebernehmen(AntragAnsicht gewaehlt) {
        aktion.setItems(gewaehlt != null && gewaehlt.antrag() instanceof Abwesenheitsantrag
                ? List.of(AENDERN, STORNIEREN) : List.of(AENDERN));
        aktion.setValue(AENDERN);
        if (gewaehlt != null && gewaehlt.antrag() instanceof Abwesenheitsantrag abwesenheit) {
            urlaubsart.setValue(abwesenheit.getUrlaubsart());
            von.setValue(abwesenheit.getVon());
            bis.setValue(abwesenheit.getBis());
        } else if (gewaehlt != null && gewaehlt.antrag() instanceof Zeitkorrekturantrag korrektur) {
            zeitkorrekturLaden(korrektur, gewaehlt.buchung());
        }
        felderZeigen();
    }

    private void buchungenLaden() {
        try {
            buchung.setItems(service.buchungenAmTag(sitzung.mitarbeiterId(), sitzung.rolle(), tag.getValue()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void buchungUebernehmen(BuchungInfo gewaehlt) {
        if (gewaehlt == null) {
            bisher.setText("");
            return;
        }
        projekteLaden(gewaehlt.projektId());
        bisher.setText("Bisher: " + buchungstext(gewaehlt) + " · " + AntragTexte.standort(gewaehlt.standort()));
        beginn.setValue(LocalTime.ofInstant(gewaehlt.beginn(), AntragTexte.ZONE));
        ende.setValue(LocalTime.ofInstant(gewaehlt.ende(), AntragTexte.ZONE));
        standort.setValue(gewaehlt.standort());
        projekt.setValue(projekte.stream()
                .filter(info -> Objects.equals(info.projektId(), gewaehlt.projektId())).findFirst().orElse(null));
    }

    /** Werte einer Zeitkorrektur (Bearbeiten oder Aenderung) ins Formular; die Buchung ist dann fest. */
    private void zeitkorrekturLaden(Zeitkorrekturantrag korrektur, BuchungInfo aktuelleBuchung) {
        tag.setValue(AntragTexte.tag(korrektur.getAlterBeginn()));
        if (aktuelleBuchung != null) {
            buchung.setItems(aktuelleBuchung);
            buchung.setValue(aktuelleBuchung);
        }
        beginn.setValue(LocalTime.ofInstant(korrektur.getNeuerBeginn(), AntragTexte.ZONE));
        ende.setValue(LocalTime.ofInstant(korrektur.getNeuesEnde(), AntragTexte.ZONE));
        standort.setValue(korrektur.getNeuerStandort());
        projekt.setValue(projekte.stream()
                .filter(info -> Objects.equals(info.projektId(), korrektur.getNeuesProjektId())).findFirst()
                .orElse(null));
    }

    /** Nur sichtbar, solange die Urlaubsfelder angezeigt werden. */
    private void vorschauAktualisieren() {
        vorschau.setVisible(false);
        if (!vorschau.isAttached() || von.getValue() == null || bis.getValue() == null) {
            return;
        }
        try {
            Urlaubsvorschau ergebnis = service.urlaubsvorschau(sitzung.mitarbeiterId(), sitzung.rolle(),
                    urlaubsart.getValue(), von.getValue(), bis.getValue(), bearbeiteteId(), bezugId());
            if (ergebnis == null) {
                return;
            }
            vorschau.setText(ergebnis.urlaubstage() + " Urlaubstage · " + (ergebnis.resturlaubDanach() == null
                    ? "Unbezahlter Urlaub zählt nicht gegen den Urlaubsanspruch (Sondergenehmigung)."
                    : "Danach bleiben " + ergebnis.resturlaubDanach() + " Tage Resturlaub."));
        } catch (RuntimeException fehler) {
            vorschau.setText(Meldungen.fehlertext(fehler));
        }
        vorschau.setVisible(true);
    }

    // ---------- Absenden ----------

    private void absenden() {
        try {
            if (inBearbeitung == null) {
                neuenAntragSenden();
                Meldungen.erfolg("Ihr Antrag wurde eingereicht.");
            } else {
                bearbeitetenAntragSenden();
                Meldungen.erfolg("Ihr Antrag wurde geändert.");
            }
            zuruecksetzen();
            nachSpeichern.run();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void neuenAntragSenden() {
        long mitarbeiterId = sitzung.mitarbeiterId();
        switch (typ.getValue()) {
            case ZEITKORREKTUR -> service.zeitkorrekturBeantragen(mitarbeiterId, sitzung.rolle(), zeitkorrekturDaten());
            case STORNO -> aenderungSenden(mitarbeiterId);
            default -> service.abwesenheitBeantragen(mitarbeiterId, sitzung.rolle(), abwesenheitsDaten(art(typ.getValue())));
        }
    }

    private void aenderungSenden(long mitarbeiterId) {
        AntragAnsicht gewaehlt = bezug.getValue();
        if (STORNIEREN.equals(aktion.getValue())) {
            service.stornoBeantragen(mitarbeiterId, sitzung.rolle(), gewaehlt.antrag().getId(), begruendung.getValue());
        } else if (gewaehlt.antrag() instanceof Abwesenheitsantrag abwesenheit) {
            service.abwesenheitAendern(mitarbeiterId, sitzung.rolle(), abwesenheit.getId(),
                    abwesenheitsDaten(abwesenheit.getArt()));
        } else {
            service.zeitkorrekturAendern(mitarbeiterId, sitzung.rolle(), gewaehlt.antrag().getId(),
                    zeitkorrekturDaten());
        }
    }

    private void bearbeitetenAntragSenden() {
        long mitarbeiterId = sitzung.mitarbeiterId();
        long antragId = inBearbeitung.antrag().getId();
        if (inBearbeitung.antrag() instanceof Abwesenheitsantrag abwesenheit) {
            service.abwesenheitBearbeiten(mitarbeiterId, sitzung.rolle(), antragId, abwesenheitsDaten(abwesenheit.getArt()));
        } else if (inBearbeitung.antrag() instanceof Zeitkorrekturantrag) {
            service.zeitkorrekturBearbeiten(mitarbeiterId, sitzung.rolle(), antragId, zeitkorrekturDaten());
        } else {
            service.stornoBearbeiten(mitarbeiterId, sitzung.rolle(), antragId, begruendung.getValue());
        }
    }

    private AbwesenheitsDaten abwesenheitsDaten(AbwesenheitsArt art) {
        return new AbwesenheitsDaten(art, art == AbwesenheitsArt.URLAUB ? urlaubsart.getValue() : null,
                von.getValue(), bis.getValue(), begruendung.getValue());
    }

    private ZeitkorrekturDaten zeitkorrekturDaten() {
        BuchungInfo gewaehlt = buchung.getValue();
        LocalDate buchungstag = gewaehlt == null ? tag.getValue() : AntragTexte.tag(gewaehlt.beginn());
        ProjektInfo zielprojekt = projekt.getValue();
        return new ZeitkorrekturDaten(gewaehlt == null ? null : gewaehlt.zeiteintragId(),
                zeitpunkt(buchungstag, beginn.getValue()), zeitpunkt(buchungstag, ende.getValue()),
                standort.getValue(), zielprojekt == null ? null : zielprojekt.projektId(), begruendung.getValue());
    }

    // ---------- kleine Helfer ----------

    private static AbwesenheitsArt art(Antragstyp gewaehlt) {
        return switch (gewaehlt) {
            case URLAUB -> AbwesenheitsArt.URLAUB;
            case KRANKHEIT -> AbwesenheitsArt.KRANKHEIT;
            default -> AbwesenheitsArt.SONSTIGE;
        };
    }

    private static Instant zeitpunkt(LocalDate datum, LocalTime uhrzeit) {
        return datum == null || uhrzeit == null ? null : datum.atTime(uhrzeit).atZone(AntragTexte.ZONE).toInstant();
    }

    private Long bearbeiteteId() {
        return inBearbeitung == null ? null : inBearbeitung.antrag().getId();
    }

    private Long bezugId() {
        if (inBearbeitung != null) {
            return inBearbeitung.antrag().getBezugsantragId();
        }
        return bezug.getValue() == null ? null : bezug.getValue().antrag().getId();
    }

    private String buchungstext(BuchungInfo info) {
        String projektname = projekte.stream().filter(p -> Objects.equals(p.projektId(), info.projektId()))
                .map(ProjektInfo::name).findFirst().orElse(info.projektId() == null ? "ohne Projekt" : "Projekt " + info.projektId());
        return AntragTexte.uhrzeit(info.beginn()) + "–" + AntragTexte.uhrzeit(info.ende()) + " · " + projektname;
    }

    private static HorizontalLayout zeile(Component... inhalte) {
        HorizontalLayout zeile = new HorizontalLayout(inhalte);
        zeile.setWidthFull();
        for (Component inhalt : inhalte) {
            zeile.setFlexGrow(1, inhalt);
        }
        return zeile;
    }
}

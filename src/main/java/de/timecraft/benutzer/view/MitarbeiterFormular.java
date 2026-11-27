package de.timecraft.benutzer.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.service.AnlegeErgebnis;
import de.timecraft.benutzer.service.EingabeFehlerException;
import de.timecraft.benutzer.service.MitarbeiterDaten;
import de.timecraft.benutzer.service.MitarbeiterService;
import de.timecraft.benutzer.service.MitarbeiterZeile;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.geruest.layout.Rollenanzeige;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mitarbeiterformular nach dem Mockup „Mitarbeiter anlegen“: neuen Mitarbeiter samt Benutzerkonto
 * anlegen (UC-M1-04) oder einen Mitarbeiter bearbeiten (UC-M1-05). Drei Karten: Stammdaten;
 * Arbeitszeit, Urlaub &amp; Vorgesetzte/r; Benutzerkonto. Beim Bearbeiten hat der Admin im Bereich
 * „Benutzerkonto“ zusätzlich „Einmalpasswort senden“ (UC-M1-10), „Konto sperren/entsperren“ (UC-M1-09)
 * und „Mitarbeiter deaktivieren“ (UC-M1-06).
 *
 * <p>Geprüft wird nur im {@link MitarbeiterService}; dessen Meldungen erscheinen am Feld (QA-M1-07).
 * Die Projektleitung sieht Urlaub, Startsaldo, Vorgesetzte/r und Konto nur lesend; der Service
 * übernimmt diese Angaben von ihr ohnehin nicht (FA-M1-11).
 *
 * @author Leon-Selim Kannenberg
 */
@Route(value = MitarbeiterListeView.FORMULAR, layout = HauptLayout.class)
@PageTitle("Mitarbeiter")
@RolesAllowed({"PROJEKTLEITUNG", "ADMIN"})
public class MitarbeiterFormular extends Div implements HasUrlParameter<Long> {

    static final String LISTE = MitarbeiterListeView.ROUTE;
    static final String FALSCHER_STARTSALDO =
            "Bitte geben Sie den Startsaldo als Stunden:Minuten ein, z. B. 12:30 oder -4:00.";
    static final String FALSCHES_DATUM = "Bitte geben Sie ein Datum zwischen 1900 und 2099 ein.";

    private static final String[] TAGESKUERZEL = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MitarbeiterService mitarbeiterService;
    private final AktuelleSitzung sitzung;
    private final boolean istAdmin;

    /** {@code null} bei einem neuen Mitarbeiter. */
    private Long mitarbeiterId;
    private String mitarbeiterName;
    private boolean gesperrt;

    final Span pfad = new Span();
    final H2 ueberschrift = new H2();

    final TextField personalnummer = textFeld("Personalnummer *");
    final TextField vorname = textFeld("Vorname *");
    final TextField nachname = textFeld("Nachname *");
    final TextField email = textFeld("E-Mail *");
    final TextField telefon = textFeld("Telefon");
    final DatePicker geburtsdatum = Eingabefelder.datumFeld("Geburtsdatum *");
    final Span jugendHinweis = new Span("Unter 18: Es gelten die Regeln des JArbSchG.");
    final DatePicker eintrittsdatum = Eingabefelder.datumFeld("Eintrittsdatum *");
    final Select<Qualifikation> qualifikation = new Select<>();

    final BigDecimalField wochenstunden = new BigDecimalField("Wochenstunden *");
    final TextField startsaldo = textFeld("Überstunden-Startsaldo");
    final Select<Long> vorgesetzter = new Select<>();
    final IntegerField urlaubsanspruch = new IntegerField("Urlaubsanspruch (Tage/Jahr) *");
    final IntegerField urlaubsuebertrag = new IntegerField("Resturlaub aus Vorjahr");
    final CheckboxGroup<DayOfWeek> arbeitstage = new CheckboxGroup<>("Arbeitstage *");
    final Span tagesSoll = new Span();

    final TextField benutzername = textFeld("Benutzername *");
    final Select<Rolle> rolle = new Select<>();
    final Span kontoHinweis = new Span();
    final Button einmalpasswortSenden = new Button("Einmalpasswort senden");
    final Button sperren = new Button();
    final Button deaktivieren = new Button("Mitarbeiter deaktivieren");
    final HorizontalLayout kontoAktionen = new HorizontalLayout(einmalpasswortSenden, sperren, deaktivieren);

    final Button speichern = new Button();

    /** Name des Vorgesetzten je Mitarbeiter-ID, für die Auswahlliste. */
    private final Map<Long, String> vorgesetzteNamen = new HashMap<>();
    /** Feldname des Service (z. B. {@code "vorname"}) → Eingabefeld, an dem die Meldung erscheint. */
    private final Map<String, HasValidation> felder = new LinkedHashMap<>();

    public MitarbeiterFormular(MitarbeiterService mitarbeiterService, AktuelleSitzung sitzung) {
        this.mitarbeiterService = mitarbeiterService;
        this.sitzung = sitzung;
        this.istAdmin = sitzung.rolle() == Rolle.ADMIN;

        felderEinrichten();
        felderZuordnen();

        Div kopf = new Div(new Div(new Anchor(LISTE, "Mitarbeiter"), pfad), ueberschrift);
        kopf.addClassNames(LumoUtility.Padding.Horizontal.LARGE, LumoUtility.Padding.Top.MEDIUM);

        Button abbrechen = new Button("Abbrechen", ereignis -> oeffnen(LISTE));
        speichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        speichern.addClickListener(ereignis -> speichern());
        HorizontalLayout knoepfe = new HorizontalLayout(abbrechen, speichern);
        knoepfe.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        knoepfe.addClassNames(LumoUtility.Padding.Horizontal.LARGE, LumoUtility.Padding.Bottom.LARGE);

        add(kopf,
                karte("Stammdaten", personalnummer, vorname, nachname, email, telefon, geburtsdatum,
                        eintrittsdatum, qualifikation),
                karte("Arbeitszeit, Urlaub & Vorgesetzte/r", wochenstunden, startsaldo, vorgesetzter,
                        urlaubsanspruch, urlaubsuebertrag, ganzeBreite(arbeitstage), ganzeBreite(tagesSoll)),
                karte("Benutzerkonto", benutzername, rolle, ganzeBreite(kontoHinweis), ganzeBreite(kontoAktionen)),
                knoepfe);
    }

    /** Ohne Parameter: neuer Mitarbeiter; mit ID: diesen Mitarbeiter bearbeiten. */
    @Override
    public void setParameter(BeforeEvent ereignis, @OptionalParameter Long id) {
        boolean geoeffnet = formularOeffnen(id);
        if (!geoeffnet) {
            ereignis.forwardTo(MitarbeiterListeView.class);
        }
    }

    /**
     * Füllt das Formular: leer für einen neuen Mitarbeiter oder mit den gespeicherten Daten.
     *
     * @return false, wenn der Mitarbeiter nicht geladen werden konnte (Meldung wird angezeigt)
     */
    boolean formularOeffnen(Long id) {
        try {
            vorgesetzteLaden();
            if (id == null) {
                neuesFormular();
            } else {
                vorhandenenMitarbeiterAnzeigen(mitarbeiterService.laden(id, sitzung.rolle()));
            }
            return true;
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            return false;
        }
    }

    /**
     * Leeres Formular für einen neuen Mitarbeiter. Vaadin verwendet die Seite weiter, wenn man von
     * einer Bearbeitung direkt hierher wechselt; deshalb werden alle Felder zuerst geleert.
     */
    private void neuesFormular() {
        mitarbeiterId = null;
        alleFelderLeeren();
        pfad.setText(" / Neu");
        ueberschrift.setText("Neuer Mitarbeiter");
        speichern.setText("Speichern & Einmalpasswort senden");
        nurAnzeigen(false);
        kontoAktionen.setVisible(false);
        startsaldo.setValue("0:00");
        urlaubsuebertrag.setValue(0);
        rolle.setValue(Rolle.MITARBEITER);
        kontoHinweisAktualisieren();
    }

    private void vorhandenenMitarbeiterAnzeigen(MitarbeiterDaten daten) {
        mitarbeiterId = daten.getMitarbeiterId();
        alleFelderLeeren();
        mitarbeiterName = daten.getVorname() + " " + daten.getNachname();
        gesperrt = daten.isGesperrt();
        pfad.setText(" / " + mitarbeiterName);
        ueberschrift.setText(mitarbeiterName);
        speichern.setText("Speichern");
        nurAnzeigen(!daten.isBearbeitbar());

        personalnummer.setValue(daten.getPersonalnummer());
        vorname.setValue(daten.getVorname());
        nachname.setValue(daten.getNachname());
        email.setValue(daten.getEmail());
        telefon.setValue(textOderLeer(daten.getTelefon()));
        geburtsdatum.setValue(daten.getGeburtsdatum());
        eintrittsdatum.setValue(daten.getEintrittsdatum());
        qualifikation.setValue(daten.getQualifikation());

        wochenstunden.setValue(mitEinerNachkommastelle(daten.getWochenstunden()));
        arbeitstage.setValue(daten.getArbeitstage());
        if (daten.getStartsaldoMinuten() != null) {
            startsaldo.setValue(stundenText(daten.getStartsaldoMinuten()));
        }
        urlaubsanspruch.setValue(daten.getUrlaubsanspruch());
        urlaubsuebertrag.setValue(daten.getUrlaubsuebertrag());
        // Nur wählbare Vorgesetzte (aktive Projektleitungen) erscheinen in der Liste
        if (vorgesetzteNamen.containsKey(daten.getVorgesetzterId())) {
            vorgesetzter.setValue(daten.getVorgesetzterId());
        }

        benutzername.setValue(daten.getBenutzername());
        rolle.setValue(daten.getRolle());
        kontoAktionen.setVisible(istAdmin);
        deaktivieren.setVisible(daten.isAktiv());
        sperrenKnopfAktualisieren();
        kontoHinweis.setText(kontoStatus(daten));
    }

    /**
     * Sperrt Stammdaten und Arbeitszeit und blendet „Speichern“ aus, wenn der Service sagt, dass die
     * angemeldete Person diesen Mitarbeiter nicht bearbeiten darf (Projektleitung bei Admin oder Projektleitung).
     */
    private void nurAnzeigen(boolean nurLesen) {
        personalnummer.setReadOnly(nurLesen);
        vorname.setReadOnly(nurLesen);
        nachname.setReadOnly(nurLesen);
        email.setReadOnly(nurLesen);
        telefon.setReadOnly(nurLesen);
        geburtsdatum.setReadOnly(nurLesen);
        eintrittsdatum.setReadOnly(nurLesen);
        qualifikation.setReadOnly(nurLesen);
        wochenstunden.setReadOnly(nurLesen);
        arbeitstage.setReadOnly(nurLesen);
        speichern.setVisible(!nurLesen);
    }

    /**
     * „Speichern“: liest das Formular, gibt es an den Service und zeigt dessen Meldungen am Feld.
     * Bei einem Fehler bleibt das Formular offen und nichts wird gespeichert (UC-M1-04, 5a/5b).
     */
    void speichern() {
        fehlerZeigen(null);
        MitarbeiterDaten daten = datenAusFormular();
        if (daten == null) {
            return;
        }
        try {
            if (mitarbeiterId == null) {
                neuAnlegen(daten);
            } else {
                mitarbeiterService.bearbeiten(mitarbeiterId, daten, sitzung.mitarbeiterId(), sitzung.rolle());
                Meldungen.erfolg("Die Änderungen wurden gespeichert.");
                oeffnen(LISTE);
            }
        } catch (EingabeFehlerException fehler) {
            fehlerZeigen(fehler);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /**
     * Legt an und sendet das Einmalpasswort. Klappt der Versand nicht (7a), ist der Mitarbeiter trotzdem
     * gespeichert: Das Formular wechselt zur Bearbeitung, dort lässt sich das Einmalpasswort erneut senden.
     */
    private void neuAnlegen(MitarbeiterDaten daten) {
        AnlegeErgebnis ergebnis = mitarbeiterService.anlegen(daten, sitzung.rolle());
        mitarbeiterId = ergebnis.mitarbeiterId();
        if (ergebnis.mailVersendet()) {
            Meldungen.erfolg(ergebnis.meldung());
            oeffnen(LISTE);
        } else {
            Meldungen.fehler(new FachlicheException(ergebnis.meldung()));
            oeffnen(MitarbeiterListeView.FORMULAR + "/" + ergebnis.mitarbeiterId());
        }
    }

    /**
     * Überträgt die Felder in ein {@link MitarbeiterDaten}-Objekt. Nur die Form der Eingabe wird
     * hier geprüft (Startsaldo als Stunden:Minuten, Datum im Bereich des Datumsfelds); die fachlichen
     * Regeln prüft der Service. Ist die Form falsch, erscheint die Meldung am Feld und es kommt {@code null}.
     */
    private MitarbeiterDaten datenAusFormular() {
        Integer startsaldoMinuten = minutenAus(startsaldo.getValue());
        boolean startsaldoLeer = startsaldo.getValue() == null || startsaldo.getValue().isBlank();
        boolean formFalsch = false;
        if (startsaldoMinuten == null && !startsaldoLeer) {
            Eingabefelder.fehlerZeigen(startsaldo, FALSCHER_STARTSALDO);
            formFalsch = true;
        }
        for (DatePicker datumsfeld : List.of(geburtsdatum, eintrittsdatum)) {
            if (ausserhalbDesBereichs(datumsfeld)) {
                Eingabefelder.fehlerZeigen(datumsfeld, FALSCHES_DATUM);
                formFalsch = true;
            }
        }
        if (formFalsch) {
            return null;
        }

        MitarbeiterDaten daten = new MitarbeiterDaten();
        daten.setPersonalnummer(personalnummer.getValue());
        daten.setVorname(vorname.getValue());
        daten.setNachname(nachname.getValue());
        daten.setEmail(email.getValue());
        daten.setTelefon(telefon.getValue());
        daten.setGeburtsdatum(geburtsdatum.getValue());
        daten.setEintrittsdatum(eintrittsdatum.getValue());
        daten.setQualifikation(qualifikation.getValue());
        daten.setWochenstunden(wochenstunden.getValue());
        daten.setArbeitstage(arbeitstage.getValue());
        daten.setStartsaldoMinuten(startsaldoMinuten);
        daten.setUrlaubsanspruch(urlaubsanspruch.getValue());
        daten.setUrlaubsuebertrag(urlaubsuebertrag.getValue());
        daten.setVorgesetzterId(vorgesetzter.getValue());
        daten.setBenutzername(benutzername.getValue());
        daten.setRolle(rolle.getValue());
        return daten;
    }

    /** „Einmalpasswort senden“ nach Bestätigung (UC-M1-10). Bei Versandfehler gilt das alte Passwort weiter. */
    void einmalpasswortSenden() {
        try {
            String rueckmeldung = mitarbeiterService.einmalpasswortSenden(mitarbeiterId, sitzung.rolle());
            Meldungen.erfolg("Das Einmalpasswort wurde gesendet. " + rueckmeldung);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /** Sperrt oder entsperrt das Benutzerkonto (UC-M1-09). */
    void sperrenOderEntsperren() {
        try {
            if (gesperrt) {
                mitarbeiterService.entsperren(mitarbeiterId, sitzung.rolle());
                Meldungen.erfolg("Das Konto von " + mitarbeiterName + " ist entsperrt.");
            } else {
                mitarbeiterService.sperren(mitarbeiterId, sitzung.mitarbeiterId(), sitzung.rolle());
                Meldungen.erfolg("Das Konto von " + mitarbeiterName + " ist gesperrt.");
            }
            gesperrt = !gesperrt;
            sperrenKnopfAktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /**
     * Deaktiviert den Mitarbeiter zum angegebenen Austrittsdatum (UC-M1-06). Meldungen des Service
     * erscheinen am Datumsfeld des Dialogs.
     *
     * @return true, wenn der Mitarbeiter deaktiviert wurde und der Dialog schließen kann
     */
    boolean deaktivieren(DatePicker austrittsdatum) {
        try {
            mitarbeiterService.deaktivieren(mitarbeiterId, austrittsdatum.getValue(), sitzung.mitarbeiterId(),
                    sitzung.rolle());
            Meldungen.erfolg(mitarbeiterName + " ist deaktiviert.");
            oeffnen(LISTE);
            return true;
        } catch (RuntimeException fehler) {
            Eingabefelder.fehlerZeigen(austrittsdatum, Meldungen.fehlertext(fehler));
            return false;
        }
    }

    /** Stellt alle Felder ein: Auswahllisten, Breite, Hinweise und was die Projektleitung nur lesen darf. */
    private void felderEinrichten() {
        qualifikation.setLabel("Qualifikation *");
        qualifikation.setItems(Qualifikation.values());
        qualifikation.setItemLabelGenerator(Qualifikation::getAnzeigename);

        vorgesetzter.setLabel("Vorgesetzte/r *");
        vorgesetzter.setEmptySelectionAllowed(true);
        vorgesetzter.setEmptySelectionCaption("– keine –");
        vorgesetzter.setItemLabelGenerator(id -> textOderLeer(vorgesetzteNamen.get(id)));

        rolle.setLabel("Rolle *");
        rolle.setItems(Rolle.values());
        rolle.setItemLabelGenerator(Rollenanzeige::text);

        arbeitstage.setItems(DayOfWeek.values());
        arbeitstage.setItemLabelGenerator(tag -> TAGESKUERZEL[tag.getValue() - 1]);
        arbeitstage.addValueChangeListener(ereignis -> tagesSollAktualisieren());
        wochenstunden.setLocale(Locale.GERMANY);
        wochenstunden.addValueChangeListener(ereignis -> tagesSollAktualisieren());
        tagesSoll.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        jugendHinweis.addClassNames(LumoUtility.TextColor.WARNING);
        jugendHinweis.setVisible(false);
        geburtsdatum.setHelperComponent(jugendHinweis);
        geburtsdatum.addValueChangeListener(ereignis ->
                jugendHinweis.setVisible(mitarbeiterService.istUnter18(ereignis.getValue())));

        vorname.addValueChangeListener(ereignis -> kontoHinweisAktualisieren());
        email.addValueChangeListener(ereignis -> kontoHinweisAktualisieren());
        kontoHinweis.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        einmalpasswortSenden.addClickListener(ereignis -> einmalpasswortDialog());
        sperren.addClickListener(ereignis -> sperrenOderEntsperren());
        deaktivieren.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deaktivieren.addClickListener(ereignis -> deaktivierenDialog());

        // Volle Spaltenbreite und Prüfung nur durch den Service, wie bei textFeld(...)
        qualifikation.setWidthFull();
        vorgesetzter.setWidthFull();
        rolle.setWidthFull();
        wochenstunden.setWidthFull();
        urlaubsanspruch.setWidthFull();
        urlaubsuebertrag.setWidthFull();
        qualifikation.setManualValidation(true);
        vorgesetzter.setManualValidation(true);
        rolle.setManualValidation(true);
        arbeitstage.setManualValidation(true);
        wochenstunden.setManualValidation(true);
        urlaubsanspruch.setManualValidation(true);
        urlaubsuebertrag.setManualValidation(true);

        // Die Projektleitung ändert nur Stammdaten und Arbeitszeitmodell (FA-M1-11, US-M1-11)
        boolean nurLesen = !istAdmin;
        startsaldo.setReadOnly(nurLesen);
        vorgesetzter.setReadOnly(nurLesen);
        urlaubsanspruch.setReadOnly(nurLesen);
        urlaubsuebertrag.setReadOnly(nurLesen);
        benutzername.setReadOnly(nurLesen);
        rolle.setReadOnly(nurLesen);
    }

    /** Welche Meldung des Service an welches Feld gehört. */
    private void felderZuordnen() {
        felder.put(MitarbeiterDaten.PERSONALNUMMER, personalnummer);
        felder.put(MitarbeiterDaten.VORNAME, vorname);
        felder.put(MitarbeiterDaten.NACHNAME, nachname);
        felder.put(MitarbeiterDaten.EMAIL, email);
        felder.put(MitarbeiterDaten.TELEFON, telefon);
        felder.put(MitarbeiterDaten.GEBURTSDATUM, geburtsdatum);
        felder.put(MitarbeiterDaten.EINTRITTSDATUM, eintrittsdatum);
        felder.put(MitarbeiterDaten.QUALIFIKATION, qualifikation);
        felder.put(MitarbeiterDaten.WOCHENSTUNDEN, wochenstunden);
        felder.put(MitarbeiterDaten.ARBEITSTAGE, arbeitstage);
        felder.put(MitarbeiterDaten.URLAUBSANSPRUCH, urlaubsanspruch);
        felder.put(MitarbeiterDaten.URLAUBSUEBERTRAG, urlaubsuebertrag);
        felder.put(MitarbeiterDaten.VORGESETZTER, vorgesetzter);
        felder.put(MitarbeiterDaten.BENUTZERNAME, benutzername);
        felder.put(MitarbeiterDaten.ROLLE, rolle);
    }

    /** Leert alle Eingabefelder und entfernt alle Meldungen. */
    private void alleFelderLeeren() {
        List<HasValue<?, ?>> eingabefelder = List.of(personalnummer, vorname, nachname, email, telefon,
                geburtsdatum, eintrittsdatum, qualifikation, wochenstunden, startsaldo, vorgesetzter,
                urlaubsanspruch, urlaubsuebertrag, arbeitstage, benutzername, rolle);
        for (HasValue<?, ?> feld : eingabefelder) {
            feld.clear();
        }
        fehlerZeigen(null);
    }

    /** Zeigt die Meldungen des Service an den Feldern; {@code null} entfernt alle Meldungen. */
    private void fehlerZeigen(EingabeFehlerException fehler) {
        for (Map.Entry<String, HasValidation> eintrag : felder.entrySet()) {
            String meldung = null;
            if (fehler != null) {
                meldung = fehler.meldungZu(eintrag.getKey());
            }
            Eingabefelder.fehlerZeigen(eintrag.getValue(), meldung);
        }
        Eingabefelder.fehlerZeigen(startsaldo, null);
    }

    private void vorgesetzteLaden() {
        List<Long> ids = new ArrayList<>();
        for (MitarbeiterZeile zeile : mitarbeiterService.vorgesetzteAuswahl(sitzung.rolle())) {
            ids.add(zeile.mitarbeiterId());
            vorgesetzteNamen.put(zeile.mitarbeiterId(), zeile.name() + " (" + Rollenanzeige.text(zeile.rolle()) + ")");
        }
        vorgesetzter.setItems(ids);
    }

    /** „Tages-Soll: 8:00 h (40,0 h ÷ 5 Arbeitstage)“ unter den Arbeitstagen, wie im Mockup. */
    private void tagesSollAktualisieren() {
        BigDecimal stunden = wochenstunden.getValue();
        int anzahlTage = arbeitstage.getValue().size();
        if (stunden == null || anzahlTage == 0) {
            tagesSoll.setText("");
            return;
        }
        int minuten = MitarbeiterService.tagesSollMinuten(stunden, anzahlTage);
        String tageText = anzahlTage + " Arbeitstage";
        if (anzahlTage == 1) {
            tageText = "1 Arbeitstag";
        }
        tagesSoll.setText("Tages-Soll: " + stundenText(minuten) + " h ("
                + kommazahl(stunden) + " h ÷ " + tageText + ")");
    }

    /** Hinweis im Bereich „Benutzerkonto“ für einen neuen Mitarbeiter, wie im Mockup. */
    private void kontoHinweisAktualisieren() {
        if (mitarbeiterId != null) {
            return;
        }
        String wer = vorname.getValue().isBlank() ? "der Mitarbeiter" : vorname.getValue().strip();
        String wohin = email.getValue().isBlank() ? "die hinterlegte E-Mail-Adresse" : email.getValue().strip();
        kontoHinweis.setText("Nach dem Speichern erhält " + wer + " ein Einmalpasswort an " + wohin
                + " (24 Stunden gültig).");
    }

    private String kontoStatus(MitarbeiterDaten daten) {
        String status = "Konto aktiv.";
        if (daten.isGesperrt()) {
            status = "Konto gesperrt.";
        }
        if (!daten.isAktiv() && daten.getAustrittsdatum() != null) {
            status = status + " Ausgetreten am " + daten.getAustrittsdatum().format(DATUM) + ".";
        }
        return status;
    }

    private void sperrenKnopfAktualisieren() {
        if (gesperrt) {
            sperren.setText("Konto entsperren");
        } else {
            sperren.setText("Konto sperren");
        }
    }

    /** Sicherheitsabfrage vor dem Zurücksetzen des Passworts (UC-M1-10, Schritt 2). */
    private void einmalpasswortDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Einmalpasswort senden?");
        dialog.add(new Paragraph("Das bisherige Passwort von " + mitarbeiterName + " wird ungültig. "
                + "Das Einmalpasswort ist 24 Stunden gültig; danach muss ein eigenes Passwort festgelegt werden."));
        Button senden = new Button("Einmalpasswort senden", ereignis -> {
            einmalpasswortSenden();
            dialog.close();
        });
        senden.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Abbrechen", ereignis -> dialog.close()), senden);
        dialog.open();
    }

    /** Dialog mit Austrittsdatum vor dem Deaktivieren (UC-M1-06). */
    private void deaktivierenDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Mitarbeiter deaktivieren");
        DatePicker austrittsdatum = Eingabefelder.datumFeld("Austrittsdatum *");
        dialog.add(new Paragraph(mitarbeiterName + " wird deaktiviert, nicht gelöscht. "
                + "Erfasste Zeiten und Anträge bleiben erhalten."), austrittsdatum);
        Button bestaetigen = new Button("Deaktivieren", ereignis -> {
            if (deaktivieren(austrittsdatum)) {
                dialog.close();
            }
        });
        bestaetigen.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Abbrechen", ereignis -> dialog.close()), bestaetigen);
        dialog.open();
    }

    /** Weiße Karte mit Überschrift und dreispaltigem Formular (einspaltig bei schmalem Bildschirm). */
    private Div karte(String titel, Component... inhalt) {
        FormLayout formular = new FormLayout(inhalt);
        formular.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("700px", 3));
        Div karte = new Div(new H2(titel), formular);
        karte.addClassName("karte");
        return karte;
    }

    /** Lässt eine Komponente im Formular über alle drei Spalten gehen. */
    private Component ganzeBreite(Component komponente) {
        komponente.getElement().setAttribute("colspan", "3");
        return komponente;
    }

    private static TextField textFeld(String beschriftung) {
        TextField feld = new TextField(beschriftung);
        feld.setWidthFull();
        feld.setManualValidation(true);
        return feld;
    }

    private boolean ausserhalbDesBereichs(DatePicker feld) {
        LocalDate datum = feld.getValue();
        return datum != null && (datum.isBefore(feld.getMin()) || datum.isAfter(feld.getMax()));
    }

    /** Minuten als Stunden:Minuten, z. B. 480 → „8:00“, -90 → „-1:30“. */
    static String stundenText(int minuten) {
        String vorzeichen = "";
        if (minuten < 0) {
            vorzeichen = "-";
        }
        int ohneVorzeichen = Math.abs(minuten);
        int stunden = ohneVorzeichen / 60;
        int restMinuten = ohneVorzeichen % 60;
        return vorzeichen + stunden + ":" + String.format("%02d", restMinuten);
    }

    /**
     * Liest „Stunden:Minuten“ (z. B. „12:30“ oder „-4:00“) als Minuten. Leere oder falsch geschriebene
     * Eingaben ergeben {@code null}.
     */
    static Integer minutenAus(String text) {
        if (text == null || !text.strip().matches("-?\\d{1,5}:[0-5]\\d")) {
            return null;
        }
        String eingabe = text.strip();
        boolean negativ = eingabe.startsWith("-");
        if (negativ) {
            eingabe = eingabe.substring(1);
        }
        String[] teile = eingabe.split(":");
        int minuten = Integer.parseInt(teile[0]) * 60 + Integer.parseInt(teile[1]);
        if (negativ) {
            return -minuten;
        }
        return minuten;
    }

    /** Wochenstunden mit mindestens einer Nachkommastelle wie im Mockup: 40 → „40,0“, 37,50 → „37,5“. */
    private static BigDecimal mitEinerNachkommastelle(BigDecimal zahl) {
        if (zahl == null) {
            return null;
        }
        BigDecimal ohneNullen = zahl.stripTrailingZeros();
        if (ohneNullen.scale() < 1) {
            return ohneNullen.setScale(1);
        }
        return ohneNullen;
    }

    private static String kommazahl(BigDecimal zahl) {
        return mitEinerNachkommastelle(zahl).toPlainString().replace('.', ',');
    }

    private static String textOderLeer(String text) {
        if (text == null) {
            return "";
        }
        return text;
    }

    private void oeffnen(String route) {
        getUI().ifPresent(ui -> ui.navigate(route));
    }
}

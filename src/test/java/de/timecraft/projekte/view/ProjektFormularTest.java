package de.timecraft.projekte.view;

import static de.timecraft.projekte.view.Komponenten.anmelden;
import static de.timecraft.projekte.view.Komponenten.knopf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.service.ProjektService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prüft das Projekt-Formular ohne Browser: Die Eingaben gehen unverändert an den Service, bei einem Fehler
 * bleibt das Formular offen (UC-M3-01, UC-M3-02). Admin hat die ID 3.
 *
 * @author Tim Stukenbröker
 */
class ProjektFormularTest {

    // Vaadin merkt sich die aktuelle UI nur schwach: Ohne dieses Feld kann der Müllsammler sie im Test wegräumen.
    private UI ui;

    private final ProjektService service = mock(ProjektService.class);
    private final List<Projekt> gespeichert = new ArrayList<>();

    private final MitarbeiterInfo lena = new MitarbeiterInfo(2, "P2", "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true,
            null, null, null, Set.of(), null, 0);
    private final MitarbeiterInfo sabine = new MitarbeiterInfo(7, "P7", "Sabine", "Keller", Rolle.PROJEKTLEITUNG,
            true, null, null, null, Set.of(), null, 0);

    @BeforeEach
    void meldeAdminAn() {
        anmelden(3, Rolle.ADMIN);
        ui = new UI();
        UI.setCurrent(ui);
        when(service.projektleitungenZurAuswahl(3, Rolle.ADMIN)).thenReturn(List.of(sabine, lena));
    }

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
    }

    private ProjektFormular formular(Projekt projekt) {
        ProjektFormular formular = new ProjektFormular(service, new AktuelleSitzung(), projekt, gespeichert::add);
        formular.open();
        return formular;
    }

    @SuppressWarnings("unchecked")
    private ComboBox<MitarbeiterInfo> projektleitung(ProjektFormular formular) {
        return Komponenten.mitBeschriftung(formular, ComboBox.class, "Verantwortliche Projektleitung");
    }

    private void fuelle(ProjektFormular formular) {
        Komponenten.mitBeschriftung(formular, TextField.class, "Projektname").setValue("Neubau Halle");
        Komponenten.mitBeschriftung(formular, TextArea.class, "Beschreibung").setValue("Stahlhalle");
        Komponenten.mitBeschriftung(formular, DatePicker.class, "Startdatum").setValue(LocalDate.of(2027, 1, 4));
        Komponenten.mitBeschriftung(formular, DatePicker.class, "Zieldatum").setValue(LocalDate.of(2027, 6, 30));
        projektleitung(formular).setValue(lena);
    }

    @Test
    void neuesProjektHatLeereFelderUndDieAuswahlDerProjektleitungen() {
        ProjektFormular formular = formular(null);

        assertThat(formular.getHeaderTitle()).isEqualTo("Neues Projekt");
        assertThat(Komponenten.mitBeschriftung(formular, TextField.class, "Projektname").isEmpty()).isTrue();
        assertThat(projektleitung(formular).getListDataView().getItems().toList()).containsExactly(sabine, lena);
        assertThat(projektleitung(formular).isEmpty()).isTrue();
    }

    @Test
    void speichernLegtMitDenEingabenAnUndMeldetDasProjektZurueck() {
        Projekt neu = new Projekt(2, "Neubau Halle", "Stahlhalle", LocalDate.of(2027, 1, 4), LocalDate.of(2027, 6, 30));
        when(service.anlegen(anyLong(), any(), any())).thenReturn(neu);
        ProjektFormular formular = formular(null);
        fuelle(formular);

        knopf(formular, "Speichern").click();

        ArgumentCaptor<ProjektDaten> daten = ArgumentCaptor.forClass(ProjektDaten.class);
        verify(service).anlegen(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(Rolle.ADMIN),
                daten.capture());
        assertThat(daten.getValue()).isEqualTo(new ProjektDaten("Neubau Halle", "Stahlhalle",
                LocalDate.of(2027, 1, 4), LocalDate.of(2027, 6, 30), 2L));
        assertThat(gespeichert).containsExactly(neu);
        assertThat(formular.isOpened()).isFalse();
    }

    @Test
    void ohneProjektleitungGehtNullAnDenService() {
        when(service.anlegen(anyLong(), any(), any())).thenThrow(new FachlicheException("Bitte wählen Sie eine Projektleitung."));
        ProjektFormular formular = formular(null);
        fuelle(formular);
        projektleitung(formular).clear();

        knopf(formular, "Speichern").click();

        ArgumentCaptor<ProjektDaten> daten = ArgumentCaptor.forClass(ProjektDaten.class);
        verify(service).anlegen(anyLong(), any(), daten.capture());
        assertThat(daten.getValue().projektleitungId()).isNull();
    }

    @Test
    void beiEinemFehlerBleibtDasFormularOffenUndNichtsWirdZurueckgemeldet() {
        when(service.anlegen(anyLong(), any(), any()))
                .thenThrow(new FachlicheException("Das Zieldatum darf nicht vor dem Startdatum liegen."));
        ProjektFormular formular = formular(null);
        fuelle(formular);

        knopf(formular, "Speichern").click();

        assertThat(formular.isOpened()).isTrue();
        assertThat(gespeichert).isEmpty();
    }

    @Test
    void bearbeitenFuelltDieFelderUndAendertDasProjekt() {
        // Ein noch nicht gespeichertes Projekt hat keine ID; hier bekommt es die ID 5.
        Projekt vorhanden = spy(new Projekt(7, "Website", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        doReturn(5L).when(vorhanden).getProjektId();
        when(service.aendern(anyLong(), any(), anyLong(), any())).thenReturn(vorhanden);
        ProjektFormular formular = formular(vorhanden);

        assertThat(formular.getHeaderTitle()).isEqualTo("Projekt bearbeiten");
        assertThat(Komponenten.mitBeschriftung(formular, TextField.class, "Projektname").getValue()).isEqualTo("Website");
        assertThat(Komponenten.mitBeschriftung(formular, TextArea.class, "Beschreibung").getValue()).isEmpty();
        assertThat(Komponenten.mitBeschriftung(formular, DatePicker.class, "Startdatum").getValue())
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(projektleitung(formular).getValue()).isEqualTo(sabine);

        Komponenten.mitBeschriftung(formular, TextField.class, "Projektname").setValue("Website Relaunch");
        knopf(formular, "Speichern").click();

        ArgumentCaptor<ProjektDaten> daten = ArgumentCaptor.forClass(ProjektDaten.class);
        verify(service).aendern(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(Rolle.ADMIN),
                org.mockito.ArgumentMatchers.eq(5L),
                daten.capture());
        assertThat(daten.getValue().name()).isEqualTo("Website Relaunch");
        assertThat(daten.getValue().projektleitungId()).isEqualTo(7L);
        verify(service, never()).anlegen(anyLong(), any(), any());
    }

    @Test
    void abbrechenSpeichertNichts() {
        ProjektFormular formular = formular(null);
        fuelle(formular);

        knopf(formular, "Abbrechen").click();

        verify(service, never()).anlegen(anyLong(), any(), any());
        assertThat(formular.isOpened()).isFalse();
    }
}

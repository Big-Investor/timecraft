package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prüft ProjektService.aendern (Admin) und ProjektService.beschreibungAendern (Projektleitung)
 * ohne Datenbank mit Mockito-Attrappen.
 * <p>
 * Ausgangslage in jedem Test: Das Projekt 10 "Website" gehört der Projektleitung 2.
 * Personen (frei gewählte Zahlen): Admin 3, verantwortliche Projektleitung 2,
 * andere Projektleitung 7, Mitarbeiter 1.
 *
 * @author Tim Stukenbröker
 */
class ProjektServiceAendernTest {

    private static final long PROJEKT_ID = 10L;
    private static final long ADMIN_ID = 3L;
    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final long ANDERE_PROJEKTLEITUNG_ID = 7L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate ZIEL = LocalDate.of(2026, 12, 31);

    private final ProjektRepository repository = mock(ProjektRepository.class);
    private final MitarbeiterAuskunft mitarbeiterAuskunft = mock(MitarbeiterAuskunft.class);
    private final ProjektService service = new ProjektService(repository, mock(ProjektzuordnungRepository.class),
            new ProjektBerechtigung(), mitarbeiterAuskunft, mock(BuchungsAuskunft.class), FesteUhr.UHR);

    private Projekt projekt;

    @BeforeEach
    void bereiteProjektVor() {
        projekt = new Projekt(PROJEKTLEITUNG_ID, "Website", "alte Beschreibung", START, ZIEL);
        projekt.setAngebotspreis(new BigDecimal("1500.00"));
        when(repository.findById(PROJEKT_ID)).thenReturn(Optional.of(projekt));
        when(repository.save(any(Projekt.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    }

    // ---------- aendern: Admin ----------

    @Test
    void adminAendertAlleFelderUndStatusUndPreisBleiben() {
        when(mitarbeiterAuskunft.finde(ANDERE_PROJEKTLEITUNG_ID))
                .thenReturn(Optional.of(person(ANDERE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, true)));
        ProjektDaten neu = new ProjektDaten("  Shop  ", "neu", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30),
                ANDERE_PROJEKTLEITUNG_ID);

        Projekt ergebnis = service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, neu);

        assertThat(ergebnis.getName()).isEqualTo("Shop");
        assertThat(ergebnis.getBeschreibung()).isEqualTo("neu");
        assertThat(ergebnis.getStartdatum()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(ergebnis.getZieldatum()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(ergebnis.getProjektleitungId()).isEqualTo(ANDERE_PROJEKTLEITUNG_ID);
        assertThat(ergebnis.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        assertThat(ergebnis.getAngebotspreis()).isEqualByComparingTo("1500.00");
        verify(repository).save(projekt);
    }

    @Test
    void adminDarfAuchEinArchiviertesProjektAendern() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, datenMitProjektleitung(PROJEKTLEITUNG_ID, "Neuer Name"));

        assertThat(projekt.getName()).isEqualTo("Neuer Name");
        assertThat(projekt.getStatus()).isEqualTo(ProjektStatus.ARCHIVIERT);
    }

    @Test
    void bleibtDieProjektleitungGleichWirdSieNichtNeuGeprueft() {
        // Auch wenn die Projektleitung inzwischen deaktiviert wäre: Der Name soll sich trotzdem ändern lassen.
        service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, datenMitProjektleitung(PROJEKTLEITUNG_ID, "Neuer Name"));

        assertThat(projekt.getName()).isEqualTo("Neuer Name");
        verifyNoInteractions(mitarbeiterAuskunft);
    }

    @Test
    void neueProjektleitungMussEineAktiveProjektleitungSein() {
        when(mitarbeiterAuskunft.finde(1L)).thenReturn(Optional.of(person(1L, Rolle.MITARBEITER, true)));
        when(mitarbeiterAuskunft.finde(5L)).thenReturn(Optional.of(person(5L, Rolle.PROJEKTLEITUNG, false)));
        when(mitarbeiterAuskunft.finde(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, datenMitProjektleitung(1L, "X")))
                .isInstanceOf(FachlicheException.class).hasMessage("Die gewählte Person ist keine Projektleitung.");
        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, datenMitProjektleitung(5L, "X")))
                .isInstanceOf(FachlicheException.class).hasMessage("Die gewählte Projektleitung ist nicht mehr aktiv.");
        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, datenMitProjektleitung(99L, "X")))
                .isInstanceOf(FachlicheException.class).hasMessage("Die gewählte Projektleitung wurde nicht gefunden.");

        assertProjektUnveraendert();
    }

    @Test
    void ungueltigeEingabenAendernNichts() {
        ProjektDaten zielVorStart = new ProjektDaten("Neu", "neu", ZIEL, START, PROJEKTLEITUNG_ID);
        ProjektDaten ohneName = new ProjektDaten("  ", "neu", START, ZIEL, PROJEKTLEITUNG_ID);
        ProjektDaten ohneProjektleitung = new ProjektDaten("Neu", "neu", START, ZIEL, null);

        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, zielVorStart))
                .isInstanceOf(FachlicheException.class).hasMessage("Das Zieldatum darf nicht vor dem Startdatum liegen.");
        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, ohneName))
                .isInstanceOf(FachlicheException.class).hasMessage("Bitte geben Sie einen Projektnamen ein.");
        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, ohneProjektleitung))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte wählen Sie eine verantwortliche Projektleitung aus.");

        assertProjektUnveraendert();
    }

    @Test
    void unbekanntesProjektWirdGemeldet() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(ADMIN_ID, Rolle.ADMIN, 99L, datenMitProjektleitung(PROJEKTLEITUNG_ID, "X")))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Das Projekt wurde nicht gefunden.");
        verify(repository, never()).save(any());
    }

    // ---------- aendern: Negativtests je Rolle ----------

    @Test
    void nurDerAdminDarfAendern() {
        ProjektDaten daten = datenMitProjektleitung(PROJEKTLEITUNG_ID, "Neuer Name");

        // Auch die verantwortliche Projektleitung darf hier nichts ändern (nur die Beschreibung, aber über eine andere Methode).
        assertThatThrownBy(() -> service.aendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID, daten))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.aendern(1L, Rolle.MITARBEITER, PROJEKT_ID, daten))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.aendern(ADMIN_ID, null, PROJEKT_ID, daten))
                .isInstanceOf(NichtAngemeldetException.class);

        assertProjektUnveraendert();
    }

    // ---------- beschreibungAendern: Projektleitung ----------

    @Test
    void verantwortlicheProjektleitungAendertNurDieBeschreibung() {
        Projekt ergebnis = service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID, "  neuer Text  ");

        assertThat(ergebnis.getBeschreibung()).isEqualTo("neuer Text");
        assertThat(ergebnis.getName()).isEqualTo("Website");
        assertThat(ergebnis.getStartdatum()).isEqualTo(START);
        assertThat(ergebnis.getZieldatum()).isEqualTo(ZIEL);
        assertThat(ergebnis.getProjektleitungId()).isEqualTo(PROJEKTLEITUNG_ID);
        assertThat(ergebnis.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        verify(repository).save(projekt);
    }

    @Test
    void leereBeschreibungWirdZuNull() {
        service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID, "   ");

        assertThat(projekt.getBeschreibung()).isNull();
    }

    @Test
    void beiArchiviertemProjektDarfDieProjektleitungNichtsAendern() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        assertThatThrownBy(() -> service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID, "neu"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Beschreibung eines archivierten Projekts kann nicht geändert werden.");

        assertThat(projekt.getBeschreibung()).isEqualTo("alte Beschreibung");
        verify(repository, never()).save(any());
    }

    @Test
    void beschreibungAendernBeiUnbekanntemProjekt() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, 99L, "neu"))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void fremdeProjektleitungMitarbeiterUndAdminDuerfenDieBeschreibungNichtAendern() {
        assertThatThrownBy(() -> service.beschreibungAendern(ANDERE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID, "neu"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.beschreibungAendern(1L, Rolle.MITARBEITER, PROJEKT_ID, "neu"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.beschreibungAendern(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID, "neu"))
                .isInstanceOf(ZugriffVerweigertException.class);
        // Ein Mitarbeiter mit der ID der Projektleitung ist trotzdem keine Projektleitung.
        assertThatThrownBy(() -> service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.MITARBEITER, PROJEKT_ID, "neu"))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertProjektUnveraendert();
    }

    @Test
    void ohneAnmeldungDarfNiemandDieBeschreibungAendern() {
        assertThatThrownBy(() -> service.beschreibungAendern(PROJEKTLEITUNG_ID, null, PROJEKT_ID, "neu"))
                .isInstanceOf(NichtAngemeldetException.class);

        assertProjektUnveraendert();
    }

    // ---------- Hilfsmethoden ----------

    private ProjektDaten datenMitProjektleitung(long projektleitungId, String name) {
        return new ProjektDaten(name, "alte Beschreibung", START, ZIEL, projektleitungId);
    }

    /** Nach einem Fehler oder verweigertem Zugriff ist das Projekt wie am Anfang und es wurde nichts gespeichert. */
    private void assertProjektUnveraendert() {
        assertThat(projekt.getName()).isEqualTo("Website");
        assertThat(projekt.getBeschreibung()).isEqualTo("alte Beschreibung");
        assertThat(projekt.getStartdatum()).isEqualTo(START);
        assertThat(projekt.getZieldatum()).isEqualTo(ZIEL);
        assertThat(projekt.getProjektleitungId()).isEqualTo(PROJEKTLEITUNG_ID);
        verify(repository, never()).save(any());
    }

    private MitarbeiterInfo person(long id, Rolle rolle, boolean aktiv) {
        return new MitarbeiterInfo(id, "P" + id, "Vorname", "Nachname", rolle, aktiv, null, null, null, null, null, 0);
    }
}

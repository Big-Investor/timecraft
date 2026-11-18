package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Prüft ProjektService.anlegen ohne Datenbank: Repository und MitarbeiterAuskunft sind
 * Mockito-Attrappen (Mocks), die nur das tun, was der Test ihnen vorgibt.
 * <p>
 * Die Personen im Test (frei gewählte Zahlen): Admin 3, Projektleitung 2, Mitarbeiter 1.
 */
class ProjektServiceAnlegenTest {

    private static final long ADMIN_ID = 3L;
    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate ZIEL = LocalDate.of(2026, 12, 31);

    private final ProjektRepository repository = mock(ProjektRepository.class);
    private final MitarbeiterAuskunft mitarbeiterAuskunft = mock(MitarbeiterAuskunft.class);
    private final ProjektService service = new ProjektService(repository, mock(ProjektzuordnungRepository.class),
            new ProjektBerechtigung(), mitarbeiterAuskunft, FesteUhr.UHR);

    @BeforeEach
    void bereiteMocksVor() {
        // Normalfall: Mitarbeiter 2 ist eine aktive Projektleitung. Speichern gibt das Projekt unverändert zurück.
        when(mitarbeiterAuskunft.finde(PROJEKTLEITUNG_ID))
                .thenReturn(Optional.of(person(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, true)));
        when(repository.save(any(Projekt.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    }

    // ---------- Normalfall ----------

    @Test
    void adminLegtProjektAn_esIstAktivUndHatAlleDaten() {
        ProjektDaten daten = new ProjektDaten("Website", "Neuer Auftritt", START, ZIEL, PROJEKTLEITUNG_ID);

        Projekt ergebnis = service.anlegen(ADMIN_ID, Rolle.ADMIN, daten);

        Projekt gespeichert = gespeichertesProjekt();
        assertThat(gespeichert.getName()).isEqualTo("Website");
        assertThat(gespeichert.getBeschreibung()).isEqualTo("Neuer Auftritt");
        assertThat(gespeichert.getStartdatum()).isEqualTo(START);
        assertThat(gespeichert.getZieldatum()).isEqualTo(ZIEL);
        assertThat(gespeichert.getProjektleitungId()).isEqualTo(PROJEKTLEITUNG_ID);
        assertThat(gespeichert.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        assertThat(ergebnis).isSameAs(gespeichert);
    }

    @Test
    void leerzeichenAmNamenUndAnDerBeschreibungWerdenEntfernt() {
        service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("  Website  ", "  Text  ", START, ZIEL, PROJEKTLEITUNG_ID));

        assertThat(gespeichertesProjekt().getName()).isEqualTo("Website");
        assertThat(gespeichertesProjekt().getBeschreibung()).isEqualTo("Text");
    }

    @Test
    void beschreibungDarfLeerSein() {
        service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("A", null, START, ZIEL, PROJEKTLEITUNG_ID));
        service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("B", "   ", START, ZIEL, PROJEKTLEITUNG_ID));

        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(projekt -> assertThat(projekt.getBeschreibung()).isNull());
    }

    @Test
    void gleicheNamenSindErlaubt() {
        ProjektDaten daten = new ProjektDaten("Website", null, START, ZIEL, PROJEKTLEITUNG_ID);

        service.anlegen(ADMIN_ID, Rolle.ADMIN, daten);
        service.anlegen(ADMIN_ID, Rolle.ADMIN, daten);

        verify(repository, times(2)).save(any(Projekt.class));
    }

    // ---------- Grenzfälle ----------

    @Test
    void nameMitGenau100ZeichenIstErlaubt() {
        service.anlegen(ADMIN_ID, Rolle.ADMIN, mitName("x".repeat(ProjektService.MAX_NAME_LAENGE)));

        verify(repository).save(any(Projekt.class));
    }

    @Test
    void nameMit101ZeichenWirdAbgelehnt() {
        assertAbgelehnt(mitName("x".repeat(ProjektService.MAX_NAME_LAENGE + 1)),
                "Der Projektname darf höchstens 100 Zeichen lang sein.");
    }

    @Test
    void zieldatumAmGleichenTagWieDasStartdatumIstErlaubt() {
        service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("A", null, START, START, PROJEKTLEITUNG_ID));

        verify(repository).save(any(Projekt.class));
    }

    @Test
    void zieldatumVorDemStartdatumWirdAbgelehnt() {
        assertAbgelehnt(new ProjektDaten("A", null, START, START.minusDays(1), PROJEKTLEITUNG_ID),
                "Das Zieldatum darf nicht vor dem Startdatum liegen.");
    }

    // ---------- Pflichtfelder (Fehlerfälle: Meldung, nichts gespeichert) ----------

    @Test
    void ohneNamenWirdAbgelehnt() {
        assertAbgelehnt(mitName(null), "Bitte geben Sie einen Projektnamen ein.");
        assertAbgelehnt(mitName(""), "Bitte geben Sie einen Projektnamen ein.");
        assertAbgelehnt(mitName("   "), "Bitte geben Sie einen Projektnamen ein.");
    }

    @Test
    void ohneStartdatumWirdAbgelehnt() {
        assertAbgelehnt(new ProjektDaten("A", null, null, ZIEL, PROJEKTLEITUNG_ID), "Bitte geben Sie ein Startdatum ein.");
    }

    @Test
    void ohneZieldatumWirdAbgelehnt() {
        assertAbgelehnt(new ProjektDaten("A", null, START, null, PROJEKTLEITUNG_ID), "Bitte geben Sie ein Zieldatum ein.");
    }

    @Test
    void ohneProjektleitungWirdAbgelehnt() {
        assertAbgelehnt(new ProjektDaten("A", null, START, ZIEL, null),
                "Bitte wählen Sie eine verantwortliche Projektleitung aus.");
    }

    // ---------- Prüfung der Projektleitung über M1 ----------

    @Test
    void unbekanntePersonAlsProjektleitungWirdAbgelehnt() {
        when(mitarbeiterAuskunft.finde(99L)).thenReturn(Optional.empty());

        assertAbgelehnt(new ProjektDaten("A", null, START, ZIEL, 99L), "Die gewählte Projektleitung wurde nicht gefunden.");
    }

    @Test
    void mitarbeiterUndAdminAlsProjektleitungWerdenAbgelehnt() {
        when(mitarbeiterAuskunft.finde(1L)).thenReturn(Optional.of(person(1L, Rolle.MITARBEITER, true)));
        when(mitarbeiterAuskunft.finde(ADMIN_ID)).thenReturn(Optional.of(person(ADMIN_ID, Rolle.ADMIN, true)));

        assertAbgelehnt(new ProjektDaten("A", null, START, ZIEL, 1L), "Die gewählte Person ist keine Projektleitung.");
        assertAbgelehnt(new ProjektDaten("A", null, START, ZIEL, ADMIN_ID), "Die gewählte Person ist keine Projektleitung.");
    }

    @Test
    void inaktiveProjektleitungWirdAbgelehnt() {
        when(mitarbeiterAuskunft.finde(5L)).thenReturn(Optional.of(person(5L, Rolle.PROJEKTLEITUNG, false)));

        assertAbgelehnt(new ProjektDaten("A", null, START, ZIEL, 5L), "Die gewählte Projektleitung ist nicht mehr aktiv.");
    }

    // ---------- Rechte (Negativtests je Rolle, direkter Service-Aufruf) ----------

    @Test
    void mitarbeiterDarfNichtAnlegen() {
        assertThatThrownBy(() -> service.anlegen(1L, Rolle.MITARBEITER, gueltigeDaten()))
                .isInstanceOf(ZugriffVerweigertException.class);

        verifyNichtsPassiert();
    }

    @Test
    void projektleitungDarfNichtAnlegen() {
        assertThatThrownBy(() -> service.anlegen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, gueltigeDaten()))
                .isInstanceOf(ZugriffVerweigertException.class);

        verifyNichtsPassiert();
    }

    @Test
    void ohneAnmeldungDarfNiemandAnlegen() {
        assertThatThrownBy(() -> service.anlegen(ADMIN_ID, null, gueltigeDaten()))
                .isInstanceOf(NichtAngemeldetException.class);

        verifyNichtsPassiert();
    }

    // ---------- Hilfsmethoden ----------

    private ProjektDaten gueltigeDaten() {
        return new ProjektDaten("Website", null, START, ZIEL, PROJEKTLEITUNG_ID);
    }

    private ProjektDaten mitName(String name) {
        return new ProjektDaten(name, null, START, ZIEL, PROJEKTLEITUNG_ID);
    }

    /** Der Admin legt mit diesen Daten an: Es kommt genau diese Meldung und nichts wird gespeichert. */
    private void assertAbgelehnt(ProjektDaten daten, String meldung) {
        assertThatThrownBy(() -> service.anlegen(ADMIN_ID, Rolle.ADMIN, daten))
                .isInstanceOf(FachlicheException.class)
                .hasMessage(meldung);
        verify(repository, never()).save(any());
    }

    /** Bei verweigertem Zugriff wird nichts gespeichert und nicht einmal M1 gefragt. */
    private void verifyNichtsPassiert() {
        verify(repository, never()).save(any());
        verifyNoInteractions(mitarbeiterAuskunft);
    }

    private Projekt gespeichertesProjekt() {
        ArgumentCaptor<Projekt> captor = ArgumentCaptor.forClass(Projekt.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    /** Eine Person aus M1 mit den Werten, auf die es hier ankommt; alles andere ist leer. */
    private MitarbeiterInfo person(long id, Rolle rolle, boolean aktiv) {
        return new MitarbeiterInfo(id, "P" + id, "Vorname", "Nachname", rolle, aktiv, null, null, null, null, null, 0);
    }
}

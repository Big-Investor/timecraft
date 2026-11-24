package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektSicht;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prüft ProjektService.projektSehen: Wer das Detailfenster öffnen darf und was die Person dort darf
 * (FA-M3-05, FA-M3-08, QA-M3-03). Ohne Datenbank. Ausgangslage: Das Projekt 10 gehört der
 * Projektleitung 2, dem Mitarbeiter 1 ist es zugeordnet, der Mitarbeiter 5 nicht. Admin hat die ID 3.
 *
 * @author Tim Stukenbröker
 */
class ProjektServiceSichtTest {

    private static final long PROJEKT_ID = 10L;
    private static final long ZUGEORDNET_ID = 1L;
    private static final long NICHT_ZUGEORDNET_ID = 5L;
    private static final long VERANTWORTLICH_ID = 2L;
    private static final long FREMDE_PROJEKTLEITUNG_ID = 7L;
    private static final long ADMIN_ID = 3L;

    private final ProjektRepository repository = mock(ProjektRepository.class);
    private final ProjektzuordnungRepository zuordnungRepository = mock(ProjektzuordnungRepository.class);
    private final MitarbeiterAuskunft mitarbeiterAuskunft = mock(MitarbeiterAuskunft.class);
    private final ProjektService service = new ProjektService(repository, zuordnungRepository,
            new ProjektBerechtigung(), mitarbeiterAuskunft, mock(BuchungsAuskunft.class), FesteUhr.UHR);

    private Projekt projekt;

    @BeforeEach
    void bereiteProjektVor() {
        projekt = new Projekt(VERANTWORTLICH_ID, "Website", "Neuer Auftritt", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        when(repository.findById(PROJEKT_ID)).thenReturn(Optional.of(projekt));
        when(zuordnungRepository.existsByMitarbeiterIdAndProjektId(ZUGEORDNET_ID, PROJEKT_ID)).thenReturn(true);
        when(mitarbeiterAuskunft.finde(VERANTWORTLICH_ID)).thenReturn(Optional.of(new MitarbeiterInfo(
                VERANTWORTLICH_ID, "P2", "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true, null, null, null,
                Set.of(), null, 0)));
    }

    // ---- verantwortliche Projektleitung ----

    @Test
    void verantwortlicheProjektleitungDarfAllesRundUmIhrAktivesProjekt() {
        ProjektSicht sicht = service.projektSehen(VERANTWORTLICH_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID);

        assertThat(sicht.projekt()).isSameAs(projekt);
        assertThat(sicht.projektleitungName()).isEqualTo("Lena Schröder");
        assertThat(sicht.teamSichtbar()).isTrue();
        assertThat(sicht.teamVollansicht()).isTrue();
        assertThat(sicht.beschreibungAenderbar()).isTrue();
        assertThat(sicht.verwaltbar()).isFalse();
        assertThat(sicht.materialplanungSichtbar()).isTrue();
    }

    @Test
    void fremdeProjektleitungSiehtDasAktiveProjektAberNichtDasTeam() {
        ProjektSicht sicht = service.projektSehen(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID);

        assertThat(sicht.teamSichtbar()).isFalse();
        assertThat(sicht.teamVollansicht()).isFalse();
        assertThat(sicht.beschreibungAenderbar()).isFalse();
        assertThat(sicht.verwaltbar()).isFalse();
        assertThat(sicht.materialplanungSichtbar()).isTrue();
    }

    @Test
    void projektleitungSiehtEinArchiviertesProjektNicht() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        assertThatThrownBy(() -> service.projektSehen(VERANTWORTLICH_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    // ---- Mitarbeiter ----

    @Test
    void zugeordneterMitarbeiterSiehtNurDieNamenImTeamUndKeineVerwaltung() {
        ProjektSicht sicht = service.projektSehen(ZUGEORDNET_ID, Rolle.MITARBEITER, PROJEKT_ID);

        assertThat(sicht.teamSichtbar()).isTrue();
        assertThat(sicht.teamVollansicht()).isFalse();
        assertThat(sicht.beschreibungAenderbar()).isFalse();
        assertThat(sicht.verwaltbar()).isFalse();
        assertThat(sicht.materialplanungSichtbar()).isFalse();
    }

    @Test
    void zugeordneterMitarbeiterSiehtEinArchiviertesProjektOhneTeam() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        ProjektSicht sicht = service.projektSehen(ZUGEORDNET_ID, Rolle.MITARBEITER, PROJEKT_ID);

        assertThat(sicht.projekt()).isSameAs(projekt);
        assertThat(sicht.teamSichtbar()).isFalse();
    }

    @Test
    void nichtZugeordneterMitarbeiterBekommtKeinenZugriff() {
        assertThatThrownBy(() -> service.projektSehen(NICHT_ZUGEORDNET_ID, Rolle.MITARBEITER, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    // ---- Admin ----

    @Test
    void adminVerwaltetDasProjektSiehtAberNieDasTeam() {
        ProjektSicht sicht = service.projektSehen(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);

        assertThat(sicht.verwaltbar()).isTrue();
        assertThat(sicht.teamSichtbar()).isFalse();
        assertThat(sicht.teamVollansicht()).isFalse();
        assertThat(sicht.beschreibungAenderbar()).isFalse();
        assertThat(sicht.materialplanungSichtbar()).isTrue();
    }

    @Test
    void adminSiehtAuchEinArchiviertesProjekt() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        assertThat(service.projektSehen(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID).verwaltbar()).isTrue();
    }

    // ---- Fehlerfälle ----

    @Test
    void ohneAnmeldungWirdAbgelehnt() {
        assertThatThrownBy(() -> service.projektSehen(ADMIN_ID, null, PROJEKT_ID))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void unbekanntesProjektMeldetNichtGefunden() {
        assertThatThrownBy(() -> service.projektSehen(ADMIN_ID, Rolle.ADMIN, 99L))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void unbekannteProjektleitungErgibtLeerenNamenStattFehler() {
        when(mitarbeiterAuskunft.finde(VERANTWORTLICH_ID)).thenReturn(Optional.empty());

        assertThat(service.projektSehen(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID).projektleitungName()).isEmpty();
    }
}

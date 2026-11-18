package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prüft ProjektService.archivieren und reaktivieren ohne Datenbank mit Mockito-Attrappen.
 * Ausgangslage: Das Projekt 10 gehört der Projektleitung 2. Admin hat die ID 3.
 */
class ProjektServiceArchivierenTest {

    private static final long PROJEKT_ID = 10L;
    private static final long ADMIN_ID = 3L;
    private static final long PROJEKTLEITUNG_ID = 2L;

    private final ProjektRepository repository = mock(ProjektRepository.class);
    private final MitarbeiterAuskunft mitarbeiterAuskunft = mock(MitarbeiterAuskunft.class);
    private final ProjektService service = new ProjektService(repository, mock(ProjektzuordnungRepository.class),
            new ProjektBerechtigung(), mitarbeiterAuskunft);

    private Projekt projekt;

    @BeforeEach
    void bereiteProjektVor() {
        projekt = new Projekt(PROJEKTLEITUNG_ID, "Website", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(repository.findById(PROJEKT_ID)).thenReturn(Optional.of(projekt));
        when(repository.save(any(Projekt.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    }

    @Test
    void adminArchiviertEinAktivesProjekt() {
        Projekt ergebnis = service.archivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);

        assertThat(ergebnis.getStatus()).isEqualTo(ProjektStatus.ARCHIVIERT);
        verify(repository).save(projekt);
    }

    @Test
    void adminReaktiviertEinArchiviertesProjekt() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        Projekt ergebnis = service.reaktivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);

        assertThat(ergebnis.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        verify(repository).save(projekt);
    }

    @Test
    void archivierenAendertNurDenStatus() {
        service.archivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);

        assertThat(projekt.getName()).isEqualTo("Website");
        assertThat(projekt.getProjektleitungId()).isEqualTo(PROJEKTLEITUNG_ID);
    }

    @Test
    void esWirdNieEinProjektGeloescht() {
        service.archivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);
        service.reaktivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID);

        // Das Repository wurde nur zum Suchen und Speichern benutzt, delete wurde nie aufgerufen.
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
        verify(repository, never()).deleteAll();
    }

    @Test
    void schonArchiviertesProjektKannNichtNochmalArchiviertWerden() {
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        assertThatThrownBy(() -> service.archivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Projekt ist bereits archiviert.");
        verify(repository, never()).save(any());
    }

    @Test
    void schonAktivesProjektKannNichtNochmalReaktiviertWerden() {
        assertThatThrownBy(() -> service.reaktivieren(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Projekt ist bereits aktiv.");
        verify(repository, never()).save(any());
    }

    @Test
    void unbekanntesProjektWirdGemeldet() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archivieren(ADMIN_ID, Rolle.ADMIN, 99L))
                .isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> service.reaktivieren(ADMIN_ID, Rolle.ADMIN, 99L))
                .isInstanceOf(NichtGefundenException.class);
        verify(repository, never()).save(any());
    }

    // ---------- Negativtests je Rolle (direkter Service-Aufruf) ----------

    @Test
    void nurDerAdminDarfArchivierenUndReaktivieren() {
        // Auch die verantwortliche Projektleitung des Projekts darf es nicht.
        assertThatThrownBy(() -> service.archivieren(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.archivieren(1L, Rolle.MITARBEITER, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.reaktivieren(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.reaktivieren(1L, Rolle.MITARBEITER, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(projekt.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        verifyNoInteractions(repository);
    }

    @Test
    void ohneAnmeldungDarfNiemandArchivierenOderReaktivieren() {
        assertThatThrownBy(() -> service.archivieren(ADMIN_ID, null, PROJEKT_ID))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.reaktivieren(ADMIN_ID, null, PROJEKT_ID))
                .isInstanceOf(NichtAngemeldetException.class);

        verifyNoMoreInteractions(repository);
    }
}

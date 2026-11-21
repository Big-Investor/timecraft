package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Hilfsmethoden, die die Oberfläche braucht: Namen für die Spalte "Projektleitung",
 * die Auswahl der Projektleitungen im Formular und der Hinweis vor dem Archivieren.
 * Ohne Datenbank, mit Mockito-Attrappen. Ausgangslage: Das Projekt 10 gehört der Projektleitung 2.
 */
class ProjektServiceOberflaechenHilfenTest {

    private static final long PROJEKT_ID = 10L;
    private static final long MITARBEITER_ID = 1L;
    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final long ADMIN_ID = 3L;

    private final ProjektRepository repository = mock(ProjektRepository.class);
    private final MitarbeiterAuskunft mitarbeiterAuskunft = mock(MitarbeiterAuskunft.class);
    private final BuchungsAuskunft buchungsAuskunft = mock(BuchungsAuskunft.class);
    private final ProjektService service = new ProjektService(repository, mock(ProjektzuordnungRepository.class),
            new ProjektBerechtigung(), mitarbeiterAuskunft, buchungsAuskunft, FesteUhr.UHR);

    private MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle, boolean aktiv) {
        return new MitarbeiterInfo(id, "P" + id, vorname, nachname, rolle, aktiv, null, null, null,
                Set.of(), null, 0);
    }

    // ---- namenDerPersonen ----

    @Test
    void namenDerPersonenLiefertDenVollenNamenJeId() {
        when(mitarbeiterAuskunft.alle()).thenReturn(List.of(
                person(1, "Jonas", "Becker", Rolle.MITARBEITER, true),
                person(2, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true),
                person(3, "Paul", "Hartmann", Rolle.ADMIN, true)));

        Map<Long, String> namen = service.namenDerPersonen(MITARBEITER_ID, Rolle.MITARBEITER, List.of(2L, 3L));

        assertThat(namen).containsOnly(Map.entry(2L, "Lena Schröder"), Map.entry(3L, "Paul Hartmann"));
    }

    @Test
    void namenDerPersonenKenntAuchEhemaligeMitarbeiter() {
        when(mitarbeiterAuskunft.alle()).thenReturn(List.of(person(2, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, false)));

        Map<Long, String> namen = service.namenDerPersonen(ADMIN_ID, Rolle.ADMIN, List.of(2L));

        assertThat(namen).containsEntry(2L, "Lena Schröder");
    }

    @Test
    void namenDerPersonenLaesstUnbekannteIdsAus() {
        when(mitarbeiterAuskunft.alle()).thenReturn(List.of(person(2, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true)));

        Map<Long, String> namen = service.namenDerPersonen(ADMIN_ID, Rolle.ADMIN, List.of(2L, 99L));

        assertThat(namen).containsOnlyKeys(2L);
    }

    @Test
    void namenDerPersonenOhneAnmeldungWirdAbgelehnt() {
        assertThatThrownBy(() -> service.namenDerPersonen(MITARBEITER_ID, null, List.of(2L)))
                .isInstanceOf(NichtAngemeldetException.class);
        verifyNoInteractions(mitarbeiterAuskunft);
    }

    // ---- projektleitungenZurAuswahl ----

    @Test
    void adminSiehtNurAktiveProjektleitungenSortiertNachName() {
        when(mitarbeiterAuskunft.aktive()).thenReturn(List.of(
                person(1, "Jonas", "Becker", Rolle.MITARBEITER, true),
                person(2, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true),
                person(3, "Paul", "Hartmann", Rolle.ADMIN, true),
                person(4, "Anna", "Keller", Rolle.PROJEKTLEITUNG, true)));

        List<MitarbeiterInfo> auswahl = service.projektleitungenZurAuswahl(ADMIN_ID, Rolle.ADMIN);

        assertThat(auswahl).extracting(MitarbeiterInfo::name).containsExactly("Anna Keller", "Lena Schröder");
    }

    @Test
    void mitarbeiterUndProjektleitungDuerfenDieAuswahlNichtLaden() {
        assertThatThrownBy(() -> service.projektleitungenZurAuswahl(MITARBEITER_ID, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.projektleitungenZurAuswahl(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
        verifyNoInteractions(mitarbeiterAuskunft);
    }

    @Test
    void projektleitungenZurAuswahlOhneAnmeldungWirdAbgelehnt() {
        assertThatThrownBy(() -> service.projektleitungenZurAuswahl(ADMIN_ID, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    // ---- laufendeBuchungenAnzahl ----

    private BuchungInfo laufendeBuchung(long zeiteintragId, long mitarbeiterId) {
        return new BuchungInfo(zeiteintragId, mitarbeiterId, PROJEKT_ID, Standort.BUERO,
                Instant.parse("2026-11-21T07:00:00Z"), null, 0, 0, false);
    }

    private void projektVorbereiten() {
        when(repository.findById(PROJEKT_ID)).thenReturn(Optional.of(
                new Projekt(PROJEKTLEITUNG_ID, "Website", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));
    }

    @Test
    void adminErfaehrtDieAnzahlDerLaufendenBuchungen() {
        projektVorbereiten();
        when(buchungsAuskunft.laufendeBuchungenAufProjekt(PROJEKT_ID))
                .thenReturn(List.of(laufendeBuchung(1, 1), laufendeBuchung(2, 4)));

        assertThat(service.laufendeBuchungenAnzahl(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID)).isEqualTo(2);
    }

    @Test
    void ohneLaufendeBuchungenIstDieAnzahlNull() {
        projektVorbereiten();
        when(buchungsAuskunft.laufendeBuchungenAufProjekt(PROJEKT_ID)).thenReturn(List.of());

        assertThat(service.laufendeBuchungenAnzahl(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID)).isZero();
    }

    @Test
    void laufendeBuchungenAnzahlIstFuerAndereRollenVerboten() {
        projektVorbereiten();

        assertThatThrownBy(() -> service.laufendeBuchungenAnzahl(MITARBEITER_ID, Rolle.MITARBEITER, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.laufendeBuchungenAnzahl(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, PROJEKT_ID))
                .isInstanceOf(ZugriffVerweigertException.class);
        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void laufendeBuchungenAnzahlOhneAnmeldungWirdAbgelehnt() {
        assertThatThrownBy(() -> service.laufendeBuchungenAnzahl(ADMIN_ID, null, PROJEKT_ID))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void laufendeBuchungenAnzahlBeiUnbekanntemProjektMeldetNichtGefunden() {
        when(repository.findById(PROJEKT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.laufendeBuchungenAnzahl(ADMIN_ID, Rolle.ADMIN, PROJEKT_ID))
                .isInstanceOf(NichtGefundenException.class);
        verifyNoInteractions(buchungsAuskunft);
    }
}

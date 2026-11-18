package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
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
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.model.TeamEintrag;
import de.timecraft.projekte.model.TeamFilter;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prüft die Team-Übersicht (UC-M3-07, UC-M3-08, FA-M3-08, FA-M3-09, FA-M3-10, QA-M3-01, QA-M3-03)
 * mit einer echten Datenbank (H2 im PostgreSQL-Modus). M1 und M2 sind Attrappen.
 * <p>
 * Die Personen (alle frei erfunden):
 * <pre>
 * ID  Name              Rolle            Zuordnung
 * 2   Paula Leitung     PROJEKTLEITUNG   verantwortet "Website" und "Altprojekt"
 * 98  Peter Fremd       PROJEKTLEITUNG   verantwortet "Shop"
 * 3   Adam Admin        ADMIN            -
 * 11  Anna Berger       MITARBEITER      "Website", "Altprojekt"   (eingestempelt: Büro, ab 08:15)
 * 12  Bernd Albers      MITARBEITER      "Website"                 (nicht eingestempelt)
 * 13  Clara Zander      MITARBEITER      "Website"                 (eingestempelt: Homeoffice, ab 09:30)
 * 14  Dirk Draussen     MITARBEITER      "Shop"                    (nicht dem Projekt "Website" zugeordnet)
 * </pre>
 * Das Projekt "Leer" ist aktiv und hat kein Team.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TeamService.class, ProjektBerechtigung.class})
class TeamServiceTest {

    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final long FREMDE_PROJEKTLEITUNG_ID = 98L;
    private static final long ADMIN_ID = 3L;
    private static final long ANNA = 11L;
    private static final long BERND = 12L;
    private static final long CLARA = 13L;
    private static final long DIRK = 14L;
    private static final Instant ANNA_BEGINN = Instant.parse("2026-11-18T07:15:00Z");
    private static final Instant CLARA_BEGINN = Instant.parse("2026-11-18T08:30:00Z");

    @Autowired
    private TeamService service;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    @MockitoBean
    private MitarbeiterAuskunft mitarbeiterAuskunft;

    @MockitoBean
    private BuchungsAuskunft buchungsAuskunft;

    private long websiteId;
    private long altprojektId;
    private long shopId;
    private long leerId;

    @BeforeEach
    void legeBeispieldatenAn() {
        zeigeAn(person(ANNA, "Anna", "Berger", Rolle.MITARBEITER));
        zeigeAn(person(BERND, "Bernd", "Albers", Rolle.MITARBEITER));
        zeigeAn(person(CLARA, "Clara", "Zander", Rolle.MITARBEITER));
        zeigeAn(person(DIRK, "Dirk", "Draussen", Rolle.MITARBEITER));

        websiteId = speichereProjekt("Website", PROJEKTLEITUNG_ID, ProjektStatus.AKTIV);
        altprojektId = speichereProjekt("Altprojekt", PROJEKTLEITUNG_ID, ProjektStatus.ARCHIVIERT);
        shopId = speichereProjekt("Shop", FREMDE_PROJEKTLEITUNG_ID, ProjektStatus.AKTIV);
        leerId = speichereProjekt("Leer", PROJEKTLEITUNG_ID, ProjektStatus.AKTIV);

        ordneZu(ANNA, websiteId);
        ordneZu(BERND, websiteId);
        ordneZu(CLARA, websiteId);
        ordneZu(ANNA, altprojektId);
        ordneZu(DIRK, shopId);

        when(buchungsAuskunft.laufendeBuchungenAufProjekt(websiteId)).thenReturn(List.of(
                buchung(ANNA, Standort.BUERO, ANNA_BEGINN),
                buchung(CLARA, Standort.HOMEOFFICE, CLARA_BEGINN)));
    }

    // ---------- Verantwortliche Projektleitung: volle Ansicht (FA-M3-08, QA-M3-01) ----------

    @Test
    void projektleitungSiehtAlleZugeordnetenMitStatusStandortUndBeginnSortiertNachName() {
        List<TeamEintrag> team = service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, new TeamFilter());

        assertThat(team).containsExactly(
                new TeamEintrag(ANNA, "Anna Berger", true, Standort.BUERO, ANNA_BEGINN),
                new TeamEintrag(BERND, "Bernd Albers", false, null, null),
                new TeamEintrag(CLARA, "Clara Zander", true, Standort.HOMEOFFICE, CLARA_BEGINN));
    }

    @Test
    void dieProjektleitungStehtNichtAlsZeileImTeam() {
        List<TeamEintrag> team = service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, null);

        assertThat(team).extracting(TeamEintrag::mitarbeiterId).doesNotContain(PROJEKTLEITUNG_ID);
        assertThat(team).hasSize(3);
    }

    @Test
    void einFilterNullFiltertNichts() {
        assertThat(service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, null)).hasSize(3);
    }

    @Test
    void leeresTeamGibtEineLeereListe() {
        assertThat(service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, leerId, new TeamFilter())).isEmpty();
    }

    @Test
    void aktualisierenZeigtDenNeuenStand() {
        when(buchungsAuskunft.laufendeBuchungenAufProjekt(websiteId)).thenReturn(List.of());

        List<TeamEintrag> team = service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, new TeamFilter());

        assertThat(team).extracting(TeamEintrag::eingestempelt).containsOnly(false);
    }

    @Test
    void eineDeaktivierteZugeordnetePersonBleibtImTeamStehen() {
        MitarbeiterInfo ehemalig = new MitarbeiterInfo(BERND, "P12", "Bernd", "Albers", Rolle.MITARBEITER, false,
                null, null, null, null, null, 0);
        when(mitarbeiterAuskunft.finde(BERND)).thenReturn(Optional.of(ehemalig));

        List<TeamEintrag> team = service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, new TeamFilter());

        assertThat(team).extracting(TeamEintrag::mitarbeiterId).contains(BERND);
    }

    // ---------- Filter der Projektleitung (FA-M3-09, UC-M3-08) ----------

    @Test
    void statusfilterEingestempelt() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(true);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).containsExactly("Anna Berger", "Clara Zander");
    }

    @Test
    void statusfilterNichtEingestempelt() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(false);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).containsExactly("Bernd Albers");
    }

    @Test
    void standortfilterZeigtNurEingestempeltePersonenAnDiesemStandort() {
        TeamFilter filter = new TeamFilter();
        filter.setStandort(Standort.BUERO);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).containsExactly("Anna Berger");
    }

    @Test
    void standortfilterOhneStatusfilterZeigtNiemalsNichtEingestempelte() {
        TeamFilter filter = new TeamFilter();
        filter.setStandort(Standort.HOMEOFFICE);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).containsExactly("Clara Zander");
    }

    @Test
    void statusUndStandortfilterWirkenGemeinsam() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(true);
        filter.setStandort(Standort.HOMEOFFICE);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).containsExactly("Clara Zander");
    }

    @Test
    void nichtEingestempeltMitStandortErgibtNiemanden() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(false);
        filter.setStandort(Standort.BUERO);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).isEmpty();
    }

    @Test
    void standortOhneEingestempeltePersonErgibtEineLeereListe() {
        TeamFilter filter = new TeamFilter();
        filter.setStandort(Standort.UNTERWEGS);

        assertThat(namen(PROJEKTLEITUNG_ID, filter)).isEmpty();
    }

    @Test
    void einNeuerFilterSetztAllesZurueck() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(true);
        assertThat(namen(PROJEKTLEITUNG_ID, filter)).hasSize(2);

        assertThat(namen(PROJEKTLEITUNG_ID, new TeamFilter())).hasSize(3);
    }

    // ---------- Zugeordneter Mitarbeiter: nur Namen (FA-M3-08, QA-M3-03) ----------

    @Test
    void zugeordneterMitarbeiterSiehtNurDieNamenSeinesTeamsEinschliesslichSichSelbst() {
        List<TeamEintrag> team = service.teamAnsehen(BERND, Rolle.MITARBEITER, websiteId, new TeamFilter());

        assertThat(team).containsExactly(
                new TeamEintrag(ANNA, "Anna Berger", null, null, null),
                new TeamEintrag(BERND, "Bernd Albers", null, null, null),
                new TeamEintrag(CLARA, "Clara Zander", null, null, null));
    }

    @Test
    void zugeordneterMitarbeiterSiehtWederStatusNochStandortNochBeginn() {
        List<TeamEintrag> team = service.teamAnsehen(ANNA, Rolle.MITARBEITER, websiteId, new TeamFilter());

        assertThat(team).extracting(TeamEintrag::eingestempelt).containsOnlyNulls();
        assertThat(team).extracting(TeamEintrag::standort).containsOnlyNulls();
        assertThat(team).extracting(TeamEintrag::beginn).containsOnlyNulls();
    }

    @Test
    void fuerEinenMitarbeiterWirdDieBuchungsAuskunftNichtAbgefragt() {
        service.teamAnsehen(ANNA, Rolle.MITARBEITER, websiteId, new TeamFilter());

        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void einFilterBeiEinemMitarbeiterWirdIgnoriertUndVerraetNichtsUeberStatusOderStandort() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(true);
        filter.setStandort(Standort.BUERO);

        List<TeamEintrag> team = service.teamAnsehen(BERND, Rolle.MITARBEITER, websiteId, filter);

        assertThat(team).extracting(TeamEintrag::name).containsExactly("Anna Berger", "Bernd Albers", "Clara Zander");
        assertThat(team).extracting(TeamEintrag::eingestempelt).containsOnlyNulls();
    }

    @Test
    void mitarbeiterSiehtNurDasTeamDesProjektsDemErZugeordnetIst() {
        // Dirk ist nur im Projekt "Shop" und sieht dort nur sein eigenes Team, nicht das von "Website".
        assertThat(service.teamAnsehen(DIRK, Rolle.MITARBEITER, shopId, null))
                .extracting(TeamEintrag::name).containsExactly("Dirk Draussen");
    }

    // ---------- Kein Zugriff (FA-M3-10, QA-A-01, QA-M3-03, UC-M3-07 1a) ----------

    @Test
    void adminHatKeinenZugriffAufDenReiterTeam() {
        assertThatThrownBy(() -> service.teamAnsehen(ADMIN_ID, Rolle.ADMIN, websiteId, new TeamFilter()))
                .isInstanceOf(ZugriffVerweigertException.class);
        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void nichtZugeordneterMitarbeiterHatKeinenZugriff() {
        assertThatThrownBy(() -> service.teamAnsehen(DIRK, Rolle.MITARBEITER, websiteId, new TeamFilter()))
                .isInstanceOf(ZugriffVerweigertException.class);
        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void projektleitungEinesAnderenProjektsHatKeinenZugriff() {
        assertThatThrownBy(() -> service.teamAnsehen(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, new TeamFilter()))
                .isInstanceOf(ZugriffVerweigertException.class);
        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void einMitarbeiterHatKeinenZugriffAufDasTeamEinesProjektsDemErNichtZugeordnetIst() {
        // Clara ist nur "Website" zugeordnet, das Projekt "Leer" darf sie nicht ansehen.
        assertThatThrownBy(() -> service.teamAnsehen(CLARA, Rolle.MITARBEITER, leerId, new TeamFilter()))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void einEntfernterMitarbeiterHatKeinenZugriffMehr() {
        zuordnungRepository.deleteById(new Projektzuordnung.Schluessel(BERND, websiteId));

        assertThatThrownBy(() -> service.teamAnsehen(BERND, Rolle.MITARBEITER, websiteId, new TeamFilter()))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void ohneAnmeldungGibtEsKeinenZugriff() {
        assertThatThrownBy(() -> service.teamAnsehen(0L, null, websiteId, new TeamFilter()))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void einUnbekanntesProjektGibtNichtGefunden() {
        assertThatThrownBy(() -> service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, 9999L, new TeamFilter()))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void einArchiviertesProjektHatKeineTeamUebersicht() {
        assertThatThrownBy(() -> service.teamAnsehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, altprojektId, new TeamFilter()))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("archiviert");
        assertThatThrownBy(() -> service.teamAnsehen(ANNA, Rolle.MITARBEITER, altprojektId, new TeamFilter()))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("archiviert");
    }

    // ---------- istEingestempelt (Hinweis beim Entfernen, US-M3-11) ----------

    @Test
    void projektleitungErfaehrtObEinMitarbeiterGeradeEingestempeltIst() {
        assertThat(service.istEingestempelt(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA)).isTrue();
        assertThat(service.istEingestempelt(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, BERND)).isFalse();
        verify(buchungsAuskunft, times(2)).laufendeBuchungenAufProjekt(websiteId);
    }

    @Test
    void istEingestempeltIstNurFuerDieVerantwortlicheProjektleitungErlaubt() {
        assertThatThrownBy(() -> service.istEingestempelt(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.istEingestempelt(BERND, Rolle.MITARBEITER, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.istEingestempelt(ADMIN_ID, Rolle.ADMIN, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.istEingestempelt(0L, null, websiteId, ANNA))
                .isInstanceOf(NichtAngemeldetException.class);
        verifyNoInteractions(buchungsAuskunft);
    }

    @Test
    void istEingestempeltMitUnbekanntemProjektGibtNichtGefunden() {
        assertThatThrownBy(() -> service.istEingestempelt(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, 9999L, ANNA))
                .isInstanceOf(NichtGefundenException.class);
    }

    // ---------- Hilfsmethoden ----------

    private List<String> namen(long projektleitungId, TeamFilter filter) {
        return service.teamAnsehen(projektleitungId, Rolle.PROJEKTLEITUNG, websiteId, filter).stream()
                .map(TeamEintrag::name).toList();
    }

    private MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle) {
        return new MitarbeiterInfo(id, "P" + id, vorname, nachname, rolle, true, null, null, null, null, null, 0);
    }

    private void zeigeAn(MitarbeiterInfo info) {
        when(mitarbeiterAuskunft.finde(info.mitarbeiterId())).thenReturn(Optional.of(info));
    }

    private BuchungInfo buchung(long mitarbeiterId, Standort standort, Instant beginn) {
        return new BuchungInfo(mitarbeiterId * 100, mitarbeiterId, websiteId, standort, beginn, null, 0, 0, false);
    }

    private long speichereProjekt(String name, long projektleitungId, ProjektStatus status) {
        Projekt projekt = new Projekt(projektleitungId, name, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        projekt.setStatus(status);
        return repository.save(projekt).getProjektId();
    }

    private void ordneZu(long mitarbeiterId, long projektId) {
        zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));
    }
}

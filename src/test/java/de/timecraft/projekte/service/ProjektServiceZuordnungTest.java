package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
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
 * Prüft Zuordnen, Entfernen und die Auswahlliste (UC-M3-05, UC-M3-06, FA-M3-06, FA-M3-07, FA-M3-10)
 * mit einer echten Datenbank (H2 im PostgreSQL-Modus). Nur M1 ist eine Attrappe.
 * <p>
 * Die Personen (alle frei erfunden):
 * <pre>
 * ID  Name              Rolle            aktiv
 * 2   Paula Leitung     PROJEKTLEITUNG   ja   (verantwortet das Projekt "Website")
 * 98  Peter Fremd       PROJEKTLEITUNG   ja   (verantwortet ein anderes Projekt)
 * 3   Adam Admin        ADMIN            ja
 * 11  Anna Berger       MITARBEITER      ja   (dem Projekt "Website" zugeordnet)
 * 12  Bernd Albers      MITARBEITER      ja
 * 13  Clara Zander      MITARBEITER      ja
 * 14  Dirk Alt          MITARBEITER      nein (deaktiviert)
 * </pre>
 * Projekt "Website" ist aktiv, "Altprojekt" ist archiviert (beide gehören der Projektleitung 2).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ProjektService.class, ProjektBerechtigung.class, FesteUhr.class})
class ProjektServiceZuordnungTest {

    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final long FREMDE_PROJEKTLEITUNG_ID = 98L;
    private static final long ADMIN_ID = 3L;
    private static final long ANNA = 11L;
    private static final long BERND = 12L;
    private static final long CLARA = 13L;
    private static final long DIRK_INAKTIV = 14L;

    @Autowired
    private ProjektService service;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    @MockitoBean
    private MitarbeiterAuskunft mitarbeiterAuskunft;

    private long websiteId;
    private long altprojektId;

    @BeforeEach
    void legeBeispieldatenAn() {
        MitarbeiterInfo paula = person(PROJEKTLEITUNG_ID, "Paula", "Leitung", Rolle.PROJEKTLEITUNG, true);
        MitarbeiterInfo peter = person(FREMDE_PROJEKTLEITUNG_ID, "Peter", "Fremd", Rolle.PROJEKTLEITUNG, true);
        MitarbeiterInfo adam = person(ADMIN_ID, "Adam", "Admin", Rolle.ADMIN, true);
        MitarbeiterInfo anna = person(ANNA, "Anna", "Berger", Rolle.MITARBEITER, true);
        MitarbeiterInfo bernd = person(BERND, "Bernd", "Albers", Rolle.MITARBEITER, true);
        MitarbeiterInfo clara = person(CLARA, "Clara", "Zander", Rolle.MITARBEITER, true);
        MitarbeiterInfo dirk = person(DIRK_INAKTIV, "Dirk", "Alt", Rolle.MITARBEITER, false);
        List<MitarbeiterInfo> alle = List.of(paula, peter, adam, anna, bernd, clara, dirk);
        for (MitarbeiterInfo info : alle) {
            when(mitarbeiterAuskunft.finde(info.mitarbeiterId())).thenReturn(Optional.of(info));
        }
        when(mitarbeiterAuskunft.aktive()).thenReturn(alle.stream().filter(MitarbeiterInfo::aktiv).toList());

        websiteId = speichereProjekt("Website", ProjektStatus.AKTIV);
        altprojektId = speichereProjekt("Altprojekt", ProjektStatus.ARCHIVIERT);
        ordneZu(ANNA, websiteId);
        ordneZu(ANNA, altprojektId);
    }

    // ---------- Auswahlliste ----------

    @Test
    void auswahllisteZeigtNurAktiveMitarbeiterDieNochNichtZugeordnetSindSortiertNachName() {
        List<MitarbeiterInfo> auswahl = service.zuordenbareMitarbeiter(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId);

        // Anna ist schon zugeordnet, Dirk ist inaktiv, Paula, Peter und Adam sind keine Mitarbeiter.
        assertThat(auswahl).extracting(MitarbeiterInfo::mitarbeiterId).containsExactly(BERND, CLARA);
    }

    @Test
    void auswahllisteIstLeerWennAlleSchonZugeordnetSind() {
        ordneZu(BERND, websiteId);
        ordneZu(CLARA, websiteId);

        assertThat(service.zuordenbareMitarbeiter(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId)).isEmpty();
    }

    @Test
    void auswahllisteZeigtEinenEntferntenMitarbeiterWiederAn() {
        service.entfernen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA);

        // Sortiert nach Nachname: Albers (Bernd), Berger (Anna), Zander (Clara).
        assertThat(service.zuordenbareMitarbeiter(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId))
                .extracting(MitarbeiterInfo::mitarbeiterId).containsExactly(BERND, ANNA, CLARA);
    }

    @Test
    void auswahllisteIstFuerFremdeProjektleitungMitarbeiterUndAdminVerweigert() {
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(ANNA, Rolle.MITARBEITER, websiteId))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(ADMIN_ID, Rolle.ADMIN, websiteId))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void auswahllisteOhneAnmeldungIstVerweigert() {
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(0L, null, websiteId))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void auswahllisteFuerEinArchiviertesProjektGibtEineMeldung() {
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, altprojektId))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("archiviert");
    }

    @Test
    void auswahllisteFuerEinUnbekanntesProjektGibtNichtGefunden() {
        assertThatThrownBy(() -> service.zuordenbareMitarbeiter(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, 9999L))
                .isInstanceOf(NichtGefundenException.class);
    }

    // ---------- Zuordnen ----------

    @Test
    void projektleitungOrdnetEinenMitarbeiterZuUndDasDatumKommtAusDerUhr() {
        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND));

        Projektzuordnung gespeichert = zuordnungRepository.findById(new Projektzuordnung.Schluessel(BERND, websiteId)).orElseThrow();
        assertThat(gespeichert.getZugeordnetAm()).isEqualTo(FesteUhr.JETZT);
        assertThat(alleZugeordneten(websiteId)).containsExactlyInAnyOrder(ANNA, BERND);
    }

    @Test
    void projektleitungOrdnetMehrereMitarbeiterAufEinmalZu() {
        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND, CLARA));

        assertThat(alleZugeordneten(websiteId)).containsExactlyInAnyOrder(ANNA, BERND, CLARA);
    }

    @Test
    void einMitarbeiterKannMehrerenProjektenZugeordnetSein() {
        Long zweitesProjekt = speichereProjekt("Shop", ProjektStatus.AKTIV);

        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND));
        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, zweitesProjekt, List.of(BERND));

        assertThat(zuordnungRepository.findByMitarbeiterId(BERND)).hasSize(2);
    }

    @Test
    void dieselbePersonZweimalInDerListeWirdEinmalGespeichert() {
        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND, BERND));

        assertThat(zuordnungRepository.findByMitarbeiterId(BERND)).hasSize(1);
    }

    @Test
    void zugeordneterMitarbeiterSiehtDasProjektDanachInSeinerListe() {
        assertThat(service.projekteSehen(BERND, Rolle.MITARBEITER, new ProjektFilter())).isEmpty();

        service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND));

        assertThat(service.projekteSehen(BERND, Rolle.MITARBEITER, new ProjektFilter()))
                .extracting(Projekt::getProjektId).containsExactly(websiteId);
    }

    @Test
    void bereitsZugeordnetePersonGibtEineMeldungUndNichtsWirdGespeichert() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(ANNA)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("Anna Berger")
                .hasMessageContaining("bereits zugeordnet");
        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void einFehlerBeiEinerPersonVerhindertDasSpeichernAllerAnderen() {
        // Bernd wäre in Ordnung, Anna ist schon zugeordnet: Es wird gar nichts gespeichert.
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND, ANNA)))
                .isInstanceOf(FachlicheException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void projektleitungKannSichNichtSelbstZuordnen() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId,
                List.of(BERND, PROJEKTLEITUNG_ID)))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessageContaining("selbst");

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void mitarbeiterKannSichPerDirektemAufrufNichtSelbstZuordnen() {
        assertThatThrownBy(() -> service.zuordnen(BERND, Rolle.MITARBEITER, websiteId, List.of(BERND)))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void zugeordneterMitarbeiterDarfKeinenKollegenZuordnen() {
        assertThatThrownBy(() -> service.zuordnen(ANNA, Rolle.MITARBEITER, websiteId, List.of(CLARA)))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void fremdeProjektleitungDarfNichtZuordnen() {
        assertThatThrownBy(() -> service.zuordnen(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(BERND)))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void adminDarfNichtZuordnen() {
        assertThatThrownBy(() -> service.zuordnen(ADMIN_ID, Rolle.ADMIN, websiteId, List.of(BERND)))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void ohneAnmeldungWirdNichtZugeordnet() {
        assertThatThrownBy(() -> service.zuordnen(0L, null, websiteId, List.of(BERND)))
                .isInstanceOf(NichtAngemeldetException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void ineinemArchiviertenProjektWirdNichtZugeordnet() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, altprojektId, List.of(BERND)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("archiviert");

        assertThat(alleZugeordneten(altprojektId)).containsExactly(ANNA);
    }

    @Test
    void unbekanntesProjektGibtNichtGefunden() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, 9999L, List.of(BERND)))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void ohneAuswahlGibtEsEineMeldung() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of()))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("mindestens einen Mitarbeiter");
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, null))
                .isInstanceOf(FachlicheException.class);
    }

    @Test
    void unbekanntePersonKannNichtZugeordnetWerden() {
        when(mitarbeiterAuskunft.finde(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(777L)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("nicht gefunden");
    }

    @Test
    void inaktiverMitarbeiterKannNichtZugeordnetWerden() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(DIRK_INAKTIV)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("nicht mehr aktiv");

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void projektleitungUndAdminKoennenNichtAlsMitarbeiterZugeordnetWerden() {
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(FREMDE_PROJEKTLEITUNG_ID)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("Rolle Mitarbeiter");
        assertThatThrownBy(() -> service.zuordnen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, List.of(ADMIN_ID)))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("Rolle Mitarbeiter");

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    // ---------- Entfernen ----------

    @Test
    void projektleitungEntferntNurDieGewaehlteZuordnung() {
        ordneZu(BERND, websiteId);

        service.entfernen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA);

        assertThat(alleZugeordneten(websiteId)).containsExactly(BERND);
        // Annas Zuordnung zum anderen Projekt bleibt bestehen.
        assertThat(alleZugeordneten(altprojektId)).containsExactly(ANNA);
    }

    @Test
    void entfernenLaesstDasProjektUndSeineDatenBestehen() {
        service.entfernen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA);

        Projekt projekt = repository.findById(websiteId).orElseThrow();
        assertThat(projekt.getName()).isEqualTo("Website");
        assertThat(projekt.getStatus()).isEqualTo(ProjektStatus.AKTIV);
    }

    @Test
    void nichtZugeordneterMitarbeiterKannNichtEntferntWerden() {
        assertThatThrownBy(() -> service.entfernen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, BERND))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("nicht zugeordnet");
    }

    @Test
    void fremdeProjektleitungMitarbeiterUndAdminDuerfenNichtEntfernen() {
        assertThatThrownBy(() -> service.entfernen(FREMDE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.entfernen(ANNA, Rolle.MITARBEITER, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.entfernen(ADMIN_ID, Rolle.ADMIN, websiteId, ANNA))
                .isInstanceOf(ZugriffVerweigertException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void ohneAnmeldungWirdNichtEntfernt() {
        assertThatThrownBy(() -> service.entfernen(0L, null, websiteId, ANNA))
                .isInstanceOf(NichtAngemeldetException.class);

        assertThat(alleZugeordneten(websiteId)).containsExactly(ANNA);
    }

    @Test
    void ausEinemArchiviertenProjektWirdNichtEntfernt() {
        assertThatThrownBy(() -> service.entfernen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, altprojektId, ANNA))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("archiviert");

        assertThat(alleZugeordneten(altprojektId)).containsExactly(ANNA);
    }

    // ---------- Hilfsmethoden ----------

    private MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle, boolean aktiv) {
        return new MitarbeiterInfo(id, "P" + id, vorname, nachname, rolle, aktiv, null, null, null, null, null, 0);
    }

    private long speichereProjekt(String name, ProjektStatus status) {
        Projekt projekt = new Projekt(PROJEKTLEITUNG_ID, name, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        projekt.setStatus(status);
        return repository.save(projekt).getProjektId();
    }

    private void ordneZu(long mitarbeiterId, long projektId) {
        zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));
    }

    private List<Long> alleZugeordneten(long projektId) {
        return zuordnungRepository.findByProjektId(projektId).stream().map(Projektzuordnung::getMitarbeiterId).toList();
    }
}

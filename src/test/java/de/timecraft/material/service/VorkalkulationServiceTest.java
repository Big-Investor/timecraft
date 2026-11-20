package de.timecraft.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.material.fake.FakeProjektAuskunft;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.model.Protokollaktion;
import de.timecraft.material.repository.MaterialRepository;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.material.repository.MaterialprotokollRepository;
import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({VorkalkulationService.class, ProjektZugriff.class, VorkalkulationServiceTest.Testumgebung.class})
class VorkalkulationServiceTest {

    static final long MITARBEITER = 1;
    static final long LENA_PL = 2;
    static final long ADMIN = 3;
    static final long OTTO_PL = 7;
    static final long KUECHE = 1024;
    static final long ARCHIV = 1001;

    @TestConfiguration
    static class Testumgebung {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-05T08:15:00Z"), ZoneId.of("Europe/Berlin"));
        }

        @Bean
        FakeProjektAuskunft projektAuskunft() {
            FakeProjektAuskunft projekte = new FakeProjektAuskunft();
            projekte.hinzufuegen(projekt(KUECHE, LENA_PL, true), MITARBEITER, LENA_PL);
            projekte.hinzufuegen(projekt(ARCHIV, LENA_PL, false), MITARBEITER);
            return projekte;
        }

        private static ProjektInfo projekt(long id, long projektleitung, boolean aktiv) {
            return new ProjektInfo(id, "Projekt " + id, LocalDate.of(2026, 9, 1), null, projektleitung, aktiv, null);
        }
    }

    @Autowired
    private VorkalkulationService service;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @Autowired
    private MaterialprotokollRepository protokollRepository;

    private Material eiche;
    private Material arbeitsplatte;

    @BeforeEach
    void stammAnlegen() {
        eiche = materialRepository.save(new Material("ART-1102", "Eiche Massivholzplatte 26 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("89.50"), new BigDecimal("129.00")));
        arbeitsplatte = materialRepository.save(new Material("ART-2040", "Arbeitsplatte Schichtstoff 38 mm", "Holz",
                Einheit.LAUFMETER, new BigDecimal("68.00"), new BigDecimal("98.00")));
    }

    private Material material(String nummer, String ek, String vk) {
        return materialRepository.save(new Material(nummer, "Test " + nummer, null, Einheit.STUECK,
                new BigDecimal(ek), new BigDecimal(vk)));
    }

    @Test
    void verantwortlicheProjektleitungBuchtPositionUndBekommtVorkalkulationWieImMockup() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(), new BigDecimal("12.0"));
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, arbeitsplatte.getId(),
                new BigDecimal("4.2"));

        Vorkalkulation kalkulation = service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        Kalkulationszeile zeile = kalkulation.zeilen().get(0);
        assertThat(zeile.bezeichnung()).isEqualTo("Eiche Massivholzplatte 26 mm");
        assertThat(zeile.sollMenge()).isEqualByComparingTo("12.00");
        assertThat(zeile.kosten()).isEqualByComparingTo("1074.00");
        assertThat(zeile.verkauf()).isEqualByComparingTo("1548.00");
        assertThat(zeile.deckungsbeitrag()).isEqualByComparingTo("474.00");
        assertThat(kalkulation.zeilen().get(1).kosten()).isEqualByComparingTo("285.60");
        assertThat(kalkulation.summeKosten()).isEqualByComparingTo("1359.60");
        assertThat(kalkulation.summeVerkauf()).isEqualByComparingTo("1959.60");
        assertThat(kalkulation.summeDeckungsbeitrag()).isEqualByComparingTo("600.00");
    }

    @Test
    void buchungSchreibtProtokollGebuchtMitPersonUndFesterUhrzeit() {
        Materialposition position = service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE,
                eiche.getId(), new BigDecimal("12"));

        List<Materialprotokoll> historie = protokollRepository.findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(KUECHE);
        assertThat(historie).hasSize(1);
        Materialprotokoll eintrag = historie.get(0);
        assertThat(eintrag.getMaterialposition().getId()).isEqualTo(position.getId());
        assertThat(eintrag.getAktion()).isEqualTo(Protokollaktion.GEBUCHT);
        assertThat(eintrag.getGeaendertVonId()).isEqualTo(LENA_PL);
        assertThat(eintrag.getZeitpunkt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 15));
        assertThat(eintrag.getAlterWert()).isNull();
        assertThat(eintrag.getNeuerWert()).isEqualByComparingTo("12.00");
    }

    @Test
    void zeilenWerdenKaufmaennischGerundetUndSummenAddierenDieGerundetenZeilen() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, material("R-1", "0.33", "0.35").getId(),
                new BigDecimal("2.5"));
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, material("R-2", "1.15", "1.17").getId(),
                new BigDecimal("1.15"));

        Vorkalkulation kalkulation = service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        // 2,5 x 0,33 = 0,825 -> 0,83 (HALF_UP); 1,15 x 1,15 = 1,3225 -> 1,32
        assertThat(kalkulation.zeilen()).extracting(Kalkulationszeile::kosten)
                .containsExactly(new BigDecimal("0.83"), new BigDecimal("1.32"));
        // 2,5 x 0,35 = 0,875 -> 0,88; 1,15 x 1,17 = 1,3455 -> 1,35
        assertThat(kalkulation.zeilen()).extracting(Kalkulationszeile::verkauf)
                .containsExactly(new BigDecimal("0.88"), new BigDecimal("1.35"));
        assertThat(kalkulation.summeKosten()).isEqualTo(new BigDecimal("2.15"));
        assertThat(kalkulation.summeVerkauf()).isEqualTo(new BigDecimal("2.23"));
        assertThat(kalkulation.summeDeckungsbeitrag()).isEqualTo(new BigDecimal("0.08"));
    }

    @Test
    void sollMengeWirdAufZweiStellenGerundet() {
        Materialposition position = service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE,
                eiche.getId(), new BigDecimal("3.605"));

        assertThat(position.getSollMenge()).isEqualTo(new BigDecimal("3.61"));
    }

    @Test
    void negativerDeckungsbeitragWirdAusgewiesen() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, material("V-1", "10.00", "8.00").getId(),
                new BigDecimal("3"));

        assertThat(service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE).summeDeckungsbeitrag())
                .isEqualByComparingTo("-6.00");
    }

    @Test
    void leeresProjektHatSummenNull() {
        Vorkalkulation kalkulation = service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        assertThat(kalkulation.zeilen()).isEmpty();
        assertThat(kalkulation.summeKosten()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void ungueltigeMengenWerdenAbgelehntUndNichtsGespeichert() {
        for (String menge : new String[] {"0", "-1", "0.004"}) {
            assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE,
                    eiche.getId(), new BigDecimal(menge)))
                    .isInstanceOf(FachlicheException.class)
                    .hasMessage("Bitte geben Sie die Soll-Menge größer als 0 ein.");
        }
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(),
                new BigDecimal("100000000")))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie die Soll-Menge kleiner als 100.000.000 ein.");
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(),
                null)).isInstanceOf(FachlicheException.class).hasMessage("Bitte geben Sie die Soll-Menge ein.");
        assertThat(positionRepository.count()).isZero();
        assertThat(protokollRepository.findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(KUECHE)).isEmpty();
    }

    @Test
    void abgeschlossenesProjektSperrtNeuePositionen() {
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, ARCHIV, eiche.getId(),
                BigDecimal.ONE))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Projekt ist abgeschlossen und kann nicht mehr geändert werden.");
        assertThat(positionRepository.count()).isZero();
    }

    @Test
    void vorkalkulationEinesAbgeschlossenenProjektsBleibtLesbar() {
        assertThat(service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, ARCHIV).zeilen()).isEmpty();
    }

    @Test
    void deaktiviertesMaterialKannNichtEingeplantWerden() {
        eiche.deaktivieren();
        materialRepository.save(eiche);

        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(FachlicheException.class);
        assertThat(positionRepository.count()).isZero();
    }

    @Test
    void schonEingeplantesMaterialZaehltNachDemDeaktivierenWeiterMit() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(), BigDecimal.ONE);
        eiche.deaktivieren();
        materialRepository.save(eiche);

        assertThat(service.vorkalkulation(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE).summeKosten())
                .isEqualByComparingTo("89.50");
    }

    @Test
    void jedesMaterialNurEinmalJeProjekt() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(), BigDecimal.ONE);

        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(),
                BigDecimal.TEN))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("bereits eingeplant");
        assertThat(positionRepository.count()).isEqualTo(1);
    }

    @Test
    void unbekanntesProjektOderMaterialWirdGemeldet() {
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, 4711, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, 999,
                BigDecimal.ONE)).isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void nurDieVerantwortlicheProjektleitungPlant() {
        assertThatThrownBy(() -> service.positionHinzufuegen(OTTO_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.positionHinzufuegen(ADMIN, Rolle.ADMIN, KUECHE, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.positionHinzufuegen(MITARBEITER, Rolle.MITARBEITER, KUECHE, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.positionHinzufuegen(LENA_PL, null, KUECHE, eiche.getId(),
                BigDecimal.ONE)).isInstanceOf(NichtAngemeldetException.class);
        assertThat(positionRepository.count()).isZero();
    }

    @Test
    void adminSiehtVorkalkulationAndereRollenNicht() {
        service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, eiche.getId(), BigDecimal.ONE);

        assertThat(service.vorkalkulation(ADMIN, Rolle.ADMIN, KUECHE).zeilen()).hasSize(1);
        assertThatThrownBy(() -> service.vorkalkulation(OTTO_PL, Rolle.PROJEKTLEITUNG, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.vorkalkulation(MITARBEITER, Rolle.MITARBEITER, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.vorkalkulation(LENA_PL, null, KUECHE))
                .isInstanceOf(NichtAngemeldetException.class);
    }
}

package de.timecraft.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.material.fake.FakeProjektAuskunft;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.model.Protokollaktion;
import de.timecraft.material.repository.AnpassungsantragRepository;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AnpassungsantragService.class, ProjektZugriff.class, AnpassungsantragServiceTest.Testumgebung.class})
class AnpassungsantragServiceTest {

    static final long JONAS = 1;
    static final long LENA_PL = 2;
    static final long ADMIN = 3;
    static final long MEHMET = 4;
    static final long FREMD = 5;
    static final long OTTO_PL = 7;
    static final long KUECHE = 1024;
    static final long ARCHIV = 1001;
    static final Instant JETZT = Instant.parse("2026-10-08T07:30:00Z");

    @TestConfiguration
    static class Testumgebung {

        @Bean
        Clock clock() {
            return Clock.fixed(JETZT, ZoneId.of("Europe/Berlin"));
        }

        @Bean
        FakeProjektAuskunft projektAuskunft() {
            FakeProjektAuskunft projekte = new FakeProjektAuskunft();
            projekte.hinzufuegen(projekt(KUECHE, true), JONAS, MEHMET, OTTO_PL);
            projekte.hinzufuegen(projekt(ARCHIV, false), JONAS);
            return projekte;
        }

        private static ProjektInfo projekt(long id, boolean aktiv) {
            return new ProjektInfo(id, "Projekt " + id, LocalDate.of(2026, 9, 1), null, LENA_PL, aktiv, null);
        }
    }

    @Autowired
    private AnpassungsantragService service;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @Autowired
    private MaterialprotokollRepository protokollRepository;

    @Autowired
    private AnpassungsantragRepository antragRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Materialposition multiplex;
    private Materialposition imArchiv;

    @BeforeEach
    void positionenAnlegen() {
        Material material = materialRepository.save(new Material("ART-1215", "Multiplex Birke 18 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("42.00"), new BigDecimal("61.00")));
        multiplex = positionRepository.save(new Materialposition(KUECHE, material, new BigDecimal("16.00")));
        imArchiv = positionRepository.save(new Materialposition(ARCHIV, material, new BigDecimal("5.00")));
    }

    private Materialanpassungsantrag antragVonJonas() {
        return service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(), new BigDecimal("2.5"),
                Mehrbedarfsgrund.VERSCHNITT, "Verschnitt bei Hochschrankseiten");
    }

    private Materialanpassungsantrag neuGeladen(long antragId) {
        entityManager.flush();
        entityManager.clear();
        return antragRepository.findById(antragId).orElseThrow();
    }

    private List<Materialprotokoll> historie() {
        entityManager.flush();
        return protokollRepository.findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(KUECHE);
    }

    // --- Einreichen (FA-M4-04) ---

    @Test
    void mitarbeiterReichtMehrbedarfEinStatusOffenMengeUnveraendert() {
        Materialanpassungsantrag antrag = antragVonJonas();

        Materialanpassungsantrag gelesen = neuGeladen(antrag.getId());
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(gelesen.getAntragstellerId()).isEqualTo(JONAS);
        assertThat(gelesen.getEingereichtAm()).isEqualTo(JETZT);
        assertThat(gelesen.getBegruendung()).isEqualTo("Verschnitt: Verschnitt bei Hochschrankseiten");
        assertThat(gelesen.getZusatzmenge()).isEqualByComparingTo("2.50");
        assertThat(gelesen.getMaterialposition().getSollMenge()).isEqualByComparingTo("16.00");
        assertThat(historie()).isEmpty();
    }

    @Test
    void ohneGrundWirdNurDerTextGespeichert() {
        Materialanpassungsantrag antrag = service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(),
                BigDecimal.ONE, null, "  Kunde will hoehere Schraenke  ");

        assertThat(antrag.getBegruendung()).isEqualTo("Kunde will hoehere Schraenke");
    }

    @Test
    void ohneBegruendungOderMitUngueltigerMengeWirdNichtsGespeichert() {
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(), BigDecimal.ONE,
                Mehrbedarfsgrund.VERSCHNITT, "   "))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte begründen Sie den Mehrbedarf.");
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(), BigDecimal.ZERO,
                Mehrbedarfsgrund.VERSCHNITT, "Text"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie die zusätzliche Menge größer als 0 ein.");
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(),
                new BigDecimal("-1"), null, "Text")).isInstanceOf(FachlicheException.class);
        assertThat(antragRepository.count()).isZero();
    }

    @Test
    void begruendungPasstMitGrundGenauInDieSpalte() {
        String lang = "x".repeat(AnpassungsantragService.MAX_FREITEXT);
        Materialanpassungsantrag antrag = service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(),
                BigDecimal.ONE, Mehrbedarfsgrund.KUNDENAENDERUNG, lang);

        assertThat(neuGeladen(antrag.getId()).getBegruendung()).hasSize(255);
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(), BigDecimal.ONE,
                null, lang + "x")).isInstanceOf(FachlicheException.class);
    }

    @Test
    void nichtZugeordnetePersonenUndAdminDuerfenNichtBeantragen() {
        assertThatThrownBy(() -> service.einreichen(FREMD, Rolle.MITARBEITER, multiplex.getId(), BigDecimal.ONE,
                null, "Text"))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie sind diesem Projekt nicht zugeordnet.");
        assertThatThrownBy(() -> service.einreichen(ADMIN, Rolle.ADMIN, multiplex.getId(), BigDecimal.ONE,
                null, "Text")).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.einreichen(JONAS, null, multiplex.getId(), BigDecimal.ONE,
                null, "Text")).isInstanceOf(NichtAngemeldetException.class);
        assertThat(antragRepository.count()).isZero();
    }

    @Test
    void zugeordneteFremdeProjektleitungStelltNormalenAntrag() {
        Materialanpassungsantrag antrag = service.einreichen(OTTO_PL, Rolle.PROJEKTLEITUNG, multiplex.getId(),
                BigDecimal.ONE, null, "Text");

        assertThat(antrag.getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }

    @Test
    void verantwortlicheProjektleitungErhoehtPerAntragSofortGenehmigt() {
        Materialanpassungsantrag antrag = service.einreichen(LENA_PL, Rolle.PROJEKTLEITUNG, multiplex.getId(),
                new BigDecimal("2.5"), Mehrbedarfsgrund.KUNDENAENDERUNG, "Kunde wuenscht Zusatzschrank");

        Materialanpassungsantrag gelesen = neuGeladen(antrag.getId());
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(gelesen.getEntscheiderId()).isEqualTo(LENA_PL);
        assertThat(gelesen.getMaterialposition().getSollMenge()).isEqualByComparingTo("18.50");
        Materialprotokoll eintrag = historie().get(0);
        assertThat(eintrag.getAktion()).isEqualTo(Protokollaktion.GENEHMIGT);
        assertThat(eintrag.getAlterWert()).isEqualByComparingTo("16.00");
        assertThat(eintrag.getNeuerWert()).isEqualByComparingTo("18.50");
    }

    @Test
    void imAbgeschlossenenProjektKeinMehrbedarf() {
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, imArchiv.getId(), BigDecimal.ONE,
                null, "Text"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Projekt ist abgeschlossen. Das Material kann nicht mehr geändert werden.");
        assertThat(antragRepository.count()).isZero();
    }

    @Test
    void unbekannteDatensaetzeWerdenGemeldet() {
        assertThatThrownBy(() -> service.einreichen(JONAS, Rolle.MITARBEITER, 999, BigDecimal.ONE, null, "Text"))
                .isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, 999))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Der Antrag wurde nicht gefunden.");
    }

    // --- Entscheiden (FA-M4-05, FA-M4-06, QA-M4-01) ---

    @Test
    void freigabeErhoehtSollMengeUndProtokolliertGenehmigt() {
        Materialanpassungsantrag antrag = antragVonJonas();

        service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId());

        Materialanpassungsantrag gelesen = neuGeladen(antrag.getId());
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(gelesen.getEntscheiderId()).isEqualTo(LENA_PL);
        assertThat(gelesen.getEntschiedenAm()).isEqualTo(JETZT);
        assertThat(gelesen.getMaterialposition().getSollMenge()).isEqualByComparingTo("18.50");
        Materialprotokoll eintrag = historie().get(0);
        assertThat(eintrag.getAktion()).isEqualTo(Protokollaktion.GENEHMIGT);
        assertThat(eintrag.getGeaendertVonId()).isEqualTo(LENA_PL);
        assertThat(eintrag.getZeitpunkt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 9, 30));
        assertThat(eintrag.getAlterWert()).isEqualByComparingTo("16.00");
        assertThat(eintrag.getNeuerWert()).isEqualByComparingTo("18.50");
    }

    @Test
    void ablehnungBrauchtKommentarUndLaesstMengenUnveraendert() {
        Materialanpassungsantrag antrag = antragVonJonas();

        assertThatThrownBy(() -> service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), " "))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte begründen Sie die Ablehnung.");
        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.OFFEN);

        service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), "Reststueck aus Lager verwenden.");

        Materialanpassungsantrag gelesen = neuGeladen(antrag.getId());
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
        assertThat(gelesen.getEntscheidungshinweis()).isEqualTo("Reststueck aus Lager verwenden.");
        assertThat(gelesen.getEntscheiderId()).isEqualTo(LENA_PL);
        assertThat(gelesen.getMaterialposition().getSollMenge()).isEqualByComparingTo("16.00");
        assertThat(historie()).isEmpty();
    }

    @Test
    void schonEntschiedenerAntragKannNichtNochmalEntschiedenWerden() {
        Materialanpassungsantrag antrag = antragVonJonas();
        service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId());

        assertThatThrownBy(() -> service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId()))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("nicht mehr offen");
        assertThatThrownBy(() -> service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), "zu spaet"))
                .isInstanceOf(FachlicheException.class);
        assertThat(neuGeladen(antrag.getId()).getMaterialposition().getSollMenge()).isEqualByComparingTo("18.50");
        assertThat(historie()).hasSize(1);
    }

    @Test
    void nurDieVerantwortlicheProjektleitungEntscheidet() {
        Materialanpassungsantrag antrag = antragVonJonas();

        assertThatThrownBy(() -> service.freigeben(OTTO_PL, Rolle.PROJEKTLEITUNG, antrag.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.freigeben(ADMIN, Rolle.ADMIN, antrag.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.freigeben(JONAS, Rolle.MITARBEITER, antrag.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.ablehnen(OTTO_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), "nein"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.freigeben(LENA_PL, null, antrag.getId()))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }

    @Test
    void imAbgeschlossenenProjektKeineFreigabeAberAblehnung() {
        Materialanpassungsantrag offen = new Materialanpassungsantrag(imArchiv, BigDecimal.ONE);
        offen.setAntragstellerId(JONAS);
        offen.setEingereichtAm(JETZT);
        offen.setBegruendung("vor dem Abschluss gestellt");
        Materialanpassungsantrag antrag = antragRepository.save(offen);

        assertThatThrownBy(() -> service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId()))
                .isInstanceOf(FachlicheException.class)
                .hasMessageContaining("abgeschlossen");
        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(positionRepository.findById(imArchiv.getId()).orElseThrow().getSollMenge())
                .isEqualByComparingTo("5.00");

        service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), "Projekt ist abgeschlossen.");

        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
    }

    @Test
    void zuGrosseNeueSollMengeWirdNichtGenehmigtUndNichtsGespeichert() {
        Materialposition gross = positionRepository.save(new Materialposition(KUECHE,
                multiplex.getMaterial(), new BigDecimal("99999999.00")));
        Materialanpassungsantrag antrag = service.einreichen(JONAS, Rolle.MITARBEITER, gross.getId(),
                BigDecimal.ONE, null, "Text");

        assertThatThrownBy(() -> service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId()))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die neue Soll-Menge wäre zu groß. Bitte prüfen Sie die Menge.");
        assertThatThrownBy(() -> service.einreichen(LENA_PL, Rolle.PROJEKTLEITUNG, gross.getId(), BigDecimal.ONE,
                null, "Text")).isInstanceOf(FachlicheException.class);

        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(antragRepository.count()).isEqualTo(1);
        assertThat(positionRepository.findById(gross.getId()).orElseThrow().getSollMenge())
                .isEqualByComparingTo("99999999.00");
        assertThat(historie()).isEmpty();
    }

    // --- Zurueckziehen ---

    @Test
    void antragstellerZiehtOffenenAntragZurueck() {
        Materialanpassungsantrag antrag = antragVonJonas();

        service.zurueckziehen(JONAS, Rolle.MITARBEITER, antrag.getId());

        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.ZURUECKGEZOGEN);
        assertThatThrownBy(() -> service.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId()))
                .isInstanceOf(FachlicheException.class);
    }

    @Test
    void fremderOderEntschiedenerAntragKannNichtZurueckgezogenWerden() {
        Materialanpassungsantrag antrag = antragVonJonas();

        assertThatThrownBy(() -> service.zurueckziehen(MEHMET, Rolle.MITARBEITER, antrag.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, antrag.getId(), "nein");
        assertThatThrownBy(() -> service.zurueckziehen(JONAS, Rolle.MITARBEITER, antrag.getId()))
                .isInstanceOf(FachlicheException.class);
        assertThat(neuGeladen(antrag.getId()).getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
    }

    // --- Lesen ---

    @Test
    void projektleitungSiehtOffeneAntraegeMitAltNeuUndKostendifferenz() {
        antragVonJonas();
        Materialanpassungsantrag erledigt = service.einreichen(MEHMET, Rolle.MITARBEITER, multiplex.getId(),
                BigDecimal.ONE, null, "Text");
        service.zurueckziehen(MEHMET, Rolle.MITARBEITER, erledigt.getId());

        List<AntragZurPruefung> offene = service.offeneAntraege(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        assertThat(offene).hasSize(1);
        AntragZurPruefung pruefung = offene.get(0);
        assertThat(pruefung.antragstellerId()).isEqualTo(JONAS);
        assertThat(pruefung.alteMenge()).isEqualByComparingTo("16.00");
        assertThat(pruefung.neueMenge()).isEqualByComparingTo("18.50");
        assertThat(pruefung.kostendifferenz()).isEqualByComparingTo("105.00");
        assertThat(pruefung.begruendung()).isEqualTo("Verschnitt: Verschnitt bei Hochschrankseiten");
        assertThat(service.offeneAntraege(ADMIN, Rolle.ADMIN, KUECHE)).hasSize(1);
        assertThatThrownBy(() -> service.offeneAntraege(JONAS, Rolle.MITARBEITER, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.offeneAntraege(OTTO_PL, Rolle.PROJEKTLEITUNG, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void mitarbeiterSiehtEigeneAntraegeNeuesteZuerstMitAblehnungsgrund() {
        Materialanpassungsantrag erster = antragVonJonas();
        service.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, erster.getId(), "Reststueck verwenden.");
        service.einreichen(MEHMET, Rolle.MITARBEITER, multiplex.getId(), BigDecimal.ONE, null, "Mehmet");
        Materialanpassungsantrag zweiter = service.einreichen(JONAS, Rolle.MITARBEITER, multiplex.getId(),
                BigDecimal.ONE, null, "noch mehr");

        List<MeinMehrbedarf> meine = service.meineAntraege(JONAS, Rolle.MITARBEITER);

        assertThat(meine).extracting(MeinMehrbedarf::antragId).containsExactly(zweiter.getId(), erster.getId());
        meine = meine.subList(1, 2);
        assertThat(meine.get(0).bezeichnung()).isEqualTo("Multiplex Birke 18 mm");
        assertThat(meine.get(0).einheit()).isEqualTo(Einheit.QUADRATMETER);
        assertThat(meine.get(0).status()).isEqualTo(Antragsstatus.ABGELEHNT);
        assertThat(meine.get(0).entscheidungshinweis()).isEqualTo("Reststueck verwenden.");
        assertThatThrownBy(() -> service.meineAntraege(JONAS, null)).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void geplanteMaterialienOhnePreiseNurFuerBeteiligte() {
        List<PlanungOhnePreis> geplant = service.geplanteMaterialien(JONAS, Rolle.MITARBEITER, KUECHE);

        assertThat(geplant).containsExactly(new PlanungOhnePreis(multiplex.getId(), "Multiplex Birke 18 mm",
                Einheit.QUADRATMETER, new BigDecimal("16.00")));
        assertThat(service.geplanteMaterialien(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE)).hasSize(1);
        assertThatThrownBy(() -> service.geplanteMaterialien(FREMD, Rolle.MITARBEITER, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.geplanteMaterialien(ADMIN, Rolle.ADMIN, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
    }
}

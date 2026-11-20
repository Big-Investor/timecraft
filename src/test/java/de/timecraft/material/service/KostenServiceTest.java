package de.timecraft.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.material.fake.FakeAngebotspreisPflege;
import de.timecraft.material.fake.FakeBuchungsAuskunft;
import de.timecraft.material.fake.FakeMitarbeiterAuskunft;
import de.timecraft.material.fake.FakeProjektAuskunft;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Kostendeckung;
import de.timecraft.material.model.Kostenuebersicht;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.repository.MaterialRepository;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Import({KostenService.class, ProjektZugriff.class, Personennamen.class, KostenServiceTest.Testumgebung.class})
class KostenServiceTest {

    static final long JONAS = 1;
    static final long LENA_PL = 2;
    static final long ADMIN = 3;
    static final long MEHMET = 4;
    static final long NEU_OHNE_SATZ = 5;
    static final long OTTO_PL = 7;
    static final long ANNA = 8;
    static final long BEN = 9;
    static final long KUECHE = 1024;
    static final long OHNE_PREIS = 1031;
    static final long ARCHIV = 1001;
    static final long RUNDUNG = 1050;
    static final long OHNE_START = 1060;

    @TestConfiguration
    static class Testumgebung {

        /** Heute ist der 20.11.2026, das Kuechenprojekt startete im September. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneId.of("Europe/Berlin"));
        }

        @Bean
        FakeProjektAuskunft projektAuskunft() {
            FakeProjektAuskunft projekte = new FakeProjektAuskunft();
            projekte.hinzufuegen(projekt(KUECHE, true, new BigDecimal("9800.00")), JONAS, MEHMET);
            projekte.hinzufuegen(projekt(OHNE_PREIS, true, null), JONAS);
            projekte.hinzufuegen(projekt(ARCHIV, false, new BigDecimal("100.00")), JONAS);
            projekte.hinzufuegen(projekt(RUNDUNG, true, null), ANNA, BEN);
            projekte.hinzufuegen(new ProjektInfo(OHNE_START, "Ohne Start", null, null, LENA_PL, true, null), JONAS);
            return projekte;
        }

        @Bean
        FakeAngebotspreisPflege angebotspreisPflege(FakeProjektAuskunft projekte) {
            return new FakeAngebotspreisPflege(projekte);
        }

        @Bean
        FakeMitarbeiterAuskunft mitarbeiterAuskunft() {
            FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
            personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, new BigDecimal("28.00"));
            personen.hinzufuegen(MEHMET, "Mehmet", "Yilmaz", Rolle.MITARBEITER, new BigDecimal("28.00"));
            personen.hinzufuegen(NEU_OHNE_SATZ, "Mia", "Fischer", Rolle.MITARBEITER, null);
            personen.hinzufuegen(ANNA, "Anna", "Klein", Rolle.MITARBEITER, new BigDecimal("0.30"));
            personen.hinzufuegen(BEN, "Ben", "Wolf", Rolle.MITARBEITER, new BigDecimal("0.30"));
            return personen;
        }

        @Bean
        FakeBuchungsAuskunft buchungsAuskunft() {
            FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
            buchungen.segment(JONAS, KUECHE, LocalDate.of(2026, 9, 14), 3000);
            buchungen.segment(JONAS, KUECHE, LocalDate.of(2026, 11, 19), 2000);
            buchungen.segment(MEHMET, KUECHE, LocalDate.of(2026, 10, 7), 3835);
            buchungen.segment(JONAS, OHNE_PREIS, LocalDate.of(2026, 10, 7), 600);
            buchungen.segment(NEU_OHNE_SATZ, OHNE_PREIS, LocalDate.of(2026, 10, 8), 480);
            // je 1 min x 0,30 €/h = 0,005 € (genau auf der Rundungsgrenze)
            buchungen.segment(ANNA, RUNDUNG, LocalDate.of(2026, 10, 1), 1);
            buchungen.segment(BEN, RUNDUNG, LocalDate.of(2026, 10, 1), 1);
            // Projekt ohne Startdatum: nur der aktuelle Monat zaehlt
            buchungen.segment(JONAS, OHNE_START, LocalDate.of(2026, 11, 2), 60);
            buchungen.segment(JONAS, OHNE_START, LocalDate.of(2026, 10, 30), 60);
            // vor dem Projektstart und nach heute: zaehlen nicht
            buchungen.segment(JONAS, KUECHE, LocalDate.of(2026, 8, 31), 999);
            buchungen.segment(JONAS, KUECHE, LocalDate.of(2026, 12, 1), 999);
            return buchungen;
        }

        static ProjektInfo projekt(long id, boolean aktiv, BigDecimal angebotspreis) {
            return new ProjektInfo(id, "Projekt " + id, LocalDate.of(2026, 9, 1), null, LENA_PL, aktiv,
                    angebotspreis);
        }
    }

    @Autowired
    private KostenService service;

    @Autowired
    private FakeProjektAuskunft projekte;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @BeforeEach
    void ausgangslage() {
        // Die Fakes sind Beans des gemeinsamen Testkontexts: Angebotspreise vor jedem Test zuruecksetzen
        projekte.ersetzen(Testumgebung.projekt(KUECHE, true, new BigDecimal("9800.00")));
        projekte.ersetzen(Testumgebung.projekt(OHNE_PREIS, true, null));
        projekte.ersetzen(Testumgebung.projekt(ARCHIV, false, new BigDecimal("100.00")));
        projekte.ersetzen(Testumgebung.projekt(RUNDUNG, true, null));
        Material eiche = materialRepository.save(new Material("ART-1102", "Eiche Massivholzplatte 26 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("89.50"), new BigDecimal("129.00")));
        positionRepository.save(new Materialposition(KUECHE, eiche, new BigDecimal("12.00")));
        Material platte = materialRepository.save(new Material("ART-2040", "Arbeitsplatte", null,
                Einheit.LAUFMETER, new BigDecimal("68.00"), new BigDecimal("98.00")));
        positionRepository.save(new Materialposition(KUECHE, platte, new BigDecimal("4.20")));
    }

    @Test
    void gesamtkostenAusMaterialEkUndPersonalVomProjektstartBisHeute() {
        Kostenuebersicht kosten = service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        // Material: 12 x 89,50 + 4,2 x 68,00 = 1.074,00 + 285,60
        assertThat(kosten.materialkosten()).isEqualByComparingTo("1359.60");
        // Jonas 5.000 min x 28 €/h = 2.333,33; Mehmet 3.835 min x 28 €/h = 1.789,67
        assertThat(kosten.personalMinuten()).isEqualTo(8835);
        assertThat(kosten.personalkosten()).isEqualTo(new BigDecimal("4123.00"));
        assertThat(kosten.gesamtkosten()).isEqualByComparingTo("5482.60");
        assertThat(kosten.angebotspreis()).isEqualByComparingTo("9800.00");
        assertThat(kosten.differenz()).isEqualByComparingTo("4317.40");
        assertThat(kosten.kostendeckung()).isEqualTo(Kostendeckung.GEDECKT);
        assertThat(kosten.ohneStundensatz()).isEmpty();
    }

    @Test
    void unterdeckungWennAngebotspreisUnterLohnPlusMaterialEk() {
        service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, new BigDecimal("5482.59"));

        Kostenuebersicht kosten = service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        assertThat(kosten.kostendeckung()).isEqualTo(Kostendeckung.UNTERDECKUNG);
        assertThat(kosten.differenz()).isEqualByComparingTo("-0.01");
    }

    @Test
    void genauGleicherAngebotspreisIstGedeckt() {
        service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, new BigDecimal("5482.60"));

        Kostenuebersicht kosten = service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE);

        assertThat(kosten.kostendeckung()).isEqualTo(Kostendeckung.GEDECKT);
        assertThat(kosten.differenz()).isEqualByComparingTo("0");
    }

    @Test
    void ohneAngebotspreisKeineWarnungSondernHinweis() {
        Kostenuebersicht kosten = service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, OHNE_PREIS);

        assertThat(kosten.kostendeckung()).isEqualTo(Kostendeckung.ANGEBOTSPREIS_FEHLT);
        assertThat(kosten.angebotspreis()).isNull();
        assertThat(kosten.differenz()).isNull();
    }

    @Test
    void fehlenderStundensatzZaehltMitNullUndWirdGenannt() {
        Kostenuebersicht kosten = service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, OHNE_PREIS);

        // Jonas 600 min x 28 = 280,00; Mia 480 min ohne Satz = 0
        assertThat(kosten.personalkosten()).isEqualByComparingTo("280.00");
        assertThat(kosten.personalMinuten()).isEqualTo(1080);
        assertThat(kosten.ohneStundensatz()).containsExactly("Mia Fischer");
        assertThat(kosten.materialkosten()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void personalkostenWerdenJePersonKaufmaennischGerundet() {
        // je Person 0,005 € -> 0,01 € (HALF_UP, nicht HALF_EVEN); Summe 0,02 € (Summe erst runden ergaebe 0,01 €)
        assertThat(service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, RUNDUNG).personalkosten())
                .isEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void ohneStartdatumZaehltNurDerAktuelleMonat() {
        assertThat(service.kosten(LENA_PL, Rolle.PROJEKTLEITUNG, OHNE_START).personalMinuten()).isEqualTo(60);
    }

    @Test
    void kostenSiehtNurVerantwortlicheProjektleitungUndAdmin() {
        assertThat(service.kosten(ADMIN, Rolle.ADMIN, KUECHE).gesamtkosten()).isEqualByComparingTo("5482.60");
        assertThatThrownBy(() -> service.kosten(OTTO_PL, Rolle.PROJEKTLEITUNG, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.kosten(JONAS, Rolle.MITARBEITER, KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.kosten(LENA_PL, null, KUECHE))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void angebotspreisWirdGerundetUeberM3Gespeichert() {
        service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, OHNE_PREIS, new BigDecimal("1234.565"));

        assertThat(projekte.finde(OHNE_PREIS).orElseThrow().angebotspreis()).isEqualTo(new BigDecimal("1234.57"));
    }

    @Test
    void angebotspreisSetztNurVerantwortlicheProjektleitungImAktivenProjekt() {
        assertThatThrownBy(() -> service.setzeAngebotspreis(ADMIN, Rolle.ADMIN, KUECHE, BigDecimal.ONE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.setzeAngebotspreis(OTTO_PL, Rolle.PROJEKTLEITUNG, KUECHE, BigDecimal.ONE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.setzeAngebotspreis(JONAS, Rolle.MITARBEITER, KUECHE, BigDecimal.ONE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, ARCHIV, BigDecimal.ONE))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Projekt ist abgeschlossen und kann nicht mehr geändert werden.");
        assertThatThrownBy(() -> service.setzeAngebotspreis(LENA_PL, null, KUECHE, BigDecimal.ONE))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThat(projekte.finde(KUECHE).orElseThrow().angebotspreis()).isEqualByComparingTo("9800.00");
        assertThat(projekte.finde(ARCHIV).orElseThrow().angebotspreis()).isEqualByComparingTo("100.00");
    }

    @Test
    void ungueltigerAngebotspreisWirdAbgelehnt() {
        assertThatThrownBy(() -> service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE, null))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie den Angebotspreis ein.");
        assertThatThrownBy(() -> service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE,
                new BigDecimal("-1"))).isInstanceOf(FachlicheException.class);
        assertThatThrownBy(() -> service.setzeAngebotspreis(LENA_PL, Rolle.PROJEKTLEITUNG, KUECHE,
                new BigDecimal("100000000"))).isInstanceOf(FachlicheException.class);
        assertThat(projekte.finde(KUECHE).orElseThrow().angebotspreis()).isEqualByComparingTo("9800.00");
    }
}

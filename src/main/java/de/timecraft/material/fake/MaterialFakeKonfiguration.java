package de.timecraft.material.fake;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.service.AnpassungsantragService;
import de.timecraft.material.service.MaterialDaten;
import de.timecraft.material.service.MaterialService;
import de.timecraft.material.service.Mehrbedarfsgrund;
import de.timecraft.material.service.VorkalkulationService;
import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Nur fuer den lokalen Start ohne M1, M2 und M3 (Profil "fake"): Fakes mit Beispieldaten als Beans und
 * Beispielmaterial wie in den Mockups. Die IDs 1 bis 3 passen zu den Platzhalter-Benutzern des Geruests
 * (mitarbeiter = Jonas, projektleitung = Lena, admin = Sabine). Die Beispieldaten laufen ueber die Services,
 * also mit denselben Regeln wie in der Oberflaeche.
 */
@Configuration
@Profile("fake")
public class MaterialFakeKonfiguration {

    static final long JONAS = 1;
    static final long LENA_PL = 2;
    static final long SABINE_ADMIN = 3;
    static final long MEHMET = 4;
    static final long OTTO_PL = 7;

    static final long KUECHE = 1024;
    static final long TREPPE = 1031;
    static final long DACHSTUHL = 1040;
    static final long ALTBAU_ARCHIV = 1001;

    @Bean
    public FakeMitarbeiterAuskunft fakeMitarbeiterAuskunft() {
        FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
        personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, new BigDecimal("28.00"));
        personen.hinzufuegen(LENA_PL, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, new BigDecimal("45.00"));
        personen.hinzufuegen(SABINE_ADMIN, "Sabine", "Hartmann", Rolle.ADMIN, null);
        personen.hinzufuegen(MEHMET, "Mehmet", "Yilmaz", Rolle.MITARBEITER, null);
        personen.hinzufuegen(OTTO_PL, "Otto", "Graf", Rolle.PROJEKTLEITUNG, new BigDecimal("45.00"));
        return personen;
    }

    @Bean
    public FakeProjektAuskunft fakeProjektAuskunft(Clock clock) {
        LocalDate start = LocalDate.now(clock).minusMonths(2).withDayOfMonth(1);
        FakeProjektAuskunft projekte = new FakeProjektAuskunft();
        projekte.hinzufuegen(projekt(KUECHE, "P-1024 Einbauküche Wagner", start, LENA_PL, true,
                new BigDecimal("9800.00")), JONAS, MEHMET, LENA_PL);
        projekte.hinzufuegen(projekt(TREPPE, "P-1031 Treppe Lindenstraße", start, LENA_PL, true, null),
                JONAS, MEHMET);
        projekte.hinzufuegen(projekt(DACHSTUHL, "P-1040 Dachstuhl Meier", start, OTTO_PL, true,
                new BigDecimal("2500.00")), JONAS, OTTO_PL);
        projekte.hinzufuegen(projekt(ALTBAU_ARCHIV, "P-1001 Altbau Sanierung", start.minusYears(1), LENA_PL,
                false, new BigDecimal("15000.00")), JONAS);
        return projekte;
    }

    @Bean
    public FakeAngebotspreisPflege fakeAngebotspreisPflege(FakeProjektAuskunft fakeProjektAuskunft) {
        return new FakeAngebotspreisPflege(fakeProjektAuskunft);
    }

    /** Arbeitszeiten auf die Projekte, an Werktagen der letzten Wochen. */
    @Bean
    public FakeBuchungsAuskunft fakeBuchungsAuskunft(Clock clock) {
        FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
        LocalDate heute = LocalDate.now(clock);
        for (int tage = 1; tage <= 40; tage++) {
            LocalDate tag = heute.minusDays(tage);
            if (tag.getDayOfWeek().getValue() <= 5) {
                buchungen.segment(JONAS, tage % 3 == 0 ? TREPPE : KUECHE, tag, 465);
                buchungen.segment(MEHMET, KUECHE, tag, 450);
                buchungen.segment(LENA_PL, KUECHE, tag, 120);
                buchungen.segment(OTTO_PL, DACHSTUHL, tag, 480);
            }
        }
        return buchungen;
    }

    /** Legt beim ersten Start Material, Planung und Mehrbedarfe wie in den Mockups an. */
    @Bean
    public ApplicationRunner materialBeispieldaten(MaterialService materialService,
            VorkalkulationService vorkalkulation, AnpassungsantragService antraege) {
        return argumente -> {
            if (!materialService.alle(Rolle.ADMIN).isEmpty()) {
                return;
            }
            Material eiche = material(materialService, "ART-1102", "Eiche Massivholzplatte 26 mm", "Holz",
                    Einheit.QUADRATMETER, "89.50", "129.00");
            Material multiplex = material(materialService, "ART-1215", "Multiplex Birke 18 mm", "Holz",
                    Einheit.QUADRATMETER, "42.00", "61.00");
            Material arbeitsplatte = material(materialService, "ART-2040", "Arbeitsplatte Schichtstoff 38 mm",
                    "Platten", Einheit.LAUFMETER, "68.00", "98.00");
            Material topfband = material(materialService, "ART-3301", "Topfband-Scharnier 110°", "Beschläge",
                    Einheit.STUECK, "3.20", "5.90");
            Material auszug = material(materialService, "ART-3410", "Vollauszug-Schubkastenführung", "Beschläge",
                    Einheit.STUECK, "24.50", "39.00");
            material(materialService, "ART-1330", "Sockelblende Alu", "Profile", Einheit.LAUFMETER, "12.40", "19.90");
            Material leimholz = material(materialService, "ART-1410", "Leimholz Buche", "Holz", Einheit.LAUFMETER,
                    "18.00", "27.50");

            Materialposition eichePosition = planen(vorkalkulation, KUECHE, eiche, "12.0");
            Materialposition multiplexPosition = planen(vorkalkulation, KUECHE, multiplex, "16.0");
            planen(vorkalkulation, KUECHE, arbeitsplatte, "4.2");
            planen(vorkalkulation, KUECHE, topfband, "36");
            planen(vorkalkulation, KUECHE, auszug, "8");
            Materialposition leimholzPosition = planen(vorkalkulation, TREPPE, leimholz, "10");

            Materialanpassungsantrag genehmigt = antraege.einreichen(JONAS, Rolle.MITARBEITER,
                    multiplexPosition.getId(), new BigDecimal("2.5"), Mehrbedarfsgrund.VERSCHNITT,
                    "Verschnitt bei Hochschrankseiten");
            antraege.freigeben(LENA_PL, Rolle.PROJEKTLEITUNG, genehmigt.getId());
            Materialanpassungsantrag abgelehnt = antraege.einreichen(JONAS, Rolle.MITARBEITER,
                    leimholzPosition.getId(), new BigDecimal("3"), Mehrbedarfsgrund.KUNDENAENDERUNG,
                    "Kunde möchte eine Stufe mehr");
            antraege.ablehnen(LENA_PL, Rolle.PROJEKTLEITUNG, abgelehnt.getId(), "Reststück aus Lager verwenden.");
            antraege.einreichen(JONAS, Rolle.MITARBEITER, eichePosition.getId(), new BigDecimal("1.5"),
                    Mehrbedarfsgrund.VERSCHNITT, "Astlöcher in zwei Platten, Seitenteile Hochschrank mussten neu "
                            + "zugeschnitten werden.");
        };
    }

    private static Material material(MaterialService service, String nummer, String bezeichnung, String kategorie,
            Einheit einheit, String ek, String vk) {
        return service.anlegen(Rolle.ADMIN, new MaterialDaten(nummer, bezeichnung, kategorie, einheit,
                new BigDecimal(ek), new BigDecimal(vk)));
    }

    private static Materialposition planen(VorkalkulationService service, long projektId, Material material,
            String menge) {
        return service.positionHinzufuegen(LENA_PL, Rolle.PROJEKTLEITUNG, projektId, material.getId(),
                new BigDecimal(menge));
    }

    private static ProjektInfo projekt(long id, String name, LocalDate start, long projektleitung, boolean aktiv,
            BigDecimal angebotspreis) {
        return new ProjektInfo(id, name, start, null, projektleitung, aktiv, angebotspreis);
    }
}

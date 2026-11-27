package de.timecraft.projekte.fake;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt beim Start im Profil "fake" erfundene Demoprojekte an, aber nur, wenn es noch kein Projekt gibt.
 * Die Personen stammen aus {@link FakeMitarbeiterAuskunft}: Lena Schröder (2) und Sabine Keller (7) sind
 * Projektleitungen, Jonas (1), Mehmet (4), Felix (5) und Anna (6) sind Mitarbeiter.
 *
 * @author Tim Stukenbröker
 */
@Component
@Profile("fake")
public class FakeDemoDaten implements CommandLineRunner {

    private final ProjektRepository projektRepository;
    private final ProjektzuordnungRepository zuordnungRepository;
    private final Clock clock;

    public FakeDemoDaten(ProjektRepository projektRepository, ProjektzuordnungRepository zuordnungRepository,
            Clock clock) {
        this.projektRepository = projektRepository;
        this.zuordnungRepository = zuordnungRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... argumente) {
        if (projektRepository.count() > 0) {
            return;
        }
        // Das erste gespeicherte Projekt bekommt die ID 1: Darauf laufen die Buchungen der FakeBuchungsAuskunft.
        Projekt kueche = speichern(2, "Einbauküche Familie Wagner", "Küche aus Eiche mit Kochinsel.",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 13));
        Projekt treppe = speichern(2, "Treppe Altbau Lindenstraße", "Wangentreppe, Buche geölt.",
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 20));
        Projekt laden = speichern(7, "Ladenbau Bäckerei Krause", "Verkaufstheke und Regale.",
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 12, 18));
        Projekt fenster = speichern(2, "Fenstertausch Grundschule Brake", null,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 27));
        Projekt schrank = speichern(2, "Einbauschrank Praxis Dr. Ohm", "Abgeschlossen im Juli.",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31));
        schrank.setStatus(ProjektStatus.ARCHIVIERT);

        zuordnen(kueche, 1, 4, 5, 6);
        zuordnen(treppe, 1, 5);
        zuordnen(laden, 6);
        zuordnen(fenster, 4, 5);
        zuordnen(schrank, 1);
    }

    private Projekt speichern(long projektleitungId, String name, String beschreibung, LocalDate start, LocalDate ziel) {
        return projektRepository.save(new Projekt(projektleitungId, name, beschreibung, start, ziel));
    }

    private void zuordnen(Projekt projekt, long... mitarbeiterIds) {
        LocalDateTime jetzt = LocalDateTime.now(clock);
        for (long mitarbeiterId : mitarbeiterIds) {
            zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projekt.getProjektId(), jetzt));
        }
    }
}

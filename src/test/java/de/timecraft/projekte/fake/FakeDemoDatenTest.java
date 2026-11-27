package de.timecraft.projekte.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Demodaten für das Profil "fake" mit einer echten Datenbank (H2 im PostgreSQL-Modus).
 * Die Klasse wird hier bewusst von Hand erzeugt und nicht als Bean geladen: Als Bean würde ihr
 * CommandLineRunner beim Laden des Test-Kontexts laufen und Projekte in die gemeinsame Testdatenbank
 * schreiben, die andere Tests stören würden. So entstehen die Daten nur in der Test-Transaktion.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FakeDemoDatenTest {

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    private FakeDemoDaten demoDaten;

    @BeforeEach
    void erzeugeDemoDaten() {
        demoDaten = new FakeDemoDaten(repository, zuordnungRepository, FesteUhr.UHR);
    }

    @Test
    void legtFuenfProjekteAnDavonEinesArchiviert() {
        demoDaten.run();

        assertThat(repository.findAll()).hasSize(5);
        assertThat(repository.findAll()).filteredOn(projekt -> projekt.getStatus() == ProjektStatus.ARCHIVIERT)
                .extracting(Projekt::getName).containsExactly("Einbauschrank Praxis Dr. Ohm");
    }

    @Test
    void dasErsteProjektDieKuecheHatDieKleinsteIdUndVierZugeordnete() {
        demoDaten.run();

        // Auf einer leeren Datenbank ist das die ID 1, auf die die Beispielbuchungen (M6) gehen.
        Projekt erstes = repository.findAll().stream().min(Comparator.comparing(Projekt::getProjektId)).orElseThrow();
        assertThat(erstes.getName()).isEqualTo("Einbauküche Familie Wagner");
        assertThat(zuordnungRepository.findByProjektId(erstes.getProjektId())).extracting("mitarbeiterId")
                .containsExactlyInAnyOrder(1L, 4L, 5L, 6L);
    }

    @Test
    void zweiterStartLegtNichtNochEinmalDieselbenProjekteAn() {
        demoDaten.run();
        demoDaten.run();

        assertThat(repository.count()).isEqualTo(5);
    }
}

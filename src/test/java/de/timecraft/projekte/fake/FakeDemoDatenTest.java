package de.timecraft.projekte.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Prüft die Demodaten für das Profil "fake" mit einer echten Datenbank (H2 im PostgreSQL-Modus). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "fake"})
@Import({FakeDemoDaten.class, FesteUhr.class})
class FakeDemoDatenTest {

    @Autowired
    private FakeDemoDaten demoDaten;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    @Test
    void legtFuenfProjekteAnDavonEinesArchiviert() {
        demoDaten.run();

        assertThat(repository.findAll()).hasSize(5);
        assertThat(repository.findAll()).filteredOn(projekt -> projekt.getStatus() == ProjektStatus.ARCHIVIERT)
                .extracting(Projekt::getName).containsExactly("Einbauschrank Praxis Dr. Ohm");
    }

    @Test
    void dasErsteProjektTraegtDieIdDerFakeBuchungen() {
        demoDaten.run();

        Projekt erstes = repository.findAll().stream().filter(projekt -> projekt.getProjektId() == 1L).findFirst().orElseThrow();
        assertThat(erstes.getName()).isEqualTo("Einbauküche Familie Wagner");
        assertThat(zuordnungRepository.findByProjektId(1L)).extracting("mitarbeiterId")
                .containsExactlyInAnyOrder(1L, 4L, 5L, 6L);
    }

    @Test
    void zweiterStartLegtNichtNochEinmalDieselbenProjekteAn() {
        demoDaten.run();
        demoDaten.run();

        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void zuordnungenTragenDasDatumAusDerUhr() {
        demoDaten.run();

        assertThat(zuordnungRepository.findAll()).allSatisfy(zuordnung ->
                assertThat(zuordnung.getZugeordnetAm().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 18)));
    }
}

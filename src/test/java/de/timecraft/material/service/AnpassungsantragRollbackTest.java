package de.timecraft.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.repository.AnpassungsantragRepository;
import de.timecraft.material.repository.MaterialRepository;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.material.repository.MaterialprotokollRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datenintegritaet (AP4 "Rollback", RB-M4-02): Scheitert das Protokoll bei der Freigabe, wird nichts gespeichert.
 * Ohne Test-Transaktion, damit die Service-Transaktion wirklich zurueckrollt; danach wird aufgeraeumt.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AnpassungsantragService.class, ProjektZugriff.class, Personennamen.class,
        AnpassungsantragServiceTest.Testumgebung.class})
/**
 * Tests fuer AnpassungsantragRollback.
 *
 * @author Tharsan Arumuganathar
 */
class AnpassungsantragRollbackTest {

    @Autowired
    private AnpassungsantragService service;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @Autowired
    private AnpassungsantragRepository antragRepository;

    @MockitoSpyBean
    private MaterialprotokollRepository protokollRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void aufraeumen() {
        for (String tabelle : new String[] {"materialprotokoll", "materialanpassungsantrag", "antrag",
                "materialposition", "material"}) {
            jdbc.update("delete from " + tabelle);
        }
    }

    @Test
    void scheitertDasProtokollBleibenAntragUndSollMengeUnveraendert() {
        Material material = materialRepository.save(new Material("ART-1215", "Multiplex Birke 18 mm", null,
                Einheit.QUADRATMETER, new BigDecimal("42.00"), new BigDecimal("61.00")));
        Materialposition position = positionRepository.save(new Materialposition(AnpassungsantragServiceTest.KUECHE,
                material, new BigDecimal("16.00")));
        Materialanpassungsantrag antrag = service.einreichen(AnpassungsantragServiceTest.JONAS, Rolle.MITARBEITER,
                position.getId(), new BigDecimal("2.5"), null, "Verschnitt");
        doThrow(new IllegalStateException("Datenbank nicht erreichbar"))
                .when(protokollRepository).save(any(Materialprotokoll.class));

        assertThatThrownBy(() -> service.freigeben(AnpassungsantragServiceTest.LENA_PL, Rolle.PROJEKTLEITUNG,
                antrag.getId())).isInstanceOf(IllegalStateException.class);

        assertThat(antragRepository.findById(antrag.getId()).orElseThrow().getStatus())
                .isEqualTo(Antragsstatus.OFFEN);
        assertThat(positionRepository.findById(position.getId()).orElseThrow().getSollMenge())
                .isEqualByComparingTo("16.00");
        assertThat(jdbc.queryForObject("select count(*) from materialprotokoll", Integer.class)).isZero();
    }
}

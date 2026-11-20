package de.timecraft.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AnpassungsantragRepositoryTest {

    private static final Instant EINGEREICHT = Instant.parse("2026-10-07T14:15:00Z");

    @Autowired
    private AnpassungsantragRepository repository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void speichertAntragInAntragUndMaterialanpassungsantrag() {
        Materialanpassungsantrag gespeichert = repository.save(neuerAntrag());
        entityManager.flush();

        Map<String, Object> basis = jdbc.queryForMap(
                "select typ, status, antragsteller_id, begruendung from antrag where antrag_id = ?",
                gespeichert.getId());
        Map<String, Object> unter = jdbc.queryForMap(
                "select materialposition_id, zusatzmenge from materialanpassungsantrag where antrag_id = ?",
                gespeichert.getId());

        assertThat(basis.get("typ")).isEqualTo("MATERIALANPASSUNG");
        assertThat(basis.get("status")).isEqualTo("OFFEN");
        assertThat(((Number) basis.get("antragsteller_id")).longValue()).isEqualTo(1);
        assertThat(basis.get("begruendung")).isEqualTo("Verschnitt bei Hochschrankseiten");
        assertThat(((Number) unter.get("materialposition_id")).longValue())
                .isEqualTo(gespeichert.getMaterialposition().getId());
        assertThat((BigDecimal) unter.get("zusatzmenge")).isEqualByComparingTo("2.50");
    }

    @Test
    void liestAntragMitGemeinsamenUndEigenenFeldernWiederEin() {
        Materialanpassungsantrag gespeichert = repository.save(neuerAntrag());
        entityManager.flush();
        entityManager.clear();

        Materialanpassungsantrag gelesen = repository.findById(gespeichert.getId()).orElseThrow();

        assertThat(gelesen.getAntragstellerId()).isEqualTo(1);
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(gelesen.getEingereichtAm()).isEqualTo(EINGEREICHT);
        assertThat(gelesen.getBegruendung()).isEqualTo("Verschnitt bei Hochschrankseiten");
        assertThat(gelesen.getEntscheiderId()).isNull();
        assertThat(gelesen.getZusatzmenge()).isEqualByComparingTo("2.50");
        assertThat(gelesen.getMaterialposition().getMaterial().getBezeichnung()).isEqualTo("Multiplex Birke 18 mm");
    }

    @Test
    void speichertEntscheidungImAntrag() {
        Materialanpassungsantrag antrag = repository.save(neuerAntrag());
        antrag.setStatus(Antragsstatus.ABGELEHNT);
        antrag.setEntscheiderId(2L);
        antrag.setEntschiedenAm(EINGEREICHT.plusSeconds(3600));
        antrag.setEntscheidungshinweis("Reststueck aus Lager verwenden.");
        entityManager.flush();
        entityManager.clear();

        Materialanpassungsantrag gelesen = repository.findById(antrag.getId()).orElseThrow();

        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
        assertThat(gelesen.getEntscheiderId()).isEqualTo(2L);
        assertThat(gelesen.getEntschiedenAm()).isEqualTo(EINGEREICHT.plusSeconds(3600));
        assertThat(gelesen.getEntscheidungshinweis()).isEqualTo("Reststueck aus Lager verwenden.");
    }

    private Materialanpassungsantrag neuerAntrag() {
        Material material = materialRepository.save(new Material("ART-1215", "Multiplex Birke 18 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("42.00"), new BigDecimal("61.00")));
        Materialposition position = positionRepository.save(
                new Materialposition(1024, material, new BigDecimal("16.00")));
        Materialanpassungsantrag antrag = new Materialanpassungsantrag(position, new BigDecimal("2.50"));
        antrag.setAntragstellerId(1);
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setBegruendung("Verschnitt bei Hochschrankseiten");
        return antrag;
    }
}

package de.timecraft.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests fuer MaterialpositionRepository.
 *
 * @author Tharsan Arumuganathar
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MaterialpositionRepositoryTest {

    @Autowired
    private MaterialpositionRepository repository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void speichertPositionMitMaterialUndSollMenge() {
        Material material = materialRepository.save(new Material("ART-1215", "Multiplex Birke 18 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("42.00"), new BigDecimal("61.00")));
        Materialposition gespeichert = repository.save(new Materialposition(1024, material, new BigDecimal("18.50")));
        entityManager.flush();
        entityManager.clear();

        Materialposition gelesen = repository.findById(gespeichert.getId()).orElseThrow();

        assertThat(gelesen.getProjektId()).isEqualTo(1024);
        assertThat(gelesen.getMaterial().getArtikelnummer()).isEqualTo("ART-1215");
        assertThat(gelesen.getSollMenge()).isEqualByComparingTo("18.50");
    }

    @Test
    void liefertNurDiePositionenDesProjektsInAnlagereihenfolge() {
        Material material = materialRepository.save(new Material("ART-3410", "Vollauszug-Schubkastenfuehrung",
                null, Einheit.STUECK, new BigDecimal("24.50"), new BigDecimal("39.00")));
        repository.save(new Materialposition(1024, material, new BigDecimal("8")));
        repository.save(new Materialposition(1031, material, new BigDecimal("2")));
        repository.save(new Materialposition(1024, material, new BigDecimal("4")));

        List<Materialposition> positionen = repository.findByProjektIdOrderByIdAsc(1024);

        assertThat(positionen).extracting(Materialposition::getSollMenge)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("8"), new BigDecimal("4"));
    }
}

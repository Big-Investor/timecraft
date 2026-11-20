package de.timecraft.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.model.Protokollaktion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MaterialprotokollRepositoryTest {

    private static final LocalDateTime ANGELEGT = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final LocalDateTime GENEHMIGT = LocalDateTime.of(2026, 10, 8, 11, 30);

    @Autowired
    private MaterialprotokollRepository repository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialpositionRepository positionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void liefertDieHistorieEinesProjektsZeitlichSortiert() {
        Materialposition position = position(1024);
        Materialposition anderesProjekt = position(1031);
        repository.save(new Materialprotokoll(position, 2, GENEHMIGT, Protokollaktion.GENEHMIGT,
                new BigDecimal("16.00"), new BigDecimal("18.50")));
        repository.save(new Materialprotokoll(position, 2, ANGELEGT, Protokollaktion.GEBUCHT,
                null, new BigDecimal("16.00")));
        repository.save(new Materialprotokoll(anderesProjekt, 2, ANGELEGT, Protokollaktion.GEBUCHT,
                null, new BigDecimal("3.00")));
        entityManager.flush();
        entityManager.clear();

        List<Materialprotokoll> historie = repository.findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(1024);

        assertThat(historie).extracting(Materialprotokoll::getAktion)
                .containsExactly(Protokollaktion.GEBUCHT, Protokollaktion.GENEHMIGT);
        Materialprotokoll gebucht = historie.get(0);
        assertThat(gebucht.getGeaendertVonId()).isEqualTo(2);
        assertThat(gebucht.getZeitpunkt()).isEqualTo(ANGELEGT);
        assertThat(gebucht.getAlterWert()).isNull();
        assertThat(gebucht.getNeuerWert()).isEqualByComparingTo("16.00");
        assertThat(historie.get(1).getAlterWert()).isEqualByComparingTo("16.00");
    }

    @Test
    void speichertDieAktionAlsText() {
        Materialprotokoll eintrag = repository.save(new Materialprotokoll(position(1024), 2, ANGELEGT,
                Protokollaktion.GEBUCHT, null, new BigDecimal("12.00")));
        entityManager.flush();

        String aktion = jdbc.queryForObject("select aktion from materialprotokoll where materialprotokoll_id = ?",
                String.class, eintrag.getId());

        assertThat(aktion).isEqualTo("GEBUCHT");
    }

    @Test
    void protokollBietetWederAendernNochLoeschenAn() {
        // Unveraenderlich (QA-M4-01): kein CrudRepository (kein delete), Entity ohne Setter, Spalten updatable=false
        assertThat(CrudRepository.class.isAssignableFrom(MaterialprotokollRepository.class)).isFalse();
    }

    private Materialposition position(long projektId) {
        Material material = materialRepository.save(new Material("ART-" + projektId, "Eiche Massivholzplatte 26 mm",
                "Holz", Einheit.QUADRATMETER, new BigDecimal("89.50"), new BigDecimal("129.00")));
        return positionRepository.save(new Materialposition(projektId, material, new BigDecimal("16.00")));
    }
}

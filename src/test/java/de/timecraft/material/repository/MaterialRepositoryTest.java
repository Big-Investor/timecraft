package de.timecraft.material.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MaterialRepositoryTest {

    @Autowired
    private MaterialRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void speichertMaterialUndLiestAlleFelderWiederEin() {
        Material gespeichert = repository.save(new Material("ART-1102", "Eiche Massivholzplatte 26 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("89.50"), new BigDecimal("129.00")));
        entityManager.flush();
        entityManager.clear();

        Material gelesen = repository.findById(gespeichert.getId()).orElseThrow();

        assertThat(gelesen.getArtikelnummer()).isEqualTo("ART-1102");
        assertThat(gelesen.getBezeichnung()).isEqualTo("Eiche Massivholzplatte 26 mm");
        assertThat(gelesen.getKategorie()).isEqualTo("Holz");
        assertThat(gelesen.getEinheit()).isEqualTo(Einheit.QUADRATMETER);
        assertThat(gelesen.getEinkaufspreis()).isEqualByComparingTo("89.50");
        assertThat(gelesen.getVerkaufspreis()).isEqualByComparingTo("129.00");
        assertThat(gelesen.isAktiv()).isTrue();
    }

    @Test
    void speichertDieEinheitAlsKuerzelInDerSpalteEinheit() {
        Material gespeichert = repository.save(new Material("ART-3301", "Topfband-Scharnier 110°", null,
                Einheit.STUECK, new BigDecimal("3.20"), new BigDecimal("5.90")));
        entityManager.flush();

        String spalte = jdbc.queryForObject("select einheit from material where material_id = ?", String.class,
                gespeichert.getId());

        assertThat(spalte).isEqualTo("Stk.");
    }

    @Test
    void kategorieDarfLeerSein() {
        Material gespeichert = repository.save(new Material("ART-2040", "Arbeitsplatte Schichtstoff 38 mm", null,
                Einheit.LAUFMETER, new BigDecimal("68.00"), new BigDecimal("98.00")));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(gespeichert.getId()).orElseThrow().getKategorie()).isNull();
    }

    @Test
    void lehntMaterialOhneArtikelnummerAb() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Material(null, "Ohne Nummer", null,
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.TEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lehntZuLangeArtikelnummerAb() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Material("A".repeat(21), "Zu lang", null,
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.TEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

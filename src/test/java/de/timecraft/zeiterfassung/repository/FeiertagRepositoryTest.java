package de.timecraft.zeiterfassung.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import de.timecraft.zeiterfassung.model.Feiertag;

/** @author Noah Cipa */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FeiertagRepositoryTest {

    @Autowired
    private FeiertagRepository repository;

    @Test
    void speichertFeiertagUndFindetIhnUeberDasDatum() {
        repository.save(new Feiertag(LocalDate.of(2026, 12, 24), "Heiligabend"));

        assertThat(repository.existsByDatum(LocalDate.of(2026, 12, 24))).isTrue();
        assertThat(repository.existsByDatum(LocalDate.of(2026, 12, 23))).isFalse();
    }

    @Test
    void datenbankVerhindertZweiFeiertageAmSelbenDatum() {
        repository.saveAndFlush(new Feiertag(LocalDate.of(2026, 12, 24), "Heiligabend"));

        assertThatThrownBy(() -> repository.saveAndFlush(new Feiertag(LocalDate.of(2026, 12, 24), "Doppelt")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

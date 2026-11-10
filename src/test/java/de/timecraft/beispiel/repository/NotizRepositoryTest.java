package de.timecraft.beispiel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.beispiel.model.Notiz;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotizRepositoryTest {

    @Autowired
    private NotizRepository repository;

    @Test
    void speichertNotizMitErzeugterIdUndLiestSieWiederEin() {
        Notiz gespeichert = repository.save(new Notiz("Holz bestellen", LocalDateTime.of(2026, 10, 22, 9, 30), 2));

        Notiz gelesen = repository.findById(gespeichert.getId()).orElseThrow();

        assertThat(gelesen.getId()).isNotNull();
        assertThat(gelesen.getText()).isEqualTo("Holz bestellen");
        assertThat(gelesen.getErstelltAm()).isEqualTo(LocalDateTime.of(2026, 10, 22, 9, 30));
        assertThat(gelesen.getErstellerId()).isEqualTo(2);
    }

    @Test
    void liefertNotizenNeuesteZuerst() {
        repository.save(new Notiz("alt", LocalDateTime.of(2026, 10, 20, 8, 0), 1));
        repository.save(new Notiz("neu", LocalDateTime.of(2026, 10, 22, 8, 0), 1));
        repository.save(new Notiz("mittel", LocalDateTime.of(2026, 10, 21, 8, 0), 1));

        List<Notiz> notizen = repository.findAllByOrderByErstelltAmDesc();

        assertThat(notizen).extracting(Notiz::getText).containsExactly("neu", "mittel", "alt");
    }
}

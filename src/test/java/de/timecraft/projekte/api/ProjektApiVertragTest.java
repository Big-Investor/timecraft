package de.timecraft.projekte.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft im vollen Spring-Kontext, dass die anderen Module die beiden Interfaces des Vertrags injizieren
 * können (je genau eine Bean) und dass beide zusammenarbeiten: M4 setzt den Angebotspreis über
 * AngebotspreisPflege, M2/M7 lesen ihn über ProjektAuskunft.
 * <p>
 * Die Methode ist "@Transactional": Alles wird am Ende zurückgerollt, damit die gemeinsame Test-Datenbank
 * für andere Tests unverändert bleibt.
 *
 * @author Tim Stukenbröker
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjektApiVertragTest {

    @Autowired
    private ProjektAuskunft auskunft;

    @Autowired
    private AngebotspreisPflege pflege;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    @Test
    void angebotspreisAusDerPflegeIstUeberDieAuskunftSichtbar() {
        long projektId = repository.save(
                new Projekt(99L, "Website Relaunch", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))).getProjektId();
        zuordnungRepository.save(new Projektzuordnung(11L, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));

        pflege.setzeAngebotspreis(projektId, new BigDecimal("1500.00"));

        ProjektInfo info = auskunft.finde(projektId).orElseThrow();
        assertThat(info.angebotspreis()).isEqualByComparingTo("1500.00");
        assertThat(auskunft.aktiveProjekteVon(11L)).extracting(ProjektInfo::projektId).contains(projektId);
        assertThat(auskunft.istZugeordnet(11L, projektId)).isTrue();
        assertThat(auskunft.zugeordneteMitarbeiter(projektId)).containsExactly(11L);
        assertThat(auskunft.verantworteteProjekte(99L)).extracting(ProjektInfo::projektId).containsExactly(projektId);
        assertThat(auskunft.alle()).extracting(ProjektInfo::projektId).contains(projektId);
    }
}

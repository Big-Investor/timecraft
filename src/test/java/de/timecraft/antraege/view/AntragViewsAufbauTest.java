package de.timecraft.antraege.view;

import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.JONAS;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.LENA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.vaadin.flow.component.UI;
import de.timecraft.antraege.FesteUhrKonfiguration;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.antraege.service.AbwesenheitsDaten;
import de.timecraft.antraege.service.AntragService;
import de.timecraft.antraege.service.EntscheidungService;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Baut die Views mit echten Services und Fakes auf, wie es beim Aufruf im Browser passiert
 * (ohne Browser). Das Aussehen wird von Hand mit den Mockups verglichen.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FesteUhrKonfiguration.class)
class AntragViewsAufbauTest {

    @Autowired
    private AntragService antragService;

    @Autowired
    private EntscheidungService entscheidungService;

    @Autowired
    private AktuelleSitzung sitzung;

    @Autowired
    private AntragRepository repository;

    @BeforeEach
    void oberflaeche() {
        UI.setCurrent(new UI());
        antragService.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, new AbwesenheitsDaten(AbwesenheitsArt.URLAUB,
                Urlaubsart.ERHOLUNGSURLAUB, LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 11), "Familie"));
    }

    @AfterEach
    void aufraeumen() {
        UI.setCurrent(null);
        SecurityContextHolder.clearContext();
        repository.deleteAll();
    }

    private void anmelden(long mitarbeiterId, Rolle rolle) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(mitarbeiterId, rolle.name().toLowerCase(), "x", rolle);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(benutzer, null, benutzer.getAuthorities()));
    }

    @Test
    void antragsseiteFuerMitarbeiter() {
        anmelden(JONAS, Rolle.MITARBEITER);
        assertThatCode(() -> new AntragListeView(antragService, sitzung)).doesNotThrowAnyException();
    }

    @Test
    void pruefseiteFuerProjektleitung() {
        anmelden(LENA, Rolle.PROJEKTLEITUNG);
        assertThatCode(() -> new AntragPruefungView(antragService, entscheidungService, sitzung))
                .doesNotThrowAnyException();
        assertThatCode(() -> new AntragListeView(antragService, sitzung)).doesNotThrowAnyException();
    }

    @Test
    void seitenSindNurFuerPassendeRollenFreigegeben() {
        assertThat(AntragListeView.class.getAnnotation(RolesAllowed.class).value())
                .containsExactlyInAnyOrder("MITARBEITER", "PROJEKTLEITUNG");
        assertThat(AntragPruefungView.class.getAnnotation(RolesAllowed.class).value()).containsExactly("PROJEKTLEITUNG");
        assertThat(new AntragMenuEintrag().erlaubteRollen()).containsExactlyInAnyOrder(Rolle.MITARBEITER,
                Rolle.PROJEKTLEITUNG);
        assertThat(new AntragPruefungMenuEintrag().erlaubteRollen()).containsExactly(Rolle.PROJEKTLEITUNG);
    }
}

package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.model.ProjektStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prüft den ProjektService mit einer echten Datenbank (H2 im PostgreSQL-Modus) und dem echten
 * Repository. Nur M1 ist eine Attrappe, weil es das Modul in diesem Branch noch nicht gibt.
 * <p>
 * Nach jedem Schritt werden die Daten mit flush() in die Datenbank geschrieben und mit clear()
 * aus dem Zwischenspeicher geworfen. Das anschließende find() liest dann wirklich aus der Datenbank.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ProjektService.class, ProjektBerechtigung.class, FesteUhr.class})
class ProjektServiceIntegrationTest {

    private static final long ADMIN_ID = 3L;
    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate ZIEL = LocalDate.of(2026, 12, 31);

    @Autowired
    private ProjektService service;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private MitarbeiterAuskunft mitarbeiterAuskunft;

    @BeforeEach
    void projektleitungExistiert() {
        when(mitarbeiterAuskunft.finde(PROJEKTLEITUNG_ID)).thenReturn(Optional.of(new MitarbeiterInfo(
                PROJEKTLEITUNG_ID, "P2", "Paula", "Leitung", Rolle.PROJEKTLEITUNG, true, null, null, null, null, null, 0)));
    }

    @Test
    void angelegtesProjektHatEineIdIstAktivUndStehtInDerDatenbank() {
        Projekt angelegt = service.anlegen(ADMIN_ID, Rolle.ADMIN,
                new ProjektDaten("Website", "Neuer Auftritt", START, ZIEL, PROJEKTLEITUNG_ID));
        Projekt gelesen = neuLaden(angelegt.getProjektId());

        assertThat(gelesen.getProjektId()).isNotNull();
        assertThat(gelesen.getName()).isEqualTo("Website");
        assertThat(gelesen.getBeschreibung()).isEqualTo("Neuer Auftritt");
        assertThat(gelesen.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        assertThat(gelesen.getProjektleitungId()).isEqualTo(PROJEKTLEITUNG_ID);
    }

    @Test
    void geaendertesProjektIstInDerDatenbankUndDieIdBleibtGleich() {
        Long id = service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("Website", null, START, ZIEL, PROJEKTLEITUNG_ID))
                .getProjektId();
        entityManager.flush();
        entityManager.clear();

        service.aendern(ADMIN_ID, Rolle.ADMIN, id,
                new ProjektDaten("Shop", "neu", START, LocalDate.of(2027, 3, 31), PROJEKTLEITUNG_ID));
        Projekt gelesen = neuLaden(id);

        assertThat(gelesen.getProjektId()).isEqualTo(id);
        assertThat(gelesen.getName()).isEqualTo("Shop");
        assertThat(gelesen.getBeschreibung()).isEqualTo("neu");
        assertThat(gelesen.getZieldatum()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void beschreibungAendernUndArchivierenUndReaktivierenSindInDerDatenbank() {
        Long id = service.anlegen(ADMIN_ID, Rolle.ADMIN, new ProjektDaten("Website", "alt", START, ZIEL, PROJEKTLEITUNG_ID))
                .getProjektId();
        entityManager.flush();
        entityManager.clear();

        service.beschreibungAendern(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, id, "neuer Text");
        assertThat(neuLaden(id).getBeschreibung()).isEqualTo("neuer Text");

        service.archivieren(ADMIN_ID, Rolle.ADMIN, id);
        assertThat(neuLaden(id).getStatus()).isEqualTo(ProjektStatus.ARCHIVIERT);

        service.reaktivieren(ADMIN_ID, Rolle.ADMIN, id);
        assertThat(neuLaden(id).getStatus()).isEqualTo(ProjektStatus.AKTIV);
    }

    /** Schreibt alles in die Datenbank, leert den Zwischenspeicher und liest das Projekt frisch. */
    private Projekt neuLaden(Long projektId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Projekt.class, projektId);
    }
}

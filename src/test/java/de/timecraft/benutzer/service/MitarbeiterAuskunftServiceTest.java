package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die {@link MitarbeiterAuskunftService} (Schnittstellenvertrag, FA-M1-14) mit echter
 * Testdatenbank (H2): angemeldete Person für alle drei Rollen und ohne Anmeldung, Suche auch nach
 * deaktivierten Mitarbeitern, aktive und alle Mitarbeiter, Urlaubsdaten und Stundensatz.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MitarbeiterAuskunftServiceTest {

    private static final Set<DayOfWeek> MONTAG_BIS_FREITAG = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private ArbeitszeitmodellRepository arbeitszeitmodellRepository;

    @Autowired
    private ZeitkontoRepository zeitkontoRepository;

    @Autowired
    private UrlaubskontoRepository urlaubskontoRepository;

    private MitarbeiterAuskunftService auskunft;
    private Mitarbeiter paul;
    private Mitarbeiter lena;
    private Mitarbeiter jonas;
    private Mitarbeiter sophie;

    @BeforeEach
    void vorbereiten() {
        auskunft = new MitarbeiterAuskunftService(mitarbeiterRepository, arbeitszeitmodellRepository,
                zeitkontoRepository, urlaubskontoRepository, new AktuelleSitzung());

        // Absichtlich nicht in der Reihenfolge der Personalnummern angelegt (Sortierung prüfen).
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker",
                Rolle.MITARBEITER, "1012");
        lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "l.schroeder",
                Rolle.PROJEKTLEITUNG, "1004");
        paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "p.hartmann", Rolle.ADMIN, "1001");
        sophie = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "s.kuehn",
                Rolle.MITARBEITER, "1015");

        jonas.setVorgesetzter(lena);
        jonas.setStundensatz(new BigDecimal("32.50"));
        mitarbeiterRepository.save(jonas);
        arbeitszeitmodellRepository.save(new Arbeitszeitmodell(jonas, new BigDecimal("38.50"), MONTAG_BIS_FREITAG));
        zeitkontoRepository.save(new Zeitkonto(jonas, LocalDate.of(2026, 1, 1), -90));
        urlaubskontoRepository.save(new Urlaubskonto(jonas, 2026, 30, 4));

        sophie.setVorgesetzter(lena);
        sophie.setAustrittsdatum(LocalDate.of(2026, 6, 30));
        sophie.setAktiv(false);
        mitarbeiterRepository.save(sophie);
    }

    @AfterEach
    void abmelden() {
        SecurityContextHolder.clearContext();
    }

    /** Meldet eine Person so an, wie es die Anmeldung von M1 tut (TimeCraftBenutzer im Security-Kontext). */
    private void anmelden(Mitarbeiter mitarbeiter, Rolle rolle) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(mitarbeiter.getMitarbeiterId(),
                mitarbeiter.getBenutzer().getBenutzername(), "", rolle, mitarbeiter.getName());
        UsernamePasswordAuthenticationToken anmeldung =
                new UsernamePasswordAuthenticationToken(benutzer, null, benutzer.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(anmeldung);
    }

    private List<String> personalnummern(List<MitarbeiterInfo> infos) {
        List<String> nummern = new ArrayList<>();
        for (MitarbeiterInfo info : infos) {
            nummern.add(info.personalnummer());
        }
        return nummern;
    }

    @Test
    void angemeldetePersonJeRolleUndOhneAnmeldung() {
        anmelden(jonas, Rolle.MITARBEITER);
        MitarbeiterInfo info = auskunft.angemeldetePerson();
        assertThat(info.mitarbeiterId()).isEqualTo(jonas.getMitarbeiterId());
        assertThat(info.personalnummer()).isEqualTo("1012");
        assertThat(info.name()).isEqualTo("Test Person 1012");
        assertThat(info.rolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(info.aktiv()).isTrue();
        assertThat(info.vorgesetzterId()).isEqualTo(lena.getMitarbeiterId());
        assertThat(info.geburtsdatum()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(info.wochenstunden()).isEqualByComparingTo("38.50");
        assertThat(info.arbeitstage()).isEqualTo(MONTAG_BIS_FREITAG);
        assertThat(info.zeitkontoStartdatum()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(info.startsaldoMinuten()).isEqualTo(-90);

        // Eine geänderte Rolle gilt erst ab der nächsten Anmeldung; finde liefert schon die neue
        jonas.getBenutzer().setRolle(Rolle.PROJEKTLEITUNG);
        benutzerRepository.save(jonas.getBenutzer());
        assertThat(auskunft.angemeldetePerson().rolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(auskunft.finde(jonas.getMitarbeiterId()).orElseThrow().rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);

        anmelden(lena, Rolle.PROJEKTLEITUNG);
        assertThat(auskunft.angemeldetePerson().rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
        assertThat(auskunft.angemeldetePerson().vorgesetzterId()).isNull();
        anmelden(paul, Rolle.ADMIN);
        assertThat(auskunft.angemeldetePerson().rolle()).isEqualTo(Rolle.ADMIN);

        // Niemand angemeldet oder unbekannte Mitarbeiter-ID: Fachausnahme
        TimeCraftBenutzer unbekannt = new TimeCraftBenutzer(999_999L, "geist", "", Rolle.MITARBEITER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(unbekannt, null, unbekannt.getAuthorities()));
        assertThatThrownBy(() -> auskunft.angemeldetePerson()).isInstanceOf(NichtAngemeldetException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> auskunft.angemeldetePerson())
                .isInstanceOf(NichtAngemeldetException.class)
                .hasMessage("Bitte melden Sie sich an.");
    }

    @Test
    void findeListenUrlaubsdatenUndStundensatz() {
        // finde: auch Deaktivierte; unbekannt leer; ohne Arbeitszeitmodell/Zeitkonto leere Werte
        MitarbeiterInfo ehemalige = auskunft.finde(sophie.getMitarbeiterId()).orElseThrow();
        assertThat(ehemalige.aktiv()).isFalse();
        assertThat(auskunft.finde(999_999L)).isEmpty();
        MitarbeiterInfo ohneModell = auskunft.finde(paul.getMitarbeiterId()).orElseThrow();
        assertThat(ohneModell.wochenstunden()).isNull();
        assertThat(ohneModell.arbeitstage()).isEmpty();
        assertThat(ohneModell.zeitkontoStartdatum()).isNull();
        assertThat(ohneModell.startsaldoMinuten()).isZero();
        MitarbeiterInfo jonasInfo = auskunft.finde(jonas.getMitarbeiterId()).orElseThrow();
        assertThatThrownBy(() -> jonasInfo.arbeitstage().add(DayOfWeek.SUNDAY))
                .isInstanceOf(UnsupportedOperationException.class);

        // aktive ohne Ehemalige, alle mit ihnen; beide sortiert nach Personalnummer
        assertThat(personalnummern(auskunft.aktive())).containsExactly("1001", "1004", "1012");
        assertThat(personalnummern(auskunft.alle())).containsExactly("1001", "1004", "1012", "1015");

        // Urlaubsdaten je Jahr; fehlendes Jahr, unbekannter Mitarbeiter, Jahr außerhalb SMALLINT: leer
        assertThat(auskunft.urlaubsdaten(jonas.getMitarbeiterId(), 2026)).contains(new UrlaubsDaten(2026, 30, 4));
        assertThat(auskunft.urlaubsdaten(jonas.getMitarbeiterId(), 2027)).isEmpty();
        assertThat(auskunft.urlaubsdaten(999_999L, 2026)).isEmpty();
        assertThat(auskunft.urlaubsdaten(jonas.getMitarbeiterId(), 100_000)).isEmpty();

        // Stundensatz (vorbereitet für M4): vorhanden, nicht eingetragen, unbekannt
        assertThat(auskunft.stundensatz(jonas.getMitarbeiterId())).contains(new BigDecimal("32.50"));
        assertThat(auskunft.stundensatz(lena.getMitarbeiterId())).isEmpty();
        assertThat(auskunft.stundensatz(999_999L)).isEmpty();
    }
}

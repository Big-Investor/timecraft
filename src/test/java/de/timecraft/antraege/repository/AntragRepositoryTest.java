package de.timecraft.antraege.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.MaterialanpassungsantragTestersatz;
import de.timecraft.antraege.model.Stornoantrag;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
class AntragRepositoryTest {

    private static final Instant EINGEREICHT = Instant.parse("2026-10-22T06:12:00Z");

    @Autowired
    private AntragRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void speichertAbwesenheitsantragMitAllenFeldern() {
        Abwesenheitsantrag antrag = urlaub(1, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25));
        antrag.setBegruendung("Weihnachten bei der Familie in Kiel.");
        Long id = repository.save(antrag).getId();
        entityManager.flush();
        entityManager.clear();

        Abwesenheitsantrag gelesen = (Abwesenheitsantrag) repository.findById(id).orElseThrow();

        assertThat(gelesen.getAntragstellerId()).isEqualTo(1);
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(gelesen.getEingereichtAm()).isEqualTo(EINGEREICHT);
        assertThat(gelesen.getArt()).isEqualTo(AbwesenheitsArt.URLAUB);
        assertThat(gelesen.getUrlaubsart()).isEqualTo(Urlaubsart.ERHOLUNGSURLAUB);
        assertThat(gelesen.getVon()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(gelesen.getBis()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(gelesen.getBegruendung()).isEqualTo("Weihnachten bei der Familie in Kiel.");
        assertThat(gelesen.getEntscheiderId()).isNull();
        assertThat(gelesen.getEntschiedenAm()).isNull();
    }

    @Test
    void speichertZeitkorrekturMitAltenUndNeuenWerten() {
        Long id = repository.save(zeitkorrektur(1, 48213)).getId();
        entityManager.flush();
        entityManager.clear();

        Zeitkorrekturantrag gelesen = (Zeitkorrekturantrag) repository.findById(id).orElseThrow();

        assertThat(gelesen.getZeiteintragId()).isEqualTo(48213);
        assertThat(gelesen.getAlterBeginn()).isEqualTo(Instant.parse("2026-10-20T05:00:00Z"));
        assertThat(gelesen.getAltesEnde()).isEqualTo(Instant.parse("2026-10-20T12:30:00Z"));
        assertThat(gelesen.getAlterStandort()).isEqualTo(Standort.BUERO);
        assertThat(gelesen.getNeuerBeginn()).isEqualTo(Instant.parse("2026-10-20T05:00:00Z"));
        assertThat(gelesen.getNeuesEnde()).isEqualTo(Instant.parse("2026-10-20T14:15:00Z"));
        assertThat(gelesen.getNeuerStandort()).isEqualTo(Standort.UNTERWEGS);
        assertThat(gelesen.getNeuesProjektId()).isEqualTo(1024L);
    }

    @Test
    void speichertStornoMitBezugUndEntscheidung() {
        Long bezugId = repository.save(urlaub(1, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 30))).getId();
        Stornoantrag storno = new Stornoantrag();
        storno.setAntragstellerId(1);
        storno.setEingereichtAm(EINGEREICHT);
        storno.setBezugsantragId(bezugId);
        storno.setBegruendung("Reise faellt aus.");
        storno.setStatus(Antragsstatus.ABGELEHNT);
        storno.setEntscheiderId(2L);
        storno.setEntschiedenAm(Instant.parse("2026-10-23T08:00:00Z"));
        storno.setEntscheidungshinweis("Bitte neu beantragen.");
        Long id = repository.save(storno).getId();
        entityManager.flush();
        entityManager.clear();

        Antrag gelesen = repository.findById(id).orElseThrow();

        assertThat(gelesen).isInstanceOf(Stornoantrag.class);
        assertThat(gelesen.getBezugsantragId()).isEqualTo(bezugId);
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
        assertThat(gelesen.getEntscheiderId()).isEqualTo(2L);
        assertThat(gelesen.getEntscheidungshinweis()).isEqualTo("Bitte neu beantragen.");
        assertThat(repository.findByBezugsantragId(bezugId)).extracting(Antrag::getId).containsExactly(id);
    }

    @Test
    void schreibtDiskriminatorUndUntertabellenNachRelationenmodell() {
        Long urlaubId = repository.save(urlaub(1, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25))).getId();
        Long korrekturId = repository.save(zeitkorrektur(1, 7)).getId();
        Stornoantrag storno = new Stornoantrag();
        storno.setAntragstellerId(1);
        storno.setEingereichtAm(EINGEREICHT);
        Long stornoId = repository.save(storno).getId();
        entityManager.flush();

        assertThat(typ(urlaubId)).isEqualTo("ABWESENHEIT");
        assertThat(typ(korrekturId)).isEqualTo("ZEITKORREKTUR");
        assertThat(typ(stornoId)).isEqualTo("STORNO");
        assertThat(anzahl("abwesenheitsantrag", urlaubId)).isEqualTo(1);
        assertThat(anzahl("zeitkorrekturantrag", korrekturId)).isEqualTo(1);
        assertThat(anzahl("stornoantrag", stornoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select art from abwesenheitsantrag where antrag_id = ?", String.class,
                urlaubId)).isEqualTo("URLAUB");
    }

    /**
     * Enum-Spalten (status, art, urlaubsart, Standorte) legt Hibernate in H2 als ENUM an, in PostgreSQL
     * als VARCHAR mit der Laenge aus @Column; deren Laenge laesst sich deshalb hier nicht pruefen.
     */
    @Test
    void spaltenPassenZumRelationenmodell() {
        assertThat(spalten("antrag")).containsOnlyKeys("antrag_id", "antragsteller_id", "entscheider_id",
                "bezugsantrag_id", "typ", "status", "eingereicht_am", "begruendung", "entschieden_am",
                "entscheidungshinweis");
        assertThat(laenge("antrag", "typ")).isEqualTo(20);
        assertThat(laenge("antrag", "begruendung")).isEqualTo(255);
        assertThat(laenge("antrag", "entscheidungshinweis")).isEqualTo(255);
        assertThat(spalten("antrag").get("eingereicht_am")).asString().startsWithIgnoringCase("timestamp");
        assertThat(spalten("abwesenheitsantrag")).containsOnlyKeys("antrag_id", "art", "urlaubsart", "von", "bis");
        assertThat(spalten("abwesenheitsantrag").get("von")).asString().isEqualToIgnoringCase("date");
        assertThat(spalten("zeitkorrekturantrag")).containsOnlyKeys("antrag_id", "zeiteintrag_id",
                "neues_projekt_id", "alter_beginn", "altes_ende", "alter_standort", "neuer_beginn",
                "neues_ende", "neuer_standort");
        assertThat(spalten("stornoantrag")).containsOnlyKeys("antrag_id");
    }

    @Test
    void ohneEinreichungszeitpunktWirdNichtGespeichert() {
        Stornoantrag ohneZeitpunkt = new Stornoantrag();
        ohneZeitpunkt.setAntragstellerId(1);

        assertThatThrownBy(() -> {
            repository.save(ohneZeitpunkt);
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void anderesModulKannVonAntragErben() {
        MaterialanpassungsantragTestersatz material = new MaterialanpassungsantragTestersatz();
        material.setAntragstellerId(1);
        material.setEingereichtAm(EINGEREICHT);
        material.setProjektId(1024);
        Long id = repository.save(material).getId();
        repository.save(urlaub(1, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25)));
        entityManager.flush();
        entityManager.clear();

        assertThat(typ(id)).isEqualTo("MATERIAL_TEST");
        assertThat(repository.findById(id).orElseThrow()).isInstanceOf(MaterialanpassungsantragTestersatz.class);
        assertThat(repository.findByAntragstellerIdOrderByEingereichtAmDesc(1)).hasSize(2);
    }

    @Test
    void findetAbwesenheitenDieDenZeitraumBeruehren() {
        repository.save(urlaub(1, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 30)));
        Abwesenheitsantrag zurueckgezogen = urlaub(1, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27));
        zurueckgezogen.setStatus(Antragsstatus.ZURUECKGEZOGEN);
        repository.save(zurueckgezogen);
        repository.save(urlaub(4, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 30)));
        List<Antragsstatus> aktiv = List.of(Antragsstatus.OFFEN, Antragsstatus.GENEHMIGT);

        assertThat(repository.findeAbwesenheiten(1, aktiv, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)))
                .hasSize(1);
        assertThat(repository.findeAbwesenheiten(1, aktiv, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 26)))
                .hasSize(1);
        assertThat(repository.findeAbwesenheiten(1, aktiv, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2)))
                .isEmpty();
    }

    @Test
    void findetOffeneZeitkorrekturenEinerBuchung() {
        repository.save(zeitkorrektur(1, 7));
        Zeitkorrekturantrag abgelehnt = zeitkorrektur(1, 7);
        abgelehnt.setStatus(Antragsstatus.ABGELEHNT);
        repository.save(abgelehnt);
        repository.save(zeitkorrektur(1, 8));

        assertThat(repository.findeZeitkorrekturen(7, Antragsstatus.OFFEN)).hasSize(1);
        assertThat(repository.findeZeitkorrekturen(9, Antragsstatus.OFFEN)).isEmpty();
    }

    @Test
    void liefertEigeneAntraegeNeuesteZuerstUndSperrbarenAntrag() {
        Abwesenheitsantrag alt = urlaub(1, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18));
        alt.setEingereichtAm(Instant.parse("2026-09-01T08:00:00Z"));
        Long altId = repository.save(alt).getId();
        Long neuId = repository.save(urlaub(1, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25))).getId();
        repository.save(urlaub(4, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25)));

        assertThat(repository.findByAntragstellerIdOrderByEingereichtAmDesc(1))
                .extracting(Antrag::getId).containsExactly(neuId, altId);
        assertThat(repository.findByAntragstellerIdInOrderByEingereichtAmDesc(List.of(1L, 4L))).hasSize(3);
        assertThat(repository.findeZumAendern(altId)).isPresent();
        assertThat(repository.findeZumAendern(-1)).isEmpty();
    }

    private String typ(Long id) {
        return jdbc.queryForObject("select typ from antrag where antrag_id = ?", String.class, id);
    }

    private int anzahl(String tabelle, Long id) {
        return jdbc.queryForObject("select count(*) from " + tabelle + " where antrag_id = ?", Integer.class, id);
    }

    private Map<String, Object> spalten(String tabelle) {
        Map<String, Object> ergebnis = new HashMap<>();
        jdbc.queryForList("select column_name, data_type from information_schema.columns where table_name = ?",
                tabelle).forEach(zeile -> ergebnis.put(((String) zeile.get("column_name")).toLowerCase(),
                        zeile.get("data_type")));
        return ergebnis;
    }

    private Integer laenge(String tabelle, String spalte) {
        return jdbc.queryForObject("select character_maximum_length from information_schema.columns"
                + " where table_name = ? and column_name = ?", Integer.class, tabelle, spalte);
    }

    private static Abwesenheitsantrag urlaub(long mitarbeiterId, LocalDate von, LocalDate bis) {
        Abwesenheitsantrag antrag = new Abwesenheitsantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setArt(AbwesenheitsArt.URLAUB);
        antrag.setUrlaubsart(Urlaubsart.ERHOLUNGSURLAUB);
        antrag.setVon(von);
        antrag.setBis(bis);
        return antrag;
    }

    private static Zeitkorrekturantrag zeitkorrektur(long mitarbeiterId, long zeiteintragId) {
        Zeitkorrekturantrag antrag = new Zeitkorrekturantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(EINGEREICHT);
        antrag.setZeiteintragId(zeiteintragId);
        antrag.setAlterBeginn(Instant.parse("2026-10-20T05:00:00Z"));
        antrag.setAltesEnde(Instant.parse("2026-10-20T12:30:00Z"));
        antrag.setAlterStandort(Standort.BUERO);
        antrag.setNeuerBeginn(Instant.parse("2026-10-20T05:00:00Z"));
        antrag.setNeuesEnde(Instant.parse("2026-10-20T14:15:00Z"));
        antrag.setNeuerStandort(Standort.UNTERWEGS);
        antrag.setNeuesProjektId(1024L);
        return antrag;
    }
}

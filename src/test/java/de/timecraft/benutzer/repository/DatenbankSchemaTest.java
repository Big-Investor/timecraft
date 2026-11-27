package de.timecraft.benutzer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vergleicht die von Hibernate erzeugten Tabellen mit dem Relationenmodell (Abb. 48 im Projektbericht):
 * Spaltennamen, Datentypen, Längen und Pflichtangaben der sechs Tabellen von M1.
 * Die Erwartung steht als einfacher Text, z. B. "benutzername VARCHAR(50) NOT NULL".
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DatenbankSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tabellenBenutzerUndMitarbeiterPassenZumRelationenmodell() {
        assertThat(spaltenDerTabelle("benutzer")).containsExactlyInAnyOrder(
                "benutzer_id BIGINT NOT NULL",
                "benutzername VARCHAR(50) NOT NULL",
                "passwort_hash VARCHAR(60)",
                "rolle VARCHAR(15) NOT NULL",
                "gesperrt BOOLEAN NOT NULL",
                "fehlversuche SMALLINT NOT NULL",
                "einmalpasswort_hash VARCHAR(60)",
                "einmalpasswort_gueltig_bis TIMESTAMP",
                "passwort_aenderung_noetig BOOLEAN NOT NULL");
        assertThat(spaltenDerTabelle("mitarbeiter")).containsExactlyInAnyOrder(
                "mitarbeiter_id BIGINT NOT NULL",
                "benutzer_id BIGINT NOT NULL",
                "vorgesetzter_id BIGINT",
                "personalnummer VARCHAR(10) NOT NULL",
                "vorname VARCHAR(50) NOT NULL",
                "nachname VARCHAR(50) NOT NULL",
                "email VARCHAR(100) NOT NULL",
                "telefon VARCHAR(30)",
                "geburtsdatum DATE NOT NULL",
                "eintrittsdatum DATE NOT NULL",
                "austrittsdatum DATE",
                "qualifikation VARCHAR(10) NOT NULL",
                "aktiv BOOLEAN NOT NULL",
                "stundensatz NUMERIC(8,2)");
    }

    @Test
    void tabellenDerStartwertePassenZumRelationenmodell() {
        assertThat(spaltenDerTabelle("arbeitszeitmodell")).containsExactlyInAnyOrder(
                "arbeitszeitmodell_id BIGINT NOT NULL",
                "mitarbeiter_id BIGINT NOT NULL",
                "wochenstunden NUMERIC(4,2) NOT NULL");
        assertThat(spaltenDerTabelle("arbeitstag")).containsExactlyInAnyOrder(
                "arbeitszeitmodell_id BIGINT NOT NULL",
                "wochentag SMALLINT NOT NULL");
        assertThat(spaltenDerTabelle("zeitkonto")).containsExactlyInAnyOrder(
                "zeitkonto_id BIGINT NOT NULL",
                "mitarbeiter_id BIGINT NOT NULL",
                "startdatum DATE NOT NULL",
                "anfangssaldo_min INTEGER NOT NULL");
        assertThat(spaltenDerTabelle("urlaubskonto")).containsExactlyInAnyOrder(
                "urlaubskonto_id BIGINT NOT NULL",
                "mitarbeiter_id BIGINT NOT NULL",
                "jahr SMALLINT NOT NULL",
                "anspruch_tage SMALLINT NOT NULL",
                "uebertrag_tage SMALLINT NOT NULL");

        // Die Tabelle arbeitstag hat einen zusammengesetzten Primärschlüssel
        List<String> schluesselSpalten = jdbc.queryForList(
                "SELECT k.column_name FROM information_schema.table_constraints t "
                        + "JOIN information_schema.key_column_usage k ON t.constraint_name = k.constraint_name "
                        + "WHERE t.table_name = 'arbeitstag' AND t.constraint_type = 'PRIMARY KEY'",
                String.class);

        assertThat(schluesselSpalten).containsExactlyInAnyOrder("arbeitszeitmodell_id", "wochentag");
    }

    /** Liest alle Spalten einer Tabelle aus der Datenbank und beschreibt jede als kurzen Text. */
    private List<String> spaltenDerTabelle(String tabelle) {
        List<Map<String, Object>> zeilen = jdbc.queryForList(
                "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, "
                        + "is_nullable FROM information_schema.columns WHERE table_name = ?",
                tabelle);

        List<String> spalten = new ArrayList<>();
        for (Map<String, Object> zeile : zeilen) {
            String text = zeile.get("column_name") + " " + typAlsText(zeile);
            if ("NO".equals(zeile.get("is_nullable"))) {
                text = text + " NOT NULL";
            }
            spalten.add(text);
        }
        return spalten;
    }

    /** Macht aus den Angaben der Datenbank einen Typ wie "VARCHAR(50)" oder "NUMERIC(8,2)". */
    private String typAlsText(Map<String, Object> zeile) {
        // Die Testdatenbank liefert die Typnamen klein geschrieben, wir vergleichen in Großbuchstaben.
        String typ = zeile.get("data_type").toString().toUpperCase(Locale.ROOT);
        if (typ.equals("CHARACTER VARYING")) {
            return "VARCHAR(" + zeile.get("character_maximum_length") + ")";
        }
        if (typ.equals("NUMERIC")) {
            return "NUMERIC(" + zeile.get("numeric_precision") + "," + zeile.get("numeric_scale") + ")";
        }
        return typ;
    }
}

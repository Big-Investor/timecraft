package de.timecraft.benutzer.repository;

import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff auf die Tabelle {@code mitarbeiter}.
 *
 * @author Leon-Selim Kannenberg
 */
public interface MitarbeiterRepository extends JpaRepository<Mitarbeiter, Long> {

    /** Der Mitarbeiter, der zu einem Benutzerkonto gehört (1:1). */
    Optional<Mitarbeiter> findByBenutzer(Benutzer benutzer);

    /** Prüft, ob eine Personalnummer schon vergeben ist (QA-M1-05). */
    boolean existsByPersonalnummer(String personalnummer);

    /** Alle Mitarbeiter, auch ehemalige, sortiert nach Personalnummer. */
    List<Mitarbeiter> findAllByOrderByPersonalnummerAsc();

    /** Nur aktive Mitarbeiter, sortiert nach Personalnummer. */
    List<Mitarbeiter> findByAktivTrueOrderByPersonalnummerAsc();

    /** Aktive Mitarbeiter, die diesen Vorgesetzten haben (Regel beim Deaktivieren, QA-M1-06). */
    List<Mitarbeiter> findByVorgesetzterAndAktivTrue(Mitarbeiter vorgesetzter);
}

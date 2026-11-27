package de.timecraft.benutzer.repository;

import de.timecraft.benutzer.model.Benutzer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff auf die Tabelle {@code benutzer}.
 * Spring Data erzeugt die Abfragen aus den Methodennamen.
 *
 * @author Leon-Selim Kannenberg
 */
public interface BenutzerRepository extends JpaRepository<Benutzer, Long> {

    /** Sucht ein Konto über den Benutzernamen (für die Anmeldung). */
    Optional<Benutzer> findByBenutzername(String benutzername);

    /** Prüft, ob ein Benutzername schon vergeben ist (QA-M1-05). */
    boolean existsByBenutzername(String benutzername);
}

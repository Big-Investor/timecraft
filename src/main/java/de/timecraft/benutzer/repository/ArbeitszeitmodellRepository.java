package de.timecraft.benutzer.repository;

import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Mitarbeiter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff auf die Tabellen {@code arbeitszeitmodell} und {@code arbeitstag}.
 *
 * @author Leon-Selim Kannenberg
 */
public interface ArbeitszeitmodellRepository extends JpaRepository<Arbeitszeitmodell, Long> {

    Optional<Arbeitszeitmodell> findByMitarbeiter(Mitarbeiter mitarbeiter);
}

package de.timecraft.benutzer.repository;

import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Zeitkonto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff auf die Tabelle {@code zeitkonto} (nur Startwerte).
 *
 * @author Leon-Selim Kannenberg
 */
public interface ZeitkontoRepository extends JpaRepository<Zeitkonto, Long> {

    Optional<Zeitkonto> findByMitarbeiter(Mitarbeiter mitarbeiter);
}

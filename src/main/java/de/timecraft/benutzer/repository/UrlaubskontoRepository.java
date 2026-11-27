package de.timecraft.benutzer.repository;

import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Urlaubskonto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff auf die Tabelle {@code urlaubskonto}.
 *
 * @author Leon-Selim Kannenberg
 */
public interface UrlaubskontoRepository extends JpaRepository<Urlaubskonto, Long> {

    /** Das Urlaubskonto eines Mitarbeiters für ein bestimmtes Jahr. */
    Optional<Urlaubskonto> findByMitarbeiterAndJahr(Mitarbeiter mitarbeiter, short jahr);
}

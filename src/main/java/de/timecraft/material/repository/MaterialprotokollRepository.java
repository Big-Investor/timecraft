package de.timecraft.material.repository;

import de.timecraft.material.model.Materialprotokoll;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Nur Anlegen und Lesen: Das Protokoll ist unveraenderlich, deshalb kein JpaRepository mit Loeschen.
 *
 * @author Tharsan Arumuganathar
 */
public interface MaterialprotokollRepository extends Repository<Materialprotokoll, Long> {

    /**
     * @param eintrag neuer Protokolleintrag
     * @return gespeicherter Eintrag
     */
    Materialprotokoll save(Materialprotokoll eintrag);

    /**
     * @param projektId Projekt
     * @return Protokoll des Projekts, zeitlich sortiert
     */
    List<Materialprotokoll> findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(long projektId);
}

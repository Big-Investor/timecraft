package de.timecraft.material.repository;

import de.timecraft.material.model.Materialprotokoll;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Nur Anlegen und Lesen: Das Protokoll ist unveraenderlich, deshalb kein JpaRepository mit Loeschen. */
public interface MaterialprotokollRepository extends Repository<Materialprotokoll, Long> {

    Materialprotokoll save(Materialprotokoll eintrag);

    List<Materialprotokoll> findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(long projektId);
}

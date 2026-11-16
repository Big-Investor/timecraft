package de.timecraft.projekte.repository;

import de.timecraft.projekte.model.Projekt;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Zugriff auf die Tabelle "projekt".
 * <p>
 * Von JpaRepository gibt es fertig: save (speichern), findById (suchen), findAll (alle holen).
 * Die Methode zum Filtern der Projektliste kommt in einem späteren Schritt dazu.
 */
public interface ProjektRepository extends JpaRepository<Projekt, Long> {
}

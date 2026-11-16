package de.timecraft.projekte.repository;

import de.timecraft.projekte.model.Projektzuordnung;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Zugriff auf die Tabelle "projektzuordnung".
 * <p>
 * Der Schlüssel ist das Paar (Mitarbeiter, Projekt), siehe {@link Projektzuordnung.Schluessel}.
 * Spring Data baut die Abfragen unten allein aus den Methodennamen:
 * "findByProjektId" heißt "suche alle Zeilen, deren projektId diesen Wert hat".
 */
public interface ProjektzuordnungRepository extends JpaRepository<Projektzuordnung, Projektzuordnung.Schluessel> {

    /** Alle Zuordnungen eines Projekts, also das Team des Projekts. */
    List<Projektzuordnung> findByProjektId(long projektId);

    /** Alle Zuordnungen eines Mitarbeiters, also alle Projekte, in denen er arbeitet. */
    List<Projektzuordnung> findByMitarbeiterId(long mitarbeiterId);

    /** Ist dieser Mitarbeiter diesem Projekt zugeordnet? */
    boolean existsByMitarbeiterIdAndProjektId(long mitarbeiterId, long projektId);
}

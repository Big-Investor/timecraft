package de.timecraft.projekte.repository;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Zugriff auf die Tabelle "projekt".
 * <p>
 * Von JpaRepository gibt es fertig: save (speichern), findById (suchen), findAll (alle holen).
 * Dazu kommt die Filterabfrage für die Projektliste.
 */
public interface ProjektRepository extends JpaRepository<Projekt, Long> {

    /**
     * Die Projektliste mit allen gesetzten Filtern, sortiert nach Projekt-ID.
     * Das ist die Kurzform für die Oberfläche und den Service: Sie reicht den Filter nur an
     * {@link #sucheProjekte} weiter.
     */
    default List<Projekt> filtern(ProjektFilter filter) {
        return sucheProjekte(filter.getMitarbeiterId(), filter.getStatus(),
                filter.getStartVon(), filter.getStartBis(),
                filter.getZielVon(), filter.getZielBis(), filter.getName());
    }

    /**
     * Die eigentliche Abfrage. Jede Zeile der Bedingung heißt: "Dieser Filter ist nicht gesetzt (null)
     * ODER das Projekt erfüllt ihn." Ein nicht gesetzter Filter lässt also alle Projekte durch.
     * Alle Zeilen sind mit UND verbunden, deshalb wirken die gesetzten Filter gemeinsam.
     * <ul>
     *   <li>Mitarbeiter: Es muss eine Zuordnung dieses Mitarbeiters zu diesem Projekt geben.</li>
     *   <li>Datumsgrenzen: "von" und "bis" nehmen den Grenztag selbst mit (größer-gleich, kleiner-gleich).</li>
     *   <li>Name: "locate" liefert die Stelle, an der der Suchtext im Namen steht (0 = kommt nicht vor).
     *       Beide Texte werden vorher klein geschrieben, daher ist die Schreibweise egal.
     *       Anders als bei "like" haben "%" und "_" im Suchtext keine Sonderbedeutung.</li>
     * </ul>
     */
    @Query("""
            select p from Projekt p
            where (:mitarbeiterId is null or exists (
                      select z from Projektzuordnung z
                      where z.projektId = p.projektId and z.mitarbeiterId = :mitarbeiterId))
              and (:status is null or p.status = :status)
              and (:startVon is null or p.startdatum >= :startVon)
              and (:startBis is null or p.startdatum <= :startBis)
              and (:zielVon is null or p.zieldatum >= :zielVon)
              and (:zielBis is null or p.zieldatum <= :zielBis)
              and (:name is null or locate(lower(:name), lower(p.name)) > 0)
            order by p.projektId
            """)
    List<Projekt> sucheProjekte(
            @Param("mitarbeiterId") Long mitarbeiterId,
            @Param("status") ProjektStatus status,
            @Param("startVon") LocalDate startVon,
            @Param("startBis") LocalDate startBis,
            @Param("zielVon") LocalDate zielVon,
            @Param("zielBis") LocalDate zielBis,
            @Param("name") String name);
}

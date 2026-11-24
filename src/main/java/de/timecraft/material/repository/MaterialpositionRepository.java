package de.timecraft.material.repository;

import de.timecraft.material.model.Materialposition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Tharsan Arumuganathar
 */
public interface MaterialpositionRepository extends JpaRepository<Materialposition, Long> {

    /**
     * @param projektId Projekt
     * @return Positionen des Projekts in Anlagereihenfolge
     */
    List<Materialposition> findByProjektIdOrderByIdAsc(long projektId);

    /**
     * @param projektId Projekt
     * @param materialId Material
     * @return true, wenn das Material im Projekt schon eingeplant ist
     */
    boolean existsByProjektIdAndMaterialId(long projektId, long materialId);
}

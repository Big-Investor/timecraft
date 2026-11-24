package de.timecraft.material.repository;

import de.timecraft.material.model.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Tharsan Arumuganathar
 */
public interface MaterialRepository extends JpaRepository<Material, Long> {

    /**
     * @return ganzer Materialstamm nach Artikelnummer
     */
    List<Material> findAllByOrderByArtikelnummerAsc();

    /**
     * @return aktive Materialien nach Artikelnummer
     */
    List<Material> findByAktivTrueOrderByArtikelnummerAsc();

    /**
     * @param artikelnummer gesuchte Artikelnummer
     * @return true, wenn sie schon vergeben ist
     */
    boolean existsByArtikelnummer(String artikelnummer);

    /**
     * @param artikelnummer gesuchte Artikelnummer
     * @param id Material, das nicht mitzaehlt (das gerade bearbeitete)
     * @return true, wenn ein anderes Material sie schon hat
     */
    boolean existsByArtikelnummerAndIdNot(String artikelnummer, Long id);
}

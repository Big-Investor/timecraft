package de.timecraft.material.repository;

import de.timecraft.material.model.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findAllByOrderByArtikelnummerAsc();

    List<Material> findByAktivTrueOrderByArtikelnummerAsc();

    boolean existsByArtikelnummer(String artikelnummer);

    boolean existsByArtikelnummerAndIdNot(String artikelnummer, Long id);
}

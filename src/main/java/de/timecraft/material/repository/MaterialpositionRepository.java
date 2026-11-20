package de.timecraft.material.repository;

import de.timecraft.material.model.Materialposition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialpositionRepository extends JpaRepository<Materialposition, Long> {

    List<Materialposition> findByProjektIdOrderByIdAsc(long projektId);
}

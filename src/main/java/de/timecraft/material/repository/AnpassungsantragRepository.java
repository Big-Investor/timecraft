package de.timecraft.material.repository;

import de.timecraft.material.model.Materialanpassungsantrag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnpassungsantragRepository extends JpaRepository<Materialanpassungsantrag, Long> {
}

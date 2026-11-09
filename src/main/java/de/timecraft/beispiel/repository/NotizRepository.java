package de.timecraft.beispiel.repository;

import de.timecraft.beispiel.model.Notiz;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotizRepository extends JpaRepository<Notiz, Long> {

    List<Notiz> findAllByOrderByErstelltAmDesc();
}

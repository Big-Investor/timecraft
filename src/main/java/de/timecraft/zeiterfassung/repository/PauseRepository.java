package de.timecraft.zeiterfassung.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.timecraft.zeiterfassung.model.Pause;

/** @author Noah Cipa */
public interface PauseRepository extends JpaRepository<Pause, Long> {

    Optional<Pause> findByZeiteintrag_IdAndEndeIsNull(long zeiteintragId);

    List<Pause> findByZeiteintrag_Id(long zeiteintragId);
}

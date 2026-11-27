package de.timecraft.zeiterfassung.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import de.timecraft.zeiterfassung.model.Feiertag;

/** @author Noah Cipa */
public interface FeiertagRepository extends JpaRepository<Feiertag, Long> {

    boolean existsByDatum(LocalDate datum);

    List<Feiertag> findAllByOrderByDatum();

    List<Feiertag> findByDatumBetweenOrderByDatum(LocalDate von, LocalDate bis);
}

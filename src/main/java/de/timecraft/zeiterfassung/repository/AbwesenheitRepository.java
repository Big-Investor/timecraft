package de.timecraft.zeiterfassung.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import de.timecraft.zeiterfassung.model.Abwesenheit;
import jakarta.persistence.LockModeType;

/** @author Noah Cipa */
public interface AbwesenheitRepository extends JpaRepository<Abwesenheit, Long> {

    List<Abwesenheit> findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(long mitarbeiterId, LocalDate bis, LocalDate von);

    List<Abwesenheit> findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(LocalDate bis, LocalDate von);

    Optional<Abwesenheit> findByAntragId(long antragId);

    /** Alle Abwesenheiten der Person, bis zum Ende der Transaktion fuer andere gesperrt (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Abwesenheit> findWithLockByMitarbeiterId(long mitarbeiterId);
}

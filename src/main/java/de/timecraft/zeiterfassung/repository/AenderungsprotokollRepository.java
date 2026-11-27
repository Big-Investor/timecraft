package de.timecraft.zeiterfassung.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import de.timecraft.zeiterfassung.model.Aenderungsprotokoll;

/** @author Noah Cipa */
public interface AenderungsprotokollRepository extends Repository<Aenderungsprotokoll, Long> {

    Aenderungsprotokoll save(Aenderungsprotokoll eintrag);

    List<Aenderungsprotokoll> findByZeiteintrag_IdOrderByZeitpunkt(long zeiteintragId);
}

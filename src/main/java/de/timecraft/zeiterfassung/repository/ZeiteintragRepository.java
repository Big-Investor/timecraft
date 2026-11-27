package de.timecraft.zeiterfassung.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.timecraft.zeiterfassung.model.Zeiteintrag;
import jakarta.persistence.LockModeType;

/** @author Noah Cipa */
public interface ZeiteintragRepository extends JpaRepository<Zeiteintrag, Long> {

    Optional<Zeiteintrag> findByMitarbeiterIdAndEndeIsNull(long mitarbeiterId);

    /** Nur die ID, ohne den Eintrag zu laden (siehe BuchungsAenderung: erst sperren, dann laden). */
    @Query("select z.mitarbeiterId from Zeiteintrag z where z.id = :id")
    Optional<Long> findeMitarbeiterId(@Param("id") long zeiteintragId);

    /** Neuester Eintrag, bis zum Ende der Transaktion fuer andere gesperrt (Standard-JPA, SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Zeiteintrag> findFirstByMitarbeiterIdOrderByBeginnDesc(long mitarbeiterId);

    /** Eintraege mit Beginn im halboffenen Intervall [von, bis). */
    List<Zeiteintrag> findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
            long mitarbeiterId, Instant von, Instant bis);

    /**
     * Eintraege, die sich mit dem Zeitraum (beginn, ende) ueberschneiden, egal wie lange vorher sie begonnen
     * haben. Ein laufender Eintrag hat noch kein Ende. Angrenzende Eintraege zaehlen nicht.
     */
    @Query("select z from Zeiteintrag z where z.mitarbeiterId = :mitarbeiterId and z.beginn < :ende "
            + "and (z.ende is null or z.ende > :beginn) order by z.beginn")
    List<Zeiteintrag> findeUeberschneidende(@Param("mitarbeiterId") long mitarbeiterId,
            @Param("beginn") Instant beginn, @Param("ende") Instant ende);

    List<Zeiteintrag> findByProjektIdAndEndeIsNull(Long projektId);

    /** Wie {@link #findeUeberschneidende}, aber fuer alle Mitarbeiter eines Projekts. */
    @Query("select z from Zeiteintrag z where z.projektId = :projektId and z.beginn < :ende "
            + "and (z.ende is null or z.ende > :beginn) order by z.beginn")
    List<Zeiteintrag> findeUeberschneidendeAufProjekt(@Param("projektId") long projektId,
            @Param("beginn") Instant beginn, @Param("ende") Instant ende);
}

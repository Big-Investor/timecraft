package de.timecraft.antraege.repository;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AntragRepository extends JpaRepository<Antrag, Long> {

    List<Antrag> findByAntragstellerIdOrderByEingereichtAmDesc(long antragstellerId);

    List<Antrag> findByAntragstellerIdInOrderByEingereichtAmDesc(Collection<Long> antragstellerIds);

    /** Folgeantraege (Aenderung oder Storno) zu einem Antrag. */
    List<Antrag> findByBezugsantragId(long bezugsantragId);

    /** Liest den Antrag und sperrt die Zeile bis zum Ende der Transaktion (gegen doppelte Entscheidungen). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Antrag a where a.id = :id")
    Optional<Antrag> findeZumAendern(@Param("id") long id);

    /** Abwesenheitsantraege einer Person mit diesen Status, die den Zeitraum [von, bis] beruehren. */
    @Query("select a from Abwesenheitsantrag a where a.antragstellerId = :mitarbeiterId"
            + " and a.status in :status and a.von <= :bis and a.bis >= :von")
    List<Abwesenheitsantrag> findeAbwesenheiten(@Param("mitarbeiterId") long mitarbeiterId,
            @Param("status") Collection<Antragsstatus> status,
            @Param("von") LocalDate von, @Param("bis") LocalDate bis);

    @Query("select z from Zeitkorrekturantrag z where z.zeiteintragId = :zeiteintragId and z.status = :status")
    List<Zeitkorrekturantrag> findeZeitkorrekturen(@Param("zeiteintragId") long zeiteintragId,
            @Param("status") Antragsstatus status);
}

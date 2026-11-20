package de.timecraft.material.repository;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.material.model.Materialanpassungsantrag;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnpassungsantragRepository extends JpaRepository<Materialanpassungsantrag, Long> {

    List<Materialanpassungsantrag> findByAntragstellerIdOrderByEingereichtAmDescIdDesc(long antragstellerId);

    List<Materialanpassungsantrag> findByMaterialpositionProjektIdAndStatusOrderByEingereichtAmAsc(long projektId,
            Antragsstatus status);

    /** Sperrt den Antrag bis zum Ende der Transaktion, damit niemand gleichzeitig entscheidet. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Materialanpassungsantrag a where a.id = :antragId")
    Optional<Materialanpassungsantrag> findeGesperrt(@Param("antragId") long antragId);
}

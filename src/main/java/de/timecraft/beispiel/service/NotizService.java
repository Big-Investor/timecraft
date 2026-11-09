package de.timecraft.beispiel.service;

import de.timecraft.beispiel.model.Notiz;
import de.timecraft.beispiel.repository.NotizRepository;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vorlage fuer einen Service: Die Person kommt als Parameter von der View (aus AktuelleSitzung),
 * der Service prueft die Rechte selbst und kennt den Webkontext nicht. Eine fehlende Rolle
 * (null) bedeutet: niemand ist angemeldet.
 */
@Service
public class NotizService {

    public static final int MAX_LAENGE = 500;

    private final NotizRepository repository;
    private final Clock clock;

    public NotizService(NotizRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Alle angemeldeten Personen duerfen Notizen anlegen. */
    @Transactional
    public Notiz anlegen(long mitarbeiterId, Rolle rolle, String text) {
        pruefeAngemeldet(rolle);
        if (text == null || text.isBlank()) {
            throw new FachlicheException("Bitte geben Sie einen Text ein.");
        }
        String bereinigt = text.strip();
        if (bereinigt.length() > MAX_LAENGE) {
            throw new FachlicheException("Die Notiz darf höchstens " + MAX_LAENGE + " Zeichen lang sein.");
        }
        return repository.save(new Notiz(bereinigt, LocalDateTime.now(clock), mitarbeiterId));
    }

    /** Alle angemeldeten Personen duerfen alle Notizen lesen, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<Notiz> alle(Rolle rolle) {
        pruefeAngemeldet(rolle);
        return repository.findAllByOrderByErstelltAmDesc();
    }

    /** Nur Admin darf Notizen loeschen. */
    @Transactional
    public void loeschen(Rolle rolle, long notizId) {
        pruefeAngemeldet(rolle);
        if (rolle != Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Nur Administratoren dürfen Notizen löschen.");
        }
        Notiz notiz = repository.findById(notizId)
                .orElseThrow(() -> new NichtGefundenException("Die Notiz wurde nicht gefunden."));
        repository.delete(notiz);
    }

    private void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }
}

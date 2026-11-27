package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Stornoantrag;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genehmigen und Ablehnen durch die zustaendige Projektleitung (UC-M5-08, UC-M5-09).
 * Der Antrag wird gesperrt gelesen, damit niemand gleichzeitig entscheidet (FA-M5-16).
 * Bei Genehmigung aendern sich Antrag und Fachdaten in M2 in einer Transaktion (FA-M5-13,
 * QA-M5-02); jeder Fehler rollt alles zurueck und der Antrag bleibt offen.
 */
@Service
public class EntscheidungService {

    private final AntragRepository repository;
    private final AntragRegelnService regeln;
    private final AbwesenheitsUebernahme abwesenheitsUebernahme;
    private final BuchungsAenderung buchungsAenderung;
    private final Clock clock;

    public EntscheidungService(AntragRepository repository, AntragRegelnService regeln,
            AbwesenheitsUebernahme abwesenheitsUebernahme, BuchungsAenderung buchungsAenderung, Clock clock) {
        this.repository = repository;
        this.regeln = regeln;
        this.abwesenheitsUebernahme = abwesenheitsUebernahme;
        this.buchungsAenderung = buchungsAenderung;
        this.clock = clock;
    }

    /** Prueft Zustaendigkeit, Status und Fachregeln erneut und uebernimmt die Fachdaten in M2. */
    @Transactional
    public void genehmigen(long entscheiderId, Rolle rolle, long antragId, String hinweis) {
        Antrag antrag = offenerZustaendigerAntrag(entscheiderId, rolle, antragId, hinweis);
        regeln.pruefe(antrag);
        fachdatenUebernehmen(antrag, entscheiderId);
        entscheiden(antrag, Antragsstatus.GENEHMIGT, entscheiderId, hinweis);
    }

    /** Fachdaten bleiben unveraendert (FA-M5-14); der Hinweis ist fuer den Antragsteller sichtbar. */
    @Transactional
    public void ablehnen(long entscheiderId, Rolle rolle, long antragId, String hinweis) {
        Antrag antrag = offenerZustaendigerAntrag(entscheiderId, rolle, antragId, hinweis);
        entscheiden(antrag, Antragsstatus.ABGELEHNT, entscheiderId, hinweis);
    }

    private Antrag offenerZustaendigerAntrag(long entscheiderId, Rolle rolle, long antragId, String hinweis) {
        AntragRechte.pruefeProjektleitung(rolle);
        AntragRegelnService.pruefeTextlaenge(hinweis, "Der Entscheidungshinweis");
        Antrag antrag = repository.findeZumAendern(antragId)
                .filter(gefunden -> Antragstyp.von(gefunden) != null)
                .orElseThrow(() -> new NichtGefundenException("Der Antrag wurde nicht gefunden."));
        if (antrag.getAntragstellerId() == entscheiderId) {
            throw new ZugriffVerweigertException("Eigene Anträge können Sie nicht selbst entscheiden.");
        }
        if (!regeln.istZustaendig(entscheiderId, antrag.getAntragstellerId())) {
            throw new ZugriffVerweigertException("Sie sind für diesen Antrag nicht zuständig.");
        }
        if (antrag.getStatus() == Antragsstatus.ZURUECKGEZOGEN) {
            throw new FachlicheException("Der Antrag wurde zurückgezogen und kann nicht mehr entschieden werden.");
        }
        if (antrag.getStatus() != Antragsstatus.OFFEN) {
            throw new FachlicheException("Der Antrag wurde bereits entschieden.");
        }
        return antrag;
    }

    private void fachdatenUebernehmen(Antrag antrag, long entscheiderId) {
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            if (abwesenheit.getBezugsantragId() != null) {
                abwesenheitAufheben(abwesenheit.getBezugsantragId());
            }
            abwesenheitsUebernahme.uebernehmen(abwesenheit.getId(), abwesenheit.getAntragstellerId(),
                    abwesenheit.getArt(), abwesenheit.getVon(), abwesenheit.getBis());
        } else if (antrag instanceof Zeitkorrekturantrag korrektur) {
            buchungsAenderung.aendern(korrektur.getZeiteintragId(), korrektur.getNeuerBeginn(),
                    korrektur.getNeuesEnde(), korrektur.getNeuerStandort(), korrektur.getNeuesProjektId(),
                    AenderungsArt.KORREKTUR, entscheiderId, korrektur.getBegruendung());
        } else if (antrag instanceof Stornoantrag) {
            abwesenheitAufheben(antrag.getBezugsantragId());
        }
    }

    /** Hebt die Abwesenheit eines genehmigten Antrags in M2 auf (Aenderung oder Storno, FA-M5-15). */
    private void abwesenheitAufheben(long bezugsantragId) {
        long abwesenheitId = abwesenheitsUebernahme.abwesenheitZuAntrag(bezugsantragId).orElseThrow(
                () -> new FachlicheException("Die genehmigte Abwesenheit wurde in der Zeiterfassung nicht gefunden."));
        abwesenheitsUebernahme.aufheben(abwesenheitId);
    }

    private void entscheiden(Antrag antrag, Antragsstatus status, long entscheiderId, String hinweis) {
        antrag.setStatus(status);
        antrag.setEntscheiderId(entscheiderId);
        antrag.setEntschiedenAm(clock.instant());
        antrag.setEntscheidungshinweis(hinweis == null || hinweis.isBlank() ? null : hinweis.strip());
    }
}

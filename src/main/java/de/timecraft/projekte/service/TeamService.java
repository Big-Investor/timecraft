package de.timecraft.projekte.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.model.TeamEintrag;
import de.timecraft.projekte.model.TeamFilter;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Team-Übersicht eines Projekts ("wer arbeitet wo und woran", Reiter "Team").
 * <p>
 * Datensparsamkeit ist hier der Kern, und die Regeln liegen nur in diesem Service:
 * <ul>
 *   <li>Ein zugeordneter Mitarbeiter sieht nur die Namen seines Teams. Die Buchungsdaten von M2
 *       werden für ihn gar nicht erst abgefragt.</li>
 *   <li>Die verantwortliche Projektleitung sieht je Person zusätzlich, ob sie eingestempelt ist, und bei
 *       eingestempelten Personen Standort und Beginn der laufenden Buchung auf dieses Projekt.
 *       Arbeitszeiten, Salden und Buchungen auf andere Projekte bekommt sie nie.</li>
 *   <li>Der Admin, nicht zugeordnete Personen und die Projektleitung anderer Projekte bekommen
 *       keinen Zugriff (auch nicht bei direktem Aufruf, z. B. über einen Link).</li>
 * </ul>
 * Die angemeldete Person kommt als Parameter (Mitarbeiter-ID und Rolle), eine Rolle null bedeutet:
 * Es ist niemand angemeldet.
 *
 * @author Tim Stukenbröker
 */
@Service
public class TeamService {

    private final ProjektRepository repository;
    private final ProjektzuordnungRepository zuordnungRepository;
    private final ProjektBerechtigung berechtigung;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final BuchungsAuskunft buchungsAuskunft;

    /**
     * "@Lazy" heißt: Spring besorgt sich MitarbeiterAuskunft (M1) und BuchungsAuskunft (M2) erst, wenn sie
     * zum ersten Mal gebraucht werden. Solange diese Module in diesem Branch noch fehlen, startet die
     * Anwendung so trotzdem.
     */
    public TeamService(ProjektRepository repository, ProjektzuordnungRepository zuordnungRepository,
            ProjektBerechtigung berechtigung, @Lazy MitarbeiterAuskunft mitarbeiterAuskunft,
            @Lazy BuchungsAuskunft buchungsAuskunft) {
        this.repository = repository;
        this.zuordnungRepository = zuordnungRepository;
        this.berechtigung = berechtigung;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.buchungsAuskunft = buchungsAuskunft;
    }

    /**
     * Die Team-Übersicht des Projekts, sortiert nach Name. Zeigt genau die dem Projekt zugeordneten
     * Mitarbeiter, die verantwortliche Projektleitung steht nicht als Zeile darin. Ein leeres Team
     * ergibt eine leere Liste (die Oberfläche zeigt dann einen Hinweis).
     * <p>
     * Der Filter (eingestempelt, Standort) gilt nur für die verantwortliche Projektleitung. Ein zugeordneter
     * Mitarbeiter bekommt die ungefilterte Namensliste: So verrät auch ein direkter Aufruf mit Filter nichts
     * über Status oder Standort. Ein Filter null bedeutet: nichts filtern.
     * <p>
     * Die Übersicht zeigt immer den Stand beim Aufruf. "Aktualisieren" ruft diese Methode einfach noch einmal auf.
     */
    @Transactional(readOnly = true)
    public List<TeamEintrag> teamAnsehen(long mitarbeiterId, Rolle rolle, long projektId, TeamFilter filter) {
        berechtigung.pruefeAngemeldet(rolle);
        Projekt projekt = ladeProjekt(projektId);
        // Hier sind zwei Personengruppen erlaubt (Projektleitung und zugeordneter Mitarbeiter, mit
        // unterschiedlicher Sicht). Deshalb prüft diese Methode selbst und nutzt nicht
        // ProjektBerechtigung.pruefeVerantwortlicheProjektleitung, das nur eine Gruppe zulässt.
        boolean istProjektleitung = rolle == Rolle.PROJEKTLEITUNG && projekt.getProjektleitungId() == mitarbeiterId;
        boolean istZugeordnet = rolle == Rolle.MITARBEITER
                && zuordnungRepository.existsByMitarbeiterIdAndProjektId(mitarbeiterId, projektId);
        if (!istProjektleitung && !istZugeordnet) {
            throw new ZugriffVerweigertException("Sie haben keinen Zugriff auf das Team dieses Projekts.");
        }
        if (projekt.getStatus() != ProjektStatus.AKTIV) {
            throw new FachlicheException("Das Projekt ist archiviert. Die Team-Übersicht gibt es nur für aktive Projekte.");
        }

        List<TeamEintrag> eintraege = istProjektleitung
                ? eintraegeMitStatus(projektId, filter)
                : eintraegeNurNamen(projektId);
        return eintraege.stream()
                .sorted(Comparator.comparing(TeamEintrag::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Ist dieser Mitarbeiter gerade auf dieses Projekt eingestempelt? Die Oberfläche nutzt das vor dem
     * Entfernen einer Zuordnung für den Hinweis (US-M3-11). Nur die verantwortliche Projektleitung des
     * Projekts darf fragen, denn die Antwort ist eine Statusangabe.
     */
    @Transactional(readOnly = true)
    public boolean istEingestempelt(long mitarbeiterId, Rolle rolle, long projektId, long gefragteMitarbeiterId) {
        berechtigung.pruefeAngemeldet(rolle);
        berechtigung.pruefeVerantwortlicheProjektleitung(mitarbeiterId, rolle, ladeProjekt(projektId));

        return buchungsAuskunft.laufendeBuchungenAufProjekt(projektId).stream()
                .anyMatch(buchung -> buchung.mitarbeiterId() == gefragteMitarbeiterId);
    }

    /** Ansicht der Projektleitung: Name, Status und bei eingestempelten Personen Standort und Beginn. */
    private List<TeamEintrag> eintraegeMitStatus(long projektId, TeamFilter filter) {
        // Pro Person gibt es höchstens eine laufende Buchung; falls M2 doch zwei liefert, zählt die erste.
        Map<Long, BuchungInfo> laufende = buchungsAuskunft.laufendeBuchungenAufProjekt(projektId).stream()
                .collect(Collectors.toMap(BuchungInfo::mitarbeiterId, Function.identity(), (erste, zweite) -> erste));

        return zugeordneteMitarbeiter(projektId).stream()
                .map(person -> eintragMitStatus(person, laufende.get(person.mitarbeiterId())))
                .filter(eintrag -> passtZumFilter(eintrag, filter))
                .toList();
    }

    private TeamEintrag eintragMitStatus(MitarbeiterInfo person, BuchungInfo laufendeBuchung) {
        if (laufendeBuchung == null) {
            return new TeamEintrag(person.mitarbeiterId(), person.name(), false, null, null);
        }
        return new TeamEintrag(person.mitarbeiterId(), person.name(), true, laufendeBuchung.standort(), laufendeBuchung.beginn());
    }

    /** Ansicht der zugeordneten Mitarbeiter: nur Namen, Status bleibt null. */
    private List<TeamEintrag> eintraegeNurNamen(long projektId) {
        return zugeordneteMitarbeiter(projektId).stream()
                .map(person -> new TeamEintrag(person.mitarbeiterId(), person.name(), null, null, null))
                .toList();
    }

    /**
     * Die Personen, die dem Projekt zugeordnet sind, mit ihren Daten aus M1. Auch eine inzwischen
     * deaktivierte Person bleibt im Team stehen, solange die Zuordnung besteht.
     */
    private List<MitarbeiterInfo> zugeordneteMitarbeiter(long projektId) {
        return zuordnungRepository.findByProjektId(projektId).stream()
                .map(Projektzuordnung::getMitarbeiterId)
                .map(id -> mitarbeiterAuskunft.finde(id).orElseThrow(
                        () -> new FachlicheException("Eine zugeordnete Person wurde nicht gefunden.")))
                .toList();
    }

    /**
     * Beide Filter wirken gemeinsam. Wer nicht eingestempelt ist, hat keinen Standort (null) und
     * fällt deshalb bei gesetztem Standortfilter heraus: Der Standortfilter gilt nur für Eingestempelte.
     */
    private boolean passtZumFilter(TeamEintrag eintrag, TeamFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.getEingestempelt() != null && !filter.getEingestempelt().equals(eintrag.eingestempelt())) {
            return false;
        }
        return filter.getStandort() == null || filter.getStandort() == eintrag.standort();
    }

    private Projekt ladeProjekt(long projektId) {
        return repository.findById(projektId)
                .orElseThrow(() -> new NichtGefundenException("Das Projekt wurde nicht gefunden."));
    }
}

package de.timecraft.projekte.service;

import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die lesende Schnittstelle von M3 für die anderen Module (M2, M4, M5, M6, M7), siehe Schnittstellenvertrag.
 * <p>
 * Diese Klasse prüft bewusst keine Rechte: Das ist im Vertrag so festgelegt, die aufrufende Service-Schicht
 * des anderen Moduls prüft sie. Sie gibt nur Daten weiter (als {@link ProjektInfo}, nie als Entity) und
 * ändert nichts. Alle Listen sind nach Projekt-ID bzw. Mitarbeiter-ID sortiert, damit die Reihenfolge
 * immer gleich ist. Eine unbekannte ID ergibt eine leere Liste oder ein leeres Optional, nie einen Fehler.
 *
 * @author Tim Stukenbröker
 */
@Service
@Transactional(readOnly = true)
public class ProjektAuskunftService implements ProjektAuskunft {

    private final ProjektRepository repository;
    private final ProjektzuordnungRepository zuordnungRepository;

    public ProjektAuskunftService(ProjektRepository repository, ProjektzuordnungRepository zuordnungRepository) {
        this.repository = repository;
        this.zuordnungRepository = zuordnungRepository;
    }

    @Override
    public Optional<ProjektInfo> finde(long projektId) {
        return repository.findById(projektId).map(ProjektAuskunftService::alsInfo);
    }

    /**
     * Die Auswahl beim Einstempeln: Projekte, denen der Mitarbeiter zugeordnet ist UND die aktiv sind.
     * Ein archiviertes Projekt fehlt hier (FA-M3-03). Das ist auch die Zuordnungs-Abfrage für die
     * zurückgestellte "freie Kapazität" (K1): Die Zahl der aktiven Zuordnungen ist die Länge dieser Liste.
     */
    @Override
    public List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId) {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(mitarbeiterId);
        filter.setStatus(ProjektStatus.AKTIV);
        return alsInfos(repository.filtern(filter));
    }

    @Override
    public List<ProjektInfo> verantworteteProjekte(long projektleitungId) {
        return alsInfos(repository.findByProjektleitungId(projektleitungId));
    }

    @Override
    public List<ProjektInfo> alle() {
        return alsInfos(repository.filtern(new ProjektFilter()));
    }

    @Override
    public List<Long> zugeordneteMitarbeiter(long projektId) {
        return zuordnungRepository.findByProjektId(projektId).stream()
                .map(Projektzuordnung::getMitarbeiterId)
                .sorted()
                .toList();
    }

    @Override
    public boolean istZugeordnet(long mitarbeiterId, long projektId) {
        return zuordnungRepository.existsByMitarbeiterIdAndProjektId(mitarbeiterId, projektId);
    }

    private static List<ProjektInfo> alsInfos(List<Projekt> projekte) {
        return projekte.stream()
                .sorted(Comparator.comparing(Projekt::getProjektId))
                .map(ProjektAuskunftService::alsInfo)
                .toList();
    }

    private static ProjektInfo alsInfo(Projekt projekt) {
        return new ProjektInfo(projekt.getProjektId(), projekt.getName(), projekt.getStartdatum(),
                projekt.getZieldatum(), projekt.getProjektleitungId(),
                projekt.getStatus() == ProjektStatus.AKTIV, projekt.getAngebotspreis());
    }
}

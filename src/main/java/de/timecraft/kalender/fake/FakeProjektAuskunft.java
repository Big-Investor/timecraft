package de.timecraft.kalender.fake;

import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Fake fuer die ProjektAuskunft von M3: Projekte und Zuordnungen liegen im Speicher. */
public class FakeProjektAuskunft implements ProjektAuskunft {

    private final Map<Long, ProjektInfo> projekte = new LinkedHashMap<>();
    private final Map<Long, Set<Long>> zuordnungen = new LinkedHashMap<>();

    public void hinzufuegen(ProjektInfo projekt, Long... mitarbeiterIds) {
        projekte.put(projekt.projektId(), projekt);
        zuordnungen.put(projekt.projektId(), new HashSet<>(List.of(mitarbeiterIds)));
    }

    @Override
    public Optional<ProjektInfo> finde(long projektId) {
        return Optional.ofNullable(projekte.get(projektId));
    }

    @Override
    public List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId) {
        return projekte.values().stream()
                .filter(ProjektInfo::aktiv)
                .filter(p -> istZugeordnet(mitarbeiterId, p.projektId()))
                .toList();
    }

    @Override
    public List<ProjektInfo> verantworteteProjekte(long projektleitungId) {
        return projekte.values().stream().filter(p -> p.projektleitungId() == projektleitungId).toList();
    }

    @Override
    public List<ProjektInfo> alle() {
        return new ArrayList<>(projekte.values());
    }

    @Override
    public List<Long> zugeordneteMitarbeiter(long projektId) {
        return new ArrayList<>(zuordnungen.getOrDefault(projektId, Set.of()));
    }

    @Override
    public boolean istZugeordnet(long mitarbeiterId, long projektId) {
        return zuordnungen.getOrDefault(projektId, Set.of()).contains(mitarbeiterId);
    }
}

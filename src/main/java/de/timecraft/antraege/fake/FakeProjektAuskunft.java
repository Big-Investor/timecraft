package de.timecraft.antraege.fake;

import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Ersatz fuer M3 (nur Tests und Profil "fake"). */
public class FakeProjektAuskunft implements ProjektAuskunft {

    public static final long KUECHE = 1024;
    public static final long TREPPE = 1031;
    public static final long ARCHIVIERT = 999;

    private final Map<Long, ProjektInfo> projekte = new HashMap<>();
    private final Map<Long, Set<Long>> zuordnungen = new HashMap<>();

    /** Jonas, Mehmet, Felix und Lena arbeiten auf Kueche und Treppe; Sara nur auf Kueche. */
    public static FakeProjektAuskunft beispiel() {
        FakeProjektAuskunft fake = new FakeProjektAuskunft();
        fake.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", true));
        fake.hinzufuegen(projekt(TREPPE, "Treppe Lindenstraße", true));
        fake.hinzufuegen(projekt(ARCHIVIERT, "Altbau Schulz", false));
        for (long person : List.of(FakeMitarbeiterAuskunft.JONAS, FakeMitarbeiterAuskunft.MEHMET,
                FakeMitarbeiterAuskunft.FELIX, FakeMitarbeiterAuskunft.LENA)) {
            fake.zuordnen(person, KUECHE);
            fake.zuordnen(person, TREPPE);
            fake.zuordnen(person, ARCHIVIERT);
        }
        fake.zuordnen(FakeMitarbeiterAuskunft.SARA, KUECHE);
        return fake;
    }

    public static ProjektInfo projekt(long id, String name, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, FakeMitarbeiterAuskunft.LENA, aktiv, null);
    }

    public void hinzufuegen(ProjektInfo projekt) {
        projekte.put(projekt.projektId(), projekt);
    }

    public void zuordnen(long mitarbeiterId, long projektId) {
        zuordnungen.computeIfAbsent(projektId, id -> new HashSet<>()).add(mitarbeiterId);
    }

    @Override
    public Optional<ProjektInfo> finde(long projektId) {
        return Optional.ofNullable(projekte.get(projektId));
    }

    @Override
    public List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId) {
        return alle().stream().filter(ProjektInfo::aktiv)
                .filter(projekt -> istZugeordnet(mitarbeiterId, projekt.projektId())).toList();
    }

    @Override
    public List<ProjektInfo> verantworteteProjekte(long projektleitungId) {
        return alle().stream().filter(projekt -> projekt.projektleitungId() == projektleitungId).toList();
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

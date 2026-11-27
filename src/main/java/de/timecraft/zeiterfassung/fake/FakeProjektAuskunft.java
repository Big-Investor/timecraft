package de.timecraft.zeiterfassung.fake;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;

/**
 * Ersatz fuer M3, solange das echte Modul fehlt (Profil "fake" und Tests):
 * drei Projekte von Paul Hartmann (ID 2, zwei aktiv, eins archiviert) und ein aktives von Sabine Engel (ID 10).
 *
 * @author Noah Cipa
 */
@Component
@Profile("fake")
public class FakeProjektAuskunft implements ProjektAuskunft {

    public static final long EINBAUKUECHE_WAGNER = 1024;
    public static final long TREPPE_LINDENSTRASSE = 1031;
    public static final long LADENBAU_KRAUSE = 1035;
    public static final long BUEROUMBAU_SCHMITT = 1040;

    private final List<ProjektInfo> projekte = List.of(
            new ProjektInfo(EINBAUKUECHE_WAGNER, "Einbauküche Wagner", LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 12, 18), FakeMitarbeiterAuskunft.PAUL_HARTMANN, true, new BigDecimal("18500.00")),
            new ProjektInfo(TREPPE_LINDENSTRASSE, "Treppe Lindenstraße", LocalDate.of(2026, 10, 5),
                    LocalDate.of(2027, 2, 26), FakeMitarbeiterAuskunft.PAUL_HARTMANN, true, new BigDecimal("9200.00")),
            new ProjektInfo(LADENBAU_KRAUSE, "Ladenbau Krause", LocalDate.of(2026, 3, 2),
                    LocalDate.of(2026, 7, 31), FakeMitarbeiterAuskunft.PAUL_HARTMANN, false, new BigDecimal("24000.00")),
            new ProjektInfo(BUEROUMBAU_SCHMITT, "Büroumbau Schmitt", LocalDate.of(2026, 11, 2),
                    LocalDate.of(2027, 3, 31), FakeMitarbeiterAuskunft.SABINE_ENGEL, true, new BigDecimal("31000.00")));

    private final Map<Long, List<Long>> zuordnungen = Map.of(
            EINBAUKUECHE_WAGNER, List.of(FakeMitarbeiterAuskunft.JONAS_BECKER, FakeMitarbeiterAuskunft.PAUL_HARTMANN,
                    FakeMitarbeiterAuskunft.MEHMET_YILMAZ),
            TREPPE_LINDENSTRASSE, List.of(FakeMitarbeiterAuskunft.JONAS_BECKER, FakeMitarbeiterAuskunft.LENA_SCHROEDER,
                    FakeMitarbeiterAuskunft.FELIX_BRANDT),
            LADENBAU_KRAUSE, List.of(FakeMitarbeiterAuskunft.JONAS_BECKER, FakeMitarbeiterAuskunft.FELIX_BRANDT),
            BUEROUMBAU_SCHMITT, List.of(FakeMitarbeiterAuskunft.SABINE_ENGEL, FakeMitarbeiterAuskunft.ANNA_VOGT,
                    FakeMitarbeiterAuskunft.LUKAS_MEYER));

    @Override
    public Optional<ProjektInfo> finde(long projektId) {
        for (ProjektInfo projekt : projekte) {
            if (projekt.projektId() == projektId) {
                return Optional.of(projekt);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId) {
        List<ProjektInfo> ergebnis = new ArrayList<>();
        for (ProjektInfo projekt : projekte) {
            if (projekt.aktiv() && istZugeordnet(mitarbeiterId, projekt.projektId())) {
                ergebnis.add(projekt);
            }
        }
        return ergebnis;
    }

    @Override
    public List<ProjektInfo> verantworteteProjekte(long projektleitungId) {
        List<ProjektInfo> ergebnis = new ArrayList<>();
        for (ProjektInfo projekt : projekte) {
            if (projekt.projektleitungId() == projektleitungId) {
                ergebnis.add(projekt);
            }
        }
        return ergebnis;
    }

    @Override
    public List<ProjektInfo> alle() {
        return projekte;
    }

    @Override
    public List<Long> zugeordneteMitarbeiter(long projektId) {
        return zuordnungen.getOrDefault(projektId, List.of());
    }

    @Override
    public boolean istZugeordnet(long mitarbeiterId, long projektId) {
        return zugeordneteMitarbeiter(projektId).contains(mitarbeiterId);
    }
}

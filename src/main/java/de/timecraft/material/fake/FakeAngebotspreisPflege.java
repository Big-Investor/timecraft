package de.timecraft.material.fake;

import de.timecraft.projekte.api.AngebotspreisPflege;
import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;

/**
 * Fake (Tests und Profil "fake") fuer AngebotspreisPflege von M3: schreibt den Preis in die Projekte der FakeProjektAuskunft.
 *
 * @author Tharsan Arumuganathar
 */
public class FakeAngebotspreisPflege implements AngebotspreisPflege {

    private final FakeProjektAuskunft projekte;

    /**
     * @param projekte Fake-Projekte, deren Angebotspreis gesetzt wird
     */
    public FakeAngebotspreisPflege(FakeProjektAuskunft projekte) {
        this.projekte = projekte;
    }

    @Override
    public void setzeAngebotspreis(long projektId, BigDecimal angebotspreis) {
        ProjektInfo alt = projekte.finde(projektId).orElseThrow();
        projekte.ersetzen(new ProjektInfo(alt.projektId(), alt.name(), alt.startdatum(), alt.zieldatum(),
                alt.projektleitungId(), alt.aktiv(), angebotspreis));
    }
}

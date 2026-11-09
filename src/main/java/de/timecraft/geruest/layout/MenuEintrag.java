package de.timecraft.geruest.layout;

import de.timecraft.benutzer.api.Rolle;
import java.util.Set;

/**
 * Ein Punkt im Seitenmenue. Jedes Modul stellt seine Eintraege als Spring-Beans bereit
 * ({@code @Component}); das Layout sammelt alle Beans, sortiert sie nach {@link #reihenfolge()}
 * und zeigt nur die Eintraege der Rolle der angemeldeten Person.
 *
 * <p>Das Menue ist nur Anzeige. Die Rechte prueft immer der Service, bei Views zusaetzlich
 * {@code @RolesAllowed}.
 *
 * <p>Die Reihenfolge bestimmt auch die Gruppe im Menue: 100 bis 199 = ZEIT, 200 bis 299 = BETRIEB,
 * ab 300 = VERWALTUNG.
 */
public interface MenuEintrag {

    String bezeichnung();

    /** Route der View ohne fuehrenden Schraegstrich, z. B. "notizen". */
    String route();

    Set<Rolle> erlaubteRollen();

    int reihenfolge();
}

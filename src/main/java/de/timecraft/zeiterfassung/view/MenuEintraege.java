package de.timecraft.zeiterfassung.view;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;

/**
 * Die Menuepunkte von M2. Die Zahl bestimmt Reihenfolge und Gruppe im Menue, siehe {@link MenuEintrag}.
 *
 * @author Noah Cipa
 */
@Configuration
public class MenuEintraege {

    private record Eintrag(String bezeichnung, String route, int reihenfolge, Set<Rolle> erlaubteRollen)
            implements MenuEintrag {
    }

    private static final Set<Rolle> ZEITERFASSUNG = Set.of(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);

    @Bean
    static MenuEintrag stempelMenuEintrag() {
        return new Eintrag("Stempeln", "stempeln", 110, ZEITERFASSUNG);
    }

    @Bean
    static MenuEintrag meineZeitenMenuEintrag() {
        return new Eintrag("Meine Zeiten", "meine-zeiten", 120, ZEITERFASSUNG);
    }

    @Bean
    static MenuEintrag zeitkontoMenuEintrag() {
        return new Eintrag("Zeitkonto", "zeitkonto", 130, ZEITERFASSUNG);
    }

    @Bean
    static MenuEintrag abwesenheitMenuEintrag() {
        return new Eintrag("Abwesenheit", "abwesenheit", 140, Set.of(Rolle.values()));
    }

    @Bean
    static MenuEintrag feiertagMenuEintrag() {
        return new Eintrag("Feiertage", "feiertage", 190, Set.of(Rolle.ADMIN));
    }

    @Bean
    static MenuEintrag anwesenheitMenuEintrag() {
        return new Eintrag("Anwesenheit", "anwesenheit", 210, ZEITERFASSUNG);
    }
}

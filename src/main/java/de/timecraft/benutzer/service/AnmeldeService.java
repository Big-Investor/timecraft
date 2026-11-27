package de.timecraft.benutzer.service;

import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft eine Anmeldung mit Benutzername und Passwort (UC-M1-01, FA-M1-01, FA-M1-05, FA-M1-09).
 *
 * <p>Ablauf:
 * <ol>
 *   <li>Passt weder das Passwort noch das Einmalpasswort, zählt ein Fehlversuch und es kommt
 *       eine neutrale Meldung, die nicht verrät, welches Feld falsch war.</li>
 *   <li>Ein gesperrtes Konto oder ein deaktivierter Mitarbeiter kann sich nicht anmelden.</li>
 *   <li>Ein Einmalpasswort gilt höchstens 24 Stunden und nur für eine Anmeldung. Danach muss
 *       die Person ein eigenes Passwort festlegen.</li>
 * </ol>
 * Ist die Anmeldung nicht möglich, wirft die Methode eine Ausnahme von Spring Security.
 * Die Zählung der Fehlversuche wird trotzdem gespeichert ({@code noRollbackFor}).
 *
 * @author Leon-Selim Kannenberg
 */
@Service
public class AnmeldeService {

    public static final String FALSCHE_DATEN = "Benutzername oder Passwort ist falsch.";
    static final String KONTO_GESPERRT =
            "Ihr Konto ist gesperrt. Bitte wenden Sie sich an die Verwaltung.";
    static final String KONTO_INAKTIV =
            "Ihr Konto ist nicht mehr aktiv. Bitte wenden Sie sich an die Verwaltung.";
    static final String EINMALPASSWORT_ABGELAUFEN =
            "Das Einmalpasswort ist abgelaufen. Bitte lassen Sie sich von der Verwaltung ein neues senden.";

    private final BenutzerRepository benutzerRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final PasswortService passwortService;

    public AnmeldeService(BenutzerRepository benutzerRepository, MitarbeiterRepository mitarbeiterRepository,
            PasswortService passwortService) {
        this.benutzerRepository = benutzerRepository;
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.passwortService = passwortService;
    }

    /**
     * Meldet eine Person an und liefert sie als {@link TimeCraftBenutzer} (jedes Mal ein neues Objekt).
     * Die Rolle wird bei jeder Anmeldung neu gelesen; eine geänderte Rolle gilt deshalb ab der
     * nächsten Anmeldung.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public TimeCraftBenutzer anmelden(String benutzername, String passwort) {
        Optional<Benutzer> gefunden = benutzerRepository.findByBenutzername(benutzername);
        if (gefunden.isEmpty()) {
            throw new BadCredentialsException(FALSCHE_DATEN);
        }
        Benutzer benutzer = gefunden.get();

        Optional<Mitarbeiter> mitarbeiterGefunden = mitarbeiterRepository.findByBenutzer(benutzer);
        if (mitarbeiterGefunden.isEmpty()) {
            throw new BadCredentialsException(FALSCHE_DATEN);
        }
        Mitarbeiter mitarbeiter = mitarbeiterGefunden.get();

        boolean passwortRichtig = passwortService.passt(passwort, benutzer.getPasswortHash());
        boolean einmalpasswortRichtig = passwortService.passt(passwort, benutzer.getEinmalpasswortHash());

        if (!passwortRichtig && !einmalpasswortRichtig) {
            fehlversuchRegistrieren(benutzer);
            throw new BadCredentialsException(FALSCHE_DATEN);
        }

        // Ab hier waren die Zugangsdaten richtig. Erst jetzt nennen wir den genauen Grund einer Ablehnung.
        if (benutzer.isGesperrt()) {
            throw new LockedException(KONTO_GESPERRT);
        }
        if (!mitarbeiter.isAktiv()) {
            throw new DisabledException(KONTO_INAKTIV);
        }
        if (!passwortRichtig) {
            einmalpasswortVerbrauchen(benutzer);
        }

        benutzer.fehlversucheZuruecksetzen();
        benutzerRepository.save(benutzer);

        // Der Passwort-Hash wird nach der Anmeldung nicht mehr gebraucht und bleibt deshalb leer.
        return new TimeCraftBenutzer(mitarbeiter.getMitarbeiterId(), benutzer.getBenutzername(), "",
                benutzer.getRolle(), mitarbeiter.getName());
    }

    /**
     * Text für die Anmeldeseite nach einer abgelehnten Anmeldung (US-M1-01).
     * Nur bei richtigen Zugangsdaten nennen wir den genauen Grund (gesperrt, nicht mehr aktiv,
     * Einmalpasswort abgelaufen). In allen anderen Fällen kommt die neutrale Meldung, die nicht
     * verrät, ob der Benutzername oder das Passwort falsch war.
     *
     * @param ausnahme die Ausnahme der letzten Anmeldung oder {@code null}
     */
    public static String meldungFuer(Object ausnahme) {
        if (ausnahme instanceof LockedException gesperrt) {
            return gesperrt.getMessage();
        }
        if (ausnahme instanceof DisabledException inaktiv) {
            return inaktiv.getMessage();
        }
        if (ausnahme instanceof CredentialsExpiredException abgelaufen) {
            return abgelaufen.getMessage();
        }
        return FALSCHE_DATEN;
    }

    /**
     * Zählt einen Fehlversuch. Das ist die einzige Stelle dafür: Hier ließe sich später die
     * automatische Sperre nach 5 Fehlversuchen (FA-M1-K2, zurückgestellt) ergänzen. Heute wird
     * nichts gesperrt.
     */
    void fehlversuchRegistrieren(Benutzer benutzer) {
        benutzer.fehlversuchHochzaehlen();
        benutzerRepository.save(benutzer);
    }

    /** Ein Einmalpasswort gilt nur einmal: Nach der Anmeldung wird es gelöscht. */
    private void einmalpasswortVerbrauchen(Benutzer benutzer) {
        if (passwortService.einmalpasswortAbgelaufen(benutzer)) {
            throw new CredentialsExpiredException(EINMALPASSWORT_ABGELAUFEN);
        }
        benutzer.einmalpasswortLoeschen();
        benutzer.setPasswortAenderungNoetig(true);
    }
}

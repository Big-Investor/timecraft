package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das eigene Benutzerkonto, für jede Rolle: Passwort ändern (FA-M1-06, UC-M1-03) und nach einem
 * Einmalpasswort ein eigenes Passwort festlegen (FA-M1-09). Sperren, Entsperren und Zurücksetzen
 * durch den Admin stehen im {@link MitarbeiterService}.
 *
 * <p>Wie im Gerüst üblich, kommt die angemeldete Person als Parameter
 * ({@code mitarbeiterId}, {@code rolle}) aus der View. Eine Rolle {@code null} heißt:
 * niemand ist angemeldet.
 *
 * @author Leon-Selim Kannenberg
 */
@Service
public class BenutzerService {

    /** Feldnamen der Passwort-Masken: Fehler kommen als {@link EingabeFehlerException} je Feld. */
    public static final String FELD_ALTES_PASSWORT = "altesPasswort";
    public static final String FELD_NEUES_PASSWORT = "neuesPasswort";
    public static final String FELD_WIEDERHOLUNG = "wiederholung";

    static final String FALSCHES_ALTES_PASSWORT = "Das bisherige Passwort ist nicht richtig.";
    static final String WIEDERHOLUNG_FALSCH = "Die beiden neuen Passwörter stimmen nicht überein.";
    static final String KEINE_AENDERUNG_NOETIG =
            "Ihr Passwort ist bereits festgelegt. Bitte nutzen Sie „Passwort ändern“.";

    private final BenutzerRepository benutzerRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final PasswortService passwortService;

    public BenutzerService(BenutzerRepository benutzerRepository, MitarbeiterRepository mitarbeiterRepository,
            PasswortService passwortService) {
        this.benutzerRepository = benutzerRepository;
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.passwortService = passwortService;
    }

    /**
     * Ändert das eigene Passwort. Das alte Passwort muss zur Bestätigung eingegeben werden,
     * das neue muss die Passwortregeln erfüllen und zweimal gleich eingegeben werden.
     * Eine falsche Eingabe meldet eine {@link EingabeFehlerException} mit dem betroffenen Feld.
     */
    @Transactional
    public void passwortAendern(long mitarbeiterId, Rolle rolle, String altesPasswort,
            String neuesPasswort, String wiederholung) {
        Benutzer benutzer = eigenesKonto(mitarbeiterId, rolle);
        if (!passwortService.passt(altesPasswort, benutzer.getPasswortHash())) {
            throw fehlerAmFeld(FELD_ALTES_PASSWORT, FALSCHES_ALTES_PASSWORT);
        }
        neuesPasswortSpeichern(benutzer, neuesPasswort, wiederholung);
    }

    /**
     * Legt nach der Anmeldung mit einem Einmalpasswort das eigene Passwort fest.
     * Das geht nur, solange eine Passwortänderung nötig ist; sonst gilt „Passwort ändern“.
     */
    @Transactional
    public void erstesPasswortFestlegen(long mitarbeiterId, Rolle rolle, String neuesPasswort,
            String wiederholung) {
        Benutzer benutzer = eigenesKonto(mitarbeiterId, rolle);
        if (!benutzer.isPasswortAenderungNoetig()) {
            throw new FachlicheException(KEINE_AENDERUNG_NOETIG);
        }
        neuesPasswortSpeichern(benutzer, neuesPasswort, wiederholung);
    }

    /**
     * True, wenn die Person zuerst ein eigenes Passwort festlegen muss. Solange das gilt,
     * darf die Oberfläche keine anderen Seiten zeigen (US-M1-02).
     */
    @Transactional(readOnly = true)
    public boolean mussPasswortAendern(long mitarbeiterId, Rolle rolle) {
        Benutzer benutzer = eigenesKonto(mitarbeiterId, rolle);
        return benutzer.isPasswortAenderungNoetig();
    }

    private void neuesPasswortSpeichern(Benutzer benutzer, String neuesPasswort, String wiederholung) {
        try {
            passwortService.pruefeRegeln(neuesPasswort);
        } catch (FachlicheException regelVerletzt) {
            throw fehlerAmFeld(FELD_NEUES_PASSWORT, regelVerletzt.getMessage());
        }
        if (!neuesPasswort.equals(wiederholung)) {
            throw fehlerAmFeld(FELD_WIEDERHOLUNG, WIEDERHOLUNG_FALSCH);
        }
        benutzer.setPasswortHash(passwortService.hashen(neuesPasswort));
        benutzer.setPasswortAenderungNoetig(false);
        benutzer.einmalpasswortLoeschen();
        benutzerRepository.save(benutzer);
    }

    /** Fehler an genau einem Feld, damit die Oberfläche die Meldung dort anzeigen kann (QA-M1-07). */
    private EingabeFehlerException fehlerAmFeld(String feld, String meldung) {
        return new EingabeFehlerException(Map.of(feld, meldung));
    }

    /** Das Benutzerkonto der angemeldeten Person. */
    private Benutzer eigenesKonto(long mitarbeiterId, Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
        Optional<Mitarbeiter> mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId);
        if (mitarbeiter.isEmpty()) {
            throw new NichtGefundenException("Ihr Benutzerkonto wurde nicht gefunden.");
        }
        return mitarbeiter.get().getBenutzer();
    }
}

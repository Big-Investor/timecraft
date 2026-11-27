package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mitarbeiterverwaltung: anlegen (UC-M1-04), laden und bearbeiten (UC-M1-05), deaktivieren (UC-M1-06),
 * Mitarbeiterliste (UC-M1-11) und das Benutzerkonto eines Mitarbeiters: sperren und entsperren
 * (UC-M1-09) sowie Passwort zurücksetzen mit Einmalpasswort (UC-M1-10).
 *
 * <p>Rechte (Rechtetabelle M1, Projektbericht Abschnitt 2.2.1) werden hier geprüft, nicht in der Oberfläche:
 * Anlegen, Deaktivieren und alles am Benutzerkonto nur Admin; Liste und Bearbeiten Projektleitung
 * und Admin, wobei die Projektleitung nur Stammdaten und Arbeitszeitmodell ändert. Die angemeldete Person kommt als
 * Parameter aus der View ({@code angemeldeteMitarbeiterId}, {@code angemeldeteRolle}).
 *
 * @author Leon-Selim Kannenberg
 */
@Service
public class MitarbeiterService {

    static final String NICHT_GEFUNDEN = "Der Mitarbeiter wurde nicht gefunden.";
    static final String EIGENE_ROLLE = "Sie können Ihre eigene Rolle nicht ändern.";
    static final String SICH_SELBST_DEAKTIVIEREN = "Sie können sich nicht selbst deaktivieren.";
    static final String SICH_SELBST_SPERREN = "Sie können Ihr eigenes Konto nicht sperren.";

    private final MitarbeiterRepository mitarbeiterRepository;
    private final BenutzerRepository benutzerRepository;
    private final ArbeitszeitmodellRepository arbeitszeitmodellRepository;
    private final ZeitkontoRepository zeitkontoRepository;
    private final UrlaubskontoRepository urlaubskontoRepository;
    private final MitarbeiterPruefung pruefung;
    private final EinmalpasswortVersand einmalpasswortVersand;
    private final Clock clock;

    public MitarbeiterService(MitarbeiterRepository mitarbeiterRepository, BenutzerRepository benutzerRepository,
            ArbeitszeitmodellRepository arbeitszeitmodellRepository, ZeitkontoRepository zeitkontoRepository,
            UrlaubskontoRepository urlaubskontoRepository, MitarbeiterPruefung pruefung,
            EinmalpasswortVersand einmalpasswortVersand, Clock clock) {
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.benutzerRepository = benutzerRepository;
        this.arbeitszeitmodellRepository = arbeitszeitmodellRepository;
        this.zeitkontoRepository = zeitkontoRepository;
        this.urlaubskontoRepository = urlaubskontoRepository;
        this.pruefung = pruefung;
        this.einmalpasswortVersand = einmalpasswortVersand;
        this.clock = clock;
    }

    /**
     * Legt einen Mitarbeiter mit allem an, was dazugehört (UC-M1-04): Benutzerkonto, Mitarbeiter,
     * Arbeitszeitmodell, Zeitkonto-Startwert und Urlaubskonto – alles in einer Transaktion.
     * Danach wird das Einmalpasswort gesendet. Schlägt nur der Versand fehl (7a), bleibt alles
     * gespeichert und das Ergebnis enthält einen Hinweis.
     */
    @Transactional
    public AnlegeErgebnis anlegen(MitarbeiterDaten daten, Rolle angemeldeteRolle) {
        Rechte.nurAdmin(angemeldeteRolle);
        daten.eingabenBereinigen();
        pruefung.pruefeNeuenMitarbeiter(daten);

        Benutzer benutzer = new Benutzer(daten.getBenutzername(), daten.getRolle());
        benutzerRepository.save(benutzer);

        Mitarbeiter mitarbeiter = new Mitarbeiter(benutzer, daten.getPersonalnummer(), daten.getVorname(),
                daten.getNachname());
        stammdatenUebernehmen(daten, mitarbeiter);
        mitarbeiter.setVorgesetzter(vorgesetzterOderNull(daten.getVorgesetzterId()));
        mitarbeiterRepository.save(mitarbeiter);

        arbeitszeitmodellRepository.save(
                new Arbeitszeitmodell(mitarbeiter, daten.getWochenstunden(), daten.getArbeitstage()));

        LocalDate startdatum = startdatum(daten.getEintrittsdatum());
        zeitkontoRepository.save(new Zeitkonto(mitarbeiter, startdatum, zahlOderStandard(daten.getStartsaldoMinuten())));
        urlaubskontoRepository.save(new Urlaubskonto(mitarbeiter, startdatum.getYear(),
                daten.getUrlaubsanspruch(), zahlOderStandard(daten.getUrlaubsuebertrag())));

        long neueId = mitarbeiter.getMitarbeiterId();
        try {
            String rueckmeldung = einmalpasswortVersand.sendenUndSpeichern(mitarbeiter);
            return new AnlegeErgebnis(neueId, true, "Mitarbeiter gespeichert. " + rueckmeldung);
        } catch (FachlicheException versandFehler) {
            // Fehlerfall 7a: Mitarbeiter und Konto bleiben gespeichert.
            return new AnlegeErgebnis(neueId, false, "Mitarbeiter gespeichert. " + versandFehler.getMessage()
                    + " Bitte senden Sie das Einmalpasswort im Bereich „Benutzerkonto“ erneut.");
        }
    }

    /** Lädt alle Daten eines Mitarbeiters für das Formular. */
    @Transactional(readOnly = true)
    public MitarbeiterDaten laden(long mitarbeiterId, Rolle angemeldeteRolle) {
        Rechte.projektleitungOderAdmin(angemeldeteRolle);
        Mitarbeiter mitarbeiter = mitarbeiterHolen(mitarbeiterId);
        Benutzer benutzer = mitarbeiter.getBenutzer();

        MitarbeiterDaten daten = new MitarbeiterDaten();
        daten.setMitarbeiterId(mitarbeiter.getMitarbeiterId());
        daten.setPersonalnummer(mitarbeiter.getPersonalnummer());
        daten.setVorname(mitarbeiter.getVorname());
        daten.setNachname(mitarbeiter.getNachname());
        daten.setEmail(mitarbeiter.getEmail());
        daten.setTelefon(mitarbeiter.getTelefon());
        daten.setGeburtsdatum(mitarbeiter.getGeburtsdatum());
        daten.setEintrittsdatum(mitarbeiter.getEintrittsdatum());
        daten.setQualifikation(mitarbeiter.getQualifikation());
        if (mitarbeiter.getVorgesetzter() != null) {
            daten.setVorgesetzterId(mitarbeiter.getVorgesetzter().getMitarbeiterId());
        }
        daten.setBenutzername(benutzer.getBenutzername());
        daten.setRolle(benutzer.getRolle());
        daten.setAktiv(mitarbeiter.isAktiv());
        daten.setAustrittsdatum(mitarbeiter.getAustrittsdatum());
        daten.setGesperrt(benutzer.isGesperrt());
        daten.setBearbeitbar(Rechte.darfBearbeiten(angemeldeteRolle, benutzer.getRolle()));

        Optional<Arbeitszeitmodell> modell = arbeitszeitmodellRepository.findByMitarbeiter(mitarbeiter);
        if (modell.isPresent()) {
            daten.setWochenstunden(modell.get().getWochenstunden());
            daten.setArbeitstage(modell.get().getArbeitstage());
        }
        Optional<Zeitkonto> zeitkonto = zeitkontoRepository.findByMitarbeiter(mitarbeiter);
        if (zeitkonto.isPresent()) {
            daten.setStartsaldoMinuten(zeitkonto.get().getAnfangssaldoMin());
        }
        Optional<Urlaubskonto> urlaub = urlaubskontoHolen(mitarbeiter);
        if (urlaub.isPresent()) {
            daten.setUrlaubsanspruch(urlaub.get().getAnspruchTage());
            daten.setUrlaubsuebertrag(urlaub.get().getUebertragTage());
        }
        return daten;
    }

    /**
     * Speichert Änderungen an einem Mitarbeiter (UC-M1-05). Der Admin ändert alles. Die Projektleitung
     * ändert nur Stammdaten und Arbeitszeitmodell; ihre Angaben zu Urlaub, Startsaldo, Vorgesetztem,
     * Benutzername und Rolle werden nicht übernommen (FA-M1-11, US-M1-11). Außerdem bearbeitet sie nur
     * Personen mit der Rolle Mitarbeiter (Entscheidung Leon-Selim Kannenberg, 18.11.2026).
     * Eine geänderte Rolle gilt ab der nächsten Anmeldung.
     */
    @Transactional
    public void bearbeiten(long mitarbeiterId, MitarbeiterDaten daten, long angemeldeteMitarbeiterId,
            Rolle angemeldeteRolle) {
        Rechte.projektleitungOderAdmin(angemeldeteRolle);
        Mitarbeiter mitarbeiter = mitarbeiterHolen(mitarbeiterId);
        if (!Rechte.darfBearbeiten(angemeldeteRolle, mitarbeiter.getBenutzer().getRolle())) {
            throw new ZugriffVerweigertException(Rechte.NUR_ROLLE_MITARBEITER);
        }
        daten.eingabenBereinigen();

        if (angemeldeteRolle == Rolle.ADMIN) {
            eigeneRolleNichtAendern(mitarbeiter, daten, angemeldeteMitarbeiterId);
            pruefung.pruefeAenderungDurchAdmin(daten, mitarbeiter);
            stammdatenUebernehmen(daten, mitarbeiter);
            arbeitszeitUebernehmen(daten, mitarbeiter);
            alleWeiterenDatenUebernehmen(daten, mitarbeiter);
        } else {
            pruefung.pruefeAenderungDurchProjektleitung(daten, mitarbeiter);
            stammdatenUebernehmen(daten, mitarbeiter);
            arbeitszeitUebernehmen(daten, mitarbeiter);
        }
        mitarbeiterRepository.save(mitarbeiter);
    }

    /**
     * Deaktiviert einen ausgetretenen Mitarbeiter statt ihn zu löschen (UC-M1-06, FA-M1-12).
     * Erfasste Zeiten und Anträge bleiben erhalten. Ein Vorgesetzter lässt sich erst deaktivieren,
     * wenn seine Mitarbeiter neu zugeordnet sind (QA-M1-06).
     */
    @Transactional
    public void deaktivieren(long mitarbeiterId, LocalDate austrittsdatum, long angemeldeteMitarbeiterId,
            Rolle angemeldeteRolle) {
        Rechte.nurAdmin(angemeldeteRolle);
        if (mitarbeiterId == angemeldeteMitarbeiterId) {
            throw new FachlicheException(SICH_SELBST_DEAKTIVIEREN);
        }
        Mitarbeiter mitarbeiter = mitarbeiterHolen(mitarbeiterId);
        pruefung.pruefeDeaktivieren(mitarbeiter, austrittsdatum);
        mitarbeiter.setAustrittsdatum(austrittsdatum);
        mitarbeiter.setAktiv(false);
        mitarbeiterRepository.save(mitarbeiter);
    }

    /**
     * Mitarbeiterliste (UC-M1-11, US-M1-14): alle Mitarbeiter, auch ehemalige. Wie im Mockup stehen
     * zuerst die aktiven, danach die inaktiven, jeweils sortiert nach Personalnummer.
     */
    @Transactional(readOnly = true)
    public List<MitarbeiterZeile> liste(Rolle angemeldeteRolle) {
        Rechte.projektleitungOderAdmin(angemeldeteRolle);
        List<MitarbeiterZeile> aktive = new ArrayList<>();
        List<MitarbeiterZeile> inaktive = new ArrayList<>();
        for (Mitarbeiter mitarbeiter : mitarbeiterRepository.findAllByOrderByPersonalnummerAsc()) {
            if (mitarbeiter.isAktiv()) {
                aktive.add(zeile(mitarbeiter));
            } else {
                inaktive.add(zeile(mitarbeiter));
            }
        }
        List<MitarbeiterZeile> ergebnis = new ArrayList<>(aktive);
        ergebnis.addAll(inaktive);
        return ergebnis;
    }

    /** Wählbare Vorgesetzte für das Formular: alle aktiven Projektleitungen (Teamentscheidung 04.11.2026). */
    @Transactional(readOnly = true)
    public List<MitarbeiterZeile> vorgesetzteAuswahl(Rolle angemeldeteRolle) {
        Rechte.projektleitungOderAdmin(angemeldeteRolle);
        List<MitarbeiterZeile> ergebnis = new ArrayList<>();
        for (Mitarbeiter mitarbeiter : mitarbeiterRepository.findByAktivTrueOrderByPersonalnummerAsc()) {
            if (mitarbeiter.getBenutzer().getRolle() == Rolle.PROJEKTLEITUNG) {
                ergebnis.add(zeile(mitarbeiter));
            }
        }
        return ergebnis;
    }

    /**
     * Sperrt das Benutzerkonto eines Mitarbeiters (FA-M1-05). Ein gesperrtes Konto kann sich nicht
     * anmelden; alle Daten bleiben erhalten. Der Admin kann sich nicht selbst aussperren.
     */
    @Transactional
    public void sperren(long mitarbeiterId, long angemeldeteMitarbeiterId, Rolle angemeldeteRolle) {
        Rechte.nurAdmin(angemeldeteRolle);
        if (mitarbeiterId == angemeldeteMitarbeiterId) {
            throw new FachlicheException(SICH_SELBST_SPERREN);
        }
        Benutzer benutzer = mitarbeiterHolen(mitarbeiterId).getBenutzer();
        benutzer.setGesperrt(true);
        benutzerRepository.save(benutzer);
    }

    /** Entsperrt das Benutzerkonto eines Mitarbeiters (FA-M1-05). Danach ist die Anmeldung wieder möglich. */
    @Transactional
    public void entsperren(long mitarbeiterId, Rolle angemeldeteRolle) {
        Rechte.nurAdmin(angemeldeteRolle);
        Benutzer benutzer = mitarbeiterHolen(mitarbeiterId).getBenutzer();
        benutzer.setGesperrt(false);
        benutzerRepository.save(benutzer);
    }

    /**
     * Passwort zurücksetzen (UC-M1-10): sendet ein neues Einmalpasswort an die hinterlegte Adresse.
     * Erst nach erfolgreichem Versand wird es gespeichert und das alte Passwort ungültig.
     * Schlägt der Versand fehl, bleibt das alte Passwort gültig und es kommt eine
     * {@code FachlicheException} mit deutschem Text (QA-M1-11).
     *
     * @return Bestätigung für die Oberfläche
     */
    @Transactional
    public String einmalpasswortSenden(long mitarbeiterId, Rolle angemeldeteRolle) {
        Rechte.nurAdmin(angemeldeteRolle);
        return einmalpasswortVersand.sendenUndSpeichern(mitarbeiterHolen(mitarbeiterId));
    }

    /**
     * Ist die Person heute jünger als 18 Jahre? Dann zeigt das Formular den Hinweis auf das
     * Jugendarbeitsschutzgesetz (AP5). Ohne Geburtsdatum: nein.
     */
    public boolean istUnter18(LocalDate geburtsdatum) {
        if (geburtsdatum == null) {
            return false;
        }
        LocalDate achtzehnterGeburtstag = geburtsdatum.plusYears(18);
        return achtzehnterGeburtstag.isAfter(LocalDate.now(clock));
    }

    /**
     * Tages-Soll in Minuten: Wochenstunden geteilt durch die Zahl der Arbeitstage, auf ganze
     * Minuten gerundet (z. B. 40 Stunden an 5 Tagen = 480 Minuten). Nur zur Anzeige im Formular;
     * die eigentliche Soll-Berechnung macht die Zeiterfassung (FA-M1-16).
     */
    public static int tagesSollMinuten(BigDecimal wochenstunden, int anzahlArbeitstage) {
        BigDecimal minutenProWoche = wochenstunden.multiply(BigDecimal.valueOf(60));
        BigDecimal minutenProTag = minutenProWoche.divide(BigDecimal.valueOf(anzahlArbeitstage), 0,
                RoundingMode.HALF_UP);
        return minutenProTag.intValue();
    }

    private void stammdatenUebernehmen(MitarbeiterDaten daten, Mitarbeiter mitarbeiter) {
        mitarbeiter.setPersonalnummer(daten.getPersonalnummer());
        mitarbeiter.setVorname(daten.getVorname());
        mitarbeiter.setNachname(daten.getNachname());
        mitarbeiter.setEmail(daten.getEmail());
        mitarbeiter.setTelefon(daten.getTelefon());
        mitarbeiter.setGeburtsdatum(daten.getGeburtsdatum());
        mitarbeiter.setEintrittsdatum(daten.getEintrittsdatum());
        mitarbeiter.setQualifikation(daten.getQualifikation());
    }

    private void arbeitszeitUebernehmen(MitarbeiterDaten daten, Mitarbeiter mitarbeiter) {
        Optional<Arbeitszeitmodell> gefunden = arbeitszeitmodellRepository.findByMitarbeiter(mitarbeiter);
        Arbeitszeitmodell modell;
        if (gefunden.isPresent()) {
            modell = gefunden.get();
            modell.setWochenstunden(daten.getWochenstunden());
            modell.setArbeitstage(daten.getArbeitstage());
        } else {
            modell = new Arbeitszeitmodell(mitarbeiter, daten.getWochenstunden(), daten.getArbeitstage());
        }
        arbeitszeitmodellRepository.save(modell);
    }

    /** Nur für den Admin: Vorgesetzte/r, Konto, Startsaldo und Urlaub. */
    private void alleWeiterenDatenUebernehmen(MitarbeiterDaten daten, Mitarbeiter mitarbeiter) {
        mitarbeiter.setVorgesetzter(vorgesetzterOderNull(daten.getVorgesetzterId()));

        Benutzer benutzer = mitarbeiter.getBenutzer();
        benutzer.setBenutzername(daten.getBenutzername());
        benutzer.setRolle(daten.getRolle());
        benutzerRepository.save(benutzer);

        Optional<Zeitkonto> zeitkonto = zeitkontoRepository.findByMitarbeiter(mitarbeiter);
        if (zeitkonto.isPresent()) {
            zeitkonto.get().setAnfangssaldoMin(zahlOderStandard(daten.getStartsaldoMinuten()));
            zeitkontoRepository.save(zeitkonto.get());
        } else {
            zeitkontoRepository.save(new Zeitkonto(mitarbeiter, startdatum(mitarbeiter.getEintrittsdatum()),
                    zahlOderStandard(daten.getStartsaldoMinuten())));
        }

        Optional<Urlaubskonto> urlaub = urlaubskontoHolen(mitarbeiter);
        if (urlaub.isPresent()) {
            urlaub.get().setAnspruchTage(daten.getUrlaubsanspruch());
            urlaub.get().setUebertragTage(zahlOderStandard(daten.getUrlaubsuebertrag()));
            urlaubskontoRepository.save(urlaub.get());
        } else {
            urlaubskontoRepository.save(new Urlaubskonto(mitarbeiter, urlaubsjahr(mitarbeiter),
                    daten.getUrlaubsanspruch(), zahlOderStandard(daten.getUrlaubsuebertrag())));
        }
    }

    /** Ein Admin darf seine eigene Rolle nicht ändern, sonst sperrt er sich womöglich selbst aus. */
    private void eigeneRolleNichtAendern(Mitarbeiter mitarbeiter, MitarbeiterDaten daten,
            long angemeldeteMitarbeiterId) {
        boolean istEigenesKonto = mitarbeiter.getMitarbeiterId() == angemeldeteMitarbeiterId;
        boolean rolleGeaendert = daten.getRolle() != mitarbeiter.getBenutzer().getRolle();
        if (istEigenesKonto && rolleGeaendert) {
            throw new EingabeFehlerException(Map.of(MitarbeiterDaten.ROLLE, EIGENE_ROLLE));
        }
    }

    /**
     * Ab wann das Zeitkonto zählt: ab dem Eintritt, bei schon länger Beschäftigten ab heute
     * (Einführung von TimeCraft, FA-M1-17).
     */
    private LocalDate startdatum(LocalDate eintrittsdatum) {
        LocalDate heute = LocalDate.now(clock);
        if (eintrittsdatum.isAfter(heute)) {
            return eintrittsdatum;
        }
        return heute;
    }

    /**
     * Das Jahr, für das das Formular den Urlaub zeigt und speichert: das Jahr, in dem das Zeitkonto
     * beginnt. Die Startwerte werden nur beim Anlegen festgelegt und ändern sich danach nicht.
     */
    private int urlaubsjahr(Mitarbeiter mitarbeiter) {
        Optional<Zeitkonto> zeitkonto = zeitkontoRepository.findByMitarbeiter(mitarbeiter);
        if (zeitkonto.isPresent()) {
            return zeitkonto.get().getStartdatum().getYear();
        }
        return startdatum(mitarbeiter.getEintrittsdatum()).getYear();
    }

    private Optional<Urlaubskonto> urlaubskontoHolen(Mitarbeiter mitarbeiter) {
        short jahr = (short) urlaubsjahr(mitarbeiter);
        return urlaubskontoRepository.findByMitarbeiterAndJahr(mitarbeiter, jahr);
    }

    private Mitarbeiter vorgesetzterOderNull(Long vorgesetzterId) {
        if (vorgesetzterId == null) {
            return null;
        }
        return mitarbeiterHolen(vorgesetzterId);
    }

    private Mitarbeiter mitarbeiterHolen(long mitarbeiterId) {
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(mitarbeiterId);
        if (gefunden.isEmpty()) {
            throw new NichtGefundenException(NICHT_GEFUNDEN);
        }
        return gefunden.get();
    }

    /** Leere Zahlenfelder (Startsaldo, Resturlaub) bekommen den Standardwert 0. */
    private int zahlOderStandard(Integer zahl) {
        if (zahl == null) {
            return 0;
        }
        return zahl;
    }

    private MitarbeiterZeile zeile(Mitarbeiter mitarbeiter) {
        String vorgesetzterName = null;
        if (mitarbeiter.getVorgesetzter() != null) {
            vorgesetzterName = mitarbeiter.getVorgesetzter().getName();
        }
        return new MitarbeiterZeile(mitarbeiter.getMitarbeiterId(), mitarbeiter.getPersonalnummer(),
                mitarbeiter.getName(), mitarbeiter.getQualifikation(), mitarbeiter.getBenutzer().getRolle(),
                vorgesetzterName, mitarbeiter.isAktiv(), mitarbeiter.getAustrittsdatum());
    }
}

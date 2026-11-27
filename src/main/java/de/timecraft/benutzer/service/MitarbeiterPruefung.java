package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.FachlicheException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Prüft die Eingaben des Mitarbeiterformulars (UC-M1-04 Schritt 5, QA-M1-05, QA-M1-07).
 * Alle Fehler werden gesammelt und zusammen als {@link EingabeFehlerException} gemeldet,
 * damit die Oberfläche jede Meldung am passenden Feld zeigen kann. Gespeichert wird hier nichts.
 *
 * @author Leon-Selim Kannenberg
 */
@Component
class MitarbeiterPruefung {

    /** Gesetzlicher Mindesturlaub, pauschal 20 Tage (Teamentscheidung 04.11.2026). */
    static final int MINDESTURLAUB_TAGE = 20;
    /** Obergrenze für Urlaubstage; größere Zahlen passen nicht sicher in die Spalten (SMALLINT). */
    static final int HOECHSTENS_URLAUBSTAGE = 365;
    /** Größter sinnvoller Wert; in die Spalte NUMERIC(4,2) passen höchstens 99,99 Stunden. */
    static final BigDecimal HOECHSTENS_WOCHENSTUNDEN = new BigDecimal("60");

    private final BenutzerRepository benutzerRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    MitarbeiterPruefung(BenutzerRepository benutzerRepository, MitarbeiterRepository mitarbeiterRepository) {
        this.benutzerRepository = benutzerRepository;
        this.mitarbeiterRepository = mitarbeiterRepository;
    }

    /** Prüft ein neues Formular vollständig. */
    void pruefeNeuenMitarbeiter(MitarbeiterDaten daten) {
        Map<String, String> fehler = new LinkedHashMap<>();
        stammdatenPruefen(daten, null, fehler);
        arbeitszeitPruefen(daten, fehler);
        urlaubPruefen(daten, fehler);
        vorgesetztenPruefen(daten, null, fehler);
        benutzerkontoPruefen(daten, null, fehler);
        werfenFallsFehler(fehler);
    }

    /** Der Admin ändert alles: Es gelten dieselben Prüfungen wie beim Anlegen. */
    void pruefeAenderungDurchAdmin(MitarbeiterDaten daten, Mitarbeiter bestehend) {
        Map<String, String> fehler = new LinkedHashMap<>();
        stammdatenPruefen(daten, bestehend, fehler);
        arbeitszeitPruefen(daten, fehler);
        urlaubPruefen(daten, fehler);
        vorgesetztenPruefen(daten, bestehend, fehler);
        benutzerkontoPruefen(daten, bestehend, fehler);
        werfenFallsFehler(fehler);
    }

    /** Die Projektleitung ändert nur Stammdaten und Arbeitszeitmodell (FA-M1-11). */
    void pruefeAenderungDurchProjektleitung(MitarbeiterDaten daten, Mitarbeiter bestehend) {
        Map<String, String> fehler = new LinkedHashMap<>();
        stammdatenPruefen(daten, bestehend, fehler);
        arbeitszeitPruefen(daten, fehler);
        werfenFallsFehler(fehler);
    }

    /** Austritt (UC-M1-06): Datum Pflicht und nach dem Eintritt, keine aktiven Mitarbeiter mehr unter sich. */
    void pruefeDeaktivieren(Mitarbeiter mitarbeiter, LocalDate austrittsdatum) {
        if (!mitarbeiter.isAktiv()) {
            throw new FachlicheException(
                    mitarbeiter.getName() + " ist bereits deaktiviert.");
        }
        if (austrittsdatum == null) {
            throw new FachlicheException("Bitte geben Sie das Austrittsdatum an.");
        }
        if (!austrittsdatum.isAfter(mitarbeiter.getEintrittsdatum())) {
            throw new FachlicheException(
                    "Das Austrittsdatum muss nach dem Eintrittsdatum liegen.");
        }
        String offeneMitarbeiter = namenDerAktivenMitarbeiter(mitarbeiter);
        if (offeneMitarbeiter != null) {
            throw new FachlicheException(nochVorgesetzteMeldung(mitarbeiter, offeneMitarbeiter));
        }
    }

    private String nochVorgesetzteMeldung(Mitarbeiter vorgesetzter, String offeneMitarbeiter) {
        return vorgesetzter.getName() + " ist noch Vorgesetzte/r von " + offeneMitarbeiter
                + ". Bitte ordnen Sie diesen Mitarbeitern zuerst eine andere Projektleitung zu.";
    }

    private void stammdatenPruefen(MitarbeiterDaten daten, Mitarbeiter bestehend, Map<String, String> fehler) {
        String personalnummer = daten.getPersonalnummer();
        if (istLeer(personalnummer)) {
            fehler.put(MitarbeiterDaten.PERSONALNUMMER, "Bitte geben Sie die Personalnummer ein.");
        } else if (personalnummer.length() > 10) {
            fehler.put(MitarbeiterDaten.PERSONALNUMMER, "Die Personalnummer darf höchstens 10 Zeichen haben.");
        } else if (personalnummerVergeben(personalnummer, bestehend)) {
            fehler.put(MitarbeiterDaten.PERSONALNUMMER,
                    "Die Personalnummer " + personalnummer + " ist bereits vergeben.");
        }

        pflichtText(daten.getVorname(), 50, MitarbeiterDaten.VORNAME, "den Vornamen", fehler);
        pflichtText(daten.getNachname(), 50, MitarbeiterDaten.NACHNAME, "den Nachnamen", fehler);

        String email = daten.getEmail();
        if (istLeer(email)) {
            fehler.put(MitarbeiterDaten.EMAIL, "Bitte geben Sie die E-Mail-Adresse ein.");
        } else if (email.length() > 100) {
            fehler.put(MitarbeiterDaten.EMAIL, "Die E-Mail-Adresse darf höchstens 100 Zeichen haben.");
        } else if (!EinmalpasswortMail.istGueltigeAdresse(email)) {
            fehler.put(MitarbeiterDaten.EMAIL, "Bitte geben Sie eine gültige E-Mail-Adresse ein, z. B. name@firma.de.");
        }

        if (daten.getTelefon() != null && daten.getTelefon().length() > 30) {
            fehler.put(MitarbeiterDaten.TELEFON, "Die Telefonnummer darf höchstens 30 Zeichen haben.");
        }

        if (daten.getGeburtsdatum() == null) {
            fehler.put(MitarbeiterDaten.GEBURTSDATUM, "Bitte geben Sie das Geburtsdatum ein.");
        }
        LocalDate eintrittsdatum = daten.getEintrittsdatum();
        if (eintrittsdatum == null) {
            fehler.put(MitarbeiterDaten.EINTRITTSDATUM, "Bitte geben Sie das Eintrittsdatum ein.");
        } else if (bestehend != null && bestehend.getAustrittsdatum() != null
                && !bestehend.getAustrittsdatum().isAfter(eintrittsdatum)) {
            // QA-M1-07: Das Austrittsdatum liegt nach dem Eintrittsdatum, auch beim Bearbeiten.
            fehler.put(MitarbeiterDaten.EINTRITTSDATUM, "Das Eintrittsdatum muss vor dem Austrittsdatum liegen.");
        }

        if (daten.getQualifikation() == null) {
            fehler.put(MitarbeiterDaten.QUALIFIKATION, "Bitte wählen Sie eine Qualifikation.");
        }
    }

    private void arbeitszeitPruefen(MitarbeiterDaten daten, Map<String, String> fehler) {
        BigDecimal wochenstunden = daten.getWochenstunden();
        if (wochenstunden == null) {
            fehler.put(MitarbeiterDaten.WOCHENSTUNDEN, "Bitte geben Sie die Wochenstunden ein.");
        } else if (wochenstunden.signum() <= 0 || wochenstunden.compareTo(HOECHSTENS_WOCHENSTUNDEN) > 0) {
            fehler.put(MitarbeiterDaten.WOCHENSTUNDEN, "Die Wochenstunden müssen größer als 0 und höchstens 60 sein.");
        }

        if (daten.getArbeitstage().isEmpty()) {
            fehler.put(MitarbeiterDaten.ARBEITSTAGE, "Bitte wählen Sie mindestens einen Arbeitstag.");
        }
    }

    private void urlaubPruefen(MitarbeiterDaten daten, Map<String, String> fehler) {
        Integer anspruch = daten.getUrlaubsanspruch();
        if (anspruch == null) {
            fehler.put(MitarbeiterDaten.URLAUBSANSPRUCH, "Bitte geben Sie den Urlaubsanspruch ein.");
        } else if (anspruch < MINDESTURLAUB_TAGE) {
            fehler.put(MitarbeiterDaten.URLAUBSANSPRUCH,
                    "Der Urlaubsanspruch muss mindestens 20 Tage betragen (gesetzlicher Mindesturlaub).");
        } else if (anspruch > HOECHSTENS_URLAUBSTAGE) {
            fehler.put(MitarbeiterDaten.URLAUBSANSPRUCH, "Der Urlaubsanspruch darf höchstens 365 Tage betragen.");
        }

        Integer uebertrag = daten.getUrlaubsuebertrag();
        if (uebertrag != null && (uebertrag < 0 || uebertrag > HOECHSTENS_URLAUBSTAGE)) {
            fehler.put(MitarbeiterDaten.URLAUBSUEBERTRAG,
                    "Der Resturlaub aus dem Vorjahr muss zwischen 0 und 365 Tagen liegen.");
        }
    }

    /**
     * Regeln für den Vorgesetzten (FA-M1-19, Teamentscheidung und Entscheidung Leon 14.11.2026):
     * Pflicht für die Rolle Mitarbeiter, freiwillig für die Projektleitung, kein Vorgesetzter für den Admin.
     * Wählbar ist nur eine aktive Projektleitung, und niemand ist sein eigener Vorgesetzter.
     */
    private void vorgesetztenPruefen(MitarbeiterDaten daten, Mitarbeiter bestehend, Map<String, String> fehler) {
        Long vorgesetzterId = daten.getVorgesetzterId();
        Rolle rolle = daten.getRolle();
        if (vorgesetzterId == null) {
            if (rolle == Rolle.MITARBEITER) {
                fehler.put(MitarbeiterDaten.VORGESETZTER, "Bitte wählen Sie eine Vorgesetzte oder einen Vorgesetzten.");
            }
            return;
        }
        if (rolle == Rolle.ADMIN) {
            fehler.put(MitarbeiterDaten.VORGESETZTER, "Ein Admin-Konto hat keine Vorgesetzte und keinen Vorgesetzten.");
            return;
        }
        if (bestehend != null && vorgesetzterId.equals(bestehend.getMitarbeiterId())) {
            fehler.put(MitarbeiterDaten.VORGESETZTER, "Ein Mitarbeiter kann nicht sein eigener Vorgesetzter sein.");
            return;
        }
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(vorgesetzterId);
        if (gefunden.isEmpty()) {
            fehler.put(MitarbeiterDaten.VORGESETZTER, "Die gewählte Vorgesetzte bzw. der gewählte Vorgesetzte wurde nicht gefunden.");
            return;
        }
        Mitarbeiter vorgesetzter = gefunden.get();
        if (vorgesetzter.getBenutzer().getRolle() != Rolle.PROJEKTLEITUNG) {
            fehler.put(MitarbeiterDaten.VORGESETZTER, "Als Vorgesetzte/r ist nur eine Projektleitung wählbar.");
        } else if (!vorgesetzter.isAktiv()) {
            fehler.put(MitarbeiterDaten.VORGESETZTER, vorgesetzter.getName() + " ist nicht mehr aktiv.");
        }
    }

    private void benutzerkontoPruefen(MitarbeiterDaten daten, Mitarbeiter bestehend, Map<String, String> fehler) {
        String benutzername = daten.getBenutzername();
        if (istLeer(benutzername)) {
            fehler.put(MitarbeiterDaten.BENUTZERNAME, "Bitte geben Sie einen Benutzernamen ein.");
        } else if (benutzername.length() > 50) {
            fehler.put(MitarbeiterDaten.BENUTZERNAME, "Der Benutzername darf höchstens 50 Zeichen haben.");
        } else if (benutzernameVergeben(benutzername, bestehend)) {
            fehler.put(MitarbeiterDaten.BENUTZERNAME, "Der Benutzername " + benutzername + " ist bereits vergeben.");
        }

        if (daten.getRolle() == null) {
            fehler.put(MitarbeiterDaten.ROLLE, "Bitte wählen Sie eine Rolle.");
        } else if (bestehend != null && verliertProjektleitung(bestehend, daten.getRolle())) {
            // Vorgesetzte sind immer Projektleitungen (Teamentscheidung 04.11.2026, Entscheidung Leon 20.11.2026)
            String offeneMitarbeiter = namenDerAktivenMitarbeiter(bestehend);
            if (offeneMitarbeiter != null) {
                fehler.put(MitarbeiterDaten.ROLLE, nochVorgesetzteMeldung(bestehend, offeneMitarbeiter));
            }
        }
    }

    private boolean verliertProjektleitung(Mitarbeiter bestehend, Rolle neueRolle) {
        return bestehend.getBenutzer().getRolle() == Rolle.PROJEKTLEITUNG && neueRolle != Rolle.PROJEKTLEITUNG;
    }

    /** Namen der aktiven Mitarbeiter, die diese Person als Vorgesetzte/n haben, oder {@code null}. */
    private String namenDerAktivenMitarbeiter(Mitarbeiter vorgesetzter) {
        List<Mitarbeiter> mitarbeiter = mitarbeiterRepository.findByVorgesetzterAndAktivTrue(vorgesetzter);
        if (mitarbeiter.isEmpty()) {
            return null;
        }
        StringBuilder namen = new StringBuilder();
        for (Mitarbeiter einer : mitarbeiter) {
            if (namen.length() > 0) {
                namen.append(", ");
            }
            namen.append(einer.getName());
        }
        return namen.toString();
    }

    private boolean personalnummerVergeben(String personalnummer, Mitarbeiter bestehend) {
        if (bestehend != null && personalnummer.equals(bestehend.getPersonalnummer())) {
            return false;
        }
        return mitarbeiterRepository.existsByPersonalnummer(personalnummer);
    }

    private boolean benutzernameVergeben(String benutzername, Mitarbeiter bestehend) {
        if (bestehend != null && benutzername.equals(bestehend.getBenutzer().getBenutzername())) {
            return false;
        }
        return benutzerRepository.existsByBenutzername(benutzername);
    }

    private void pflichtText(String text, int hoechstlaenge, String feld, String bezeichnung,
            Map<String, String> fehler) {
        if (istLeer(text)) {
            fehler.put(feld, "Bitte geben Sie " + bezeichnung + " ein.");
        } else if (text.length() > hoechstlaenge) {
            fehler.put(feld, "Bitte kürzen Sie " + bezeichnung + " auf höchstens " + hoechstlaenge + " Zeichen.");
        }
    }

    private boolean istLeer(String text) {
        return text == null || text.isBlank();
    }

    private void werfenFallsFehler(Map<String, String> fehler) {
        if (!fehler.isEmpty()) {
            throw new EingabeFehlerException(fehler);
        }
    }
}

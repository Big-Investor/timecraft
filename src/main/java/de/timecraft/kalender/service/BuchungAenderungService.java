package de.timecraft.kalender.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.model.BuchungAenderungEingabe;
import de.timecraft.kalender.model.ProjektAuswahl;
import de.timecraft.kalender.service.UngueltigeAenderungException.Feld;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Aendern einer abgeschlossenen Buchung einer anderen Person durch Projektleitung oder Admin
 * (FA-M6-08, 09, 11, UC-M6-05). Prueft Rechte, Eingaben und Zielprojekt; die Integritaet
 * (Ueberschneidung …) prueft zusaetzlich M2 in BuchungsAenderung.aendern und schreibt Buchung
 * und Protokoll in einer Transaktion. Deren Fachausnahme geht unveraendert an die Oberflaeche.
 */
@Service
public class BuchungAenderungService {

    private static final String KEINE_AENDERUNG_ALS_MITARBEITER = "Sie dürfen im Kalender keine Buchungen ändern.";
    private static final String NUR_EIGENE_PROJEKTE = "Sie dürfen nur Buchungen auf Ihre aktiven Projekte ändern.";

    private final BuchungsAuskunft buchungsAuskunft;
    private final BuchungsAenderung buchungsAenderung;
    private final ProjektAuskunft projektAuskunft;
    private final Clock clock;
    private final KalenderRechte rechte;

    /** "@Lazy": M1, M2 und M3 gibt es in diesem Branch noch nicht (wie im KalenderService). */
    public BuchungAenderungService(@Lazy BuchungsAuskunft buchungsAuskunft, @Lazy BuchungsAenderung buchungsAenderung,
            @Lazy ProjektAuskunft projektAuskunft, @Lazy MitarbeiterAuskunft mitarbeiterAuskunft, Clock clock) {
        this.buchungsAuskunft = buchungsAuskunft;
        this.buchungsAenderung = buchungsAenderung;
        this.projektAuskunft = projektAuskunft;
        this.clock = clock;
        this.rechte = new KalenderRechte(projektAuskunft, mitarbeiterAuskunft);
    }

    /**
     * Aktueller Stand einer Buchung fuer das Aenderungsformular, nur wenn die Person sie aendern darf.
     * Das Formular braucht dafuer nur die Buchungs-ID (auch ohne Detailfenster, z. B. spaeter fuer K1).
     * Den Stand merkt sich das Formular und gibt ihn beim Speichern an aendern zurueck (UC-M6-05 4d).
     */
    public BuchungInfo buchungZumAendern(long mitarbeiterId, Rolle rolle, long zeiteintragId) {
        return aenderbareBuchung(mitarbeiterId, rolle, zeiteintragId);
    }

    /**
     * Startwerte des Aenderungsformulars aus einem Stand: Beginn und Ende als Ortszeit (Zeitzone der Clock),
     * Standort und Projekt wie gebucht, Begruendung leer.
     */
    public BuchungAenderungEingabe formularwerte(BuchungInfo stand) {
        return new BuchungAenderungEingabe(ortszeit(stand.beginn()), stand.ende() == null ? null : ortszeit(stand.ende()),
                stand.standort(), stand.projektId(), "");
    }

    /** Ob „Bearbeiten“ angeboten wird: dieselben Regeln wie buchungZumAendern (UC-M6-05 1a, 1b). */
    public boolean darfAendern(long mitarbeiterId, Rolle rolle, long zeiteintragId) {
        try {
            aenderbareBuchung(mitarbeiterId, rolle, zeiteintragId);
            return true;
        } catch (FachlicheException | ZugriffVerweigertException | NichtGefundenException
                | NichtAngemeldetException abgelehnt) {
            return false;
        }
    }

    /**
     * Projekte, auf die umgebucht werden darf, sortiert nach Name: aktive Projekte, denen die Person
     * der Buchung zugeordnet ist; fuer die Projektleitung nur ihre eigenen (UC-M6-05 Schritt 2).
     * Das bisherige Projekt der Buchung ist immer dabei, damit es beibehalten werden kann
     * (auch wenn es inzwischen archiviert ist oder die Person nicht mehr zugeordnet ist).
     */
    public List<ProjektAuswahl> waehlbareZielprojekte(long mitarbeiterId, Rolle rolle, long zeiteintragId) {
        BuchungInfo buchung = aenderbareBuchung(mitarbeiterId, rolle, zeiteintragId);
        List<ProjektInfo> projekte = new ArrayList<>(projektAuskunft.aktiveProjekteVon(buchung.mitarbeiterId()).stream()
                .filter(projekt -> rolle == Rolle.ADMIN || projekt.projektleitungId() == mitarbeiterId)
                .toList());
        if (buchung.projektId() != null
                && projekte.stream().noneMatch(projekt -> projekt.projektId() == buchung.projektId())) {
            projektAuskunft.finde(buchung.projektId()).ifPresent(projekte::add);
        }
        return projekte.stream()
                .sorted(Comparator.comparing(ProjektInfo::name))
                .map(projekt -> new ProjektAuswahl(projekt.projektId(), projekt.name()))
                .toList();
    }

    /**
     * Speichert die neuen Werte ueber M2 (FA-M6-08, 09, 12). Prueft vorher in dieser Reihenfolge:
     * Rechte, laufende Buchung, Stand seit dem Oeffnen (4d), Eingaben (4a, 4c), Zielprojekt.
     * Bei jedem Fehler wird nichts gespeichert.
     *
     * @param stand die Buchung, wie sie beim Oeffnen des Formulars aussah (aus buchungZumAendern)
     */
    public void aendern(long mitarbeiterId, Rolle rolle, BuchungInfo stand, BuchungAenderungEingabe eingabe) {
        Objects.requireNonNull(stand, "stand");
        Objects.requireNonNull(eingabe, "eingabe");
        BuchungInfo aktuell = aenderbareBuchung(mitarbeiterId, rolle, stand.zeiteintragId());
        if (!aktuell.equals(stand)) {
            throw new BuchungInzwischenGeaendertException(aktuell);
        }
        pruefeEingabe(eingabe);
        pruefeZielprojekt(mitarbeiterId, rolle, aktuell, eingabe.projektId());
        buchungsAenderung.aendern(aktuell.zeiteintragId(), alsInstant(eingabe.beginn()), alsInstant(eingabe.ende()),
                eingabe.standort(), eingabe.projektId(), AenderungsArt.AENDERUNG, mitarbeiterId,
                eingabe.begruendung().strip());
    }

    /**
     * Die Buchung, wenn die angemeldete Person sie aendern darf: Projektleitung oder Admin, Buchung einer
     * anderen Person, abgeschlossen; die Projektleitung nur auf ihre aktiven Projekte, denen die Person
     * noch zugeordnet ist (dieselben Buchungen, die sie im fremden Kalender sieht).
     */
    private BuchungInfo aenderbareBuchung(long mitarbeiterId, Rolle rolle, long zeiteintragId) {
        KalenderRechte.pruefeAngemeldet(rolle);
        if (rolle == Rolle.MITARBEITER) {
            throw new ZugriffVerweigertException(KEINE_AENDERUNG_ALS_MITARBEITER);
        }
        BuchungInfo buchung = buchungsAuskunft.buchung(zeiteintragId).orElseThrow(() -> rolle == Rolle.ADMIN
                ? new NichtGefundenException("Diese Buchung gibt es nicht.")
                : new ZugriffVerweigertException(NUR_EIGENE_PROJEKTE));
        if (buchung.mitarbeiterId() == mitarbeiterId) {
            throw new ZugriffVerweigertException("Ihre eigenen Buchungen können Sie im Kalender nicht ändern.");
        }
        if (rolle == Rolle.PROJEKTLEITUNG && !rechte
                .sichtbareProjekteFuerProjektleitung(mitarbeiterId, buchung.mitarbeiterId())
                .contains(buchung.projektId())) {
            throw new ZugriffVerweigertException(NUR_EIGENE_PROJEKTE);
        }
        if (buchung.laeuft()) {
            throw new FachlicheException("Die Buchung läuft noch. Sie ist erst nach dem Ausstempeln änderbar.");
        }
        return buchung;
    }

    /** Pflichtfelder, Ende nach Beginn, Ende nicht in der Zukunft, Begruendung (UC-M6-05 4a, 4c). */
    private void pruefeEingabe(BuchungAenderungEingabe eingabe) {
        if (eingabe.beginn() == null) {
            throw new UngueltigeAenderungException(Feld.BEGINN, "Bitte geben Sie den Beginn an.");
        }
        if (eingabe.ende() == null) {
            throw new UngueltigeAenderungException(Feld.ENDE, "Bitte geben Sie das Ende an.");
        }
        if (!alsInstant(eingabe.ende()).isAfter(alsInstant(eingabe.beginn()))) {
            throw new UngueltigeAenderungException(Feld.ENDE, "Das Ende muss nach dem Beginn liegen.");
        }
        if (alsInstant(eingabe.ende()).isAfter(clock.instant())) {
            throw new UngueltigeAenderungException(Feld.ENDE, "Das Ende darf nicht in der Zukunft liegen.");
        }
        if (eingabe.standort() == null) {
            throw new UngueltigeAenderungException(Feld.STANDORT, "Bitte wählen Sie einen Standort.");
        }
        if (eingabe.begruendung() == null || eingabe.begruendung().isBlank()) {
            throw new UngueltigeAenderungException(Feld.BEGRUENDUNG, "Bitte geben Sie eine Begründung an.");
        }
    }

    /**
     * Zielprojekt (FA-M6-08, 09) beim Umbuchen: vorhanden, bei der Projektleitung eines ihrer Projekte,
     * aktiv und die Person ist ihm zugeordnet. Bleibt das Projekt gleich, wird es nicht erneut geprueft
     * (z. B. Admin korrigiert nur die Zeit einer Buchung auf ein archiviertes Projekt).
     * Ohne Projekt nur, wenn die Buchung schon ohne Projekt war.
     */
    private void pruefeZielprojekt(long mitarbeiterId, Rolle rolle, BuchungInfo buchung, Long projektId) {
        if (Objects.equals(projektId, buchung.projektId())) {
            return;
        }
        if (projektId == null) {
            throw new UngueltigeAenderungException(Feld.PROJEKT, "Bitte wählen Sie ein Projekt.");
        }
        ProjektInfo projekt = projektAuskunft.finde(projektId).orElseThrow(() ->
                new UngueltigeAenderungException(Feld.PROJEKT, "Dieses Projekt gibt es nicht."));
        if (rolle == Rolle.PROJEKTLEITUNG && projekt.projektleitungId() != mitarbeiterId) {
            throw new ZugriffVerweigertException("Sie können nur auf eines Ihrer Projekte umbuchen.");
        }
        if (!projekt.aktiv()) {
            throw new UngueltigeAenderungException(Feld.PROJEKT,
                    "Das Projekt „" + projekt.name() + "“ ist nicht aktiv.");
        }
        if (!projektAuskunft.istZugeordnet(buchung.mitarbeiterId(), projektId)) {
            throw new UngueltigeAenderungException(Feld.PROJEKT,
                    "Die Person ist dem Projekt „" + projekt.name() + "“ nicht zugeordnet.");
        }
    }

    private LocalDateTime ortszeit(Instant zeitpunkt) {
        return LocalDateTime.ofInstant(zeitpunkt, clock.getZone());
    }

    /** Ortszeit (Europe/Berlin aus der Clock) als Zeitpunkt; in der Herbst-Umstellungsnacht gilt die Sommerzeit. */
    private Instant alsInstant(LocalDateTime ortszeit) {
        return ortszeit.atZone(clock.getZone()).toInstant();
    }
}

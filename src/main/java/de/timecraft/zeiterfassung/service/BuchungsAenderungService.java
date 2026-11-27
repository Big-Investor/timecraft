package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.Aenderungsprotokoll;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.AenderungsprotokollRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/** @author Noah Cipa */
@Service
public class BuchungsAenderungService implements BuchungsAenderung {

    private static final DateTimeFormatter DATUM_UHRZEIT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int MAX_LAENGE_BEGRUENDUNG = 255;

    private final ZeiteintragRepository zeiteintragRepository;
    private final AenderungsprotokollRepository aenderungsprotokollRepository;
    private final ZeiteintragService zeiteintragService;
    private final Clock clock;

    public BuchungsAenderungService(ZeiteintragRepository zeiteintragRepository,
            AenderungsprotokollRepository aenderungsprotokollRepository, ZeiteintragService zeiteintragService,
            Clock clock) {
        this.zeiteintragRepository = zeiteintragRepository;
        this.aenderungsprotokollRepository = aenderungsprotokollRepository;
        this.zeiteintragService = zeiteintragService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) {
        if (art == null) {
            throw new FachlicheException("Bitte geben Sie die Art der Änderung an.");
        }
        pruefeEingaben(beginn, ende, standort, begruendung);
        beginn = aufMinute(beginn);
        ende = aufMinute(ende);
        pruefeZeiten(beginn, ende);
        zeiteintragService.pruefeMitarbeiterBekannt(geaendertVonMitarbeiterId);
        Zeiteintrag eintrag = sperreUndLade(zeiteintragId);
        if (eintrag.getEnde() == null) {
            throw new FachlicheException("Eine laufende Buchung kann nicht geändert werden.");
        }
        zeiteintragService.pruefeUeberschneidung(eintrag.getMitarbeiterId(), beginn, ende, eintrag.getId());

        Instant jetzt = clock.instant();
        protokolliereFallsGeaendert(eintrag, "beginn", text(eintrag.getBeginn()), text(beginn),
                art, geaendertVonMitarbeiterId, begruendung, jetzt);
        protokolliereFallsGeaendert(eintrag, "ende", text(eintrag.getEnde()), text(ende),
                art, geaendertVonMitarbeiterId, begruendung, jetzt);
        protokolliereFallsGeaendert(eintrag, "standort", eintrag.getStandort().name(), standort.name(),
                art, geaendertVonMitarbeiterId, begruendung, jetzt);
        protokolliereFallsGeaendert(eintrag, "projekt", text(eintrag.getProjektId()), text(projektId),
                art, geaendertVonMitarbeiterId, begruendung, jetzt);

        eintrag.aendern(beginn, ende, standort, projektId);
    }

    @Override
    @Transactional
    public long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
            long geaendertVonMitarbeiterId, String begruendung) {
        pruefeEingaben(beginn, ende, standort, begruendung);
        beginn = aufMinute(beginn);
        ende = aufMinute(ende);
        pruefeZeiten(beginn, ende);
        zeiteintragService.pruefeMitarbeiterBekannt(mitarbeiterId);
        zeiteintragService.pruefeMitarbeiterBekannt(geaendertVonMitarbeiterId);
        zeiteintragService.sperreMitarbeiter(mitarbeiterId);
        zeiteintragService.pruefeUeberschneidung(mitarbeiterId, beginn, ende, null);

        Zeiteintrag eintrag = new Zeiteintrag(mitarbeiterId, beginn, standort, projektId);
        eintrag.ausstempeln(ende);
        eintrag.alsGeaendertMarkieren();
        Zeiteintrag gespeichert = zeiteintragRepository.save(eintrag);
        aenderungsprotokollRepository.save(new Aenderungsprotokoll(gespeichert, geaendertVonMitarbeiterId,
                clock.instant(), AenderungsArt.NACHTRAG, "eintrag", null,
                text(beginn) + " bis " + text(ende), begruendung));
        return gespeichert.getId();
    }

    /**
     * Erst sperren, dann laden: Vorher wird nur die Mitarbeiter-ID gelesen. So stammt der geladene Stand (und
     * damit der alte Wert im Protokoll) nie aus der Zeit vor dem Speichern eines anderen Fensters.
     */
    private Zeiteintrag sperreUndLade(long zeiteintragId) {
        long mitarbeiterId = zeiteintragRepository.findeMitarbeiterId(zeiteintragId)
                .orElseThrow(() -> new NichtGefundenException("Die Buchung wurde nicht gefunden."));
        zeiteintragService.sperreMitarbeiter(mitarbeiterId);
        return zeiteintragRepository.findById(zeiteintragId)
                .orElseThrow(() -> new NichtGefundenException("Die Buchung wurde nicht gefunden."));
    }

    private void pruefeEingaben(Instant beginn, Instant ende, Standort standort, String begruendung) {
        if (beginn == null || ende == null || standort == null) {
            throw new FachlicheException("Bitte geben Sie Beginn, Ende und Standort an.");
        }
        if (begruendung != null && begruendung.length() > MAX_LAENGE_BEGRUENDUNG) {
            throw new FachlicheException("Die Begründung darf höchstens " + MAX_LAENGE_BEGRUENDUNG + " Zeichen lang sein.");
        }
    }

    private void pruefeZeiten(Instant beginn, Instant ende) {
        if (!ende.isAfter(beginn)) {
            throw new FachlicheException("Das Ende muss nach dem Beginn liegen.");
        }
        if (ende.isAfter(clock.instant())) {
            throw new FachlicheException("Das Ende darf nicht in der Zukunft liegen.");
        }
    }

    /** Zeiten werden minutengenau gespeichert (RB-M2-01). */
    private Instant aufMinute(Instant zeitpunkt) {
        return zeitpunkt.truncatedTo(ChronoUnit.MINUTES);
    }

    private void protokolliereFallsGeaendert(Zeiteintrag eintrag, String feld, String alterWert, String neuerWert,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung, Instant jetzt) {
        if (!Objects.equals(alterWert, neuerWert)) {
            aenderungsprotokollRepository.save(new Aenderungsprotokoll(eintrag, geaendertVonMitarbeiterId,
                    jetzt, art, feld, alterWert, neuerWert, begruendung));
        }
    }

    /** Datum und Uhrzeit in Europe/Berlin, z. B. "10.11.2026 08:00". */
    private String text(Instant zeitpunkt) {
        return DATUM_UHRZEIT_FORMATTER.format(zeitpunkt.atZone(clock.getZone()));
    }

    private String text(Long id) {
        if (id == null) {
            return null;
        }
        return String.valueOf(id);
    }
}

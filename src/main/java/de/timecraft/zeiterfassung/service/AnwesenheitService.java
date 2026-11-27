package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.model.Anwesenheit;
import de.timecraft.zeiterfassung.model.AnwesenheitsUebersicht;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.model.Zeiteintrag;

/** @author Noah Cipa */
@Service
public class AnwesenheitService {

    private final ZeiteintragService zeiteintragService;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final ProjektAuskunft projektAuskunft;
    private final Clock clock;

    public AnwesenheitService(ZeiteintragService zeiteintragService, MitarbeiterAuskunft mitarbeiterAuskunft,
            ProjektAuskunft projektAuskunft, Clock clock) {
        this.zeiteintragService = zeiteintragService;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.projektAuskunft = projektAuskunft;
        this.clock = clock;
    }

    /**
     * Alle aktiven Mitarbeiter aus M1, gefiltert (FA-M2-22 bis 24). Die Anzahl je Status zaehlt ohne Filter.
     * Admin-Konten erscheinen nicht (Entscheidung Noah 27.11.2026).
     */
    @Transactional(readOnly = true)
    public AnwesenheitsUebersicht uebersicht(Rolle rolle, String namensuche, StempelStatus status, Standort standort) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        if (rolle == Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Die Anwesenheit sehen nur Mitarbeiter und Projektleitungen.");
        }
        Map<StempelStatus, Integer> anzahlJeStatus = new EnumMap<>(StempelStatus.class);
        for (StempelStatus wert : StempelStatus.values()) {
            anzahlJeStatus.put(wert, 0);
        }
        List<Anwesenheit> treffer = new ArrayList<>();
        for (MitarbeiterInfo mitarbeiter : mitarbeiterAuskunft.aktive()) {
            if (mitarbeiter.rolle() == Rolle.ADMIN) {
                continue;
            }
            Anwesenheit anwesenheit = anwesenheitVon(mitarbeiter.mitarbeiterId(), mitarbeiter.name());
            anzahlJeStatus.merge(anwesenheit.status(), 1, Integer::sum);
            if (passtZumFilter(anwesenheit, namensuche, status, standort)) {
                treffer.add(anwesenheit);
            }
        }
        return new AnwesenheitsUebersicht(anzahlJeStatus, treffer, LocalTime.now(clock));
    }

    private Anwesenheit anwesenheitVon(long mitarbeiterId, String name) {
        StempelStatus status = zeiteintragService.statusVon(mitarbeiterId);
        Optional<Zeiteintrag> laufenderEintrag = zeiteintragService.laufenderEintragVon(mitarbeiterId);
        if (laufenderEintrag.isEmpty()) {
            return new Anwesenheit(mitarbeiterId, name, status, null, null, null);
        }
        Zeiteintrag eintrag = laufenderEintrag.get();
        Long projektId = eintrag.getProjektId();
        String projektName = projektId == null ? null
                : projektAuskunft.finde(projektId).map(ProjektInfo::name).orElse(null);
        return new Anwesenheit(mitarbeiterId, name, status, eintrag.getStandort(), projektId, projektName);
    }

    private boolean passtZumFilter(Anwesenheit anwesenheit, String namensuche, StempelStatus status, Standort standort) {
        if (namensuche != null && !namensuche.isBlank()
                && !anwesenheit.name().toLowerCase().contains(namensuche.trim().toLowerCase())) {
            return false;
        }
        if (status != null && anwesenheit.status() != status) {
            return false;
        }
        if (standort != null && anwesenheit.standort() != standort) {
            return false;
        }
        return true;
    }
}

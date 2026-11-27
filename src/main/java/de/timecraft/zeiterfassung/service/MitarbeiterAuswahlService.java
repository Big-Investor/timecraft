package de.timecraft.zeiterfassung.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.zeiterfassung.model.MitarbeiterEintrag;

/**
 * Wessen Zeiten und Zeitkonto die angemeldete Person ansehen darf (FA-M2-10, FA-M2-17).
 *
 * @author Noah Cipa
 */
@Service
public class MitarbeiterAuswahlService {

    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final ZugriffsPruefung zugriffsPruefung;

    public MitarbeiterAuswahlService(MitarbeiterAuskunft mitarbeiterAuskunft, ZugriffsPruefung zugriffsPruefung) {
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.zugriffsPruefung = zugriffsPruefung;
    }

    /**
     * Zuerst die angemeldete Person, danach nach Nachname sortiert die Mitarbeiter, die sie als Projektleitung
     * zusaetzlich lesen darf. Mitarbeiter, die M1 nicht kennt, fehlen.
     */
    @Transactional(readOnly = true)
    public List<MitarbeiterEintrag> lesbareMitarbeiter(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        List<MitarbeiterInfo> andere = new ArrayList<>();
        for (long mitarbeiterId : zugriffsPruefung.projektMitarbeiter(angemeldeteId, rolle)) {
            if (mitarbeiterId != angemeldeteId) {
                mitarbeiterAuskunft.finde(mitarbeiterId).ifPresent(andere::add);
            }
        }
        Collator deutsch = Collator.getInstance(Locale.GERMANY);
        andere.sort(Comparator.comparing(MitarbeiterInfo::nachname, deutsch)
                .thenComparing(MitarbeiterInfo::vorname, deutsch));

        List<MitarbeiterEintrag> auswahl = new ArrayList<>();
        mitarbeiterAuskunft.finde(angemeldeteId).ifPresent(selbst -> auswahl.add(eintrag(selbst)));
        for (MitarbeiterInfo mitarbeiter : andere) {
            auswahl.add(eintrag(mitarbeiter));
        }
        return auswahl;
    }

    private MitarbeiterEintrag eintrag(MitarbeiterInfo mitarbeiter) {
        return new MitarbeiterEintrag(mitarbeiter.mitarbeiterId(), mitarbeiter.name());
    }
}

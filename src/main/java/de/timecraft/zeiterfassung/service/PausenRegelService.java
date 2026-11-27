package de.timecraft.zeiterfassung.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import de.timecraft.zeiterfassung.model.Arbeitsblock;

/** @author Noah Cipa */
@Service
public class PausenRegelService {

    public static final int KUERZESTE_PAUSE_MINUTEN = 15;
    public static final int VORWARNUNG_MINUTEN = 30;
    public static final int WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE = 40 * 60;

    /**
     * Minderjaehrig bis zum Tag vor dem 18. Geburtstag (FA-M2-12). Ohne Geburtsdatum gelten die Regeln
     * fuer Erwachsene.
     */
    public boolean istMinderjaehrig(LocalDate geburtsdatum, LocalDate tag) {
        if (geburtsdatum == null) {
            return false;
        }
        return tag.isBefore(geburtsdatum.plusYears(18));
    }

    public int mindestPauseMinuten(int arbeitsMinuten, boolean minderjaehrig) {
        if (minderjaehrig) {
            if (arbeitsMinuten > 6 * 60) {
                return 60;
            }
            if (arbeitsMinuten > 4 * 60 + 30) {
                return 30;
            }
            return 0;
        }
        if (arbeitsMinuten > 9 * 60) {
            return 45;
        }
        if (arbeitsMinuten > 6 * 60) {
            return 30;
        }
        return 0;
    }

    public int anrechenbarePausenMinuten(List<Duration> pausen) {
        int summe = 0;
        for (Duration pause : pausen) {
            if (pause.toMinutes() >= KUERZESTE_PAUSE_MINUTEN) {
                summe = summe + (int) pause.toMinutes();
            }
        }
        return summe;
    }

    public int fehlendePauseMinuten(int arbeitsMinuten, List<Duration> pausen, boolean minderjaehrig) {
        int fehlend = mindestPauseMinuten(arbeitsMinuten, minderjaehrig) - anrechenbarePausenMinuten(pausen);
        return Math.max(fehlend, 0);
    }

    /**
     * Pausenregel je Tag (FA-M2-13, FA-M2-14, Entscheidung Noah 27.11.2026): Die Arbeitszeit aller Bloecke
     * eines Tages zaehlt zusammen. Als Pause zaehlen erfasste Pausen und Luecken zwischen zwei Bloecken, jeweils
     * ab 15 Minuten. Die fehlende Zeit wird vom spaetesten Block her abgezogen, nie mehr als dessen Arbeitszeit.
     *
     * @param bloecke Bloecke eines Tages ohne Ueberschneidung, sortiert nach Beginn
     * @return Abzug in Minuten je Block, in derselben Reihenfolge
     */
    public List<Integer> pflichtpausenAbzuege(List<Arbeitsblock> bloecke, boolean minderjaehrig) {
        int arbeitsMinuten = 0;
        List<Duration> pausen = new ArrayList<>();
        for (int i = 0; i < bloecke.size(); i++) {
            arbeitsMinuten = arbeitsMinuten + bloecke.get(i).arbeitsMinuten();
            pausen.addAll(bloecke.get(i).pausen());
            if (i > 0) {
                pausen.add(Duration.between(bloecke.get(i - 1).ende(), bloecke.get(i).beginn()));
            }
        }
        int offen = fehlendePauseMinuten(arbeitsMinuten, pausen, minderjaehrig);
        Integer[] abzuege = new Integer[bloecke.size()];
        for (int i = bloecke.size() - 1; i >= 0; i--) {
            abzuege[i] = Math.min(offen, bloecke.get(i).arbeitsMinuten());
            offen = offen - abzuege[i];
        }
        return Arrays.asList(abzuege);
    }

    /** Hoechstarbeitszeit am Tag: Erwachsene 10 h (§ 3 ArbZG), Minderjaehrige 8 h (§ 8 JArbSchG). */
    public int tagesHoechstMinuten(boolean minderjaehrig) {
        if (minderjaehrig) {
            return 8 * 60;
        }
        return 10 * 60;
    }

    public boolean hoechstarbeitszeitUeberschritten(int arbeitsMinuten, boolean minderjaehrig) {
        return arbeitsMinuten > tagesHoechstMinuten(minderjaehrig);
    }

    /** Laufender Eintrag: Warnung ab 30 Minuten vor Erreichen der Tagesgrenze (FA-M2-15). */
    public boolean vorwarnungTag(int arbeitsMinuten, boolean minderjaehrig) {
        return arbeitsMinuten >= tagesHoechstMinuten(minderjaehrig) - VORWARNUNG_MINUTEN;
    }

    /** Nur Minderjaehrige haben eine Wochengrenze: mehr als 40 h (§ 8 JArbSchG). */
    public boolean wochenhoechstarbeitszeitUeberschritten(int wochenMinuten, boolean minderjaehrig) {
        return minderjaehrig && wochenMinuten > WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE;
    }

    /** Genau an der Wochengrenze der Minderjaehrigen. */
    public boolean wochenhoechstarbeitszeitErreicht(int wochenMinuten, boolean minderjaehrig) {
        return minderjaehrig && wochenMinuten == WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE;
    }

    /** Laufender Eintrag: Warnung ab 30 Minuten vor Erreichen der Wochengrenze fuer Minderjaehrige (FA-M2-15). */
    public boolean vorwarnungWoche(int wochenMinuten, boolean minderjaehrig) {
        return minderjaehrig && wochenMinuten >= WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE - VORWARNUNG_MINUTEN;
    }
}

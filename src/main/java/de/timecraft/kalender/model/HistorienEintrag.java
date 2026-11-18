package de.timecraft.kalender.model;

import java.time.LocalDateTime;

/**
 * Eine Zeile der Aenderungshistorie im Detailfenster (FA-M6-10): Zeitpunkt (Ortszeit), Name der
 * aendernden Person, geaendertes Feld, alter und neuer Wert, Begruendung.
 */
public record HistorienEintrag(
        LocalDateTime zeitpunkt, String geaendertVon, String feld, String alterWert, String neuerWert,
        String begruendung) { }

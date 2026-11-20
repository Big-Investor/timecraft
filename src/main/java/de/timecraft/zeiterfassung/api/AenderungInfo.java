// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

public record AenderungInfo(
        Instant zeitpunkt, long geaendertVonMitarbeiterId, AenderungsArt art,
        String feld, String alterWert, String neuerWert, String begruendung) { }

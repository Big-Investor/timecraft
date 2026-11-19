// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjektInfo(
        long projektId, String name, LocalDate startdatum, LocalDate zieldatum,
        long projektleitungId, boolean aktiv, BigDecimal angebotspreis) { }

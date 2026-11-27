// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.math.BigDecimal;

/**
 * Schreibzugriff nur auf die Spalte projekt.angebotspreis (fachlich M4).
 * Keine Rechteprüfung hier: Der Aufrufer (M4) prüft, dass die angemeldete Person die
 * verantwortliche Projektleitung des aktiven Projekts ist.
 */
public interface AngebotspreisPflege {
    void setzeAngebotspreis(long projektId, BigDecimal angebotspreis);
}

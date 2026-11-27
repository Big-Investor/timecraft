package de.timecraft.antraege.service;

/**
 * Urlaubstage eines geplanten Urlaubs und der danach verbleibende Resturlaub im Jahr des Beginns.
 * resturlaubDanach ist null, wenn der Urlaub nicht gegen den Anspruch zaehlt (unbezahlter Urlaub).
 */
public record Urlaubsvorschau(int urlaubstage, Integer resturlaubDanach) {
}

package de.timecraft.zeiterfassung.model;

/**
 * Urlaubskonto eines Jahres. Anspruch, Uebertrag und Resturlaub sind null, wenn M1 keine Urlaubsdaten hat.
 *
 * @author Noah Cipa
 */
public record Urlaubskonto(int jahr, Integer anspruchTage, Integer uebertragTage, int genommenTage,
        Integer resturlaubTage) {
}

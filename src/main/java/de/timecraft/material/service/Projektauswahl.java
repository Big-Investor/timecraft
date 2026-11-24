package de.timecraft.material.service;

/**
 * Projekt zur Auswahl beim Mehrbedarf: nur ID und Name, ohne Angebotspreis (QA-M4-02).
 *
 * @param projektId Projekt
 * @param name Projektname
 *
 * @author Tharsan Arumuganathar
 */
public record Projektauswahl(long projektId, String name) {
}

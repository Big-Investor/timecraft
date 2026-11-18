package de.timecraft.kalender.model;

/**
 * Eine Person in der Auswahl „Person wählen“ (FA-M6-07). Nur ID und Name, damit die Oberfläche
 * keine weiteren Personendaten (Saldo, Geburtsdatum …) bekommt (QA-M6-04).
 */
public record WaehlbarePerson(long mitarbeiterId, String name) { }

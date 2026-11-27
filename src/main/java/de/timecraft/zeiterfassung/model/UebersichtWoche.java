package de.timecraft.zeiterfassung.model;

import java.time.Duration;
import java.util.List;

/**
 * Eine Kalenderwoche der Zeitenuebersicht mit allen Tagen, zu denen es etwas anzuzeigen gibt.
 *
 * @author Noah Cipa
 */
public record UebersichtWoche(int kalenderwoche, List<UebersichtTag> tage, Duration summe) {
}

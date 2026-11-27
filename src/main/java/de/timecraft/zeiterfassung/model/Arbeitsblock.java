package de.timecraft.zeiterfassung.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ein Zeiteintrag eines Tages mit seinen erfassten Pausen, Grundlage der Pausenregel.
 *
 * @author Noah Cipa
 */
public record Arbeitsblock(Instant beginn, Instant ende, List<Duration> pausen) {

    /** Dauer ohne alle erfassten Pausen, auch ohne die unter 15 Minuten. */
    public int arbeitsMinuten() {
        Duration arbeit = Duration.between(beginn, ende);
        for (Duration pause : pausen) {
            arbeit = arbeit.minus(pause);
        }
        return (int) arbeit.toMinutes();
    }
}

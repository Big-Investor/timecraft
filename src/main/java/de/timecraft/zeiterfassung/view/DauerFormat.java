package de.timecraft.zeiterfassung.view;

import java.time.Duration;
/** @author Noah Cipa */
public final class DauerFormat {

    private DauerFormat() {
        // Nur statische Methoden
    }

    public static String text(Duration dauer) {
        return dauer.toHours() + ":" + String.format("%02d", dauer.toMinutesPart());
    }

    public static String saldo(int minuten) {
        String vorzeichen = minuten < 0 ? "−" : "+";
        return vorzeichen + text(Duration.ofMinutes(Math.abs(minuten)));
    }
}

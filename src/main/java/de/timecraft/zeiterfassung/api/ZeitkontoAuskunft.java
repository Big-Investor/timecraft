// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.YearMonth;
import java.util.Optional;

public interface ZeitkontoAuskunft {
    Optional<ZeitkontoInfo> monat(long mitarbeiterId, YearMonth monat);
}

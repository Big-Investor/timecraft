// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M4.
package de.timecraft.material.api;

import java.util.List;

/** Nur für das Kann-Ziel FA-M4-K2 (Materialrückmeldung beim Ausstempeln). Keine Preise! */
public interface MaterialAuskunft {
    List<MaterialInfo> aktiveMaterialien();
}

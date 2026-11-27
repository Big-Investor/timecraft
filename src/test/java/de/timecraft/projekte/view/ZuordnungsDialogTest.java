package de.timecraft.projekte.view;

import static de.timecraft.projekte.view.Komponenten.anmelden;
import static de.timecraft.projekte.view.Komponenten.knopf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Paragraph;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.projekte.service.ProjektService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prüft den Dialog "Mitarbeiter zuordnen" ohne Browser (UC-M3-05). Das Projekt 10 gehört der Projektleitung 2.
 *
 * @author Tim Stukenbröker
 */
class ZuordnungsDialogTest {

    // Vaadin merkt sich die aktuelle UI nur schwach: Ohne dieses Feld kann der Müllsammler sie im Test wegräumen.
    private UI ui;

    private static final long PROJEKT_ID = 10L;

    private final ProjektService service = mock(ProjektService.class);
    private final AtomicInteger nachDemZuordnen = new AtomicInteger();

    private final MitarbeiterInfo jonas = person(1, "Jonas", "Becker");
    private final MitarbeiterInfo anna = person(6, "Anna", "Vogt");

    private static MitarbeiterInfo person(long id, String vorname, String nachname) {
        return new MitarbeiterInfo(id, "P" + id, vorname, nachname, Rolle.MITARBEITER, true, null, null, null,
                Set.of(), null, 0);
    }

    @BeforeEach
    void meldeProjektleitungAn() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        ui = new UI();
        UI.setCurrent(ui);
        when(service.zuordenbareMitarbeiter(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID)).thenReturn(List.of(jonas, anna));
    }

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
    }

    private ZuordnungsDialog dialog() {
        return new ZuordnungsDialog(service, new AktuelleSitzung(), PROJEKT_ID, nachDemZuordnen::incrementAndGet);
    }

    @SuppressWarnings("unchecked")
    private MultiSelectComboBox<MitarbeiterInfo> auswahl(ZuordnungsDialog dialog) {
        return Komponenten.alle(dialog, MultiSelectComboBox.class).get(0);
    }

    @Test
    void bietetDieZuordenbarenMitarbeiterZurAuswahlAn() {
        ZuordnungsDialog dialog = dialog();

        assertThat(auswahl(dialog).getListDataView().getItems().toList()).containsExactly(jonas, anna);
        assertThat(dialog.getHeaderTitle()).isEqualTo("Mitarbeiter zuordnen");
    }

    @Test
    void sindAlleSchonZugeordnetGibtEsNurEinenHinweis() {
        when(service.zuordenbareMitarbeiter(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID)).thenReturn(List.of());

        ZuordnungsDialog dialog = dialog();

        assertThat(Komponenten.alle(dialog, Paragraph.class)).extracting(Paragraph::getText)
                .containsExactly("Alle Mitarbeiter sind dem Projekt bereits zugeordnet.");
        assertThat(Komponenten.alle(dialog, MultiSelectComboBox.class)).isEmpty();
        assertThat(knopf(dialog, "Schließen")).isNotNull();
    }

    @Test
    void lehntDerServiceDasLadenAbEntstehtKeinDialogMitFalschemHinweis() {
        when(service.zuordenbareMitarbeiter(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID))
                .thenThrow(new FachlicheException("Das Projekt ist archiviert."));

        assertThatThrownBy(this::dialog).isInstanceOf(FachlicheException.class);
    }

    @Test
    void zuordnenGibtDieGewaehltenIdsAnDenServiceUndLaedtNeu() {
        ZuordnungsDialog dialog = dialog();
        auswahl(dialog).select(jonas, anna);

        knopf(dialog, "Zuordnen").click();

        verify(service).zuordnen(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID, List.of(1L, 6L));
        assertThat(nachDemZuordnen.get()).isEqualTo(1);
    }

    @Test
    void ohneAuswahlMeldetDerServiceUndNichtsWirdNachgeladen() {
        doThrow(new FachlicheException("Bitte wählen Sie mindestens einen Mitarbeiter aus."))
                .when(service).zuordnen(anyLong(), any(), anyLong(), any());
        ZuordnungsDialog dialog = dialog();

        knopf(dialog, "Zuordnen").click();

        assertThat(nachDemZuordnen.get()).isZero();
    }

    @Test
    void abbrechenSpeichertNichts() {
        ZuordnungsDialog dialog = dialog();
        auswahl(dialog).select(jonas);

        knopf(dialog, "Abbrechen").click();

        verify(service, never()).zuordnen(anyLong(), any(), anyLong(), any());
        assertThat(nachDemZuordnen.get()).isZero();
    }
}

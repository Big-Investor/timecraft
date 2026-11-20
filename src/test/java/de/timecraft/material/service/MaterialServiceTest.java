package de.timecraft.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.material.api.MaterialInfo;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.repository.MaterialRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Service gegen die echte H2-Datenbank, damit Eindeutigkeit und Speichern wirklich geprueft werden. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(MaterialService.class)
class MaterialServiceTest {

    @Autowired
    private MaterialService service;

    @Autowired
    private MaterialRepository repository;

    private static MaterialDaten eiche() {
        return new MaterialDaten(" ART-1102 ", " Eiche Massivholzplatte 26 mm ", " Holz ", Einheit.QUADRATMETER,
                new BigDecimal("89.50"), new BigDecimal("129.00"));
    }

    private static MaterialDaten mitPreisen(String ek, String vk) {
        return new MaterialDaten("ART-1", "Test", null, Einheit.STUECK,
                ek == null ? null : new BigDecimal(ek), vk == null ? null : new BigDecimal(vk));
    }

    @Test
    void adminLegtMaterialAnMitBereinigtenTextenUndAktiv() {
        Material material = service.anlegen(Rolle.ADMIN, eiche());

        Material gelesen = repository.findById(material.getId()).orElseThrow();
        assertThat(gelesen.getArtikelnummer()).isEqualTo("ART-1102");
        assertThat(gelesen.getBezeichnung()).isEqualTo("Eiche Massivholzplatte 26 mm");
        assertThat(gelesen.getKategorie()).isEqualTo("Holz");
        assertThat(gelesen.getEinheit()).isEqualTo(Einheit.QUADRATMETER);
        assertThat(gelesen.isAktiv()).isTrue();
    }

    @Test
    void leereKategorieWirdAlsLeerGespeichert() {
        Material material = service.anlegen(Rolle.ADMIN, new MaterialDaten("ART-2", "Leim", "  ",
                Einheit.LITER, BigDecimal.ONE, BigDecimal.TEN));

        assertThat(material.getKategorie()).isNull();
    }

    @Test
    void preiseWerdenKaufmaennischAufZweiStellenGerundet() {
        Material material = service.anlegen(Rolle.ADMIN, mitPreisen("3.205", "5.904"));

        assertThat(material.getEinkaufspreis()).isEqualTo(new BigDecimal("3.21"));
        assertThat(material.getVerkaufspreis()).isEqualTo(new BigDecimal("5.90"));
    }

    @Test
    void preisNullIstErlaubtUndHoechstwertPasstGenau() {
        Material material = service.anlegen(Rolle.ADMIN, mitPreisen("0", "99999999.99"));

        assertThat(material.getEinkaufspreis()).isEqualByComparingTo("0");
        assertThat(material.getVerkaufspreis()).isEqualByComparingTo("99999999.99");
    }

    @Test
    void ungueltigePreiseWerdenAbgelehntUndNichtsGespeichert() {
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, mitPreisen("-0.01", "1")))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie den Einkaufspreis ohne Minuszeichen ein.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, mitPreisen("1", null)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie den Verkaufspreis ein.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, mitPreisen("1", "100000000.00")))
                .isInstanceOf(FachlicheException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void pflichtfelderUndLaengenWerdenGeprueft() {
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, new MaterialDaten("  ", "Test", null,
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie die Artikelnummer ein.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, new MaterialDaten("A".repeat(21), "Test", null,
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte kürzen Sie die Artikelnummer auf höchstens 20 Zeichen.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, new MaterialDaten("ART-3", null, null,
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie die Bezeichnung ein.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, new MaterialDaten("ART-3", "Test", "K".repeat(51),
                Einheit.STUECK, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(FachlicheException.class);
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, new MaterialDaten("ART-3", "Test", null,
                null, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte wählen Sie eine Einheit.");
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, null)).isInstanceOf(FachlicheException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void artikelnummerMussEindeutigSein() {
        service.anlegen(Rolle.ADMIN, eiche());

        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, eiche()))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Artikelnummer ART-1102 ist bereits vergeben.");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void adminBearbeitetMaterialUndDarfEigeneArtikelnummerBehalten() {
        Material material = service.anlegen(Rolle.ADMIN, eiche());

        service.bearbeiten(Rolle.ADMIN, material.getId(), new MaterialDaten("ART-1102", "Eiche 28 mm", "Holz",
                Einheit.QUADRATMETER, new BigDecimal("95.00"), new BigDecimal("135.00")));

        Material gelesen = repository.findById(material.getId()).orElseThrow();
        assertThat(gelesen.getBezeichnung()).isEqualTo("Eiche 28 mm");
        assertThat(gelesen.getEinkaufspreis()).isEqualByComparingTo("95.00");
    }

    @Test
    void bearbeitenAufVergebeneArtikelnummerWirdAbgelehnt() {
        service.anlegen(Rolle.ADMIN, eiche());
        Material zweites = service.anlegen(Rolle.ADMIN, mitPreisen("1", "2"));

        assertThatThrownBy(() -> service.bearbeiten(Rolle.ADMIN, zweites.getId(), eiche()))
                .isInstanceOf(FachlicheException.class);
        assertThat(repository.findById(zweites.getId()).orElseThrow().getArtikelnummer()).isEqualTo("ART-1");
    }

    @Test
    void bearbeitenMitUngueltigerEingabeLaesstMaterialUnveraendert() {
        Material material = service.anlegen(Rolle.ADMIN, eiche());

        assertThatThrownBy(() -> service.bearbeiten(Rolle.ADMIN, material.getId(), new MaterialDaten("ART-1102",
                "", "Holz", Einheit.QUADRATMETER, new BigDecimal("1.00"), new BigDecimal("2.00"))))
                .isInstanceOf(FachlicheException.class);

        Material gelesen = repository.findById(material.getId()).orElseThrow();
        assertThat(gelesen.getBezeichnung()).isEqualTo("Eiche Massivholzplatte 26 mm");
        assertThat(gelesen.getEinkaufspreis()).isEqualByComparingTo("89.50");
    }

    @Test
    void unbekanntesMaterialWirdGemeldet() {
        assertThatThrownBy(() -> service.bearbeiten(Rolle.ADMIN, 999, eiche()))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Das Material wurde nicht gefunden.");
        assertThatThrownBy(() -> service.deaktivieren(Rolle.ADMIN, 999))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void deaktiviertesMaterialBleibtImStammFehltAberImKatalog() {
        Material material = service.anlegen(Rolle.ADMIN, eiche());
        service.anlegen(Rolle.ADMIN, mitPreisen("1", "2"));

        service.deaktivieren(Rolle.ADMIN, material.getId());

        assertThat(repository.findById(material.getId()).orElseThrow().isAktiv()).isFalse();
        assertThat(service.alle(Rolle.ADMIN)).extracting(Material::getArtikelnummer)
                .containsExactly("ART-1", "ART-1102");
        assertThat(service.aktive(Rolle.PROJEKTLEITUNG)).extracting(Material::getArtikelnummer)
                .containsExactly("ART-1");
    }

    @Test
    void adminAktiviertDeaktiviertesMaterialWieder() {
        Material material = service.anlegen(Rolle.ADMIN, eiche());
        service.deaktivieren(Rolle.ADMIN, material.getId());

        service.aktivieren(Rolle.ADMIN, material.getId());

        assertThat(service.aktive(Rolle.ADMIN)).extracting(Material::getId).containsExactly(material.getId());
    }

    @ParameterizedTest
    @EnumSource(value = Rolle.class, names = {"MITARBEITER", "PROJEKTLEITUNG"})
    void nurAdminPflegtDenStamm(Rolle rolle) {
        Material material = service.anlegen(Rolle.ADMIN, eiche());

        assertThatThrownBy(() -> service.anlegen(rolle, mitPreisen("1", "2")))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.bearbeiten(rolle, material.getId(), mitPreisen("1", "2")))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.deaktivieren(rolle, material.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.aktivieren(rolle, material.getId()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(repository.findById(material.getId()).orElseThrow().isAktiv()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void projektleitungSiehtPreise() {
        service.anlegen(Rolle.ADMIN, eiche());

        assertThat(service.alle(Rolle.PROJEKTLEITUNG)).extracting(Material::getEinkaufspreis)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("89.50"));
    }

    @Test
    void mitarbeiterBekommtKeinePreise() {
        service.anlegen(Rolle.ADMIN, eiche());

        assertThatThrownBy(() -> service.alle(Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Preise und Kosten sind nur für Projektleitung und Admin sichtbar.");
        assertThatThrownBy(() -> service.aktive(Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void ohneAnmeldungKeinZugriff() {
        assertThatThrownBy(() -> service.anlegen(null, eiche())).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.alle(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.aktive(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.deaktivieren(null, 1)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.bearbeiten(null, 1, eiche())).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void schnittstelleLiefertNurAktiveMaterialienOhnePreise() {
        Material eiche = service.anlegen(Rolle.ADMIN, eiche());
        Material alt = service.anlegen(Rolle.ADMIN, mitPreisen("1", "2"));
        service.deaktivieren(Rolle.ADMIN, alt.getId());

        assertThat(service.aktiveMaterialien())
                .containsExactly(new MaterialInfo(eiche.getId(), "Eiche Massivholzplatte 26 mm", "m²"));
    }
}

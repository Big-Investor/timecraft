package de.timecraft.geruest.common;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeigt Benachrichtigungen in der Oberflaeche. Fehler erscheinen nur als verstaendlicher
 * deutscher Text, nie als Stacktrace.
 */
public final class Meldungen {

    static final String UNERWARTETER_FEHLER =
            "Es ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es erneut.";

    private static final Logger LOG = LoggerFactory.getLogger(Meldungen.class);
    private static final int DAUER_MS = 5000;

    private Meldungen() {
    }

    public static void erfolg(String text) {
        Notification.show(text, DAUER_MS, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void fehler(RuntimeException ausnahme) {
        Notification.show(fehlertext(ausnahme), DAUER_MS, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /** Text fuer die Person: Meldung der Fehlerklassen, sonst ein allgemeiner Hinweis. */
    public static String fehlertext(RuntimeException ausnahme) {
        if (istBekannt(ausnahme)) {
            return ausnahme.getMessage();
        }
        LOG.error("Unerwarteter Fehler", ausnahme);
        return UNERWARTETER_FEHLER;
    }

    private static boolean istBekannt(RuntimeException ausnahme) {
        return ausnahme instanceof FachlicheException
                || ausnahme instanceof ZugriffVerweigertException
                || ausnahme instanceof NichtAngemeldetException
                || ausnahme instanceof NichtGefundenException;
    }
}

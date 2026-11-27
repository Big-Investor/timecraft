package de.timecraft.zeiterfassung.view;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** @author Noah Cipa */
public final class Hinweis {

    private static final int DAUER_MS = 8000;

    private Hinweis() {
        // Nur statische Methoden
    }

    public static void zeigen(String text) {
        if (text.isEmpty()) {
            return;
        }
        Notification.show(text, DAUER_MS, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }
}

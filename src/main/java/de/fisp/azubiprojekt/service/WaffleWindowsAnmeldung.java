package de.fisp.azubiprojekt.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import waffle.windows.auth.IWindowsAuthProvider;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.IWindowsSecurityContext;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

/**
 * Windows-Anmeldung ueber die Windows-Schnittstelle SSPI (Bibliothek Waffle). Funktioniert nur, wenn
 * der Server Mitglied der Domaene ist; der Dienst laeuft dafuer als "Lokales System" (Rechnerkonto).
 * Wird nur mit {@code SSO_ENABLED=true} angelegt.
 */
@Service
@ConditionalOnProperty(name = "app.sso.enabled", havingValue = "true")
public class WaffleWindowsAnmeldung implements WindowsAnmeldung {

    private volatile IWindowsAuthProvider provider;

    @Override
    public Schritt pruefe(String verbindung, byte[] token, String paket) {
        IWindowsSecurityContext kontext = provider().acceptSecurityToken(verbindung, token, paket);
        try {
            byte[] antwort = kontext.getToken();
            if (kontext.isContinue()) {
                return new Schritt(false, antwort, null);
            }
            IWindowsIdentity identitaet = kontext.getIdentity();
            try {
                return new Schritt(true, antwort, identitaet.isGuest() ? null : identitaet.getFqn());
            } finally {
                identitaet.dispose();
            }
        } finally {
            if (!kontext.isContinue()) {
                kontext.dispose();
            }
        }
    }

    @Override
    public void abbrechen(String verbindung) {
        provider().resetSecurityToken(verbindung);
    }

    /** Erst beim ersten Aufruf anlegen – laedt die Windows-Bibliotheken (JNA). */
    private IWindowsAuthProvider provider() {
        if (provider == null) {
            synchronized (this) {
                if (provider == null) {
                    provider = new WindowsAuthProviderImpl();
                }
            }
        }
        return provider;
    }
}

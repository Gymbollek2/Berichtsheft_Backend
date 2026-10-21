package de.fisp.azubiprojekt.ssl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Erzeugt fuer die lokale Entwicklung beim ersten Start ein selbstsigniertes TLS-Zertifikat fuer
 * {@code localhost}, damit das Backend ohne weitere Vorbereitung per HTTPS laeuft.
 *
 * <p>Verwendet das {@code keytool} des laufenden JDK – es wird keine zusaetzliche Software benoetigt.
 * Ist {@code SSL_KEYSTORE} gesetzt (eigenes Zertifikat, z. B. in Produktion), passiert nichts.</p>
 */
public final class DevCertificate {

    public static final String DEFAULT_PATH = "data/ssl/berichtsheft-dev.p12";
    public static final String DEFAULT_PASSWORD = "berichtsheft-dev";

    private DevCertificate() {
    }

    /** Legt den Entwicklungs-Keystore an, falls kein eigener konfiguriert ist und er noch fehlt. */
    public static void ensureDevKeystore() {
        if (System.getenv("SSL_KEYSTORE") != null || "false".equalsIgnoreCase(System.getenv("SSL_ENABLED"))) {
            return;
        }
        Path keystore = Path.of(DEFAULT_PATH).toAbsolutePath();
        if (Files.exists(keystore)) {
            return;
        }
        String password = System.getenv().getOrDefault("SSL_KEYSTORE_PASSWORD", DEFAULT_PASSWORD);
        try {
            Files.createDirectories(keystore.getParent());
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
            List<String> command = List.of(keytool.toString(),
                    "-genkeypair", "-noprompt",
                    "-alias", "berichtsheft",
                    "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "825",
                    "-dname", "CN=localhost, OU=Entwicklung, O=Azubi-Portal",
                    "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                    "-storetype", "PKCS12",
                    "-keystore", keystore.toString(),
                    "-storepass", password,
                    "-keypass", password);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool fehlgeschlagen: " + output);
            }
            System.out.println("[Berichtsheft] Selbstsigniertes Entwicklungszertifikat erzeugt: " + keystore);
        } catch (IOException e) {
            throw new IllegalStateException("Entwicklungszertifikat konnte nicht erzeugt werden: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Erzeugen des Entwicklungszertifikats abgebrochen", e);
        }
    }
}

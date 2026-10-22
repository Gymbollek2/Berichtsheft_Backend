package de.fisp.azubiprojekt.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Erzeugt beim ersten Start ein selbstsigniertes TLS-Zertifikat, damit das Backend ohne weitere
 * Vorbereitung per HTTPS laeuft – lokal fuer {@code localhost}, auf einem Server zusaetzlich fuer
 * dessen Rechnernamen und IP-Adressen.
 *
 * <p>Verwendet das {@code keytool} des laufenden Java – es wird keine zusaetzliche Software benoetigt.
 * Weitere Namen (z. B. {@code berichtsheft.firma.local}) per Umgebungsvariable {@code SSL_HOSTNAMES}
 * (kommagetrennt). Ist {@code SSL_KEYSTORE} gesetzt (eigenes Zertifikat, empfohlen fuer den Betrieb),
 * passiert nichts. Neue Namen wirken erst, wenn die Datei {@value #DEFAULT_PATH} geloescht wird.</p>
 */
public final class DevCertificate {

    public static final String DEFAULT_PATH = "data/ssl/berichtsheft-dev.p12";
    public static final String DEFAULT_PASSWORD = "berichtsheft-dev";

    private DevCertificate() {
    }

    /** Legt den Keystore an, falls kein eigener konfiguriert ist und er noch fehlt. */
    public static void ensureDevKeystore() {
        if (System.getenv("SSL_KEYSTORE") != null || "false".equalsIgnoreCase(System.getenv("SSL_ENABLED"))) {
            return;
        }
        Path keystore = Path.of(DEFAULT_PATH).toAbsolutePath();
        if (Files.exists(keystore)) {
            return;
        }
        String password = System.getenv().getOrDefault("SSL_KEYSTORE_PASSWORD", DEFAULT_PASSWORD);
        Set<String> namen = hostnames(System.getenv("SSL_HOSTNAMES"));
        List<String> ips = ipAdressen();
        try {
            Files.createDirectories(keystore.getParent());
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
            List<String> command = List.of(keytool.toString(),
                    "-genkeypair", "-noprompt",
                    "-alias", "berichtsheft",
                    "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "825",
                    "-dname", "CN=" + namen.iterator().next() + ", OU=Berichtsheft, O=Azubi-Portal",
                    "-ext", "SAN=" + san(namen, ips),
                    "-storetype", "PKCS12",
                    "-keystore", keystore.toString(),
                    "-storepass", password,
                    "-keypass", password);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool fehlgeschlagen: " + output);
            }
            System.out.println("[Berichtsheft] Selbstsigniertes Zertifikat erzeugt: " + keystore
                    + " (gueltig fuer " + String.join(", ", namen) + ", " + String.join(", ", ips) + ")");
        } catch (IOException e) {
            throw new IllegalStateException("Zertifikat konnte nicht erzeugt werden: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Erzeugen des Zertifikats abgebrochen", e);
        }
    }

    /** Konfigurierte Namen zuerst (erster = CN), dann Rechnername, voller Name und localhost. */
    static Set<String> hostnames(String konfiguriert) {
        Set<String> namen = new LinkedHashSet<>();
        if (konfiguriert != null) {
            for (String n : konfiguriert.split(",")) {
                if (!n.isBlank()) {
                    namen.add(n.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        try {
            InetAddress local = InetAddress.getLocalHost();
            namen.add(local.getHostName().toLowerCase(Locale.ROOT));
            namen.add(local.getCanonicalHostName().toLowerCase(Locale.ROOT));
        } catch (UnknownHostException e) {
            // ohne Rechnernamen weiter – localhost reicht fuer die Entwicklung
        }
        String computername = System.getenv("COMPUTERNAME");
        if (computername != null && !computername.isBlank()) {
            namen.add(computername.toLowerCase(Locale.ROOT));
        }
        namen.add("localhost");
        namen.removeIf(n -> n.isBlank() || n.matches("[0-9.:]+")); // IP-Adressen kommen getrennt
        return namen;
    }

    /** IPv4-Adressen dieses Rechners (inkl. 127.0.0.1), damit auch der Aufruf per IP passt. */
    static List<String> ipAdressen() {
        List<String> ips = new ArrayList<>();
        ips.add("127.0.0.1");
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.getAddress().length == 4 && !ips.contains(a.getHostAddress())) {
                        ips.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // nur 127.0.0.1
        }
        return ips;
    }

    static String san(Set<String> namen, List<String> ips) {
        List<String> teile = new ArrayList<>();
        namen.forEach(n -> teile.add("dns:" + n));
        ips.forEach(ip -> teile.add("ip:" + ip));
        return String.join(",", teile);
    }
}

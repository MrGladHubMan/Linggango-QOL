package com.linggango.easyparry.client;

import com.linggango.easyparry.EasyParryConfig;
import com.linggango.easyparry.EasyParryMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-updater for the Easy Parry jar.
 *
 * Flow:
 *  1. On client start (background thread) download a small JSON manifest over HTTPS:
 *       {"version": "1.1.1", "url": "https://.../linggango_easy_parry-1.1.1.jar", "sha256": "<hex>", "changelog": "..."}
 *  2. If the manifest version is newer, download the jar to <game>/easyparry-update/ (NOT the mods folder,
 *     so Forge never loads two copies) and verify its SHA-256. Jars without a matching hash are discarded.
 *  3. A running jar is locked on Windows, so the swap happens when the game closes: a JVM shutdown hook
 *     launches a tiny batch script that waits for the game to exit, deletes the old jar and moves the new
 *     one into mods/. On macOS/Linux the swap is done directly inside the shutdown hook.
 */
public final class EasyParryUpdater {
    /** Used when the config value is empty. publish_update.ps1 writes update.json to the `updates` branch. */
    public static final String DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/MrGladHubMan/Linggango-QOL/updates/update.json";

    private static final Logger LOGGER = LoggerFactory.getLogger("EasyParryUpdater");
    private static final long MAX_JAR_BYTES = 64L * 1024L * 1024L;
    private static final Pattern VERSION_RE = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_RE = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHA_RE = Pattern.compile("\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"");
    private static final Pattern CHANGELOG_RE = Pattern.compile("\"changelog\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static volatile String status = "Not checked yet";
    private static volatile String pendingVersion = null;
    private static volatile String pendingChangelog = null;
    private static volatile boolean noticeShown = false;
    private static volatile boolean hookInstalled = false;
    private static volatile boolean started = false;

    private EasyParryUpdater() {
    }

    public static String currentVersion() {
        try {
            return ModList.get().getModContainerById(EasyParryMod.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String statusText() {
        return status;
    }

    /** Starts the background check once per game session. Safe to call multiple times. */
    public static synchronized void startAsync() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(EasyParryUpdater::runCheckSafely, "EasyParry-Updater");
        t.setDaemon(true);
        t.start();
    }

    private static void runCheckSafely() {
        try {
            runCheck();
        } catch (Throwable t) {
            status = "Update check failed: " + t.getClass().getSimpleName();
            LOGGER.warn("Easy Parry update check failed", t);
        }
    }

    private static Path updateDir() {
        return FMLPaths.GAMEDIR.get().resolve("easyparry-update");
    }

    private static Path currentJar() {
        IModFileInfo info = ModList.get().getModFileById(EasyParryMod.MOD_ID);
        if (info == null) {
            return null;
        }
        Path p = info.getFile().getFilePath();
        if (p == null || !p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return null; // dev environment / not running from a jar
        }
        return p;
    }

    private static void runCheck() throws Exception {
        String current = currentVersion();
        Path dir = updateDir();
        Path marker = dir.resolve("pending.properties");

        // Clean up after a successful install, or resume a pending one
        if (Files.exists(marker)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(marker)) {
                props.load(in);
            }
            String ver = props.getProperty("version", "");
            Path jar = dir.resolve(props.getProperty("file", "missing.jar"));
            if (compareVersions(ver, current) <= 0 || !Files.exists(jar)) {
                deleteQuietly(marker);
                deleteQuietly(jar);
                deleteQuietly(dir.resolve("apply_update.bat"));
            } else {
                pendingVersion = ver;
                pendingChangelog = props.getProperty("changelog", "");
                status = "v" + ver + " downloaded - restart the game to install";
                installShutdownHook(jar);
                return;
            }
        }

        if (!EasyParryConfig.AUTO_UPDATE.get()) {
            status = "Auto-update is off";
            return;
        }

        String manifestUrl = EasyParryConfig.UPDATE_MANIFEST_URL.get();
        if (manifestUrl == null || manifestUrl.isBlank()) {
            manifestUrl = DEFAULT_MANIFEST_URL;
        }
        if (manifestUrl == null || manifestUrl.isBlank()) {
            status = "No update address configured";
            return;
        }
        if (!manifestUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            status = "Refusing non-HTTPS update address";
            return;
        }
        Path ownJar = currentJar();
        if (ownJar == null) {
            status = "Not running from a jar (dev build)";
            return;
        }

        status = "Checking...";
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder(URI.create(manifestUrl + (manifestUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis()))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "LinggangoEasyParry/" + current)
                .header("Cache-Control", "no-cache")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            status = "Update server returned HTTP " + resp.statusCode();
            return;
        }
        String body = resp.body();
        String latest = match(VERSION_RE, body);
        String jarUrl = match(URL_RE, body);
        String sha = match(SHA_RE, body);
        String changelog = match(CHANGELOG_RE, body);

        if (latest == null || jarUrl == null || sha == null) {
            status = "Update manifest is missing version/url/sha256";
            return;
        }
        if (compareVersions(latest, current) <= 0) {
            status = "Up to date (v" + current + ")";
            return;
        }
        if (!jarUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            status = "Refusing non-HTTPS download";
            return;
        }

        status = "Downloading v" + latest + "...";
        Files.createDirectories(dir);
        String fileName = "linggango_easy_parry-" + latest.replaceAll("[^0-9A-Za-z._-]", "_") + ".jar";
        Path part = dir.resolve(fileName + ".part");
        Path finalJar = dir.resolve(fileName);

        HttpRequest jarReq = HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "LinggangoEasyParry/" + current)
                .GET()
                .build();
        HttpResponse<InputStream> jarResp = http.send(jarReq, HttpResponse.BodyHandlers.ofInputStream());
        if (jarResp.statusCode() != 200) {
            status = "Download failed: HTTP " + jarResp.statusCode();
            return;
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0L;
        try (InputStream in = jarResp.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_JAR_BYTES) {
                    throw new IOException("Update jar is larger than " + MAX_JAR_BYTES + " bytes");
                }
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        }
        String got = HexFormat.of().formatHex(digest.digest());
        if (!got.equalsIgnoreCase(sha)) {
            deleteQuietly(part);
            status = "Downloaded file failed the SHA-256 check - discarded";
            LOGGER.warn("Easy Parry update hash mismatch: expected {} got {}", sha, got);
            return;
        }
        Files.move(part, finalJar, StandardCopyOption.REPLACE_EXISTING);

        Properties props = new Properties();
        props.setProperty("version", latest);
        props.setProperty("file", fileName);
        props.setProperty("changelog", changelog == null ? "" : unescape(changelog));
        try (OutputStream out = Files.newOutputStream(marker)) {
            props.store(out, "Easy Parry pending update");
        }

        pendingVersion = latest;
        pendingChangelog = props.getProperty("changelog");
        status = "v" + latest + " downloaded - restart the game to install";
        LOGGER.info("Easy Parry v{} downloaded, it will be installed when the game closes", latest);
        installShutdownHook(finalJar);
    }

    private static synchronized void installShutdownHook(Path newJar) {
        if (hookInstalled) {
            return;
        }
        Path oldJar = currentJar();
        if (oldJar == null) {
            return;
        }
        hookInstalled = true;
        Path modsDir = oldJar.getParent();
        Path dir = updateDir();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> applyOnExit(oldJar, newJar, modsDir, dir), "EasyParry-ApplyUpdate"));
    }

    private static void applyOnExit(Path oldJar, Path newJar, Path modsDir, Path dir) {
        Path target = modsDir.resolve(newJar.getFileName().toString());
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows) {
            try {
                Files.move(newJar, target, StandardCopyOption.REPLACE_EXISTING);
                if (!oldJar.equals(target)) {
                    Files.deleteIfExists(oldJar);
                }
                Files.deleteIfExists(dir.resolve("pending.properties"));
            } catch (Throwable ignored) {
            }
            return;
        }
        try {
            Path bat = dir.resolve("apply_update.bat");
            String script = "@echo off\r\n"
                    + "set \"OLD=" + oldJar + "\"\r\n"
                    + "set \"NEW=" + newJar + "\"\r\n"
                    + "set \"DEST=" + target + "\"\r\n"
                    + "set \"MARK=" + dir.resolve("pending.properties") + "\"\r\n"
                    + "set /a tries=0\r\n"
                    + ":wait\r\n"
                    + "ping -n 2 127.0.0.1 >nul\r\n"
                    + "del /f /q \"%OLD%\" >nul 2>&1\r\n"
                    + "if exist \"%OLD%\" (\r\n"
                    + "  set /a tries+=1\r\n"
                    + "  if %tries% GEQ 180 goto end\r\n"
                    + "  goto wait\r\n"
                    + ")\r\n"
                    + "move /y \"%NEW%\" \"%DEST%\" >nul\r\n"
                    + "if exist \"%DEST%\" del /f /q \"%MARK%\" >nul 2>&1\r\n"
                    + ":end\r\n"
                    + "(goto) 2>nul & del \"%~f0\"\r\n";
            Files.writeString(bat, script, StandardCharsets.UTF_8);
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", bat.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Throwable ignored) {
        }
    }

    /** Shows a one-time chat notice once an update is ready. Called every client tick. */
    public static void tickClientNotice(LocalPlayer player) {
        if (noticeShown || pendingVersion == null || player == null || player.f_19797_ < 100) {
            return;
        }
        noticeShown = true;
        player.m_5661_(Component.m_237113_("[Easy Parry] Update v" + pendingVersion + " is downloaded. Close the game to install it automatically.")
                .m_130940_(ChatFormatting.GREEN), false);
        if (pendingChangelog != null && !pendingChangelog.isBlank()) {
            player.m_5661_(Component.m_237113_("What's new: " + pendingChangelog).m_130940_(ChatFormatting.GRAY), false);
        }
    }

    // ------------------------------------------------------------------------------------------

    private static String match(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String unescape(String s) {
        return s.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Numeric, dot-separated comparison ("1.10.0" > "1.9.3"). Non-numeric suffixes are ignored. */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int x = i < pa.length ? leadingInt(pa[i]) : 0;
            int y = i < pb.length ? leadingInt(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int leadingInt(String s) {
        int v = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)); i++) {
            v = v * 10 + (s.charAt(i) - '0');
            if (v > 1_000_000) {
                break;
            }
        }
        return v;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Throwable ignored) {
        }
    }
}

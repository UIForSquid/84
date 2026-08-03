package mindmap84;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.swing.*;

/**
 * Self-updates the app from GitHub Releases.
 *
 * Flow: ask the GitHub API for the latest release, compare its tag to
 * {@link Config#VERSION}; if newer, download the release's ASSET_NAME zip,
 * extract it to a staging folder, then hand off to a small detached .bat that
 * waits for this process to quit, copies the new files over the install dir
 * (so it can replace files the running JVM has locked), and relaunches.
 */
final class Updater {

    static final class Release {
        final String version, tag, zipUrl, notes;
        Release(String version, String tag, String zipUrl, String notes) {
            this.version = version; this.tag = tag; this.zipUrl = zipUrl; this.notes = notes;
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Query GitHub for the latest release. Returns null if none/parse fails. */
    static Release fetchLatest() throws Exception {
        if (!Config.isConfigured())
            throw new IllegalStateException("GitHub repo not configured yet (see Config.java).");
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.latestReleaseApi()))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", Config.APP_NAME + "-Updater")
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404)
            throw new IOException("No published releases found yet.");
        if (resp.statusCode() / 100 != 2)
            throw new IOException("GitHub returned HTTP " + resp.statusCode());
        return parseReleaseJson(resp.body());
    }

    /** Extract a Release from a GitHub /releases/latest JSON body (testable, no network). */
    static Release parseReleaseJson(String body) {
        Map<String, Object> root = Json.asMap(Json.parse(body));
        if (root == null) return null;
        String tag = Json.asStr(root.get("tag_name"));
        if (tag == null) return null;
        String notes = Json.asStr(root.get("body"));

        String zipUrl = null;
        List<Object> assets = Json.asList(root.get("assets"));
        if (assets != null) {
            for (Object a : assets) {
                Map<String, Object> am = Json.asMap(a);
                if (am == null) continue;
                String name = Json.asStr(am.get("name"));
                String url = Json.asStr(am.get("browser_download_url"));
                if (url == null) continue;
                if (Config.ASSET_NAME.equalsIgnoreCase(name)) { zipUrl = url; break; }
                if (zipUrl == null && name != null && name.toLowerCase().endsWith(".zip")) zipUrl = url;
            }
        }
        return new Release(stripV(tag), tag, zipUrl, notes == null ? "" : notes);
    }

    // ---- version comparison ----
    static String stripV(String s) {
        s = s.trim();
        return s.startsWith("v") || s.startsWith("V") ? s.substring(1) : s;
    }
    /** true if `latest` is a higher version than `current` (dot-separated ints). */
    static boolean isNewer(String latest, String current) {
        int[] a = parseVer(latest), b = parseVer(current);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }
    private static int[] parseVer(String v) {
        v = stripV(v);
        String[] parts = v.split("[.\\-+]");
        List<Integer> nums = new ArrayList<>();
        for (String p : parts) {
            try { nums.add(Integer.parseInt(p.trim())); }
            catch (NumberFormatException e) { break; } // stop at first non-numeric (e.g. "beta")
        }
        int[] out = new int[nums.size()];
        for (int i = 0; i < out.length; i++) out[i] = nums.get(i);
        return out;
    }

    // ---- install location ----
    /** Best guess at the folder that holds MINDMAP84.exe. */
    static Path installDir() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path exe = Paths.get(cmd.get());
                String fn = exe.getFileName().toString().toLowerCase();
                if (fn.endsWith(".exe") && !fn.equals("java.exe") && !fn.equals("javaw.exe"))
                    return exe.getParent();
            }
        } catch (Exception ignored) {}
        try {
            // app.jar lives at <install>/app/app.jar
            Path jar = Paths.get(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.getParent() != null && jar.getParent().getParent() != null) return jar.getParent().getParent();
        } catch (Exception ignored) {}
        return Paths.get(".").toAbsolutePath().normalize();
    }

    // ---- download + extract ----
    static Path downloadZip(Release r, Path dest) throws Exception {
        if (r.zipUrl == null) throw new IOException("Release has no '" + Config.ASSET_NAME + "' asset.");
        HttpRequest req = HttpRequest.newBuilder(URI.create(r.zipUrl))
                .header("User-Agent", Config.APP_NAME + "-Updater")
                .GET().build();
        HttpResponse<Path> resp = client().send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (resp.statusCode() / 100 != 2) throw new IOException("Download failed: HTTP " + resp.statusCode());
        return dest;
    }

    /** Extract a zip and return the app-image root inside it (folder holding the exe). */
    static Path extract(Path zip, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(e.getName()).normalize();
                if (!out.startsWith(destDir)) throw new IOException("Bad zip entry: " + e.getName()); // zip-slip guard
                if (e.isDirectory()) { Files.createDirectories(out); }
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        // find the folder that contains the exe (usually destDir/MINDMAP84)
        Path exe = findExe(destDir);
        return exe != null ? exe.getParent() : destDir;
    }
    private static Path findExe(Path dir) throws IOException {
        try (java.util.stream.Stream<Path> s = Files.walk(dir, 3)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase(Config.EXE_NAME)).findFirst().orElse(null);
        }
    }

    /**
     * Writes a detached .bat that (after this JVM exits) copies stagedAppDir over
     * installDir and relaunches the app, then starts it and quits.
     */
    static void applyAndRelaunch(Path stagedAppDir, Path installDir) throws IOException {
        Path bat = Files.createTempFile("mindmap84-update", ".bat");
        String script = "@echo off\r\n"
                + "chcp 65001 >nul\r\n"
                + "timeout /t 2 /nobreak >nul\r\n"
                + "robocopy \"" + stagedAppDir + "\" \"" + installDir + "\" /E /IS /IT /R:3 /W:1 >nul\r\n"
                + "start \"\" \"" + installDir.resolve(Config.EXE_NAME) + "\"\r\n"
                + "rmdir /s /q \"" + stagedAppDir.getParent() + "\" >nul 2>&1\r\n"
                + "del \"%~f0\"\r\n";
        Files.write(bat, script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // launch detached in its own minimized window so it survives our exit
        new ProcessBuilder("cmd", "/c", "start", "", "/min", bat.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        System.exit(0);
    }

    // ===================== UI flows =====================

    /** Interactive "Check for Updates": background check, then prompt. */
    static void checkInteractive(Component parent) {
        new SwingWorker<Release, Void>() {
            protected Release doInBackground() throws Exception { return fetchLatest(); }
            protected void done() {
                try {
                    Release r = get();
                    if (r == null || !isNewer(r.version, Config.VERSION)) {
                        JOptionPane.showMessageDialog(parent,
                                "You're up to date  (v" + Config.VERSION + ").",
                                Config.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    String msg = "A new version is available.\n\n"
                            + "Installed:  v" + Config.VERSION + "\n"
                            + "Latest:     v" + r.version + "\n\n"
                            + (r.notes.isEmpty() ? "" : trim(r.notes, 400) + "\n\n")
                            + "Download and install now? The app will restart.";
                    if (JOptionPane.showConfirmDialog(parent, msg, "Update available",
                            JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                        downloadAndApply(parent, r);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent,
                            "Couldn't check for updates:\n" + rootMsg(e),
                            "Update", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    /** Download + extract with a modal progress dialog, then relaunch. */
    static void downloadAndApply(Component parent, Release r) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent), "Updating…", Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar prog = new JProgressBar();
        prog.setIndeterminate(true);
        JLabel lbl = new JLabel("Downloading v" + r.version + " …");
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        p.add(lbl, BorderLayout.NORTH);
        p.add(prog, BorderLayout.CENTER);
        dlg.setContentPane(p);
        dlg.setSize(360, 120);
        dlg.setLocationRelativeTo(parent);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                Path work = Files.createTempDirectory("mindmap84-update");
                Path zip = work.resolve(Config.ASSET_NAME);
                downloadZip(r, zip);
                Path stagedApp = extract(zip, work.resolve("unpacked"));
                applyAndRelaunch(stagedApp, installDir()); // calls System.exit on success
                return null;
            }
            protected void done() {
                dlg.dispose();
                try { get(); }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(parent, "Update failed:\n" + rootMsg(e),
                            "Update", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        dlg.setVisible(true);
    }

    /** Headless update for `--update` (prints result). Returns true if it kicked off an install. */
    static boolean updateHeadless() {
        try {
            Release r = fetchLatest();
            if (r == null || !isNewer(r.version, Config.VERSION)) {
                System.out.println("Up to date (v" + Config.VERSION + ").");
                return false;
            }
            System.out.println("Updating v" + Config.VERSION + " -> v" + r.version + " …");
            Path work = Files.createTempDirectory("mindmap84-update");
            Path zip = work.resolve(Config.ASSET_NAME);
            downloadZip(r, zip);
            Path stagedApp = extract(zip, work.resolve("unpacked"));
            applyAndRelaunch(stagedApp, installDir());
            return true;
        } catch (Exception e) {
            System.out.println("Update failed: " + rootMsg(e));
            return false;
        }
    }

    private static String rootMsg(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return (m == null ? t.getClass().getSimpleName() : m);
    }
    private static String trim(String s, int max) {
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private Updater() {}
}

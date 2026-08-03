package mindmap84;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * File persistence. Saves live in a per-user folder so they survive no matter
 * where the app folder is moved or unzipped:  ~/.mindmap84/*.json
 */
final class Store {
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".mindmap84");

    static { try { Files.createDirectories(DIR); } catch (IOException ignored) {} }

    static Path dir() { return DIR; }

    static boolean write(String name, String content) {
        try {
            Path tmp = DIR.resolve(name + ".tmp");
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, DIR.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("save failed: " + e);
            return false;
        }
    }

    /** Returns file contents, or null if the file doesn't exist / can't be read. */
    static String read(String name) {
        try {
            Path p = DIR.resolve(name);
            if (!Files.exists(p)) return null;
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private Store() {}
}

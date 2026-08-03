package mindmap84;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * A small Swing install wizard. Copies the current portable app-image into a
 * folder of the user's choosing and (optionally) drops a desktop shortcut, then
 * offers to launch and/or check GitHub for updates.
 *
 * Run it from the app-image via  Installer.exe  (added by jpackage --add-launcher)
 * or  MINDMAP84.exe --install.
 */
final class Installer {

    private final JFrame frame = new JFrame(Config.APP_NAME + " - Setup");
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    private final JTextField locField = new JTextField();
    private final JCheckBox shortcutBox = new JCheckBox("Create a desktop shortcut", true);
    private final JCheckBox launchBox = new JCheckBox("Launch " + Config.APP_NAME + " when done", true);
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea log = new JTextArea(8, 40);
    private final JButton back = new JButton("Back");
    private final JButton next = new JButton("Next");

    private Path installedExe;   // set once installed

    static void launch() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Installer().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(560, 420);
        frame.setLocationRelativeTo(null);

        locField.setText(defaultInstallDir().toString());

        deck.add(welcomeCard(), "welcome");
        deck.add(locationCard(), "location");
        deck.add(progressCard(), "progress");
        deck.add(finishCard(), "finish");

        JPanel nav = new JPanel(new BorderLayout());
        nav.setBorder(new EmptyBorder(8, 12, 10, 12));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.add(back); btns.add(next);
        nav.add(btns, BorderLayout.EAST);

        back.addActionListener(e -> step(-1));
        next.addActionListener(e -> onNext());

        frame.setLayout(new BorderLayout());
        frame.add(deck, BorderLayout.CENTER);
        frame.add(nav, BorderLayout.SOUTH);
        page = 0;
        showPage();
        frame.setVisible(true);
    }

    private int page = 0;
    private final String[] order = { "welcome", "location", "progress", "finish" };

    private void showPage() {
        cards.show(deck, order[page]);
        back.setEnabled(page > 0 && page != 2 && page != 3);
        next.setText(page == order.length - 1 ? "Finish" : (page == 1 ? "Install" : "Next"));
        next.setEnabled(page != 2); // disabled while installing
    }
    private void step(int d) { page = Math.max(0, Math.min(order.length - 1, page + d)); showPage(); }

    private void onNext() {
        switch (page) {
            case 0 -> { page = 1; showPage(); }
            case 1 -> doInstall();                 // "Install"
            case 3 -> finish();                    // "Finish"
            default -> {}
        }
    }

    // ---------- cards ----------
    private JPanel welcomeCard() {
        JPanel p = pad();
        JLabel h = new JLabel(Config.APP_NAME + "//84 Setup");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 20f));
        JTextArea t = readonlyText(
            "This will install " + Config.APP_NAME + " v" + Config.VERSION + " on your computer.\n\n"
          + "Your saved maps and lists are kept separately in your user profile\n"
          + "(" + System.getProperty("user.home") + "\\.mindmap84), so installing or\n"
          + "updating never touches your data.\n\n"
          + "Click Next to choose where to install.");
        p.add(h, BorderLayout.NORTH);
        p.add(t, BorderLayout.CENTER);
        return p;
    }

    private JPanel locationCard() {
        JPanel p = pad();
        JLabel h = new JLabel("Install location");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 16f));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(locField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
                locField.setText(fc.getSelectedFile().toPath().resolve(Config.APP_NAME).toString());
        });
        row.add(locField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        shortcutBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        launchBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(row);
        body.add(Box.createVerticalStrut(12));
        body.add(shortcutBox);
        body.add(launchBox);

        p.add(h, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel progressCard() {
        JPanel p = pad();
        JLabel h = new JLabel("Installing...");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 16f));
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(log);
        JPanel c = new JPanel(new BorderLayout(0, 10));
        c.add(progress, BorderLayout.NORTH);
        c.add(sp, BorderLayout.CENTER);
        p.add(h, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private final JLabel finishMsg = new JLabel();
    private JPanel finishCard() {
        JPanel p = pad();
        JLabel h = new JLabel("Done");
        h.setFont(h.getFont().deriveFont(Font.BOLD, 18f));
        p.add(h, BorderLayout.NORTH);
        p.add(finishMsg, BorderLayout.CENTER);
        return p;
    }

    // ---------- install ----------
    private void doInstall() {
        final Path target = Paths.get(locField.getText().trim());
        final boolean makeShortcut = shortcutBox.isSelected();
        page = 2; showPage();
        progress.setIndeterminate(true);
        log.setText("");

        new SwingWorker<Path, String>() {
            protected Path doInBackground() throws Exception {
                Path source = Updater.installDir().toAbsolutePath().normalize();
                Path tgt = target.toAbsolutePath().normalize();
                publish("Source:  " + source);
                publish("Target:  " + tgt);
                if (source.equals(tgt))
                    throw new IOException("Source and target are the same folder.");
                if (tgt.startsWith(source))
                    throw new IOException("Pick a folder outside the current app folder.");
                Files.createDirectories(tgt);
                publish("Copying files...");
                copyTree(source, tgt, this::publish);
                Path exe = tgt.resolve(Config.EXE_NAME);
                if (makeShortcut) { publish("Creating desktop shortcut..."); makeDesktopShortcut(exe); }
                publish("Done.");
                return exe;
            }
            protected void process(java.util.List<String> chunks) { for (String s : chunks) log.append(s + "\n"); }
            protected void done() {
                progress.setIndeterminate(false);
                progress.setValue(100);
                try {
                    installedExe = get();
                    finishMsg.setText("<html>" + Config.APP_NAME + " v" + Config.VERSION
                        + " was installed to:<br><b>" + installedExe.getParent() + "</b></html>");
                    page = 3; showPage();
                } catch (Exception e) {
                    log.append("\nFAILED: " + rootMsg(e) + "\n");
                    next.setEnabled(true); next.setText("Retry");
                    next.addActionListener(ev -> { page = 1; showPage(); });
                    back.setEnabled(true);
                }
            }
        }.execute();
    }

    private void finish() {
        try {
            if (launchBox.isSelected() && installedExe != null && Files.exists(installedExe))
                new ProcessBuilder(installedExe.toString()).start();
        } catch (IOException ignored) {}
        frame.dispose();
        System.exit(0);
    }

    // ---------- helpers ----------
    interface Log { void log(String s); }

    private static void copyTree(Path src, Path dst, Log log) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(src)) {
            java.util.List<Path> all = walk.toList();
            for (Path p : all) {
                Path rel = src.relativize(p);
                Path out = dst.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void makeDesktopShortcut(Path exe) {
        try {
            Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
            Path lnk = desktop.resolve(Config.APP_NAME + ".lnk");
            String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + lnk + "');"
                    + "$s.TargetPath='" + exe + "';"
                    + "$s.WorkingDirectory='" + exe.getParent() + "';"
                    + "$s.Save()";
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
        } catch (Exception ignored) { /* shortcut is best-effort */ }
    }

    private static Path defaultInstallDir() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = (local != null && !local.isEmpty())
                ? Paths.get(local) : Paths.get(System.getProperty("user.home"));
        return base.resolve("Programs").resolve(Config.APP_NAME);
    }

    private static JPanel pad() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(new EmptyBorder(20, 22, 16, 22));
        return p;
    }
    private static JTextArea readonlyText(String s) {
        JTextArea t = new JTextArea(s);
        t.setEditable(false);
        t.setOpaque(false);
        t.setLineWrap(true); t.setWrapStyleWord(true);
        t.setBorder(null);
        return t;
    }
    private static String rootMsg(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private Installer() {}
}

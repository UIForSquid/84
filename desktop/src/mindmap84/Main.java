package mindmap84;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public final class Main {

    public static void main(String[] args) {
        for (String a : args) {
            if (a.equals("--selftest")) { System.exit(selfTest() ? 0 : 1); return; }
            if (a.equals("--guitest"))  { System.exit(guiTest() ? 0 : 1); return; }
            if (a.equals("--install"))  { Installer.launch(); return; }
            if (a.equals("--update"))   { Updater.updateHeadless(); System.exit(0); return; }
            if (a.equals("--render"))   {
                try {
                    final String path = args.length > 1 ? args[1] : "mindmap-preview.png";
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            MindMapPanel mp = new MindMapPanel();
                            JFrame fr = new JFrame();
                            fr.setContentPane(mp); fr.setSize(1240, 820); fr.addNotify(); fr.validate();
                            mp.setSize(1240, 780); mp.doLayout();
                            if (args.length > 2 && args[2].equals("edit")) mp.debugStartNameEdit();
                            if (args.length > 2 && args[2].equals("overlay")) mp.debugSelectFirst();
                            if (args.length > 2 && args[2].equals("wiki")) mp.debugOpenFirstWiki();
                            if (args.length > 2 && args[2].equals("sample")) mp.debugSampleWiki();
                            mp.invalidate(); mp.validate();   // full recursive layout before paint
                            java.awt.image.BufferedImage img =
                                new java.awt.image.BufferedImage(1240, 780, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = img.createGraphics(); mp.paint(g); g.dispose();
                            javax.imageio.ImageIO.write(img, "png", new java.io.File(path));
                            fr.dispose();
                            System.out.println("wrote " + path);
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                } catch (Exception e) { e.printStackTrace(); }
                System.exit(0);
                return;
            }
            if (a.equals("--nettest"))  {
                try {
                    java.net.http.HttpClient.newHttpClient();
                    javax.net.ssl.SSLContext.getDefault();
                    System.out.println("nettest ok");
                    System.exit(0);
                } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
                return;
            }
        }
        SwingUtilities.invokeLater(Main::buildUI);
    }

    private static JFrame frame;

    private static MindMapPanel mind;
    private static ChecklistPanel checklist;
    private static DatalistPanel datalist;
    private static final Map<String, JButton> navButtons = new LinkedHashMap<>();

    private static void buildUI() {
        try { UIManager.put("ToolTip.background", Theme.PANEL); } catch (Exception ignored) {}

        JFrame f = new JFrame("MINDMAP//84  v" + Config.VERSION);
        frame = f;
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(1240, 820);
        f.setMinimumSize(new Dimension(900, 600));
        f.setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(Theme.VOID);

        CardLayout cards = new CardLayout();
        JPanel content = new JPanel(cards);
        content.setBackground(Theme.VOID);

        mind = new MindMapPanel();
        checklist = new ChecklistPanel();
        datalist = new DatalistPanel();
        content.add(mind, "mind");
        content.add(checklist, "checklist");
        content.add(datalist, "datalist");

        rootPanel.add(buildNav(cards, content), BorderLayout.NORTH);
        rootPanel.add(content, BorderLayout.CENTER);
        f.setContentPane(rootPanel);

        // flush pending saves on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            mind.flush(); checklist.flush(); datalist.flush();
        }));

        f.setVisible(true);
        setActive("mind");
    }

    private static JComponent buildNav(CardLayout cards, JPanel content) {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(new Color(0x0a0616));
        nav.setBorder(new MatteBorder(0, 0, 1, 0, Theme.LINE));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        left.setOpaque(false);
        JLabel brand = new JLabel("//84");
        brand.setFont(Theme.HEAD);
        brand.setForeground(Theme.PURPLE);
        brand.setBorder(new EmptyBorder(0, 6, 0, 8));
        left.add(brand);
        left.add(navButton("Mindmap",   "mind",      cards, content));
        left.add(navButton("Checklist", "checklist", cards, content));
        left.add(navButton("Datalist",  "datalist",  cards, content));
        nav.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        right.setOpaque(false);
        JLabel ver = new JLabel("v" + Config.VERSION);
        ver.setFont(Theme.MONO_SM);
        ver.setForeground(Theme.MUTED);
        JButton upd = Theme.button("Check for Updates", Theme.CYAN, false);
        upd.addActionListener(e -> Updater.checkInteractive(frame));
        right.add(ver);
        right.add(upd);
        nav.add(right, BorderLayout.EAST);
        return nav;
    }

    private static JButton navButton(String label, String key, CardLayout cards, JPanel content) {
        JButton b = new JButton(label);
        b.setFont(Theme.HEAD);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new CompoundBorder(new LineBorder(Theme.LINE, 1, true), new EmptyBorder(7, 18, 7, 18)));
        b.addActionListener(e -> { cards.show(content, key); setActive(key); });
        navButtons.put(key, b);
        return b;
    }

    private static void setActive(String key) {
        for (Map.Entry<String, JButton> e : navButtons.entrySet()) {
            boolean on = e.getKey().equals(key);
            JButton b = e.getValue();
            b.setBackground(on ? Theme.MAGENTA : new Color(0x140a2c));
            b.setForeground(on ? Color.WHITE : Theme.MUTED);
            b.setBorder(new CompoundBorder(new LineBorder(on ? Theme.MAGENTA : Theme.LINE, 1, true), new EmptyBorder(7, 18, 7, 18)));
        }
    }

    // ---- offscreen GUI test: build + paint all three panels without showing a window ----
    private static boolean guiTest() {
        try {
            final boolean[] ok = {true};
            SwingUtilities.invokeAndWait(() -> {
                try {
                    MindMapPanel mp = new MindMapPanel();
                    paintOffscreen(mp);          // map view, nothing selected
                    mp.debugSelectFirst();       // -> bottom notes overlay
                    paintOffscreen(mp);          // map view with overlay
                    mp.debugOpenFirstWiki();     // -> full wiki page
                    paintOffscreen(mp);          // wiki view
                    paintOffscreen(new ChecklistPanel());
                    paintOffscreen(new DatalistPanel());
                    System.out.println("guitest: painted map + overlay + wiki + checklist + datalist");
                } catch (Throwable t) {
                    ok[0] = false;
                    t.printStackTrace();
                }
            });
            System.out.println("guitest: " + (ok[0] ? "PASS" : "FAIL"));
            return ok[0];
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void paintOffscreen(JComponent panel) {
        JFrame fr = new JFrame();
        fr.setContentPane(panel);
        fr.setSize(1240, 820);
        fr.addNotify();          // realize peers without showing a window
        fr.validate();
        panel.setSize(1240, 780);
        panel.doLayout();
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(1240, 780, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        panel.paint(g);          // exercises paintComponent + child components
        g.dispose();
        fr.dispose();
    }

    // ---- headless self-test of core logic (no GUI) ----
    private static boolean selfTest() {
        boolean ok = true;
        // JSON round trip
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 1);
        m.put("s", "hello \"world\"\nline2");
        m.put("n", -3.5);
        m.put("b", true);
        m.put("nil", null);
        List<Object> arr = new ArrayList<>();
        arr.add("a"); arr.add(2.0); arr.add(false);
        m.put("arr", arr);
        String json = Json.write(m);
        Object back = Json.parse(json);
        Map<String, Object> bm = Json.asMap(back);
        ok &= bm != null;
        ok &= "hello \"world\"\nline2".equals(Json.asStr(bm.get("s")));
        ok &= Json.asNum(bm.get("n"), 0) == -3.5;
        ok &= Json.asBool(bm.get("b"));
        ok &= bm.get("nil") == null;
        ok &= Json.asList(bm.get("arr")).size() == 3;

        // header parsing
        ok &= "Pokemon".equals(Parse.headerName("=== Pokemon ==="));
        ok &= "DC".equals(Parse.headerName("| DC |"));
        ok &= "".equals(Parse.headerName("======"));
        ok &= Parse.headerName("just a line") == null;
        ok &= "banana".equals(Parse.clean("  and banana "));

        // nested JSON with unicode escape
        Object u = Json.parse("{\"k\":\"\\u0041\\tB\"}");
        ok &= "A\tB".equals(Json.asStr(Json.asMap(u).get("k")));

        // updater version comparison
        ok &= Updater.isNewer("1.1.0", "1.0.0");
        ok &= Updater.isNewer("v2.0.0", "1.9.9");
        ok &= Updater.isNewer("1.0.10", "1.0.2");
        ok &= !Updater.isNewer("1.0.0", "1.0.0");
        ok &= !Updater.isNewer("1.0.0", "1.2.0");
        ok &= "1.0.0".equals(Updater.stripV("v1.0.0"));

        // parse a GitHub release payload -> version + zip asset url
        String sample = "{\"tag_name\":\"v1.2.0\",\"name\":\"r\",\"body\":\"notes\","
            + "\"assets\":[{\"name\":\"other.txt\",\"browser_download_url\":\"https://x/other.txt\"},"
            + "{\"name\":\"MINDMAP84-windows.zip\",\"browser_download_url\":\"https://x/app.zip\"}]}";
        Updater.Release rel = Updater.parseReleaseJson(sample);
        ok &= rel != null && "1.2.0".equals(rel.version) && "https://x/app.zip".equals(rel.zipUrl);

        System.out.println("selftest: " + (ok ? "PASS" : "FAIL"));
        System.out.println("data dir: " + Store.dir());
        return ok;
    }

    private Main() {}
}

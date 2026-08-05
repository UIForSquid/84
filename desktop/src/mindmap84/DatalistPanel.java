package mindmap84;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

/** Ordered, numbered lists with optional descriptions + reordering. */
final class DatalistPanel extends JPanel {

    static final class Item { String text; String desc; Item(String t, String d){text=t;desc=d;} }
    static final class Group { String name; boolean collapsed; List<Item> items = new ArrayList<>(); Group(String n){name=n;} }

    private final List<Group> groups = new ArrayList<>();
    private final JTextArea input = new JTextArea(5, 20);
    private final JPanel body = new JPanel();
    private final JLabel count = new JLabel("0 items");
    private final JLabel status = new JLabel(" ");
    private Timer saveTimer;

    DatalistPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.VOID);
        setBorder(new EmptyBorder(16, 24, 16, 24));
        add(buildTop(), BorderLayout.NORTH);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.VOID);
        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(new LineBorder(Theme.LINE));
        sp.getViewport().setBackground(Theme.VOID);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);

        load();
        render();
    }

    private JComponent buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(Theme.VOID);

        JLabel title = new JLabel("DATALIST//84");
        title.setFont(Theme.HEAD_BIG); title.setForeground(Theme.CYAN); title.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        JLabel sub = new JLabel("Insert Data  //  Archive");
        sub.setFont(Theme.MONO_SM); sub.setForeground(Theme.MUTED); sub.setAlignmentX(LEFT_ALIGNMENT);
        sub.setBorder(new EmptyBorder(2, 0, 12, 0));
        top.add(sub);

        input.setFont(Theme.MONO); input.setForeground(Theme.LIME); input.setBackground(new Color(0x060212));
        input.setCaretColor(Theme.LIME); input.setBorder(new EmptyBorder(8, 8, 8, 8));
        input.setLineWrap(true); input.setWrapStyleWord(true);
        JScrollPane inScroll = new JScrollPane(input);
        inScroll.setPreferredSize(new Dimension(100, 110));
        inScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        inScroll.setAlignmentX(LEFT_ALIGNMENT);
        inScroll.setBorder(new LineBorder(Theme.LINE));
        top.add(inScroll);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        row.setBackground(Theme.VOID); row.setAlignmentX(LEFT_ALIGNMENT);
        JButton gen = Theme.button("Generate", Theme.MAGENTA, true);
        JButton add = Theme.button("+ Add", Theme.CYAN, false);
        JButton purge = Theme.button("Purge", Theme.CYAN, false);
        gen.addActionListener(e -> { groups.clear(); groups.addAll(parse(input.getText())); render(); });
        add.addActionListener(e -> { mergeIn(parse(input.getText())); input.setText(""); render(); });
        purge.addActionListener(e -> {
            int n = total();
            if (n > 0 && JOptionPane.showConfirmDialog(this, "Purge " + n + " items? Your saved copy stays until you save again.",
                    "Purge", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            groups.clear(); input.setText(""); render(); say("List purged", Theme.AMBER);
        });
        row.add(gen); row.add(add); row.add(purge);
        top.add(row);

        JLabel hint = new JLabel("<html>Same headers as checklists. Add a description after a colon - e.g. Meta Knight : dodge the tornado. Items keep their order and are numbered.</html>");
        hint.setFont(Theme.MONO_SM); hint.setForeground(Theme.MUTED); hint.setAlignmentX(LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(hint);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        meta.setBackground(Theme.VOID); meta.setAlignmentX(LEFT_ALIGNMENT);
        count.setFont(Theme.HEAD); count.setForeground(Theme.AMBER);
        meta.add(count);
        JButton exp = Theme.button("Export MD", Theme.CYAN, false);
        JButton imp = Theme.button("Import MD", Theme.CYAN, false);
        JButton expand = Theme.button("Expand", Theme.CYAN, false);
        JButton collapse = Theme.button("Collapse", Theme.CYAN, false);
        exp.addActionListener(e -> exportMd());
        imp.addActionListener(e -> importMd());
        expand.addActionListener(e -> { for (Group g : groups) g.collapsed = false; render(); });
        collapse.addActionListener(e -> { for (Group g : groups) g.collapsed = true; render(); });
        meta.add(exp); meta.add(imp); meta.add(expand); meta.add(collapse);
        top.add(meta);

        status.setFont(Theme.MONO_SM); status.setForeground(Theme.MUTED); status.setAlignmentX(LEFT_ALIGNMENT);
        status.setBorder(new EmptyBorder(6, 0, 8, 0));
        top.add(status);
        return top;
    }

    private int total() { int n = 0; for (Group g : groups) n += g.items.size(); return n; }
    private void say(String m, Color c) { status.setText(m); status.setForeground(c); }

    private Item splitItem(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int ci = s.indexOf(':');
        if (ci == -1) { String t = Parse.clean(s); return t.isEmpty() ? null : new Item(t, ""); }
        String text = Parse.clean(s.substring(0, ci));
        String desc = s.substring(ci + 1).trim();
        if (text.isEmpty()) return null;
        return new Item(text, desc);
    }

    private List<Group> parse(String text) {
        List<Group> out = new ArrayList<>();
        Group cur = null;
        boolean pendingDivider = false, justTitled = false;
        for (String raw : text.split("\r?\n", -1)) {
            String h = Parse.headerName(raw);
            if (h != null) {
                if (h.isEmpty()) { if (justTitled) justTitled = false; else pendingDivider = true; }
                else { cur = new Group(h); out.add(cur); pendingDivider = false; justTitled = true; }
                continue;
            }
            if (pendingDivider && !raw.trim().isEmpty()) {
                cur = new Group(raw.trim()); out.add(cur); pendingDivider = false; justTitled = true; continue;
            }
            justTitled = false;
            if (raw.trim().isEmpty()) continue;
            if (cur == null) { cur = new Group("Ungrouped"); out.add(cur); }
            if (raw.contains(":")) {
                Item it = splitItem(raw);
                if (it != null) cur.items.add(it);
            } else {
                for (String p : raw.split(",")) { String c = Parse.clean(p); if (!c.isEmpty()) cur.items.add(new Item(c, "")); }
            }
        }
        out.removeIf(g -> g.items.isEmpty());
        return out;
    }

    private void mergeIn(List<Group> incoming) {
        for (Group ng : incoming) {
            Group ex = null;
            for (Group g : groups) if (g.name.equalsIgnoreCase(ng.name)) { ex = g; break; }
            if (ex != null) ex.items.addAll(ng.items); else groups.add(ng);
        }
    }

    private void render() {
        body.removeAll();
        if (groups.isEmpty()) {
            JLabel empty = new JLabel("No Data Loaded");
            empty.setForeground(Theme.MUTED); empty.setFont(Theme.MONO);
            empty.setBorder(new EmptyBorder(22, 12, 22, 12)); empty.setAlignmentX(LEFT_ALIGNMENT);
            body.add(empty);
        }
        for (final Group g : groups) {
            JPanel gp = new JPanel();
            gp.setLayout(new BoxLayout(gp, BoxLayout.Y_AXIS));
            gp.setBackground(new Color(0x0c051e));
            gp.setBorder(new LineBorder(Theme.LINE));
            gp.setAlignmentX(LEFT_ALIGNMENT);

            JPanel head = new JPanel(new BorderLayout());
            head.setBackground(new Color(0x1a0d38));
            head.setBorder(new EmptyBorder(9, 12, 9, 12));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel name = new JLabel((g.collapsed ? "[+] " : "[-] ") + g.name.toUpperCase());
            name.setFont(Theme.HEAD); name.setForeground(Color.WHITE);
            JLabel gcount = new JLabel(g.items.size() + (g.items.size() == 1 ? " item" : " items"));
            gcount.setFont(Theme.MONO_SM); gcount.setForeground(Theme.CYAN);
            head.add(name, BorderLayout.WEST); head.add(gcount, BorderLayout.EAST);
            head.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) { g.collapsed = !g.collapsed; render(); }
            });
            gp.add(head);

            if (!g.collapsed) {
                for (int ii = 0; ii < g.items.size(); ii++) {
                    final Item it = g.items.get(ii);
                    final int idx = ii;
                    JPanel rowP = new JPanel(new BorderLayout(8, 0));
                    rowP.setBackground(new Color(0x0c051e));
                    rowP.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, new Color(0x241a4a)), new EmptyBorder(6, 12, 6, 12)));

                    JLabel num = new JLabel(String.valueOf(ii + 1));
                    num.setFont(Theme.HEAD); num.setForeground(Theme.AMBER);
                    num.setBorder(new EmptyBorder(0, 0, 0, 8));
                    rowP.add(num, BorderLayout.WEST);

                    JPanel textCol = new JPanel();
                    textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
                    textCol.setBackground(new Color(0x0c051e));
                    JLabel t = new JLabel(it.text);
                    t.setFont(Theme.MONO); t.setForeground(Color.WHITE); t.setAlignmentX(LEFT_ALIGNMENT);
                    textCol.add(t);
                    if (it.desc != null && !it.desc.isEmpty()) {
                        JLabel d = new JLabel("<html><div style='width:360px'>" + escapeHtml(it.desc) + "</div></html>");
                        d.setFont(Theme.MONO_SM); d.setForeground(Theme.MUTED); d.setAlignmentX(LEFT_ALIGNMENT);
                        d.setBorder(new EmptyBorder(3, 0, 0, 0));
                        textCol.add(d);
                    }
                    rowP.add(textCol, BorderLayout.CENTER);

                    JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
                    ctrl.setBackground(new Color(0x0c051e));
                    JButton up = Theme.button("Up", Theme.CYAN, false);
                    JButton dn = Theme.button("Dn", Theme.CYAN, false);
                    JButton del = Theme.button("X", Theme.MAGENTA, false);
                    up.setMargin(new Insets(0, 5, 0, 5)); dn.setMargin(new Insets(0, 5, 0, 5)); del.setMargin(new Insets(0, 6, 0, 6));
                    up.setEnabled(idx > 0); dn.setEnabled(idx < g.items.size() - 1);
                    up.addActionListener(e -> { Collections.swap(g.items, idx, idx - 1); render(); scheduleSave(); });
                    dn.addActionListener(e -> { Collections.swap(g.items, idx, idx + 1); render(); scheduleSave(); });
                    del.addActionListener(e -> { g.items.remove(idx); render(); scheduleSave(); });
                    ctrl.add(up); ctrl.add(dn); ctrl.add(del);
                    rowP.add(ctrl, BorderLayout.EAST);

                    gp.add(rowP);
                }
            }
            body.add(gp);
            body.add(Box.createVerticalStrut(10));
        }
        body.add(Box.createVerticalGlue());
        int t = total();
        count.setText(t + (t == 1 ? " item" : " items"));
        body.revalidate(); body.repaint();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- markdown export / import ----
    private void exportMd() {
        java.io.File f = Md.chooseSave(this, "datalist");
        if (f == null) return;
        boolean ok = Md.writeFile(f, Md.writeDatalist(groups));
        say(ok ? "Exported " + f.getName() : "Export failed", ok ? Theme.LIME : Theme.MAGENTA);
    }
    private void importMd() {
        java.io.File f = Md.chooseOpen(this);
        if (f == null) return;
        String text = Md.readFile(f);
        List<Group> parsed = text == null ? null : Md.parseDatalist(text);
        if (parsed == null) {
            JOptionPane.showMessageDialog(this, "Could not read that file as a datalist.",
                    "Import MD", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int r = JOptionPane.showOptionDialog(this,
                "Importing will replace your current datalist (" + total() + " items).",
                "Import MD", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[] { "Import", "Cancel" }, "Cancel");
        if (r != 0) return;
        groups.clear();
        groups.addAll(parsed);
        render();
        scheduleSave();
        say("Imported " + total() + " items", Theme.LIME);
    }

    // ---- persistence ----
    private void scheduleSave() {
        if (saveTimer == null) { saveTimer = new Timer(500, e -> saveNow(false)); saveTimer.setRepeats(false); }
        saveTimer.restart();
    }
    void flush() { saveNow(false); }
    private void saveNow(boolean loud) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", 1);
        List<Object> gs = new ArrayList<>();
        for (Group g : groups) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("name", g.name);
            List<Object> items = new ArrayList<>();
            for (Item it : g.items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("text", it.text); im.put("desc", it.desc == null ? "" : it.desc);
                items.add(im);
            }
            gm.put("items", items);
            gs.add(gm);
        }
        root.put("groups", gs);
        boolean ok = Store.write("datalist.json", Json.write(root));
        if (loud) say(ok ? "Saved " + total() + " items" : "Save failed", ok ? Theme.LIME : Theme.MAGENTA);
    }
    private void load() {
        String s = Store.read("datalist.json");
        if (s == null) return;
        try {
            Map<String, Object> root = Json.asMap(Json.parse(s));
            if (root == null) return;
            List<Object> gs = Json.asList(root.get("groups"));
            if (gs == null) return;
            groups.clear();
            for (Object go : gs) {
                Map<String, Object> gm = Json.asMap(go);
                if (gm == null) continue;
                Group g = new Group(Json.asStr(gm.get("name")));
                List<Object> items = Json.asList(gm.get("items"));
                if (items != null) for (Object io : items) {
                    Map<String, Object> im = Json.asMap(io);
                    if (im == null) continue;
                    String txt = Json.asStr(im.get("text"));
                    String desc = Json.asStr(im.get("desc"));
                    g.items.add(new Item(txt == null ? "" : txt, desc == null ? "" : desc));
                }
                groups.add(g);
            }
        } catch (Exception e) { System.err.println("datalist load failed: " + e); }
    }
}

package mindmap84;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

/** Grouped checklists with checkboxes + progress. */
final class ChecklistPanel extends JPanel {

    static final class Item { String text; boolean done; Item(String t){text=t;} }
    static final class Group { String name; boolean collapsed; List<Item> items = new ArrayList<>(); Group(String n){name=n;} }

    private final List<Group> groups = new ArrayList<>();
    private boolean hideDone = false;

    private final JTextArea input = new JTextArea(5, 20);
    private final JPanel body = new JPanel();
    private final JLabel count = new JLabel("0 of 0 complete");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel status = new JLabel(" ");
    private final JButton hideBtn = Theme.button("Hide Done", Theme.CYAN, false);
    private Timer saveTimer;

    ChecklistPanel() {
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

        JLabel title = new JLabel("CHECKLIST//84");
        title.setFont(Theme.HEAD_BIG);
        title.setForeground(Theme.CYAN);
        title.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        JLabel sub = new JLabel("Insert Data  //  Execute");
        sub.setFont(Theme.MONO_SM);
        sub.setForeground(Theme.MUTED);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        sub.setBorder(new EmptyBorder(2, 0, 12, 0));
        top.add(sub);

        input.setFont(Theme.MONO);
        input.setForeground(Theme.LIME);
        input.setBackground(new Color(0x060212));
        input.setCaretColor(Theme.LIME);
        input.setBorder(new EmptyBorder(8, 8, 8, 8));
        input.setLineWrap(true); input.setWrapStyleWord(true);
        JScrollPane inScroll = new JScrollPane(input);
        inScroll.setPreferredSize(new Dimension(100, 110));
        inScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        inScroll.setAlignmentX(LEFT_ALIGNMENT);
        inScroll.setBorder(new LineBorder(Theme.LINE));
        top.add(inScroll);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        row.setBackground(Theme.VOID);
        row.setAlignmentX(LEFT_ALIGNMENT);
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

        JLabel hint = new JLabel("<html>A line is a group header if it starts/ends with = _ | ~ - e.g. === Fruit ===. Items are comma-separated.</html>");
        hint.setFont(Theme.MONO_SM);
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(hint);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        meta.setBackground(Theme.VOID);
        meta.setAlignmentX(LEFT_ALIGNMENT);
        count.setFont(Theme.HEAD);
        count.setForeground(Theme.AMBER);
        meta.add(count);
        JButton exp = Theme.button("Export MD", Theme.CYAN, false);
        JButton imp = Theme.button("Import MD", Theme.CYAN, false);
        JButton expand = Theme.button("Expand", Theme.CYAN, false);
        JButton collapse = Theme.button("Collapse", Theme.CYAN, false);
        JButton reset = Theme.button("Reset", Theme.CYAN, false);
        exp.addActionListener(e -> exportMd());
        imp.addActionListener(e -> importMd());
        expand.addActionListener(e -> { for (Group g : groups) g.collapsed = false; render(); });
        collapse.addActionListener(e -> { for (Group g : groups) g.collapsed = true; render(); });
        reset.addActionListener(e -> { for (Group g : groups) for (Item it : g.items) it.done = false; render(); scheduleSave(); });
        hideBtn.addActionListener(e -> { hideDone = !hideDone; hideBtn.setText(hideDone ? "Show Done" : "Hide Done"); render(); });
        meta.add(exp); meta.add(imp); meta.add(hideBtn); meta.add(expand); meta.add(collapse); meta.add(reset);
        top.add(meta);

        bar.setForeground(Theme.MAGENTA);
        bar.setBackground(new Color(0x060212));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(100, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setAlignmentX(LEFT_ALIGNMENT);
        top.add(bar);

        status.setFont(Theme.MONO_SM);
        status.setForeground(Theme.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setBorder(new EmptyBorder(6, 0, 8, 0));
        top.add(status);

        return top;
    }

    private int total() { int n = 0; for (Group g : groups) n += g.items.size(); return n; }
    private int doneTotal() { int n = 0; for (Group g : groups) for (Item it : g.items) if (it.done) n++; return n; }

    private void say(String m, Color c) { status.setText(m); status.setForeground(c); }

    private List<Group> parse(String text) {
        List<Group> out = new ArrayList<>();
        Group cur = null;
        boolean pendingDivider = false, justTitled = false;
        for (String raw : text.split("\r?\n", -1)) {
            String h = Parse.headerName(raw);
            if (h != null) {
                if (h.isEmpty()) {
                    if (justTitled) justTitled = false; else pendingDivider = true;
                } else {
                    cur = new Group(h); out.add(cur); pendingDivider = false; justTitled = true;
                }
                continue;
            }
            if (pendingDivider && !raw.trim().isEmpty()) {
                cur = new Group(raw.trim()); out.add(cur); pendingDivider = false; justTitled = true; continue;
            }
            justTitled = false;
            List<String> parts = new ArrayList<>();
            for (String p : raw.split(",")) { String c = Parse.clean(p); if (!c.isEmpty()) parts.add(c); }
            if (parts.isEmpty()) continue;
            if (cur == null) { cur = new Group("Ungrouped"); out.add(cur); }
            for (String p : parts) cur.items.add(new Item(p));
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
            empty.setForeground(Theme.MUTED);
            empty.setFont(Theme.MONO);
            empty.setBorder(new EmptyBorder(22, 12, 22, 12));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            body.add(empty);
        }
        for (int gi = 0; gi < groups.size(); gi++) {
            final Group g = groups.get(gi);
            int done = 0; for (Item it : g.items) if (it.done) done++;
            boolean complete = !g.items.isEmpty() && done == g.items.size();

            JPanel gp = new JPanel();
            gp.setLayout(new BoxLayout(gp, BoxLayout.Y_AXIS));
            gp.setBackground(new Color(0x0c051e));
            gp.setBorder(new LineBorder(Theme.LINE));
            gp.setAlignmentX(LEFT_ALIGNMENT);
            gp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            JPanel head = new JPanel(new BorderLayout());
            head.setBackground(new Color(0x1a0d38));
            head.setBorder(new EmptyBorder(9, 12, 9, 12));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel name = new JLabel((g.collapsed ? "[+] " : "[-] ") + g.name.toUpperCase());
            name.setFont(Theme.HEAD);
            name.setForeground(Color.WHITE);
            JLabel gcount = new JLabel(done + " / " + g.items.size());
            gcount.setFont(Theme.MONO_SM);
            gcount.setForeground(complete ? Theme.LIME : Theme.CYAN);
            head.add(name, BorderLayout.WEST);
            head.add(gcount, BorderLayout.EAST);
            head.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) { g.collapsed = !g.collapsed; render(); }
            });
            gp.add(head);

            if (!g.collapsed) {
                int shown = 0;
                for (int ii = 0; ii < g.items.size(); ii++) {
                    final Item it = g.items.get(ii);
                    if (hideDone && it.done) continue;
                    shown++;
                    JPanel rowP = new JPanel(new BorderLayout(8, 0));
                    rowP.setBackground(new Color(0x0c051e));
                    rowP.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, new Color(0x241a4a)), new EmptyBorder(4, 12, 4, 12)));
                    rowP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                    JCheckBox cb = new JCheckBox(it.text, it.done);
                    cb.setBackground(new Color(0x0c051e));
                    cb.setForeground(it.done ? new Color(0x5f5290) : Theme.INK);
                    cb.setFont(Theme.MONO);
                    cb.addActionListener(e -> { it.done = cb.isSelected(); render(); scheduleSave(); });
                    JButton del = Theme.button("X", Theme.MAGENTA, false);
                    del.setMargin(new Insets(0, 6, 0, 6));
                    final int idx = ii;
                    del.addActionListener(e -> { g.items.remove(idx); render(); scheduleSave(); });
                    rowP.add(cb, BorderLayout.CENTER);
                    rowP.add(del, BorderLayout.EAST);
                    gp.add(rowP);
                }
                if (shown == 0 && !g.items.isEmpty()) {
                    JLabel all = new JLabel("ALL " + g.items.size() + " CLEARED");
                    all.setForeground(Theme.LIME);
                    all.setFont(Theme.MONO_SM);
                    all.setBorder(new EmptyBorder(6, 12, 6, 12));
                    gp.add(all);
                }
            }
            body.add(gp);
            body.add(Box.createVerticalStrut(10));
        }
        body.add(Box.createVerticalGlue());
        int t = total(), d = doneTotal();
        count.setText(d + " of " + t + " complete");
        bar.setValue(t > 0 ? (int) Math.round(d * 100.0 / t) : 0);
        body.revalidate();
        body.repaint();
    }

    // ---- markdown export / import ----
    private void exportMd() {
        java.io.File f = Md.chooseSave(this, "checklist");
        if (f == null) return;
        boolean ok = Md.writeFile(f, Md.writeChecklist(groups));
        say(ok ? "Exported " + f.getName() : "Export failed", ok ? Theme.LIME : Theme.MAGENTA);
    }
    private void importMd() {
        java.io.File f = Md.chooseOpen(this);
        if (f == null) return;
        String text = Md.readFile(f);
        List<Group> parsed = text == null ? null : Md.parseChecklist(text);
        if (parsed == null) {
            JOptionPane.showMessageDialog(this, "Could not read that file as a checklist.",
                    "Import MD", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int r = JOptionPane.showOptionDialog(this,
                "Importing will replace your current checklist (" + total() + " items).",
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
        root.put("hideDone", hideDone);
        List<Object> gs = new ArrayList<>();
        for (Group g : groups) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("name", g.name);
            List<Object> items = new ArrayList<>();
            for (Item it : g.items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("text", it.text); im.put("done", it.done);
                items.add(im);
            }
            gm.put("items", items);
            gs.add(gm);
        }
        root.put("groups", gs);
        boolean ok = Store.write("checklist.json", Json.write(root));
        if (loud) say(ok ? "Saved " + total() + " items" : "Save failed", ok ? Theme.LIME : Theme.MAGENTA);
    }
    private void load() {
        String s = Store.read("checklist.json");
        if (s == null) return;
        try {
            Map<String, Object> root = Json.asMap(Json.parse(s));
            if (root == null) return;
            hideDone = Json.asBool(root.get("hideDone"));
            hideBtn.setText(hideDone ? "Show Done" : "Hide Done");
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
                    Item it = new Item(Json.asStr(im.get("text")));
                    it.done = Json.asBool(im.get("done"));
                    g.items.add(it);
                }
                groups.add(g);
            }
        } catch (Exception e) { System.err.println("checklist load failed: " + e); }
    }
}

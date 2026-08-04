package mindmap84;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

/** The mind map: link-scaled nodes, drag, zoom/pan, ambient wobble, notes. */
final class MindMapPanel extends JPanel {

    // ---- model ----
    static final class Node {
        int id; String label; String color; double x, y; Integer parent; String notes = "";
    }

    private final List<Node> nodes = new ArrayList<>();
    private Integer selected = null;
    private int seq = 1;

    private static final double BASE_R = 11, MAX_R = 26, STEP = 0.20;
    private static final String[] PALETTE = {
        "#00f0ff", "#ff2d95", "#b537f2", "#ffb627", "#39ff14", "#ff6b6b", "#4d9fff"
    };

    // ---- view (pan/zoom) ----
    private double viewX = 0, viewY = 0, scale = 1;
    private static final double MIN_SCALE = 0.2, MAX_SCALE = 3;

    // ---- wobble ----
    private final Map<Integer, double[]> wob = new HashMap<>(); // id -> {phase, phase2, amp, sp}
    private double animTime = 0;
    private Integer hovered = null;
    private Integer draggingId = null;

    private final Canvas canvas = new Canvas();
    private final JPanel treeList = new JPanel();

    // inspector widgets
    private final JTextField fLabel = new JTextField();
    private final JTextField fHex = new JTextField();
    private final JButton bSwatch = new JButton();
    private final JLabel lMeta = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JPanel inspector;

    // the CENTRE swaps between "map" (canvas) and "wiki"; the left sidebar always stays
    private final CardLayout viewCards = new CardLayout();
    private final JPanel centerCards = new JPanel();
    private JLayeredPane centerLayered;   // holds the canvas + floating notes overlay
    private JPanel overlayPanel;          // bottom-centre notes overlay (read-only + Edit)
    private NotesView overlayNotes;       // notes editor inside the overlay
    private JPanel wikiContent;           // wiki page centre column, rebuilt per node
    private JPanel wikiLinked;            // right-hand Linked Nodes column, rebuilt per node
    private Integer wikiNodeId = null;    // node currently shown on the wiki page
    private boolean wikiEditing = false;  // wiki notes: read (rendered) vs edit (raw markdown)
    private final JTextArea wikiArea = new JTextArea();   // raw markdown editor for the wiki page
    private boolean centeredOnce = false;

    private Timer saveTimer;

    MindMapPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.VOID);

        // left sidebar is always visible; only the centre swaps map <-> wiki
        add(buildSidebar(), BorderLayout.WEST);

        JPanel mapCard = new JPanel(new BorderLayout());
        mapCard.setBackground(Theme.VOID);
        mapCard.add(buildCenter(), BorderLayout.CENTER);
        inspector = buildInspector();
        mapCard.add(inspector, BorderLayout.EAST);

        centerCards.setLayout(viewCards);
        centerCards.setBackground(Theme.VOID);
        centerCards.add(mapCard, "map");
        centerCards.add(buildWiki(), "wiki");
        add(centerCards, BorderLayout.CENTER);
        viewCards.show(centerCards, "map");

        // ambient wobble ~30fps
        Timer anim = new Timer(33, e -> { animTime += 0.033; canvas.repaint(); });
        anim.start();

        load();
        if (nodes.isEmpty()) {
            Node root = addNode(null);
            root.label = "Central Idea";
            selectNode(root.id);
        }
        refreshInspector();
        rebuildTree();
    }

    // =================== center: canvas + floating notes overlay (#5) ===================
    private JComponent buildCenter() {
        // Layered pane whose own doLayout keeps the canvas full-size and the
        // notes overlay pinned to the bottom-centre — runs on every validate.
        JLayeredPane lp = new JLayeredPane() {
            @Override public void doLayout() {
                int w = getWidth(), h = getHeight();
                canvas.setBounds(0, 0, w, h);
                if (overlayPanel != null) {
                    int ow = Math.min(600, (int) (w * 0.62));
                    int oh = 180;
                    overlayPanel.setBounds((w - ow) / 2, h - oh - 18, ow, oh);
                }
                if (!centeredOnce && !nodes.isEmpty()) { centeredOnce = true; centerOn(nodes.get(0)); }
            }
        };
        lp.add(canvas, JLayeredPane.DEFAULT_LAYER);
        overlayPanel = buildOverlay();
        overlayPanel.setVisible(false);
        lp.add(overlayPanel, JLayeredPane.PALETTE_LAYER);
        centerLayered = lp;
        return lp;
    }

    private JPanel buildOverlay() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x0c051e));
        p.setBorder(new LineBorder(Theme.MAGENTA, 1));
        overlayNotes = new NotesView("NOTES");
        p.add(overlayNotes, BorderLayout.CENTER);
        return p;
    }

    // =================== wiki page (#6) ===================
    private JPanel buildWiki() {
        JPanel v = new JPanel(new BorderLayout());
        v.setBackground(Theme.VOID);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(new Color(0x0a0616));
        bar.setBorder(new MatteBorder(0, 0, 1, 0, Theme.LINE));
        JButton back = Theme.button("Back to Map", Theme.CYAN, false);
        back.addActionListener(e -> viewCards.show(centerCards, "map"));
        bar.add(back);
        v.add(bar, BorderLayout.NORTH);

        // centre column: fixed header (title/parent/Edit) on top, notes filling the rest
        wikiContent = new JPanel(new BorderLayout());
        wikiContent.setBackground(Theme.VOID);
        wikiContent.setBorder(new EmptyBorder(26, 46, 26, 46));
        v.add(wikiContent, BorderLayout.CENTER);

        // raw-markdown editor used by wiki edit mode (configured once)
        wikiArea.setLineWrap(true);
        wikiArea.setWrapStyleWord(true);
        wikiArea.setFont(Theme.MONO);
        wikiArea.setCaretColor(Theme.LIME);
        wikiArea.setForeground(Theme.LIME);
        wikiArea.setBackground(new Color(0x060212));
        wikiArea.setBorder(new CompoundBorder(new LineBorder(Theme.MAGENTA, 1), new EmptyBorder(8, 12, 8, 12)));
        wikiArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "listEnter");
        wikiArea.getActionMap().put("listEnter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { mdHandleEnter(wikiArea); }
        });

        // right-hand Linked Nodes column, mirroring the left sidebar
        JPanel col = new JPanel(new BorderLayout());
        col.setPreferredSize(new Dimension(240, 10));
        col.setBackground(Theme.PANEL);
        col.setBorder(new MatteBorder(0, 1, 0, 0, Theme.LINE));
        wikiLinked = new JPanel();
        wikiLinked.setLayout(new BoxLayout(wikiLinked, BoxLayout.Y_AXIS));
        wikiLinked.setBackground(Theme.PANEL);
        wikiLinked.setBorder(new EmptyBorder(26, 14, 14, 14));
        JScrollPane lsp = new JScrollPane(wikiLinked);
        lsp.setBorder(null);
        lsp.getViewport().setBackground(Theme.PANEL);
        lsp.getVerticalScrollBar().setUnitIncrement(16);
        col.add(lsp, BorderLayout.CENTER);
        v.add(col, BorderLayout.EAST);
        return v;
    }

    private void openWiki(int id) {
        Node n = byId(id); if (n == null) return;
        selected = id;
        wikiNodeId = id;
        wikiEditing = false;
        refreshInspector();
        rebuildTree();
        if (overlayPanel != null) { overlayNotes.show(id); overlayPanel.setVisible(true); }
        buildWikiContent(n);
        buildWikiLinked(n);
        viewCards.show(centerCards, "wiki");
    }

    private void wikiToggleEdit() {
        Node n = byId(wikiNodeId); if (n == null) return;
        if (!wikiEditing) {
            wikiEditing = true;
            wikiArea.setText(n.notes == null ? "" : n.notes);
            buildWikiContent(n);
            wikiArea.setCaretPosition(wikiArea.getDocument().getLength());
            wikiArea.requestFocusInWindow();
        } else {
            wikiEditing = false;
            n.notes = wikiArea.getText();
            onNotesChanged(n.id);
            buildWikiContent(n);
        }
    }

    private void buildWikiContent(Node n) {
        wikiContent.removeAll();

        // fixed header: title (+ small Edit control at the right edge), parent line
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        JPanel tl = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        tl.setOpaque(false);
        JLabel dot = new JLabel(dotIcon(Theme.hex(n.color)));
        JLabel title = new JLabel(n.label.isEmpty() ? "(untitled)" : n.label);
        title.setFont(Theme.HEAD_BIG.deriveFont(28f));
        title.setForeground(Color.WHITE);
        tl.add(dot); tl.add(title);
        titleRow.add(tl, BorderLayout.WEST);
        JButton edit = Theme.button(wikiEditing ? "Done" : "Edit", Theme.CYAN, false);
        edit.setFont(Theme.MONO_SM);
        edit.addActionListener(e -> wikiToggleEdit());
        JPanel er = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        er.setOpaque(false);
        er.add(edit);
        titleRow.add(er, BorderLayout.EAST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(6));

        if (n.parent != null) {
            Node p = byId(n.parent);
            if (p != null) {
                JPanel bc = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                bc.setOpaque(false); bc.setAlignmentX(LEFT_ALIGNMENT);
                JLabel up = new JLabel("parent:");
                up.setForeground(Theme.MUTED); up.setFont(Theme.MONO_SM);
                bc.add(up); bc.add(wikiLink(p));
                head.add(bc);
            }
        }
        head.add(Box.createVerticalStrut(12));
        wikiContent.add(head, BorderLayout.NORTH);

        if (!wikiEditing) {
            // read mode: unboxed rendered markdown flowing on the page background
            JEditorPane view = new JEditorPane();
            view.setEditable(false);
            view.setContentType("text/html");
            view.setOpaque(false);
            view.setText(mdRenderHtml(n.notes));
            view.setCaretPosition(0);
            view.setBorder(new EmptyBorder(2, 2, 8, 2));
            JScrollPane vsp = new JScrollPane(view);
            vsp.setBorder(null);
            vsp.setOpaque(false);
            vsp.getViewport().setOpaque(false);
            vsp.getVerticalScrollBar().setUnitIncrement(16);
            wikiContent.add(vsp, BorderLayout.CENTER);
        } else {
            // edit mode: list-button toolbar over the raw markdown text box
            JPanel editWrap = new JPanel(new BorderLayout(0, 6));
            editWrap.setOpaque(false);
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            bar.setBackground(new Color(0x120829));
            bar.setBorder(new EmptyBorder(3, 8, 3, 8));
            bar.add(mdButton("# Heading", () -> mdInsertLinePrefix(wikiArea, "# ")));
            bar.add(mdButton("Bold", () -> mdWrapBold(wikiArea)));
            bar.add(mdButton("- List", () -> mdInsertMarker(wikiArea, "- ")));
            bar.add(mdButton("1. List", () -> mdInsertMarker(wikiArea, "1. ")));
            bar.add(mdButton("[ ] Task", () -> mdInsertMarker(wikiArea, "[ ] ")));
            editWrap.add(bar, BorderLayout.NORTH);
            JScrollPane esp = new JScrollPane(wikiArea);
            esp.setBorder(null);
            esp.getViewport().setBackground(new Color(0x060212));
            esp.getVerticalScrollBar().setUnitIncrement(16);
            editWrap.add(esp, BorderLayout.CENTER);
            wikiContent.add(editWrap, BorderLayout.CENTER);
        }

        wikiContent.revalidate();
        wikiContent.repaint();
    }

    /** Rebuilds the right-hand Linked Nodes column for the given node. */
    private void buildWikiLinked(Node n) {
        wikiLinked.removeAll();
        List<Node> kids = childrenOf(n.id);
        JLabel h = new JLabel("LINKED NODES (" + kids.size() + ")");
        h.setFont(Theme.HEAD); h.setForeground(Theme.CYAN); h.setAlignmentX(LEFT_ALIGNMENT);
        h.setBorder(new EmptyBorder(0, 0, 8, 0));
        wikiLinked.add(h);
        if (kids.isEmpty()) {
            JLabel none = new JLabel("no child nodes");
            none.setForeground(Theme.MUTED); none.setFont(Theme.MONO_SM); none.setAlignmentX(LEFT_ALIGNMENT);
            wikiLinked.add(none);
        } else {
            for (Node c : kids) {
                JComponent link = wikiLink(c);
                link.setAlignmentX(LEFT_ALIGNMENT);
                wikiLinked.add(link);
                wikiLinked.add(Box.createVerticalStrut(5));
            }
        }
        wikiLinked.add(Box.createVerticalGlue());
        wikiLinked.revalidate();
        wikiLinked.repaint();
    }

    private JButton mdButton(String text, Runnable action) {
        JButton b = Theme.button(text, Theme.PURPLE, false);
        b.setFont(Theme.MONO_SM);
        b.addActionListener(e -> action.run());
        return b;
    }


    private JComponent wikiLink(final Node n) {
        final JLabel l = new JLabel(n.label.isEmpty() ? "(untitled)" : n.label,
                                    dotIcon(Theme.hex(n.color)), SwingConstants.LEFT);
        l.setIconTextGap(8);
        l.setForeground(Theme.INK);
        l.setFont(Theme.MONO);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { openWiki(n.id); }
            public void mouseEntered(MouseEvent e) { l.setForeground(Theme.CYAN); }
            public void mouseExited(MouseEvent e) { l.setForeground(Theme.INK); }
        });
        return l;
    }

    private void onNotesChanged(int id) {
        scheduleSave();
        canvas.repaint();
        if (overlayNotes != null) overlayNotes.refreshIfShowing(id);
        // keep the wiki read view in sync if it is showing this node
        if (!wikiEditing && wikiNodeId != null && wikiNodeId == id) {
            Node n = byId(id);
            if (n != null) buildWikiContent(n);
        }
    }

    // =================== shared markdown helpers (notes overlay + wiki page) ===================
    private static final java.util.regex.Pattern MD_NUM = java.util.regex.Pattern.compile("^(\\d+)\\. ");

    private static int mdLineStart(String text, int caret) {
        int i = text.lastIndexOf('\n', Math.max(0, caret - 1));
        return i < 0 ? 0 : i + 1;
    }
    private static String mdMarkerOf(String line) {
        if (line.startsWith("- ")) return "- ";
        if (line.length() >= 4 && line.charAt(0) == '[' && line.charAt(2) == ']' && line.charAt(3) == ' ')
            return line.substring(0, 4);            // "[ ] " or "[x] "
        java.util.regex.Matcher m = MD_NUM.matcher(line);
        if (m.find()) return m.group();             // "3. "
        return null;
    }
    private static String mdNextMarker(String marker) {
        if (marker.startsWith("-")) return "- ";
        if (marker.startsWith("[")) return "[ ] ";
        java.util.regex.Matcher m = MD_NUM.matcher(marker);
        if (m.find()) return (Integer.parseInt(m.group(1)) + 1) + ". ";
        return "";
    }
    private static boolean mdSameKind(String a, String b) {
        return (a.startsWith("-") && b.startsWith("-"))
            || (a.startsWith("[") && b.startsWith("["))
            || (Character.isDigit(a.charAt(0)) && Character.isDigit(b.charAt(0)));
    }
    /** Toggle/replace a list marker on the current line of the given editor. */
    private static void mdInsertMarker(JTextArea area, String marker) {
        String text = area.getText();
        int caret = area.getCaretPosition();
        int ls = mdLineStart(text, caret);
        int le = text.indexOf('\n', caret); if (le < 0) le = text.length();
        String existing = mdMarkerOf(text.substring(ls, le));
        try {
            if (existing != null) area.getDocument().remove(ls, existing.length());
            if (existing == null || !mdSameKind(existing, marker))
                area.getDocument().insertString(ls, marker, null);
        } catch (javax.swing.text.BadLocationException ex) { /* ignore */ }
        area.requestFocusInWindow();
    }
    /** Enter inside a list continues it; empty item ends it; otherwise plain newline. */
    private static void mdHandleEnter(JTextArea area) {
        if (!area.isEditable()) return;
        String text = area.getText();
        int caret = area.getCaretPosition();
        int ls = mdLineStart(text, caret);
        int le = text.indexOf('\n', caret); if (le < 0) le = text.length();
        String marker = mdMarkerOf(text.substring(ls, le));
        if (marker == null) { area.replaceSelection("\n"); return; }
        if (text.substring(ls, le).substring(marker.length()).trim().isEmpty()) {
            try { area.getDocument().remove(ls, marker.length()); }
            catch (javax.swing.text.BadLocationException ex) { }
            return;
        }
        area.replaceSelection("\n" + mdNextMarker(marker));
    }
    private static void mdInsertLinePrefix(JTextArea area, String prefix) {
        int ls = mdLineStart(area.getText(), area.getCaretPosition());
        try { area.getDocument().insertString(ls, prefix, null); } catch (javax.swing.text.BadLocationException ex) {}
        area.requestFocusInWindow();
    }
    private static void mdWrapBold(JTextArea area) {
        String sel = area.getSelectedText();
        if (sel != null && !sel.isEmpty()) area.replaceSelection("**" + sel + "**");
        else { int pos = area.getCaretPosition(); area.insert("****", pos); area.setCaretPosition(pos + 2); }
        area.requestFocusInWindow();
    }
    /** Renders our light markdown (headings, bold, lists) to styled HTML. */
    private static String mdRenderHtml(String md) {
        String fam = Theme.MONO.getFamily();
        StringBuilder b = new StringBuilder();
        b.append("<html><head><style>")
         .append("body{font-family:'").append(fam).append("',monospace;color:#d8d2ea;font-size:13px;margin:0;padding:0;}")
         .append("h1{color:#00f0ff;font-size:19px;margin:12px 0 6px 0;}")
         .append("h2{color:#00f0ff;font-size:16px;margin:10px 0 5px 0;}")
         .append("h3{color:#b537f2;font-size:14px;margin:9px 0 4px 0;}")
         .append("b{color:#ffffff;}")
         .append(".li{margin:1px 0 1px 16px;}")
         .append("</style></head><body>");
        if (md == null || md.isEmpty()) {
            b.append("<div style=\"color:#7a7290;\">No notes yet - press Edit to add.</div>");
        } else {
            for (String line : md.split("\n", -1)) {
                if (line.startsWith("### "))     b.append("<h3>").append(mdInline(mdEsc(line.substring(4)))).append("</h3>");
                else if (line.startsWith("## ")) b.append("<h2>").append(mdInline(mdEsc(line.substring(3)))).append("</h2>");
                else if (line.startsWith("# "))  b.append("<h1>").append(mdInline(mdEsc(line.substring(2)))).append("</h1>");
                else if (mdMarkerOf(line) != null) b.append("<div class=\"li\">").append(mdInline(mdEsc(line))).append("</div>");
                else if (line.isEmpty())         b.append("<div>&nbsp;</div>");
                else                             b.append("<div>").append(mdInline(mdEsc(line))).append("</div>");
            }
        }
        b.append("</body></html>");
        return b.toString();
    }
    private static String mdEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static String mdInline(String s) {
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
    }

    /** Reusable notes display: read-only text with an Edit/Done toggle button. */
    private final class NotesView extends JPanel {
        private Integer nodeId;
        private boolean editing;
        private final Color DOC_BG = new Color(0x0c051e);    // == panel bg -> no visible box
        private final Color EDIT_BG = new Color(0x060212);   // darker box while editing
        private final JTextArea area = new JTextArea();          // raw markdown (edit mode)
        private final JEditorPane view = new JEditorPane();      // rendered markdown (read mode)
        private final CardLayout bodyCards = new CardLayout();
        private final JPanel body = new JPanel();
        private final JButton editBtn = Theme.button("Edit", Theme.CYAN, false);
        private final JLabel head = new JLabel();
        private final JPanel listBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        private final String baseTitle;

        NotesView(String baseTitle) {
            this.baseTitle = baseTitle;
            setLayout(new BorderLayout());
            setBackground(new Color(0x0c051e));

            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(new Color(0x1a0d38));
            bar.setBorder(new EmptyBorder(6, 10, 6, 6));
            head.setFont(Theme.HEAD); head.setForeground(Theme.CYAN);
            bar.add(head, BorderLayout.WEST);
            editBtn.addActionListener(e -> toggle());
            bar.add(editBtn, BorderLayout.EAST);

            // list toolbar (only visible while editing)
            listBar.setBackground(new Color(0x120829));
            listBar.setBorder(new EmptyBorder(3, 8, 3, 8));
            listBar.add(actionButton("# Heading", () -> { if (editing) mdInsertLinePrefix(area, "# "); }));
            listBar.add(actionButton("Bold", () -> { if (editing) mdWrapBold(area); }));
            listBar.add(listButton("- List", "- "));
            listBar.add(listButton("1. List", "1. "));
            listBar.add(listButton("[ ] Task", "[ ] "));
            listBar.setVisible(false);

            JPanel north = new JPanel(new BorderLayout());
            north.setOpaque(false);
            north.add(bar, BorderLayout.NORTH);
            north.add(listBar, BorderLayout.SOUTH);
            add(north, BorderLayout.NORTH);

            area.setLineWrap(true); area.setWrapStyleWord(true);
            area.setFont(Theme.MONO); area.setCaretColor(Theme.LIME);
            area.setEditable(false);
            // Enter: a plain line break (or continues a list) — never commits
            area.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "listEnter");
            area.getActionMap().put("listEnter", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { if (editing) mdHandleEnter(area); }
            });
            JScrollPane editScroll = new JScrollPane(area);
            editScroll.setBorder(null);
            editScroll.setOpaque(false);
            editScroll.getViewport().setBackground(EDIT_BG);

            view.setEditable(false);
            view.setContentType("text/html");
            view.setOpaque(true);
            view.setBackground(DOC_BG);
            view.setBorder(new EmptyBorder(12, 16, 12, 16));
            JScrollPane readScroll = new JScrollPane(view);
            readScroll.setBorder(null);
            readScroll.setOpaque(false);
            readScroll.getViewport().setBackground(DOC_BG);

            body.setOpaque(false);
            body.setLayout(bodyCards);
            body.add(readScroll, "read");
            body.add(editScroll, "edit");
            add(body, BorderLayout.CENTER);
            applyStyle();
        }

        /** The edit box styling (the read view is rendered HTML, styled via CSS). */
        private void applyStyle() {
            area.setBackground(EDIT_BG);
            area.setForeground(Theme.LIME);
            area.setBorder(new CompoundBorder(new LineBorder(Theme.MAGENTA, 1), new EmptyBorder(8, 12, 8, 12)));
        }

        private JButton listButton(String text, String marker) {
            JButton b = Theme.button(text, Theme.PURPLE, false);
            b.setFont(Theme.MONO_SM);
            b.addActionListener(e -> { if (editing) mdInsertMarker(area, marker); });
            return b;
        }
        private JButton actionButton(String text, Runnable action) {
            JButton b = Theme.button(text, Theme.PURPLE, false);
            b.setFont(Theme.MONO_SM);
            b.addActionListener(e -> action.run());
            return b;
        }

        void show(Integer id) {
            nodeId = id; editing = false;
            Node n = byId(id);
            head.setText(baseTitle + (n != null && n.label != null && !n.label.isEmpty() ? "  -  " + n.label : ""));
            editBtn.setText("Edit");
            listBar.setVisible(false);
            view.setText(mdRenderHtml(n == null ? "" : n.notes));   // rendered markdown
            view.setCaretPosition(0);
            bodyCards.show(body, "read");
        }

        private void toggle() {
            if (nodeId == null) return;
            editing = !editing;
            if (editing) {
                Node n = byId(nodeId);
                area.setText(n == null || n.notes == null ? "" : n.notes);   // raw markdown
                area.setEditable(true);
                area.setCaretPosition(area.getDocument().getLength());
                listBar.setVisible(true);
                editBtn.setText("Done");
                applyStyle();
                bodyCards.show(body, "edit");
                area.requestFocusInWindow();
            } else {
                editBtn.setText("Edit");
                listBar.setVisible(false);
                Node n = byId(nodeId);
                if (n != null) { n.notes = area.getText(); onNotesChanged(nodeId); } // refreshes this view too
                show(nodeId);
            }
        }

        void refreshIfShowing(Integer id) {
            if (!editing && Objects.equals(id, nodeId)) show(id);
        }
    }

    // =================== model helpers ===================
    private Node byId(Integer id) {
        if (id == null) return null;
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }
    private List<Node> childrenOf(int id) {
        List<Node> r = new ArrayList<>();
        for (Node n : nodes) if (n.parent != null && n.parent == id) r.add(n);
        return r;
    }
    private int subtreeCount(Node n) {
        int total = 0;
        Deque<Node> stack = new ArrayDeque<>(childrenOf(n.id));
        Set<Integer> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            Node c = stack.pop();
            if (!seen.add(c.id)) continue;
            total++;
            stack.addAll(childrenOf(c.id));
        }
        return total;
    }
    private int depthOf(Node n) {
        int d = 0; Node cur = n; Set<Integer> seen = new HashSet<>();
        while (cur != null && cur.parent != null && seen.add(cur.id)) { cur = byId(cur.parent); d++; }
        return d;
    }
    private double radiusOf(Node n) {
        int N = subtreeCount(n);
        double r = BASE_R + (MAX_R - BASE_R) * (1 - Math.pow(1 - STEP, N));
        if (n.parent == null) r += 4;   // roots default a touch larger than the rest
        return r;
    }
    private double[] wobbleFor(int id) {
        double[] w = wob.get(id);
        if (w == null) {
            w = new double[] {
                Math.random() * Math.PI * 2, Math.random() * Math.PI * 2,
                2.4 + Math.random() * 1.8, 0.35 + Math.random() * 0.35
            };
            wob.put(id, w);
        }
        return w;
    }
    private double[] wobbleOffset(Node n) {
        if (draggingId != null && draggingId == n.id) return new double[] {0, 0};
        double[] w = wobbleFor(n.id);
        return new double[] {
            Math.sin(animTime * w[3] + w[0]) * w[2],
            Math.cos(animTime * w[3] * 0.9 + w[1]) * w[2]
        };
    }

    // =================== mutations ===================
    private Node addNode(Integer parentId) { return addNode(parentId, true); }
    private Node addNode(Integer parentId, boolean selectNew) {
        Node parent = byId(parentId);
        double nx, ny;
        if (parent != null) {
            int sibs = childrenOf(parent.id).size();
            double ang = (sibs * 0.9) - 1.2;
            double dist = radiusOf(parent) + 90;
            nx = parent.x + Math.cos(ang) * dist;
            ny = parent.y + Math.sin(ang) * dist;
        } else {
            Point2D c = screenToWorld(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
            nx = c.getX() + (Math.random() - 0.5) * 120;
            ny = c.getY() + (Math.random() - 0.5) * 120;
        }
        Node n = new Node();
        n.id = seq++;
        int rootCount = 0; for (Node m : nodes) if (m.parent == null) rootCount++;
        n.label = parent != null ? "Child " + (childrenOf(parent.id).size() + 1) : "Root " + (rootCount + 1);
        n.color = parent != null ? parent.color : PALETTE[(seq - 2 + PALETTE.length) % PALETTE.length];
        n.x = nx; n.y = ny;
        n.parent = parentId;
        nodes.add(n);
        if (selectNew) selectNode(n.id);       // selectNode rebuilds tree + repaints
        else { rebuildTree(); canvas.repaint(); }   // keep the parent selected
        scheduleSave();
        return n;
    }

    private void deleteSubtree(int id) {
        Node n = byId(id);
        if (n == null) return;
        Set<Integer> remove = new HashSet<>();
        remove.add(id);
        Deque<Node> stack = new ArrayDeque<>(childrenOf(id));
        while (!stack.isEmpty()) {
            Node c = stack.pop();
            if (!remove.add(c.id)) continue;
            stack.addAll(childrenOf(c.id));
        }
        nodes.removeIf(x -> remove.contains(x.id));
        if (selected != null && remove.contains(selected)) selectNode(null);
        rebuildTree();
        scheduleSave();
        canvas.repaint();
    }

    private void selectNode(Integer id) {
        selected = id;
        refreshInspector();
        rebuildTree();
        if (overlayPanel != null) {
            if (id != null) { overlayNotes.show(id); overlayPanel.setVisible(true); }
            else overlayPanel.setVisible(false);
        }
        canvas.repaint();
    }

    // =================== geometry ===================
    private Point2D screenToWorld(double sx, double sy) {
        return new Point2D.Double((sx - viewX) / scale, (sy - viewY) / scale);
    }
    private void centerOn(Node n) {
        if (n == null) return;
        viewX = canvas.getWidth() / 2.0 - n.x * scale;
        viewY = canvas.getHeight() / 2.0 - n.y * scale;
        canvas.repaint();
    }
    private Node hitTest(double wx, double wy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            double[] o = wobbleOffset(n);
            double dx = wx - (n.x + o[0]), dy = wy - (n.y + o[1]);
            double r = radiusOf(n);
            if (dx * dx + dy * dy <= r * r) return n;
        }
        return null;
    }

    // =================== sidebar / tree ===================
    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(240, 10));
        side.setBackground(Theme.PANEL);
        side.setBorder(new MatteBorder(0, 0, 0, 1, Theme.LINE));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBorder(new EmptyBorder(14, 14, 12, 14));
        head.setBackground(Theme.PANEL);

        JButton addRoot = Theme.button("New Root Node", Theme.MAGENTA, true);
        addRoot.setAlignmentX(LEFT_ALIGNMENT);
        addRoot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        addRoot.addActionListener(e -> { Node n = addNode(null); centerOn(n); canvas.startNameEdit(n); });

        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setBackground(Theme.PANEL);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JButton save = Theme.button("Save", Theme.CYAN, false);
        JButton loadB = Theme.button("Load", Theme.CYAN, false);
        save.addActionListener(e -> { saveNow(true); });
        loadB.addActionListener(e -> { load(); centerOn(nodes.isEmpty() ? null : nodes.get(0)); refreshInspector(); rebuildTree(); canvas.repaint(); say("Loaded " + nodes.size() + " nodes", Theme.LIME); });
        row.add(save); row.add(loadB);

        status.setFont(Theme.MONO_SM);
        status.setForeground(Theme.MUTED);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setBorder(new EmptyBorder(8, 0, 0, 0));

        head.add(addRoot);
        head.add(Box.createVerticalStrut(8));
        head.add(row);
        head.add(status);
        side.add(head, BorderLayout.NORTH);

        treeList.setLayout(new BoxLayout(treeList, BoxLayout.Y_AXIS));
        treeList.setBackground(Theme.PANEL);
        JScrollPane sp = new JScrollPane(treeList);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        side.add(sp, BorderLayout.CENTER);
        return side;
    }

    /** A small filled circle icon, vertically centered against label text. */
    private static Icon dotIcon(final Color c) {
        return new Icon() {
            public int getIconWidth() { return 11; }
            public int getIconHeight() { return 11; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.fillOval(x + 1, y + 1, 9, 9);
                g2.dispose();
            }
        };
    }

    private void rebuildTree() {
        treeList.removeAll();
        List<Node> roots = new ArrayList<>();
        for (Node n : nodes) if (n.parent == null) roots.add(n);
        for (Node r : roots) addTreeRows(r, 0);
        if (nodes.isEmpty()) {
            JLabel empty = new JLabel("No nodes yet");
            empty.setForeground(Theme.MUTED);
            empty.setFont(Theme.MONO_SM);
            empty.setBorder(new EmptyBorder(20, 14, 0, 0));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            treeList.add(empty);
        }
        treeList.revalidate();
        treeList.repaint();
    }
    private void addTreeRows(Node n, int depth) {
        JPanel rowP = new JPanel(new BorderLayout());
        rowP.setBackground(selected != null && selected == n.id ? new Color(0x2a1550) : Theme.PANEL);
        rowP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        rowP.setBorder(new EmptyBorder(3, 10 + depth * 14, 3, 8));

        JLabel lab = new JLabel(n.label.isEmpty() ? "(untitled)" : n.label,
                                dotIcon(Theme.hex(n.color)), SwingConstants.LEFT);
        lab.setIconTextGap(8);
        lab.setForeground(Theme.INK);
        lab.setFont(Theme.MONO_SM);
        rowP.add(lab, BorderLayout.WEST);

        rowP.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rowP.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { openWiki(n.id); }   // sidebar click -> wiki page
        });
        treeList.add(rowP);
        for (Node c : childrenOf(n.id)) addTreeRows(c, depth + 1);
    }

    // =================== inspector ===================
    private JPanel buildInspector() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(new Dimension(270, 10));
        p.setBackground(Theme.PANEL);
        p.setBorder(new CompoundBorder(new MatteBorder(0, 1, 0, 0, Theme.LINE), new EmptyBorder(14, 14, 14, 14)));

        p.add(fieldLabel("Label"));
        styleField(fLabel);
        fLabel.getDocument().addDocumentListener(new SimpleDoc(() -> {
            Node n = byId(selected); if (n == null) return;
            n.label = fLabel.getText(); rebuildTree(); canvas.repaint(); scheduleSave();
            if (overlayNotes != null) overlayNotes.refreshIfShowing(selected);
        }));
        p.add(fLabel);
        p.add(Box.createVerticalStrut(10));

        p.add(fieldLabel("Color"));
        JPanel colorRow = new JPanel(new BorderLayout(8, 0));
        colorRow.setOpaque(false);
        colorRow.setAlignmentX(LEFT_ALIGNMENT);
        colorRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        bSwatch.setPreferredSize(new Dimension(40, 30));
        bSwatch.setBorder(new LineBorder(Theme.LINE));
        bSwatch.setFocusPainted(false);
        bSwatch.addActionListener(e -> {
            Node n = byId(selected); if (n == null) return;
            Color c = JColorChooser.showDialog(this, "Node color", Theme.hex(n.color));
            if (c != null) applyColor(Theme.toHex(c));
        });
        styleField(fHex);
        fHex.addActionListener(e -> applyColor(fHex.getText()));
        fHex.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { applyColor(fHex.getText()); } });
        colorRow.add(bSwatch, BorderLayout.WEST);
        colorRow.add(fHex, BorderLayout.CENTER);
        p.add(colorRow);
        p.add(Box.createVerticalStrut(10));

        lMeta.setFont(Theme.MONO_SM);
        lMeta.setForeground(Theme.AMBER);
        lMeta.setAlignmentX(LEFT_ALIGNMENT);
        p.add(lMeta);
        p.add(Box.createVerticalStrut(10));

        JButton child = Theme.button("+ Add Child", Theme.MAGENTA, true);
        child.setAlignmentX(LEFT_ALIGNMENT);
        child.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        child.addActionListener(e -> { if (selected != null) { Node n = addNode(selected, false); canvas.startNameEdit(n); } });

        JPanel actRow = new JPanel(new GridLayout(1, 2, 6, 0));
        actRow.setOpaque(false);
        actRow.setAlignmentX(LEFT_ALIGNMENT);
        actRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JButton center = Theme.button("Center", Theme.CYAN, false);
        JButton del = Theme.button("Delete", Theme.MAGENTA, false);
        center.addActionListener(e -> { Node n = byId(selected); if (n != null) centerOn(n); });
        del.addActionListener(e -> deleteSelected());
        actRow.add(center); actRow.add(del);

        p.add(child);
        p.add(Box.createVerticalStrut(6));
        p.add(actRow);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JLabel fieldLabel(String t) {
        JLabel l = new JLabel(t.toUpperCase());
        l.setFont(Theme.MONO_SM);
        l.setForeground(Theme.MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }
    private void styleField(JTextField f) {
        f.setFont(Theme.MONO);
        f.setForeground(Theme.LIME);
        f.setBackground(new Color(0x060212));
        f.setCaretColor(Theme.LIME);
        f.setBorder(new CompoundBorder(new LineBorder(Theme.LINE), new EmptyBorder(6, 8, 6, 8)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        f.setAlignmentX(LEFT_ALIGNMENT);
    }

    private void applyColor(String hex) {
        Node n = byId(selected); if (n == null) return;
        if (hex == null) return;
        hex = hex.trim();
        if (!hex.startsWith("#")) hex = "#" + hex;
        if (!hex.matches("#[0-9a-fA-F]{6}")) return;
        n.color = hex.toLowerCase();
        refreshInspector();
        rebuildTree();
        canvas.repaint();
        scheduleSave();
    }

    private void deleteSelected() {
        Node n = byId(selected); if (n == null) return;
        int desc = subtreeCount(n);
        String msg = desc > 0
            ? "Delete \"" + n.label + "\" and its " + desc + " descendant node(s)? This can't be undone."
            : "Delete \"" + n.label + "\"?";
        if (JOptionPane.showConfirmDialog(this, msg, "Delete node", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            deleteSubtree(n.id);
    }

    private void refreshInspector() {
        Node n = byId(selected);
        boolean has = n != null;
        fLabel.setEnabled(has); fHex.setEnabled(has); bSwatch.setEnabled(has);
        if (!has) {
            fLabel.setText(""); fHex.setText("");
            bSwatch.setBackground(Theme.PANEL);
            lMeta.setText("no node selected");
            return;
        }
        if (!fLabel.getText().equals(n.label)) fLabel.setText(n.label);
        fHex.setText(n.color.toUpperCase());
        bSwatch.setBackground(Theme.hex(n.color));
        lMeta.setText("depth " + depthOf(n));
    }

    private void say(String msg, Color c) {
        status.setText(msg);
        status.setForeground(c);
    }

    // =================== persistence ===================
    private void scheduleSave() {
        if (saveTimer == null) {
            saveTimer = new Timer(500, e -> saveNow(false));
            saveTimer.setRepeats(false);
        }
        saveTimer.restart();
    }
    // read-only test hooks (no mutation, no save) used by the offscreen guitest
    void debugSelectFirst() { if (!nodes.isEmpty()) selectNode(nodes.get(0).id); }
    void debugOpenFirstWiki() { if (!nodes.isEmpty()) openWiki(nodes.get(0).id); }
    void debugStartNameEdit() { if (!nodes.isEmpty()) canvas.startNameEdit(nodes.get(0)); }
    void debugSampleWiki() {   // in-memory only (never saved) — for render verification
        if (nodes.isEmpty()) return;
        Node n = nodes.get(0);
        n.notes = "# Overview\nInventor is a **crafting** profession.\n\nRole:\n- Builds gadgets\n- Researches tech\n\nSteps:\n1. Gather parts\n2. Assemble\n\nTasks:\n[ ] Prototype\n[x] Test passed";
        openWiki(n.id);
    }
    void debugSampleWikiEdit() { debugSampleWiki(); wikiToggleEdit(); }   // wiki edit mode on sample notes
    void debugEmptyWiki() {     // wiki read mode on a node with no notes (in-memory only)
        if (nodes.isEmpty()) return;
        Node n = nodes.get(0);
        n.notes = "";
        openWiki(n.id);
    }

    void flush() { saveNow(false); }
    private void saveNow(boolean loud) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", 1);
        root.put("seq", seq);
        List<Object> arr = new ArrayList<>();
        for (Node n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id);
            m.put("label", n.label);
            m.put("color", n.color);
            m.put("x", n.x);
            m.put("y", n.y);
            m.put("parent", n.parent);
            m.put("notes", n.notes == null ? "" : n.notes);
            arr.add(m);
        }
        root.put("nodes", arr);
        boolean ok = Store.write("mindmap.json", Json.write(root));
        if (loud) say(ok ? "Saved " + nodes.size() + " nodes" : "Save failed", ok ? Theme.LIME : Theme.MAGENTA);
    }
    private void load() {
        String s = Store.read("mindmap.json");
        if (s == null) return;
        try {
            Map<String, Object> root = Json.asMap(Json.parse(s));
            if (root == null) return;
            List<Object> arr = Json.asList(root.get("nodes"));
            if (arr == null) return;
            nodes.clear(); wob.clear();
            int maxId = 0;
            for (Object o : arr) {
                Map<String, Object> m = Json.asMap(o);
                if (m == null) continue;
                Node n = new Node();
                n.id = (int) Json.asNum(m.get("id"), 0);
                n.label = Json.asStr(m.get("label"));
                n.color = Json.asStr(m.get("color"));
                if (n.label == null) n.label = "";
                if (n.color == null) n.color = "#00f0ff";
                n.x = Json.asNum(m.get("x"), 0);
                n.y = Json.asNum(m.get("y"), 0);
                Object par = m.get("parent");
                n.parent = (par instanceof Number) ? ((Number) par).intValue() : null;
                n.notes = Json.asStr(m.get("notes"));
                if (n.notes == null) n.notes = "";
                nodes.add(n);
                maxId = Math.max(maxId, n.id);
            }
            seq = (int) Json.asNum(root.get("seq"), maxId + 1);
            if (seq <= maxId) seq = maxId + 1;
            selected = null;
        } catch (Exception e) {
            System.err.println("mindmap load failed: " + e);
        }
    }

    // =================== canvas ===================
    private final class Canvas extends JPanel {
        private double dragStartX, dragStartY, nodeOX, nodeOY, vOX, vOY;
        private boolean movedDrag = false;
        private boolean panning = false;

        // inline node-name editor (double-click / new node)
        private final JTextField nameEditor = new JTextField();
        private Integer editingNameId = null;

        Canvas() {
            setBackground(Theme.VOID);
            setLayout(null);                       // absolute positioning for the name editor
            nameEditor.setVisible(false);
            nameEditor.setFont(Theme.MONO);
            nameEditor.setForeground(Color.WHITE);
            nameEditor.setBackground(new Color(0x1a0d38));
            nameEditor.setCaretColor(Theme.CYAN);
            nameEditor.setBorder(new LineBorder(Theme.CYAN, 2));
            nameEditor.setHorizontalAlignment(JTextField.CENTER);
            nameEditor.addActionListener(ev -> commitNameEdit());   // Enter commits
            nameEditor.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "cancelName");
            nameEditor.getActionMap().put("cancelName", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { cancelNameEdit(); }
            });
            nameEditor.addFocusListener(new FocusAdapter() {
                public void focusLost(FocusEvent e) { commitNameEdit(); }
            });
            add(nameEditor);

            MouseAdapter ma = new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {               // double-click a node to rename it
                        Point2D w = screenToWorld(e.getX(), e.getY());
                        Node hit = hitTest(w.getX(), w.getY());
                        if (hit != null) startNameEdit(hit);
                    }
                }
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    Point2D w = screenToWorld(e.getX(), e.getY());
                    Node hit = hitTest(w.getX(), w.getY());
                    movedDrag = false;
                    dragStartX = e.getX(); dragStartY = e.getY();
                    if (hit != null) {
                        draggingId = hit.id;
                        nodeOX = hit.x; nodeOY = hit.y;
                        panning = false;
                    } else {
                        panning = true;
                        vOX = viewX; vOY = viewY;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }
                public void mouseDragged(MouseEvent e) {
                    double dx = e.getX() - dragStartX, dy = e.getY() - dragStartY;
                    if (Math.abs(dx) + Math.abs(dy) > 3) movedDrag = true;
                    if (draggingId != null) {
                        Node n = byId(draggingId); if (n == null) return;
                        n.x = nodeOX + dx / scale;
                        n.y = nodeOY + dy / scale;
                        repaint();
                    } else if (panning) {
                        viewX = vOX + dx; viewY = vOY + dy;
                        repaint();
                    }
                }
                public void mouseReleased(MouseEvent e) {
                    setCursor(Cursor.getDefaultCursor());
                    if (draggingId != null) {
                        if (!movedDrag) selectNode(draggingId);
                        else { rebuildTree(); scheduleSave(); }
                        draggingId = null;
                    } else if (panning) {
                        if (!movedDrag) selectNode(null);
                        panning = false;
                    }
                    repaint();
                }
                public void mouseMoved(MouseEvent e) {
                    Point2D w = screenToWorld(e.getX(), e.getY());
                    Node hit = hitTest(w.getX(), w.getY());
                    Integer id = hit == null ? null : hit.id;
                    if (!Objects.equals(id, hovered)) { hovered = id; repaint(); }
                    setCursor(hit != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(e -> {
                if (editingNameId != null) commitNameEdit();   // don't leave the editor floating mid-zoom
                // flat zoom anchored on the centre of the screen (not the cursor)
                double cxp = getWidth() / 2.0, cyp = getHeight() / 2.0;
                Point2D before = screenToWorld(cxp, cyp);
                double factor = Math.exp(-e.getPreciseWheelRotation() * 0.12);
                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
                viewX = cxp - before.getX() * scale;
                viewY = cyp - before.getY() * scale;
                repaint();
            });
            // Delete key removes selected
            setFocusable(true);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "del");
            getActionMap().put("del", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { if (selected != null) deleteSelected(); }
            });
        }

        void startNameEdit(Node n) {
            if (n == null) return;
            editingNameId = n.id;
            nameEditor.setText(n.label == null ? "" : n.label);
            double[] o = wobbleOffset(n);
            int sx = (int) ((n.x + o[0]) * scale + viewX);
            int sy = (int) ((n.y + o[1]) * scale + viewY);
            int w = 190, h = 28;
            nameEditor.setBounds(sx - w / 2, sy - h / 2, w, h);
            nameEditor.setVisible(true);
            nameEditor.selectAll();
            nameEditor.requestFocusInWindow();
        }
        void commitNameEdit() {
            if (editingNameId == null) return;
            Integer id = editingNameId;
            editingNameId = null;                 // guard against focus/Enter re-entrancy
            Node n = byId(id);
            if (n != null) {
                n.label = nameEditor.getText();
                rebuildTree(); scheduleSave();
                if (Objects.equals(selected, id)) refreshInspector();
                if (overlayNotes != null) overlayNotes.refreshIfShowing(id);
            }
            nameEditor.setVisible(false);
            repaint();
        }
        void cancelNameEdit() {
            editingNameId = null;
            nameEditor.setVisible(false);
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            // background gradient
            g.setPaint(new GradientPaint(0, 0, Theme.VOID, 0, h, new Color(0x1d0b3a)));
            g.fillRect(0, 0, w, h);
            drawCity(g, w, h);
            drawGridFloor(g, w, h);

            Graphics2D world = (Graphics2D) g.create();
            world.translate(viewX, viewY);
            world.scale(scale, scale);

            // edges
            for (Node n : nodes) {
                if (n.parent == null) continue;
                Node p = byId(n.parent);
                if (p == null) continue;
                double[] po = wobbleOffset(p), no = wobbleOffset(n);
                Color c = Theme.hex(n.color);
                world.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
                world.setStroke(new BasicStroke(2f / (float) 1));
                world.draw(new Line2D.Double(p.x + po[0], p.y + po[1], n.x + no[0], n.y + no[1]));
            }

            // nodes
            for (Node n : nodes) {
                double[] o = wobbleOffset(n);
                drawNode(world, n, n.x + o[0], n.y + o[1]);
            }
            world.dispose();
            g.dispose();
        }

        // cached skyline (regenerated only when width changes, so it doesn't flicker)
        private int cityW = -1;
        private final java.util.List<int[]> city = new java.util.ArrayList<>(); // {x, width, height, style}

        private void buildCity(int w) {
            city.clear();
            java.util.Random rnd = new java.util.Random(19840621L); // fixed seed = stable skyline
            int x = -40;
            while (x < w + 40) {
                int bw = 24 + rnd.nextInt(64);
                int bh = 22 + rnd.nextInt(130);
                city.add(new int[] { x, bw, bh, rnd.nextInt(2) });
                x += bw + 4 + rnd.nextInt(18);
            }
            cityW = w;
        }

        private void drawCity(Graphics2D g0, int w, int h) {
            if (w != cityW) buildCity(w);
            int horizon = (int) (h * 0.74);   // buildings sit on the grid-floor horizon
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            for (int[] b : city) {
                int x = b[0], bw = b[1], bh = b[2], y = horizon - bh;
                // dark silhouette
                g.setColor(new Color(0x0e0622));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.fillRect(x, y, bw, bh);
                // faint neon roofline
                g.setColor(b[3] == 0 ? Theme.CYAN : Theme.MAGENTA);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
                g.fillRect(x, y, bw, 2);
                // sparse window lights (deterministic pattern)
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
                g.setColor(Theme.CYAN);
                for (int wy = y + 6; wy < horizon - 4; wy += 11) {
                    for (int wx = x + 4; wx < x + bw - 4; wx += 9) {
                        if (((wx * 31 + wy * 17) % 5) == 0) g.fillRect(wx, wy, 3, 4);
                    }
                }
            }
            g.dispose();
        }

        private void drawGridFloor(Graphics2D g0, int w, int h) {
            // A faint retro "horizon" floor: horizontal lines bunching toward a
            // horizon plus verticals fanning from a central vanishing point.
            // Kept low-alpha so it reads as decoration, never competing with nodes.
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int horizon = (int) (h * 0.74);
            double vpx = w / 2.0;

            // horizontal scanlines, denser near the horizon
            int rows = 16;
            for (int i = 1; i <= rows; i++) {
                double t = (double) i / rows;
                int y = (int) (horizon + Math.pow(t, 2.1) * (h - horizon));
                g.setColor(Theme.MAGENTA);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.09f));
                g.drawLine(0, y, w, y);
            }
            // verticals fanning out from the vanishing point
            int cols = 12;
            g.setColor(Theme.CYAN);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
            for (int i = -cols; i <= cols; i++) {
                double xBottom = vpx + i * (w / (double) cols);
                g.drawLine((int) vpx, horizon, (int) xBottom, h);
            }
            g.dispose();
        }

        private void drawNode(Graphics2D g, Node n, double cx, double cy) {
            double r = radiusOf(n);
            Color base = Theme.hex(n.color);
            Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);

            // glow
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
            g.setColor(base);
            double gr = r + 6;
            g.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            g.setComposite(old);

            // fill (radial gradient)
            RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Double(cx - r * 0.3, cy - r * 0.4), (float) (r * 1.4),
                new float[] {0f, 1f}, new Color[] {base, Theme.mix(base, -35)});
            g.setPaint(rg);
            g.fill(circle);

            // border
            boolean sel = selected != null && selected == n.id;
            g.setStroke(new BasicStroke(sel ? 2.4f : 2f));
            g.setColor(sel ? Color.WHITE : new Color(255, 255, 255, 130));
            g.draw(circle);

            boolean show = sel || (hovered != null && hovered == n.id);

            // caption (label) on hover/selected
            if (show) {
                String txt = n.label == null || n.label.isEmpty() ? "(untitled)" : n.label;
                g.setFont(Theme.MONO_SM);
                FontMetrics fm = g.getFontMetrics();
                int tw = Math.min(fm.stringWidth(txt), 220);
                int pad = 7, ch = fm.getHeight() + 4;
                double bx = cx - (tw + pad * 2) / 2.0, by = cy + r + 6;
                g.setColor(new Color(10, 6, 22, 235));
                g.fill(new RoundRectangle2D.Double(bx, by, tw + pad * 2, ch, 6, 6));
                g.setColor(Theme.LINE);
                g.draw(new RoundRectangle2D.Double(bx, by, tw + pad * 2, ch, 6, 6));
                g.setColor(Color.WHITE);
                Shape clip = g.getClip();
                g.setClip(new Rectangle2D.Double(bx + pad, by, tw, ch));
                g.drawString(txt, (float) (bx + pad), (float) (by + ch - 6));
                g.setClip(clip);
            }
        }
    }

    // small DocumentListener helper
    private static final class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable r;
        SimpleDoc(Runnable r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}

package mindmap84;

import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Markdown export/import for all three tools.
 *
 * Every file begins with an HTML-comment type marker (invisible in markdown
 * renderers) so imports can reject files of the wrong kind:
 *   mindmap    <!--84:mindmap v1-->
 *   checklist  <!--84:checklist v1-->
 *   datalist   <!--84:datalist v1-->
 *
 * Mind map structure lives entirely in comment lines; node notes are written
 * verbatim between notes markers, so markdown inside a note (headings, bullets,
 * bold) can never be confused with tree structure. A note line that itself
 * starts with "<!--84" is escaped with a leading backslash on export and
 * unescaped on import.
 */
final class Md {

    static final String MINDMAP_HEADER   = "<!--84:mindmap v1-->";
    static final String CHECKLIST_HEADER = "<!--84:checklist v1-->";
    static final String DATALIST_HEADER  = "<!--84:datalist v1-->";

    // ==================== mind map ====================

    static String writeMindmap(List<MindMapPanel.Node> nodes) {
        StringBuilder b = new StringBuilder(MINDMAP_HEADER).append('\n');
        for (MindMapPanel.Node r : nodes)
            if (r.parent == null) writeMindmapNode(b, nodes, r, 0);
        return b.toString();
    }

    private static void writeMindmapNode(StringBuilder b, List<MindMapPanel.Node> all, MindMapPanel.Node n, int depth) {
        b.append('\n').append("<!--84:node d").append(depth).append(' ')
         .append(n.color == null ? "#00f0ff" : n.color);
        // canvas position, rounded to whole units (sub-pixel precision is not meaningful);
        // omitted when unknown so the importer falls back to auto-layout
        if (!Double.isNaN(n.x) && !Double.isNaN(n.y))
            b.append(" @").append(Math.round(n.x)).append(',').append(Math.round(n.y));
        b.append("-->\n");
        int hashes = Math.min(depth + 1, 6);
        for (int i = 0; i < hashes; i++) b.append('#');
        b.append(' ').append(n.label == null ? "" : n.label).append('\n');
        if (n.notes != null && !n.notes.isEmpty()) {
            b.append("<!--84:notes-->\n");
            for (String line : n.notes.split("\n", -1)) b.append(escNoteLine(line)).append('\n');
            b.append("<!--84:/notes-->\n");
        }
        for (MindMapPanel.Node c : all)
            if (c.parent != null && c.parent == n.id) writeMindmapNode(b, all, c, depth + 1);
    }

    /** Parses a mindmap markdown file. Returns null if it is not one (never throws). */
    static List<MindMapPanel.Node> parseMindmap(String text) {
        try {
            String[] lines = text.split("\r?\n", -1);
            int i = skipBlank(lines, 0);
            if (i >= lines.length || !lines[i].trim().equals(MINDMAP_HEADER)) return null;
            i++;

            List<MindMapPanel.Node> out = new ArrayList<>();
            Map<Integer, MindMapPanel.Node> lastAtDepth = new HashMap<>();
            MindMapPanel.Node cur = null;
            int nextId = 1;
            // position suffix "@x,y" is optional: files written before positions were
            // stored (or hand-authored ones) still import and get auto-laid-out
            Pattern nodeP = Pattern.compile(
                "^<!--84:node d(\\d+) (#[0-9a-fA-F]{6})(?: @(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?))?-->$");
            Pattern headP = Pattern.compile("^(#{1,6}) ?(.*)$");

            while (i < lines.length) {
                String line = lines[i].trim();
                if (line.isEmpty()) { i++; continue; }
                Matcher m = nodeP.matcher(line);
                if (m.matches()) {
                    int d = Integer.parseInt(m.group(1));
                    i = skipBlank(lines, i + 1);
                    if (i >= lines.length) return null;
                    Matcher hm = headP.matcher(lines[i]);
                    if (!hm.matches()) return null;
                    MindMapPanel.Node n = new MindMapPanel.Node();
                    n.id = nextId++;
                    n.label = hm.group(2);
                    n.color = m.group(2).toLowerCase();
                    n.notes = "";
                    if (m.group(3) != null) {                  // stored position
                        n.x = Double.parseDouble(m.group(3));
                        n.y = Double.parseDouble(m.group(4));
                    } else {                                   // NaN = "needs auto-layout"
                        n.x = Double.NaN;
                        n.y = Double.NaN;
                    }
                    if (d == 0) n.parent = null;
                    else {
                        MindMapPanel.Node p = lastAtDepth.get(d - 1);
                        if (p == null) return null;      // depth jump with no parent
                        n.parent = p.id;
                    }
                    lastAtDepth.put(d, n);
                    out.add(n);
                    cur = n;
                    i++;
                } else if (line.equals("<!--84:notes-->")) {
                    if (cur == null) return null;
                    i++;
                    StringBuilder sb = new StringBuilder();
                    boolean closed = false, first = true;
                    while (i < lines.length) {
                        if (lines[i].equals("<!--84:/notes-->")) { closed = true; i++; break; }
                        if (!first) sb.append('\n');
                        sb.append(unescNoteLine(lines[i]));
                        first = false;
                        i++;
                    }
                    if (!closed) return null;
                    cur.notes = sb.toString();
                } else {
                    return null;                          // unexpected structural content
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) { return null; }
    }

    // ==================== checklist ====================

    static String writeChecklist(List<ChecklistPanel.Group> groups) {
        StringBuilder b = new StringBuilder(CHECKLIST_HEADER).append('\n');
        for (ChecklistPanel.Group g : groups) {
            b.append('\n').append("## ").append(g.name == null ? "" : g.name).append('\n');
            for (ChecklistPanel.Item it : g.items)
                b.append(it.done ? "- [x] " : "- [ ] ").append(it.text == null ? "" : it.text).append('\n');
        }
        return b.toString();
    }

    /** Parses a checklist markdown file. Returns null if it is not one. */
    static List<ChecklistPanel.Group> parseChecklist(String text) {
        try {
            String[] lines = text.split("\r?\n", -1);
            int i = skipBlank(lines, 0);
            if (i >= lines.length || !lines[i].trim().equals(CHECKLIST_HEADER)) return null;
            i++;
            List<ChecklistPanel.Group> out = new ArrayList<>();
            ChecklistPanel.Group cur = null;
            Pattern itemP = Pattern.compile("^- \\[( |x|X)\\] (.*)$");
            for (; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("## ")) { cur = new ChecklistPanel.Group(line.substring(3).trim()); out.add(cur); continue; }
                Matcher m = itemP.matcher(line);
                if (m.matches()) {
                    if (cur == null) { cur = new ChecklistPanel.Group("Ungrouped"); out.add(cur); }
                    ChecklistPanel.Item it = new ChecklistPanel.Item(m.group(2));
                    it.done = !m.group(1).trim().isEmpty();
                    cur.items.add(it);
                    continue;
                }
                return null;
            }
            return out;
        } catch (Exception e) { return null; }
    }

    // ==================== datalist ====================

    static String writeDatalist(List<DatalistPanel.Group> groups) {
        StringBuilder b = new StringBuilder(DATALIST_HEADER).append('\n');
        for (DatalistPanel.Group g : groups) {
            b.append('\n').append("## ").append(g.name == null ? "" : g.name).append('\n');
            int n = 1;
            for (DatalistPanel.Item it : g.items) {
                b.append(n++).append(". ").append(it.text == null ? "" : it.text);
                if (it.desc != null && !it.desc.isEmpty()) b.append(": ").append(it.desc);
                b.append('\n');
            }
        }
        return b.toString();
    }

    /** Parses a datalist markdown file. Returns null if it is not one. */
    static List<DatalistPanel.Group> parseDatalist(String text) {
        try {
            String[] lines = text.split("\r?\n", -1);
            int i = skipBlank(lines, 0);
            if (i >= lines.length || !lines[i].trim().equals(DATALIST_HEADER)) return null;
            i++;
            List<DatalistPanel.Group> out = new ArrayList<>();
            DatalistPanel.Group cur = null;
            Pattern itemP = Pattern.compile("^\\d+\\. (.*)$");
            for (; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("## ")) { cur = new DatalistPanel.Group(line.substring(3).trim()); out.add(cur); continue; }
                Matcher m = itemP.matcher(line);
                if (m.matches()) {
                    if (cur == null) { cur = new DatalistPanel.Group("Ungrouped"); out.add(cur); }
                    String content = m.group(1);
                    int ci = content.indexOf(':');
                    String text2 = ci < 0 ? content.trim() : content.substring(0, ci).trim();
                    String desc  = ci < 0 ? "" : content.substring(ci + 1).trim();
                    cur.items.add(new DatalistPanel.Item(text2, desc));
                    continue;
                }
                return null;
            }
            return out;
        } catch (Exception e) { return null; }
    }

    // ==================== note-line escaping ====================

    private static String escNoteLine(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == '\\') i++;
        return line.startsWith("<!--84", i) ? "\\" + line : line;
    }
    private static String unescNoteLine(String line) {
        if (line.isEmpty() || line.charAt(0) != '\\') return line;
        int i = 0;
        while (i < line.length() && line.charAt(i) == '\\') i++;
        return line.startsWith("<!--84", i) ? line.substring(1) : line;
    }

    private static int skipBlank(String[] lines, int i) {
        while (i < lines.length && lines[i].trim().isEmpty()) i++;
        return i;
    }

    // ==================== file dialogs + I/O ====================

    private static File docsDir() {
        File d = new File(System.getProperty("user.home"), "Documents");
        return d.isDirectory() ? d : new File(System.getProperty("user.home"));
    }

    /** Strips characters Windows filenames cannot contain. */
    static String safeFileName(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return t.isEmpty() ? fallback : t;
    }

    /** Save chooser defaulting to Documents\name.md; returns null if cancelled. */
    static File chooseSave(Component parent, String defaultName) {
        JFileChooser fc = new JFileChooser(docsDir());
        fc.setDialogTitle("Export MD");
        fc.setFileFilter(new FileNameExtensionFilter("Markdown (*.md)", "md"));
        fc.setSelectedFile(new File(docsDir(), defaultName + ".md"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".md"))
            f = new File(f.getParentFile(), f.getName() + ".md");
        return f;
    }

    /** Open chooser filtered to .md; returns null if cancelled. */
    static File chooseOpen(Component parent) {
        JFileChooser fc = new JFileChooser(docsDir());
        fc.setDialogTitle("Import MD");
        fc.setFileFilter(new FileNameExtensionFilter("Markdown (*.md)", "md"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        return fc.getSelectedFile();
    }

    static boolean writeFile(File f, String content) {
        try { Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8)); return true; }
        catch (Exception e) { return false; }
    }
    static String readFile(File f) {
        try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
        catch (Exception e) { return null; }
    }

    private Md() {}
}

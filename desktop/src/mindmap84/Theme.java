package mindmap84;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** Shared 80s-neon look: colors, fonts, and styled button factory. */
final class Theme {
    static final Color VOID    = new Color(0x0a0616);
    static final Color PANEL   = new Color(0x1c0e3a);
    static final Color LINE    = new Color(0x3d2a6b);
    static final Color INK     = new Color(0xe8e3ff);
    static final Color MUTED   = new Color(0x8b7bbf);
    static final Color CYAN    = new Color(0x00f0ff);
    static final Color MAGENTA = new Color(0xff2d95);
    static final Color PURPLE  = new Color(0xb537f2);
    static final Color AMBER   = new Color(0xffb627);
    static final Color LIME    = new Color(0x39ff14);

    static final Font  HEAD, HEAD_BIG, MONO, MONO_SM;

    static {
        // Orbitron / Share Tech Mono aren't guaranteed installed; fall back cleanly.
        String head = pick("Orbitron", "Bahnschrift", "Segoe UI", Font.SANS_SERIF);
        String mono = pick("Share Tech Mono", "Consolas", "Cascadia Mono", Font.MONOSPACED);
        HEAD     = new Font(head, Font.BOLD, 13);
        HEAD_BIG = new Font(head, Font.BOLD, 22);
        MONO     = new Font(mono, Font.PLAIN, 14);
        MONO_SM  = new Font(mono, Font.PLAIN, 12);
    }

    private static String pick(String... names) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            have.add(f.toLowerCase());
        for (String n : names) if (have.contains(n.toLowerCase())) return n;
        return names[names.length - 1];
    }

    /** A dark neon button that glows on hover. `accent` drives the hover color. */
    static JButton button(String text, Color accent, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(HEAD);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(primary ? Color.WHITE : MUTED);
        Color baseBg = primary ? MAGENTA.darker() : new Color(0x140a2c);
        b.setBackground(baseBg);
        b.setBorder(new CompoundBorder(new LineBorder(primary ? MAGENTA : LINE, 1, true),
                                       new EmptyBorder(7, 14, 7, 14)));
        b.getModel().addChangeListener(e -> {
            ButtonModel m = b.getModel();
            if (m.isRollover()) {
                b.setForeground(primary ? Color.WHITE : accent);
                b.setBorder(new CompoundBorder(new LineBorder(accent, 1, true), new EmptyBorder(7, 14, 7, 14)));
                b.setBackground(primary ? MAGENTA : new Color(0x1c1140));
            } else {
                b.setForeground(primary ? Color.WHITE : MUTED);
                b.setBorder(new CompoundBorder(new LineBorder(primary ? MAGENTA : LINE, 1, true), new EmptyBorder(7, 14, 7, 14)));
                b.setBackground(baseBg);
            }
        });
        return b;
    }

    static Color hex(String s) {
        try {
            if (s == null) return CYAN;
            s = s.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6) return CYAN;
            return new Color(Integer.parseInt(s, 16));
        } catch (Exception e) { return CYAN; }
    }

    static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static Color mix(Color c, int d) {
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d));
    }
    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private Theme() {}
}

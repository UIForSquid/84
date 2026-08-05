package mindmap84;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Slim purple scrollbars matching the app theme: no arrow buttons, a nearly
 * invisible track, and a rounded purple thumb that brightens on hover/drag.
 *
 * Installed globally via {@link #install()} so every scroll pane picks it up.
 * Must be public with a public static createUI for UIManager's reflection.
 */
public final class NeonScrollBarUI extends BasicScrollBarUI {

    private static final Color TRACK      = new Color(0x120829);
    private static final Color THUMB      = new Color(0x4b2b86);
    private static final Color THUMB_HOT  = Theme.PURPLE;

    /** Makes every JScrollBar in the app use this look. Call once at startup. */
    static void install() {
        UIManager.put("ScrollBarUI", NeonScrollBarUI.class.getName());
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBar.background", TRACK);
    }

    public static ComponentUI createUI(JComponent c) { return new NeonScrollBarUI(); }

    @Override protected void configureScrollBarColors() {
        trackColor = TRACK;
        thumbColor = THUMB;
    }

    // no arrow buttons at the ends
    @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
    @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
    private JButton zeroButton() {
        JButton b = new JButton();
        Dimension none = new Dimension(0, 0);
        b.setPreferredSize(none); b.setMinimumSize(none); b.setMaximumSize(none);
        b.setFocusable(false);
        b.setBorder(null);
        return b;
    }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(TRACK);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isDragging || isThumbRollover() ? THUMB_HOT : THUMB);
        int pad = 3;
        int x = r.x + pad, y = r.y + pad;
        int w = Math.max(1, r.width - pad * 2), h = Math.max(1, r.height - pad * 2);
        int arc = Math.min(w, h);
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.dispose();
    }
}

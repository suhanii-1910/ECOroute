package ecoroute.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Design system for the ECOroute desktop application.
 * Polished, modern eco green/teal/navy palette with consistent typography and responsive layouts.
 */
public final class UITheme {

    // Palette: Restrained Eco Green / Teal / Slate
    public static final Color PRIMARY = new Color(0x1B, 0x4D, 0x3E);        // Forest Green
    public static final Color PRIMARY_DARK = new Color(0x13, 0x38, 0x2D);   // Deep Pine
    public static final Color PRIMARY_LIGHT = new Color(0x2A, 0x72, 0x5D);  // Lighter Pine
    public static final Color ACCENT_TEAL = new Color(0x0D, 0x94, 0x88);    // Teal
    public static final Color ACCENT_MINT = new Color(0xCC, 0xFB, 0xF1);    // Soft Mint
    public static final Color BG_LIGHT = new Color(0xF8, 0xFA, 0xFC);       // Slate 50
    public static final Color CARD_BG = Color.WHITE;
    public static final Color TEXT_MAIN = new Color(0x0F, 0x17, 0x2A);      // Slate 900
    public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);     // Slate 500
    public static final Color BORDER_COLOR = new Color(0xE2, 0xE8, 0xF0);   // Slate 200

    // Semantic Status Colors
    public static final Color STATUS_PENDING_BG = new Color(0xFE, 0xF3, 0xC7);
    public static final Color STATUS_PENDING_FG = new Color(0xB4, 0x53, 0x09);
    public static final Color STATUS_BLUE_BG = new Color(0xDB, 0xEA, 0xFE);
    public static final Color STATUS_BLUE_FG = new Color(0x1D, 0x4E, 0xD8);
    public static final Color STATUS_SUCCESS_BG = new Color(0xDC, 0xFC, 0xE7);
    public static final Color STATUS_SUCCESS_FG = new Color(0x15, 0x80, 0x3D);
    public static final Color STATUS_DANGER_BG = new Color(0xFE, 0xE2, 0xE2);
    public static final Color STATUS_DANGER_FG = new Color(0xB9, 0x1C, 0x1C);
    public static final Color STATUS_PURPLE_BG = new Color(0xF3, 0xE8, 0xFF);
    public static final Color STATUS_PURPLE_FG = new Color(0x7E, 0x22, 0xCE);

    // Typography
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    public static final Font FONT_HEADER = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    public static final Font FONT_SUBHEADER = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font FONT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font FONT_BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private UITheme() {}

    public static void setupFlatLaf() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.thumbArc", 6);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("Table.rowHeight", 30);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.gridColor", BORDER_COLOR);
            UIManager.put("Table.selectionBackground", ACCENT_MINT);
            UIManager.put("Table.selectionForeground", TEXT_MAIN);
        } catch (Throwable t) {
            // Fall back gracefully to system look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }

    public static JPanel createCardPanel() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        return card;
    }

    public static JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_HEADER);
        label.setForeground(TEXT_MAIN);
        return label;
    }

    public static JLabel createMutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setBackground(PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 16, 7, 16));
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY);
        btn.setBackground(CARD_BG);
        btn.setForeground(TEXT_MAIN);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 14, 6, 14)
        ));
        return btn;
    }

    public static JButton createDangerButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setBackground(STATUS_DANGER_FG);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        return btn;
    }

    public static JLabel createBadge(String text, Color bg, Color fg) {
        JLabel badge = new JLabel(" " + text + " ");
        badge.setFont(FONT_SMALL);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(new LineBorder(bg, 2, true));
        return badge;
    }

    public static JLabel createStatusBadge(String status) {
        if (status == null) status = "UNKNOWN";
        Color bg;
        Color fg;
        switch (status.toUpperCase()) {
            case "PENDING":
            case "PLANNED":
                bg = STATUS_PENDING_BG;
                fg = STATUS_PENDING_FG;
                break;
            case "ASSIGNED":
            case "IN_ROUTE":
            case "IN_PROGRESS":
            case "DISPATCHED_TO_DISPOSAL":
            case "ACTIVE":
            case "AVAILABLE":
                bg = STATUS_BLUE_BG;
                fg = STATUS_BLUE_FG;
                break;
            case "COMPLETED":
            case "VERIFIED":
                bg = STATUS_SUCCESS_BG;
                fg = STATUS_SUCCESS_FG;
                break;
            case "CANCELLED":
            case "REJECTED":
            case "INACTIVE":
            case "MAINTENANCE":
                bg = STATUS_DANGER_BG;
                fg = STATUS_DANGER_FG;
                break;
            case "SKIPPED":
            case "DISCREPANCY":
                bg = STATUS_PURPLE_BG;
                fg = STATUS_PURPLE_FG;
                break;
            default:
                bg = BG_LIGHT;
                fg = TEXT_MUTED;
                break;
        }
        return createBadge(status, bg, fg);
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(30);
        table.setShowGrid(true);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(ACCENT_MINT);
        table.setSelectionForeground(TEXT_MAIN);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BODY_BOLD);
        header.setBackground(BG_LIGHT);
        header.setForeground(TEXT_MAIN);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        header.setReorderingAllowed(false);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    }

    public static JScrollPane createTableScrollPane(JTable table) {
        styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        scrollPane.getViewport().setBackground(CARD_BG);
        return scrollPane;
    }
}

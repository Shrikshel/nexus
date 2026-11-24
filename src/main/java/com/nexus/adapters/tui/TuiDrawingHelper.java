package com.nexus.adapters.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.text.DecimalFormat;

/**
 * Common drawing utilities for Lanterna-based Nexus TUI screens.
 */
public final class TuiDrawingHelper {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private TuiDrawingHelper() {}

    /**
     * Draws an outlined box with an optional title in the top border.
     */
    public static void drawBox(TextGraphics tg, int x, int y, int width, int height,
                               String title, TextColor borderColor, TextColor titleColor) {
        if (width < 2 || height < 2) {
            return;
        }

        tg.setForegroundColor(borderColor);

        // Top line
        StringBuilder top = new StringBuilder();
        top.append('┌');
        int titleSpace = width - 2;
        if (title != null && !title.isBlank() && titleSpace >= 6) {
            String formattedTitle = " " + title + " ";
            if (formattedTitle.length() > titleSpace - 2) {
                formattedTitle = formattedTitle.substring(0, titleSpace - 5) + "... ";
            }
            top.append('─');
            top.append(formattedTitle);
            int remaining = width - 2 - 1 - formattedTitle.length();
            top.append("─".repeat(Math.max(0, remaining)));
        } else {
            top.append("─".repeat(Math.max(0, width - 2)));
        }
        top.append('┐');
        tg.putString(x, y, top.toString());

        // Side borders and clear inside
        String emptyInterior = " ".repeat(Math.max(0, width - 2));
        for (int row = y + 1; row < y + height - 1; row++) {
            tg.setForegroundColor(borderColor);
            tg.putString(x, row, "│");
            tg.putString(x + 1, row, emptyInterior);
            tg.putString(x + width - 1, row, "│");
        }

        // Bottom line
        String bottom = "└" + "─".repeat(Math.max(0, width - 2)) + "┘";
        tg.putString(x, y + height - 1, bottom);

        // Highlight title if provided
        if (title != null && !title.isBlank() && titleSpace >= 6) {
            tg.setForegroundColor(titleColor != null ? titleColor : TextColor.ANSI.WHITE_BRIGHT);
            tg.enableModifiers(SGR.BOLD);
            tg.putString(x + 2, y, " " + title + " ");
            tg.clearModifiers();
        }
    }

    /**
     * Draws a percentage-filled progress bar.
     */
    public static void drawProgressBar(TextGraphics tg, int x, int y, int barWidth, double percentage,
                                       TextColor filledColor, TextColor emptyColor) {
        if (barWidth < 4) {
            return;
        }
        double clamped = Math.max(0.0, Math.min(100.0, percentage));
        int innerWidth = barWidth - 2;
        int filledCount = (int) Math.round((clamped / 100.0) * innerWidth);
        int emptyCount = innerWidth - filledCount;

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x, y, "[");

        if (filledCount > 0) {
            tg.setForegroundColor(filledColor);
            tg.putString(x + 1, y, "■".repeat(filledCount));
        }

        if (emptyCount > 0) {
            tg.setForegroundColor(emptyColor);
            tg.putString(x + 1 + filledCount, y, "□".repeat(emptyCount));
        }

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.putString(x + barWidth - 1, y, "]");
    }

    /**
     * Formats bytes into human-readable B, KB, MB, GB, TB.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return DF.format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
    }

    /**
     * Truncates a string with ellipsis if longer than maxLen.
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        if (maxLen <= 3) return text.substring(0, maxLen);
        return text.substring(0, maxLen - 3) + "...";
    }

    /**
     * Pads a string on the right with spaces to reaching target length.
     */
    public static String padRight(String text, int len) {
        if (text == null) text = "";
        if (text.length() >= len) return text;
        return text + " ".repeat(len - text.length());
    }
}

package com.chaosforge.recordcoding.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/** 统一把设计 token 应用到 Swing 全局 UI 属性，供各界面任务直接复用。 */
public final class UiTheme {
    private UiTheme() {}

    public static void apply() {
        UIManager.put("Panel.background", UiTokens.BACKGROUND);
        UIManager.put("OptionPane.background", UiTokens.CARD);
        UIManager.put("Table.background", UiTokens.CARD);
        UIManager.put("Table.foreground", UiTokens.TEXT);
        UIManager.put("Table.gridColor", UiTokens.BORDER);
        UIManager.put("Table.rowHeight", UiTokens.ROW_HEIGHT);
        UIManager.put("TableHeader.background", UiTokens.HEADER);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("Label.foreground", UiTokens.TEXT);
        UIManager.put("TextField.background", UiTokens.CARD);
        UIManager.put("TextField.foreground", UiTokens.TEXT);
        UIManager.put("Button.font", UiTokens.font(UiTokens.LEVEL_BODY));
        UIManager.put("Label.font", UiTokens.font(UiTokens.LEVEL_BODY));
        UIManager.put("Table.font", UiTokens.font(UiTokens.LEVEL_BODY));
        UIManager.put("TableHeader.font", UiTokens.font(UiTokens.LEVEL_BODY, Font.BOLD));
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTokens.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12));
    }

    public static Border focusBorder() {
        return BorderFactory.createLineBorder(UiTokens.ACCENT, 2);
    }

    public static Color background() { return UiTokens.BACKGROUND; }
    public static Color card() { return UiTokens.CARD; }
    public static Font titleFont() { return UiTokens.font(UiTokens.LEVEL_TITLE, Font.BOLD); }
}

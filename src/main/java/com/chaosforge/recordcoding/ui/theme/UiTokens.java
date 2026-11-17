package com.chaosforge.recordcoding.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;

/**
 * 设计 token：色板 / 语义色 / 8px 栅格 / 字体层级 / 表行高集中一处定义。
 * 参考企业级桌面 UI 的「主题对象统一管理」做法（FlatLaf / Ant Design 的 token 化思路），
 * 但零外部依赖：所有取值只在本类出现，渲染器一律按 token 取色，不硬编码色值。
 */
public final class UiTokens {
    public static final Color BACKGROUND = Color.decode("#F5F6F8");
    public static final Color CARD = Color.decode("#FFFFFF");
    public static final Color HEADER = Color.decode("#3C4450");
    public static final Color BORDER = Color.decode("#DDE1E7");
    public static final Color TEXT = Color.decode("#20262E");
    public static final Color TEXT_MUTED = Color.decode("#6B7280");

    public static final Color ACCENT = Color.decode("#1A6FD4");
    public static final Color CONFLICT = Color.decode("#C0392B");
    public static final Color PENDING = Color.decode("#D68910");
    public static final Color EQUIVALENT = Color.decode("#2E86C1");
    public static final Color SUCCESS = Color.decode("#1E8449");

    public static final List<Integer> SPACING = List.of(4, 8, 12, 16, 24, 32);
    public static final int ROW_HEIGHT = 28;
    public static final int BADGE_ARC = 6;
    public static final int DIFF_BAR_WIDTH = 2;

    public static final List<String> FONT_CANDIDATES = List.of(
            "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "SimHei");

    public static final int LEVEL_TITLE = 0;
    public static final int LEVEL_SECTION = 1;
    public static final int LEVEL_BODY = 2;
    public static final int LEVEL_CAPTION = 3;

    private UiTokens() {}

    /** 字体族回退链：优先微软雅黑，其次苹方，再回落系统默认中文字体，不报错。 */
    public static String resolveFontFamily() {
        List<String> available = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String candidate : FONT_CANDIDATES) {
            for (String name : available) if (name.equalsIgnoreCase(candidate)) return name;
        }
        return Font.DIALOG;
    }

    public static Font font(int level, int style) {
        int size = switch (level) {
            case LEVEL_TITLE -> 20;
            case LEVEL_SECTION -> 15;
            case LEVEL_BODY -> 13;
            default -> 11;
        };
        return new Font(resolveFontFamily(), style, size);
    }

    public static Font font(int level) { return font(level, Font.PLAIN); }

    /** 按判定语义取色：冲突红 / 待确认琥珀 / 等价蓝。 */
    public static Color badgeColor(String key) {
        if (key == null) return TEXT_MUTED;
        String k = key.toLowerCase();
        if (k.contains("conflict") || k.contains("冲突")) return CONFLICT;
        if (k.contains("pending") || k.contains("待确认") || k.contains("prompt")) return PENDING;
        if (k.contains("equivalent") || k.contains("等价")) return EQUIVALENT;
        if (k.contains("accepted") || k.contains("已采纳") || k.contains("通过")) return SUCCESS;
        return TEXT_MUTED;
    }

    /** 浅底同色描边：徽章底色按语义色做浅化处理。 */
    public static Color badgeBackground(String key) {
        Color base = badgeColor(key);
        return new Color(255 - (255 - base.getRed()) / 7,
                255 - (255 - base.getGreen()) / 7,
                255 - (255 - base.getBlue()) / 7);
    }

    public static Color diffBar() { return CONFLICT; }
}

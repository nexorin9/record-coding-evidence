package com.chaosforge.recordcoding.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;

/** 判定徽章：浅底同色描边，颜色一律取自设计 token，渲染器内不硬编码色值。 */
public class BadgeRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean focus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, false, row, column);
        String key = value == null ? "" : String.valueOf(value);
        label.setBackground(UiTokens.badgeBackground(key));
        label.setForeground(UiTokens.badgeColor(key));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTokens.badgeColor(key), 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        label.setHorizontalAlignment(CENTER);
        return label;
    }

    /** 供测试断言颜色取自 token 而非字面量。 */
    public static java.awt.Color colorFor(String key) { return UiTokens.badgeColor(key); }
}

package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.transport.ConnectionHealth;
import com.chaosforge.recordcoding.util.RuntimeSummary;

import javax.swing.SwingUtilities;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
import java.awt.MenuItem;
import java.awt.image.BufferedImage;

/** 托盘常驻：图标程序化绘制、tooltip 三项状态、关闭主窗体最小化到托盘。 */
public final class TrayController {
    private TrayIcon trayIcon;
    private boolean installed;

    /** BufferedImage 程序化绘制，不引用任何外部图片资源。 */
    public static BufferedImage createIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(com.chaosforge.recordcoding.ui.theme.UiTokens.ACCENT);
        g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6);
        g.setColor(com.chaosforge.recordcoding.ui.theme.UiTokens.CARD);
        g.fillRect(5, 5, size - 10, 3);
        g.fillRect(5, 11, size - 14, 2);
        g.fillRect(5, 16, size - 12, 2);
        g.dispose();
        return img;
    }

    public static String tooltip(ConnectionHealth health, boolean degraded) {
        RuntimeSummary.Snapshot s = RuntimeSummary.of(
                health == null ? "不可用" : health.status().name(),
                health == null ? 0 : health.pendingCount(),
                health == null ? 0 : health.submittedToday(),
                degraded, health == null ? "" : health.lastErrorCode(),
                health == null ? "" : health.lastErrorTime());
        return RuntimeSummary.render(s)
                + (degraded ? " · 模型不可用，当前为降级模式" : "");
    }

    public void install(Runnable openWindow, Runnable consistencyCheck, Runnable exit) {
        if (!SystemTray.isSupported()) return;
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("打开主窗体");
        MenuItem status = new MenuItem("连接状态");
        MenuItem check = new MenuItem("一致性自检");
        MenuItem quit = new MenuItem("退出");
        open.addActionListener(e -> openWindow.run());
        status.addActionListener(e -> { if (trayIcon != null) trayIcon.displayMessage("连接状态",
                trayIcon.getToolTip(), TrayIcon.MessageType.INFO); });
        check.addActionListener(e -> consistencyCheck.run());
        quit.addActionListener(e -> exit.run());
        menu.add(open); menu.add(status); menu.add(check); menu.addSeparator(); menu.add(quit);
        trayIcon = new TrayIcon(createIcon(24), "病案编码证据对照系统", menu);
        trayIcon.setImageAutoSize(true);
        try { SystemTray.getSystemTray().add(trayIcon); installed = true; }
        catch (Exception ignored) { installed = false; }
    }

    public void refresh(String text) {
        SwingUtilities.invokeLater(() -> { if (trayIcon != null) trayIcon.setToolTip(text); });
    }

    public boolean isInstalled() { return installed; }

    /** 关闭主窗体时最小化到托盘而不是退出；退出仅由托盘菜单触发。 */
    public boolean closeMinimizesToTray() { return true; }
}

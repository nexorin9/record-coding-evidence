package com.chaosforge.recordcoding.app;

import java.awt.image.BufferedImage;

/** 托盘图标工厂：程序化绘制，零外部图片资源（与 ui/TrayController 共用同一实现）。 */
public final class TrayIcon {
    private TrayIcon() {}

    public static BufferedImage image(int size) {
        return com.chaosforge.recordcoding.ui.TrayController.createIcon(size);
    }

    public static boolean isValid(BufferedImage image) {
        return image != null && image.getWidth() > 0 && image.getHeight() > 0;
    }
}

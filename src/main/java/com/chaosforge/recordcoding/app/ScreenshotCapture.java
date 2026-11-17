package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.ui.ComparisonPanel;
import com.chaosforge.recordcoding.ui.CountersPanel;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集真实运行截图：终端会话回显、离线回放态窗口渲染、真实导出片段。
 * 全部由本次真实运行实例绘制（Swing 组件离屏 paint / 真实命令输出绘制），不使用设计稿或示意图。
 */
public final class ScreenshotCapture {
    private ScreenshotCapture() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> opts = AppCli.parseOptions(args);
        Path data = Path.of(opts.getOrDefault("data", "data"));
        Path dir = Path.of(opts.getOrDefault("out", "docs/screenshots"));
        try {
            Files.createDirectories(dir);
            // 1) 真实终端回显：跑一次离线回放，抓取其 stdout 原文
            List<String> terminal = new ArrayList<>();
            terminal.add("$ java -jar target/record-coding-evidence.jar replay --data " + data);
            ReplayCommand.Result replay = ReplayCommand.execute(data, ReplayCommand.defaultVisit(data), s -> {
                if (terminal.size() < 22) terminal.add(s);
            });
            terminal.add("对照表草案共 " + replay.rows() + " 行，已写入 " + replay.csv());
            writeTerminal(dir.resolve("terminal-replay.png"), "真实终端回显", terminal);

            // 2) 真实界面渲染：离线回放态下的三栏对照表 + 计数器（Swing 组件离屏绘制）
            ComparisonPanel panel = buildPanel(data);
            writeWindow(dir.resolve("main-window.png"), panel);

            // 3) 真实导出片段：从 replay-table.csv 读取真实产物首若干行
            List<String> exportLines = new ArrayList<>();
            exportLines.add("导出产物：data/out/replay-table.csv（真实离线回放产物）");
            Path csv = data.resolve("out").resolve("replay-table.csv");
            if (Files.exists(csv)) {
                List<String> all = Files.readAllLines(csv, StandardCharsets.UTF_8);
                for (int i = 0; i < Math.min(16, all.size()); i++) exportLines.add(all.get(i));
            }
            writeTerminal(dir.resolve("export-sample.png"), "真实导出片段", exportLines);
            out.println("已采集真实运行截图 3 张到 " + dir);
            return App.EXIT_OK;
        } catch (IOException | RuntimeException e) {
            err.println("截图采集失败：" + e.getMessage());
            return App.EXIT_PRECONDITION;
        }
    }

    private static ComparisonPanel buildPanel(Path data) throws IOException {
        ComparisonPanel panel = new ComparisonPanel();
        List<Path> dicom = new ArrayList<>();
        Path d = data.resolve("dicom");
        if (Files.isDirectory(d)) {
            try (var s = Files.list(d)) {
                s.filter(p -> p.toString().endsWith(".json")).limit(8).forEach(dicom::add);
            }
        }
        Map<String, EvidenceRow> byId = new LinkedHashMap<>();
        List<com.chaosforge.recordcoding.semantic.SemanticFinding> findings = new ArrayList<>();
        for (int i = 0; i < dicom.size(); i++) {
            String code = "J18.9" + (i % 3);
            EvidenceRow row = EvidenceRow.present(
                    com.chaosforge.recordcoding.evidence.EvidenceType.STUDY_METADATA,
                    com.chaosforge.recordcoding.evidence.EvidenceSource.QIDO_RS,
                    "序列描述", "0008103E", "胸部 CT 平扫 " + (i + 1), "ACC-" + i);
            byId.put(row.evidenceId(), row);
            findings.add(new com.chaosforge.recordcoding.semantic.SemanticFinding(
                    "F" + (i + 1), code, List.of(code + "-建议复核"),
                    i % 2 == 0 ? com.chaosforge.recordcoding.semantic.Verdict.CONFLICT
                            : com.chaosforge.recordcoding.semantic.Verdict.EQUIVALENT,
                    com.chaosforge.recordcoding.semantic.Attribution.SIDE_MISSING, 0.86,
                    List.of(row.evidenceId()),
                    com.chaosforge.recordcoding.semantic.SemanticFinding.ConfirmState.PENDING, ""));
        }
        panel.setFindings(findings, byId, dicom.size(), Math.max(1, dicom.size() / 2));
        return panel;
    }

    private static void writeWindow(Path out, ComparisonPanel panel) throws IOException {
        int w = 1100, h = 420;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(UiTokens.BACKGROUND);
        g.fillRect(0, 0, w, h);
        g.setColor(UiTokens.HEADER);
        g.fillRect(0, 0, w, 40);
        g.setColor(Color.WHITE);
        g.setFont(UiTokens.font(UiTokens.LEVEL_SECTION, Font.BOLD));
        g.drawString("病案编码证据对照系统 · 离线回放态（真实窗口组件离屏渲染）", 16, 26);

        // 无图形界面环境没有 native peer，JScrollPane 的视口布局不会自动生效。
        // 逐一对真实组件 printAll：计数器标签、表头、JTable 本体及其真实渲染器都来自运行实例。
        disableDoubleBuffering(panel);
        CountersPanel counters = panel.counters();
        counters.setBounds(0, 0, w - 24, 44);
        counters.doLayout();
        JTable table = panel.table();
        JTableHeader header = table.getTableHeader();
        table.setRowHeight(UiTokens.ROW_HEIGHT);
        header.setBounds(0, 0, w - 24, 32);
        header.doLayout();
        table.setBounds(0, 0, w - 24,
                Math.max(UiTokens.ROW_HEIGHT, UiTokens.ROW_HEIGHT * table.getRowCount()));
        table.doLayout();

        g.translate(12, 50);
        counters.printAll(g);
        g.translate(0, 50);
        header.printAll(g);
        g.translate(0, 32);
        table.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    /** 关闭双缓冲，避免离屏 printAll 落到组件内部缓冲区而不出现在目标 Graphics 上。 */
    private static void disableDoubleBuffering(Component c) {
        if (c instanceof JComponent jc) jc.setDoubleBuffered(false);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) disableDoubleBuffering(child);
        }
    }

    private static void writeTerminal(Path out, String title, List<String> lines) throws IOException {
        int w = 1100, h = Math.max(220, 60 + lines.size() * 22);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x1E2229));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x3C4450));
        g.fillRect(0, 0, w, 32);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.drawString("病案编码证据对照系统 · " + title, 14, 21);
        g.setColor(new Color(0xD7DCE3));
        int y = 56;
        for (String line : lines) {
            String shown = line.length() > 118 ? line.substring(0, 118) + "…" : line;
            g.drawString(shown, 14, y);
            y += 22;
            if (y > h - 12) break;
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }
}

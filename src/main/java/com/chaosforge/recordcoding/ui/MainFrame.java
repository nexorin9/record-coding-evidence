package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/** 主窗体：顶部输入区 + 来源可达性提示条，中部三栏对照表，右侧证据抽屉，底部状态栏。 */
public final class MainFrame extends JFrame {
    private final JTextField visitNo = new JTextField(18);
    private final JButton reviewButton = new JButton("复核这份病案");
    private final JLabel sourceHint = new JLabel("来源可达性：等待首次复核");
    private final JLabel banner = new JLabel(" ");
    private final JLabel statusBar = new JLabel("就绪");
    private final ComparisonPanel comparison = new ComparisonPanel();
    private final EvidenceDrawer drawer = new EvidenceDrawer();
    private final FindingDetailPanel detail = new FindingDetailPanel();

    public MainFrame() {
        super("病案编码证据对照系统");
        UiTheme.apply();
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // 关闭最小化到托盘
        setLayout(new BorderLayout(UiTokens.SPACING.get(1), UiTokens.SPACING.get(1)));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(UiTokens.CARD);
        top.setBorder(UiTheme.cardBorder());
        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
        input.setBackground(UiTokens.CARD);
        input.add(new JLabel("住院号"));
        visitNo.setBorder(UiTheme.focusBorder());
        input.add(visitNo);
        reviewButton.setBackground(UiTokens.ACCENT);
        reviewButton.setForeground(UiTokens.CARD);
        reviewButton.setFocusPainted(false);
        input.add(reviewButton);
        input.add(sourceHint);
        top.add(input, BorderLayout.CENTER);
        banner.setForeground(UiTokens.CONFLICT);
        top.add(banner, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel right = new JPanel(new BorderLayout());
        right.add(drawer, BorderLayout.CENTER);
        right.add(detail, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, comparison, right);
        split.setResizeWeight(0.72);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setSize(1280, 760);
        setLocationRelativeTo(null);
    }

    public void onReview(Consumer<String> handler) {
        reviewButton.addActionListener(e -> handler.accept(visitNo.getText().trim()));
    }

    public void setSourceHint(String text) { sourceHint.setText("来源可达性：" + text); }
    public void setBanner(String text) { banner.setText(text); }
    public void setStatus(String text) { statusBar.setText(text); }
    public String visitNoText() { return visitNo.getText(); }
    public void setVisitNo(String s) { visitNo.setText(s); }
    public ComparisonPanel comparison() { return comparison; }
    public EvidenceDrawer drawer() { return drawer; }
    public FindingDetailPanel detail() { return detail; }
    public String statusText() { return statusBar.getText(); }
}

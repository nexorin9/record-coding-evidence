package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/** 建议详情面板：建议编码 / 判定 / 归因 / 置信度 / 证据出处 + 采纳 / 驳回（驳回需理由）。 */
public final class FindingDetailPanel extends JPanel {
    public record DecisionCommand(String findingId, boolean accepted, String reason, String by, String at) {}

    private final JLabel detail = new JLabel("选中对照表行查看建议详情");
    private final JTextArea rejectReason = new JTextArea(2, 18);
    private final JButton accept = new JButton("采纳");
    private final JButton reject = new JButton("驳回");
    private final JLabel hint = new JLabel("驳回需填写理由");
    private Consumer<DecisionCommand> onDecision = c -> {};
    private SemanticFinding current;

    public FindingDetailPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(UiTheme.cardBorder());
        setBackground(UiTokens.CARD);
        detail.setFont(UiTokens.font(UiTokens.LEVEL_BODY));
        add(detail);
        JPanel reasonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reasonPanel.setBackground(UiTokens.CARD);
        reasonPanel.add(new JLabel("驳回理由"));
        rejectReason.setLineWrap(true);
        reasonPanel.add(new JScrollPane(rejectReason));
        add(reasonPanel);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setBackground(UiTokens.CARD);
        accept.setBackground(UiTokens.ACCENT);
        accept.setForeground(UiTokens.CARD);
        reject.setBackground(UiTokens.CARD);
        buttons.add(accept);
        buttons.add(reject);
        buttons.add(hint);
        add(buttons);
        accept.addActionListener(e -> fire(true));
        reject.addActionListener(e -> { if (isRejectEnabled()) fire(false); });
        rejectReason.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        refresh();
    }

    public void setOnDecision(Consumer<DecisionCommand> handler) { this.onDecision = handler; }

    public void show(SemanticFinding finding) {
        this.current = finding;
        if (finding == null) { detail.setText("选中对照表行查看建议详情"); return; }
        detail.setText("<html>建议编码：" + String.join(" / ", finding.suggestedCodes())
                + "<br>判定：" + finding.verdict().label() + "　归因：" + finding.attribution().label()
                + "<br>置信度：" + finding.confidence() + "　证据出处：" + String.join(",", finding.evidenceRefs())
                + "</html>");
        refresh();
    }

    public boolean isRejectEnabled() { return !rejectReason.getText().trim().isBlank(); }

    public void setRejectReason(String text) { rejectReason.setText(text == null ? "" : text); }

    public String detailText() { return detail.getText(); }

    private void refresh() {
        reject.setEnabled(isRejectEnabled());
        hint.setText(isRejectEnabled() ? "理由已填写，可驳回" : "驳回需填写理由");
    }

    private void fire(boolean accepted) {
        if (current == null) return;
        onDecision.accept(new DecisionCommand(current.findingId(), accepted,
                rejectReason.getText().trim(), System.getProperty("user.name", "编码员"),
                java.time.Instant.now().toString()));
    }
}

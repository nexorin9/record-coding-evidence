package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 右侧可收起证据抽屉：来源系统 / 接收时间 / 哈希前 8 位 / 已被引用处数。 */
public final class EvidenceDrawer extends JPanel {
    private final DefaultListModel<String> items = new DefaultListModel<>();
    private final JList<String> list = new JList<>(items);
    private final List<EvidenceRow> evidence = new ArrayList<>();
    private boolean collapsed;
    private String receivedAt = Instant.now().toString();

    public EvidenceDrawer() {
        setLayout(new BorderLayout());
        setBorder(UiTheme.cardBorder());
        setBackground(UiTokens.CARD);
        list.setFont(UiTokens.font(UiTokens.LEVEL_CAPTION));
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public void setEvidence(List<EvidenceRow> rows, Map<String, Integer> referencedBy) {
        evidence.clear();
        items.clear();
        if (rows != null) for (EvidenceRow r : rows) {
            evidence.add(r);
            int refs = referencedBy == null ? 0 : referencedBy.getOrDefault(r.evidenceId(), 0);
            items.addElement(r.source().label() + " / " + receivedAt + " / " + r.hash().substring(0, 8)
                    + " / 已被引用 " + refs + " 处");
        }
    }

    public int itemCount() { return items.size(); }
    public String textAt(int i) { return items.get(i); }
    public List<EvidenceRow> evidence() { return List.copyOf(evidence); }
    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        setVisible(!collapsed);
    }
}

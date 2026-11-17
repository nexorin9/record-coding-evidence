package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.ui.theme.BadgeRenderer;
import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Map;

/** 三栏对照表：差异行左侧 2px 红色竖条，判定列用 token 化徽章。 */
public final class ComparisonPanel extends JPanel {
    private final ReviewTableModel model = new ReviewTableModel();
    private final JTable table = new JTable(model);
    private final CountersPanel counters = new CountersPanel();

    public ComparisonPanel() {
        setLayout(new BorderLayout(0, UiTokens.SPACING.get(1)));
        setBackground(UiTokens.BACKGROUND);
        add(counters, BorderLayout.NORTH);
        table.setRowHeight(UiTokens.ROW_HEIGHT);
        table.setFont(UiTokens.font(UiTokens.LEVEL_BODY));
        table.getTableHeader().setBackground(UiTokens.HEADER);
        table.getTableHeader().setForeground(UiTokens.CARD);
        table.getColumnModel().getColumn(2).setCellRenderer(new BadgeRenderer());
        table.getColumnModel().getColumn(0).setCellRenderer(new DiffBarRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(760);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.setAutoCreateRowSorter(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(UiTheme.cardBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public void setFindings(List<SemanticFinding> findings, Map<String, EvidenceRow> evidenceById,
                            int studyCount, int reportCount) {
        model.setFindings(findings, evidenceById);
        // 差异数必须与 ReviewTableModel.isDifferenceRow 同一口径：非提示且非语义等价。
        counters.setCounts(studyCount, reportCount,
                (int) findings.stream().filter(f -> !f.isPromptOnly()
                        && f.verdict() != com.chaosforge.recordcoding.semantic.Verdict.EQUIVALENT).count());
    }

    public ReviewTableModel model() { return model; }
    public CountersPanel counters() { return counters; }
    public JTable table() { return table; }

    /** 差异行左侧竖条由渲染器绘制，宽度取自 token，不硬编码色值。 */
    private static final class DiffBarRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, column);
            boolean diff = t.getModel() instanceof ReviewTableModel m && m.isDifferenceRow(row);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, diff ? UiTokens.DIFF_BAR_WIDTH : 0, 0, 0,
                            UiTokens.diffBar()),
                    BorderFactory.createEmptyBorder(0, 6, 0, 4)));
            return l;
        }
    }
}

package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.ui.theme.UiTheme;
import com.chaosforge.recordcoding.ui.theme.UiTokens;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/** 三枚计数器：检查数 / 报告数 / 差异数，数值变化即时刷新。 */
public final class CountersPanel extends JPanel {
    private final JLabel studies = new JLabel("检查数 0");
    private final JLabel reports = new JLabel("报告数 0");
    private final JLabel differences = new JLabel("差异数 0");
    private int studyCount;
    private int reportCount;
    private int differenceCount;

    public CountersPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, UiTokens.SPACING.get(3), UiTokens.SPACING.get(1)));
        setBackground(UiTokens.CARD);
        setBorder(UiTheme.cardBorder());
        for (JLabel l : java.util.List.of(studies, reports, differences)) {
            l.setFont(UiTokens.font(UiTokens.LEVEL_SECTION, java.awt.Font.BOLD));
            l.setForeground(UiTokens.TEXT);
            add(l);
        }
    }

    public void setCounts(int studiesCount, int reportsCount, int differencesCount) {
        this.studyCount = studiesCount;
        this.reportCount = reportsCount;
        this.differenceCount = differencesCount;
        studies.setText("检查数 " + studyCount);
        reports.setText("报告数 " + reportCount);
        differences.setText("差异数 " + differenceCount);
        differences.setForeground(differenceCount > 0 ? UiTokens.CONFLICT : UiTokens.TEXT);
        revalidate();
        repaint();
    }

    public int studyCount() { return studyCount; }
    public int reportCount() { return reportCount; }
    public int differenceCount() { return differenceCount; }
    public String studiesText() { return studies.getText(); }
    public String reportsText() { return reports.getText(); }
    public String differencesText() { return differences.getText(); }
}

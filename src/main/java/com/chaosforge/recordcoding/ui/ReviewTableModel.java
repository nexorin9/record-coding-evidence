package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.evidence.EvidenceRow;
import com.chaosforge.recordcoding.semantic.SemanticFinding;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三栏对照表模型：首页编码 / 影像证据 / 判定；每行与一条 SemanticFinding 一一对应。 */
public final class ReviewTableModel extends AbstractTableModel {
    public record Row(String homeCode, String evidence, String verdict, String evidenceId,
                      SemanticFinding finding) {}

    private static final String[] COLUMNS = {"首页编码", "影像证据（来源 / 字段 / 取值 / 哈希前 8 位）", "判定"};
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> byEvidenceId = new LinkedHashMap<>();

    public void setFindings(List<SemanticFinding> findings, Map<String, EvidenceRow> evidenceById) {
        rows.clear();
        byEvidenceId.clear();
        int i = 0;
        for (SemanticFinding f : findings) {
            String eid = f.evidenceRefs().isEmpty() ? "" : f.evidenceRefs().get(0);
            EvidenceRow e = evidenceById.get(eid);
            String text = e == null ? "missing(无对应证据行)"
                    : e.source().label() + " / " + e.fieldName() + "（" + e.fieldLocator() + "） / "
                    + e.displayValue() + " / " + e.hash().substring(0, 8);
            String verdict = f.isPromptOnly() ? "待确认" : f.verdict().label();
            rows.add(new Row(f.homeCode(), text, verdict, eid, f));
            byEvidenceId.put(eid, i);
            i++;
        }
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row r = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.homeCode();
            case 1 -> r.evidence();
            case 2 -> r.verdict();
            default -> "";
        };
    }

    public Row rowAt(int index) { return rows.get(index); }
    public List<Row> rows() { return List.copyOf(rows); }
    public boolean isDifferenceRow(int index) {
        return rows.get(index).finding() != null && !rows.get(index).finding().isPromptOnly()
                && rows.get(index).finding().verdict() != com.chaosforge.recordcoding.semantic.Verdict.EQUIVALENT;
    }
    public Row rowForEvidenceId(String evidenceId) {
        Integer i = byEvidenceId.get(evidenceId);
        return i == null ? null : rows.get(i);
    }
}

package com.chaosforge.recordcoding.evidence;

/** 统一证据行类型。 */
public enum EvidenceType {
    STUDY_METADATA("检查元数据"), REPORT_CONCLUSION("报告结论"),
    HOME_PAGE_CODING("首页编码"), PROCEDURE_CODE("操作编码");

    private final String label;
    EvidenceType(String label) { this.label = label; }
    public String label() { return label; }
}

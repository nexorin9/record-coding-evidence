package com.chaosforge.recordcoding.semantic;

/** 差异归因：编码侧缺了什么，而不是只给一个「不一致」标签。 */
public enum Attribution {
    SIDE_MISSING("side_missing", "侧别缺失"),
    SITE_MISSING("site_missing", "部位缺失"),
    CODE_ABSENT("code_absent", "编码缺失"),
    STALE_REPORT("stale_report", "报告陈旧"),
    EVIDENCE_MISSING("evidence_missing", "证据缺失"),
    NONE("none", "无差异");

    private final String wire;
    private final String label;
    Attribution(String wire, String label) { this.wire = wire; this.label = label; }
    public String wire() { return wire; }
    public String label() { return label; }
}

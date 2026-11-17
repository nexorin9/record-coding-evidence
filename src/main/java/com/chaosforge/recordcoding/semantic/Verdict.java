package com.chaosforge.recordcoding.semantic;

import java.util.Arrays;

/** 四值互斥完备判定；报告陈旧与证据缺失由确定性路径给出，不交给模型。 */
public enum Verdict {
    EQUIVALENT("equivalent", "语义等价"),
    CONFLICT("conflict", "冲突"),
    REPORT_STALE("report_stale", "报告早于住院，结论已陈旧"),
    EVIDENCE_MISSING("evidence_missing", "无对应报告证据");

    private final String wire;
    private final String label;

    Verdict(String wire, String label) { this.wire = wire; this.label = label; }

    public String wire() { return wire; }
    public String label() { return label; }

    public static Verdict fromWire(String s) {
        for (Verdict v : values()) if (v.wire.equals(s)) return v;
        throw new IllegalArgumentException("未知判定值：" + s);
    }

    /** 互斥性与完备性自检：四值互异且覆盖全部 wire 值。 */
    public static boolean mutuallyExclusiveComplete() {
        return values().length == 4
                && Arrays.stream(values()).map(Verdict::wire).distinct().count() == 4
                && Arrays.stream(values()).allMatch(v -> v.wire != null && !v.wire.isBlank());
    }
}

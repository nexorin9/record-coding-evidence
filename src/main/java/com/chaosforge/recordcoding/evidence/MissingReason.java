package com.chaosforge.recordcoding.evidence;

/** 证据行缺口原因，显式呈现而非静默填默认值。 */
public enum MissingReason {
    FIELD_ABSENT("字段未取回"), EMPTY_VALUE("取值为空"),
    UNMAPPED_FIELD("角色映射缺失"), SOURCE_UNREACHABLE("来源不可达");

    private final String label;
    MissingReason(String label) { this.label = label; }
    public String label() { return label; }
}

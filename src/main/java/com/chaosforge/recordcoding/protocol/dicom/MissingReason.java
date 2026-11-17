package com.chaosforge.recordcoding.protocol.dicom;

/** 未命中显式原因，便于对照表呈现缺口，禁止静默填默认值。 */
public enum MissingReason {
    TAG_NOT_PRESENT("tag 不存在"),
    PATH_NOT_RESOLVED("嵌套路径无法解析"),
    VALUE_EMPTY("取值为空"),
    VR_MISMATCH("取值类型与 VR 不符");

    private final String description;

    MissingReason(String description) { this.description = description; }

    public String description() { return description; }
}

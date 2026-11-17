package com.chaosforge.recordcoding.domain;

/** 编码系统与结构位数常量，依据 domain/dictionaries/医保编码规则_ICD与药品.md 官方口径。 */
public enum CodeSystem {
    ICD10_MEDICARE("ICD10_医保版", 6, "1 字母 + 2 数字类目 / 1 位亚目 / 2 位区分码"),
    ICD9CM3_MEDICARE("ICD9CM3_医保版", 6, "1–2 位类目 / 3 位亚目 / 4 位细目 / 5–6 位区分码（全数字）");

    private final String display;
    private final int digits;
    private final String structure;

    CodeSystem(String display, int digits, String structure) {
        this.display = display;
        this.digits = digits;
        this.structure = structure;
    }

    public String display() { return display; }
    public int digits() { return digits; }
    public String structure() { return structure; }
}

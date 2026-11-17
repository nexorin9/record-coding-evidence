package com.chaosforge.recordcoding.protocol.time;

/** 临床时刻精度：秒 / 分 / 时 / 天，与时刻值、时区三要素不可分离。 */
public enum InstantPrecision {
    SECOND("秒"), MINUTE("分"), HOUR("时"), DAY("天");

    private final String label;

    InstantPrecision(String label) { this.label = label; }

    public String label() { return label; }
}

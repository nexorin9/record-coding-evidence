package com.chaosforge.recordcoding.domain;

import java.util.Objects;

/** 字段缺失以显式 Missing 表达，禁止以空串 / 0 / 「未知」充当缺字段。 */
public final class Field<T> {
    private final T value;
    private final String missingReason;

    private Field(T value, String missingReason) {
        this.value = value;
        this.missingReason = missingReason;
    }

    public static <T> Field<T> present(T value) {
        return new Field<>(Objects.requireNonNull(value, "present 不接受 null"), null);
    }

    public static <T> Field<T> missing(String reason) {
        return new Field<>(null, reason == null || reason.isBlank() ? "字段缺失" : reason);
    }

    public boolean isPresent() { return missingReason == null; }
    public boolean isMissing() { return missingReason != null; }

    public T value() {
        if (isMissing()) throw new IllegalStateException("字段缺失：" + missingReason);
        return value;
    }

    public T orElse(T fallback) { return isMissing() ? fallback : value; }

    public String missingReason() { return missingReason; }

    public String display() {
        return isMissing() ? "[缺失:" + missingReason + "]" : String.valueOf(value);
    }

    public static <T> Field<T> of(T value, String missingReason) {
        return value == null ? missing(missingReason) : present(value);
    }
}

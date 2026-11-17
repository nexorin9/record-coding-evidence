package com.chaosforge.recordcoding.coding;

/** 字典结论：命中 / 未命中 / 字典不可用三态，降级时绝不产出「编码不存在」。 */
public record DictionaryResult(State state, String code, String name, String reason) {
    public enum State { FOUND, NOT_FOUND, DICTIONARY_UNAVAILABLE }

    public static DictionaryResult found(String code, String name) {
        return new DictionaryResult(State.FOUND, code, name, "");
    }

    public static DictionaryResult notFound(String code) {
        return new DictionaryResult(State.NOT_FOUND, code, "", "字典中未收录该编码");
    }

    public static DictionaryResult unavailable(String reason) {
        return new DictionaryResult(State.DICTIONARY_UNAVAILABLE, "", "",
                "字典不可用，未做存在性判定：" + reason);
    }

    public boolean isUnavailable() { return state == State.DICTIONARY_UNAVAILABLE; }
    public boolean isExistenceConclusion() { return state == State.FOUND || state == State.NOT_FOUND; }
}

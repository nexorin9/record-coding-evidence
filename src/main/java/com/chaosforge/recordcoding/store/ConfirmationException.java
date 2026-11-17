package com.chaosforge.recordcoding.store;

/** 确认动作被拒（驳回缺理由 / 非法迁移 / 提交后改判定）。 */
public class ConfirmationException extends RuntimeException {
    private final String code;
    public ConfirmationException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}

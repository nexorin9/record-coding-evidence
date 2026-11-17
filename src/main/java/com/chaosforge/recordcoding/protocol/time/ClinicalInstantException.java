package com.chaosforge.recordcoding.protocol.time;

/** 非法时刻：带原始串与字段定位，不静默替换为当前时间。 */
public class ClinicalInstantException extends RuntimeException {
    private final String rawValue;
    private final String fieldLocator;

    public ClinicalInstantException(String message, String rawValue, String fieldLocator) {
        super(message + "（原始值：" + rawValue + "，字段：" + fieldLocator + "）");
        this.rawValue = rawValue;
        this.fieldLocator = fieldLocator;
    }

    public String rawValue() { return rawValue; }
    public String fieldLocator() { return fieldLocator; }
}

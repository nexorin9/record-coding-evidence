package com.chaosforge.recordcoding.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

/** 配置解析安全处理：关闭外部实体与 DTD，避免 XXE。 */
public final class SecureParsers {
    private SecureParsers() {}

    public static SAXParserFactory secureSaxFactory() {
        SAXParserFactory f = SAXParserFactory.newInstance();
        setFeature(f, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(f, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(f, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try { f.setXIncludeAware(false); } catch (UnsupportedOperationException ignored) { /* 老实现忽略 */ }
        return f;
    }

    private static void setFeature(SAXParserFactory f, String name, boolean value) {
        try { f.setFeature(name, value); } catch (Exception ignored) { /* 不支持的特性跳过 */ }
    }

    /** JSON 配置中若出现 DTD/实体声明直接拒绝，绝不解析。 */
    public static void rejectDtd(String text) {
        if (text != null && (text.contains("<!DOCTYPE") || text.contains("<!ENTITY"))) {
            throw new IllegalArgumentException("配置禁止 DTD 与外部实体声明");
        }
    }
}

package com.chaosforge.recordcoding.protocol.hl7;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 分隔符取自 MSH 段（字段 / 组件 / 重复 / 转义 / 子组件 / 截断），不硬编码。 */
public final class Hl7Delimiter {
    private final char field;
    private final char component;
    private final char repetition;
    private final char escape;
    private final char subcomponent;
    private final char truncation;

    public Hl7Delimiter(char field, char component, char repetition, char escape,
                        char subcomponent, char truncation) {
        this.field = field;
        this.component = component;
        this.repetition = repetition;
        this.escape = escape;
        this.subcomponent = subcomponent;
        this.truncation = truncation;
    }

    public static final Hl7Delimiter STANDARD = new Hl7Delimiter('|', '^', '~', '\\', '&', '#');

    /** MSH 段形如 MSH|^~\&|... ；编码字符位于下标 4..7（第 5 位截断符可选）。 */
    public static Hl7Delimiter fromMsh(String mshLine) {
        if (mshLine == null || mshLine.length() < 8 || !mshLine.startsWith("MSH")) {
            throw new IllegalArgumentException("MSH 段过短或缺失，无法取分隔符");
        }
        char f = mshLine.charAt(3);
        char c = mshLine.charAt(4);
        char r = mshLine.charAt(5);
        char e = mshLine.charAt(6);
        char s = mshLine.charAt(7);
        char t = (mshLine.length() > 8 && mshLine.charAt(8) != f) ? mshLine.charAt(8) : '#';
        return new Hl7Delimiter(f, c, r, e, s, t);
    }

    public char field() { return field; }
    public char component() { return component; }
    public char repetition() { return repetition; }
    public char escape() { return escape; }
    public char subcomponent() { return subcomponent; }
    public char truncation() { return truncation; }

    public String encodingChars() {
        return String.valueOf(new char[] {component, repetition, escape, subcomponent, truncation});
    }

    /** 还原 HL7 转义（\F\ \S\ \R\ \T\ \E\），保留其他内容原样。 */
    public String unescape(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String e = String.valueOf(escape);
        return text
                .replace(e + "F" + e, String.valueOf(field))
                .replace(e + "S" + e, String.valueOf(component))
                .replace(e + "R" + e, String.valueOf(repetition))
                .replace(e + "T" + e, String.valueOf(subcomponent))
                .replace(e + "E" + e, e);
    }

    public List<String> components(String fieldValue) { return split(fieldValue, component); }

    public List<String> subcomponents(String componentValue) { return split(componentValue, subcomponent); }

    /** 重复字段切分（重复分隔符），保留空项以区分 empty 与 unmapped。 */
    public List<String> repetitions(String fieldValue) { return split(fieldValue, repetition); }

    private List<String> split(String value, char sep) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        String[] parts = value.split(Pattern.quote(String.valueOf(sep)), -1);
        for (String p : parts) out.add(unescape(p));
        return out;
    }
}

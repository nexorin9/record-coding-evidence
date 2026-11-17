package com.chaosforge.recordcoding.protocol.hl7;

import java.util.List;

/** HL7 段：段名 + 1-based 字段列表；分词保留原始分隔符内容，取值时再还原转义。 */
public final class Hl7Segment {
    private final String name;
    private final List<String> fields;
    private final Hl7Delimiter delimiter;

    public Hl7Segment(String name, List<String> fields, Hl7Delimiter delimiter) {
        this.name = name;
        this.fields = List.copyOf(fields);
        this.delimiter = delimiter;
    }

    public String name() { return name; }
    public int fieldCount() { return fields.size(); }
    public Hl7Delimiter delimiter() { return delimiter; }
    public List<String> rawFields() { return fields; }

    /** 1-based 字段号；越界返回空串，由映射层区分 empty 与 unmapped。 */
    public String field(int n) {
        return n >= 1 && n <= fields.size() ? fields.get(n - 1) : "";
    }

    public boolean hasField(int n) { return n >= 1 && n <= fields.size(); }

    public String unescaped(int n) { return delimiter.unescape(field(n)); }

    public List<String> components(int n) { return delimiter.components(field(n)); }

    public List<String> repetitions(int n) { return delimiter.repetitions(field(n)); }
}

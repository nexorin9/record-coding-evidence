package com.chaosforge.recordcoding.protocol.hl7;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HL7 v2 严格分词：分隔符取自 MSH，保留分隔符内容，还原转义与重复转义；
 * 段长 &lt; 3 直接抛错并计入错误码，绝不跳过该段。
 */
public final class Hl7Parser {
    private Hl7Parser() {}

    public static Hl7Message parse(String message) {
        if (message == null || message.isBlank()) {
            throw new Hl7ParseException("HL7_EMPTY", "HL7 报文为空", 0);
        }
        String[] lines = message.replace("\r\n", "\n").split("[\r\n]");
        Hl7Delimiter d = Hl7Delimiter.fromMsh(lines[0]);
        String sep = Pattern.quote(String.valueOf(d.field()));
        List<Hl7Segment> segments = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            if (line.length() < 3) {
                throw new Hl7ParseException("SEGMENT_TOO_SHORT",
                        "段长 < 3，原始段：" + line + "（第 " + (i + 1) + " 行）", i + 1);
            }
            String[] parts = line.split(sep, -1);
            String name = parts[0];
            if (name.length() != 3) {
                throw new Hl7ParseException("SEGMENT_NAME_INVALID",
                        "段名不是 3 字符：" + name, i + 1);
            }
            List<String> fields = new ArrayList<>();
            if ("MSH".equals(name)) {
                // MSH-1 = 字段分隔符；MSH-2 = 编码字符；其后从拆分结果的第 3 项起顺序对齐
                fields.add(String.valueOf(d.field()));
                fields.add(line.length() >= 8 ? line.substring(4, 8) : d.encodingChars());
                for (int j = 2; j < parts.length; j++) fields.add(parts[j]);
            } else {
                for (int j = 1; j < parts.length; j++) fields.add(parts[j]);
            }
            segments.add(new Hl7Segment(name, fields, d));
        }
        if (segments.isEmpty()) throw new Hl7ParseException("HL7_EMPTY", "无有效段", 0);
        return new Hl7Message(segments, d);
    }

    public static Hl7Message parse(byte[] bytes, Charset charset) {
        return parse(new String(bytes, charset));
    }
}

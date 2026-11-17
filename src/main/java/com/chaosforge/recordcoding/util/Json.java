package com.chaosforge.recordcoding.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖迷你 JSON 解析与生成（仅标准 JSON 类型）。
 * 解析失败统一抛 {@link JsonException}，调用方据此归入「结构校验失败」错误码，不静默填空。
 */
public final class Json {
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }

    private Json() {}

    public static Object parse(String text) {
        if (text == null || text.isBlank()) throw new JsonException("JSON 内容为空");
        Parser p = new Parser(text);
        Object v = p.value();
        p.ws();
        if (!p.end()) throw new JsonException("JSON 末尾存在多余字符，位置 " + p.pos);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new JsonException("期望 JSON 对象");
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(String text) {
        Object v = parse(text);
        if (!(v instanceof List)) throw new JsonException("期望 JSON 数组");
        return new ArrayList<>((List<Object>) v);
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { quote(s, sb); return; }
        if (v instanceof Boolean || v instanceof Number) { sb.append(v); return; }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(o, sb);
            }
            sb.append(']');
            return;
        }
        quote(String.valueOf(v), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** 取字符串字段；缺失或非字符串返回 null，由调用方显式标注缺失。 */
    public static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String s ? s : null;
    }

    /** DICOM JSON 单 tag 元素的 Value 首值；无 Value 返回 null。 */
    public static String firstValue(Map<String, Object> element) {
        Object v = element == null ? null : element.get("Value");
        if (v instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return null;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        boolean end() { return pos >= s.length(); }

        void ws() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object value() {
            ws();
            if (end()) throw new JsonException("JSON 意外结束");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> obj();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> num();
            };
        }

        private Object literal(String lit, Object val) {
            if (!s.startsWith(lit, pos)) throw new JsonException("非法字面量，位置 " + pos);
            pos += lit.length();
            return val;
        }

        private Map<String, Object> obj() {
            pos++;
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (!end() && s.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                ws();
                if (end() || s.charAt(pos) != '"') throw new JsonException("对象键必须是字符串，位置 " + pos);
                String k = str();
                ws();
                if (end() || s.charAt(pos) != ':') throw new JsonException("对象缺少冒号，位置 " + pos);
                pos++;
                m.put(k, value());
                ws();
                if (end()) throw new JsonException("对象未闭合");
                char d = s.charAt(pos++);
                if (d == '}') return m;
                if (d != ',') throw new JsonException("对象缺少逗号，位置 " + (pos - 1));
            }
        }

        private List<Object> arr() {
            pos++;
            List<Object> l = new ArrayList<>();
            ws();
            if (!end() && s.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (end()) throw new JsonException("数组未闭合");
                char d = s.charAt(pos++);
                if (d == ']') return l;
                if (d != ',') throw new JsonException("数组缺少逗号，位置 " + (pos - 1));
            }
        }

        private String str() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (!end()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (end()) throw new JsonException("转义未闭合");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new JsonException("\\u 转义不完整");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new JsonException("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("字符串未闭合");
        }

        private Number num() {
            int start = pos;
            if (!end() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            while (!end()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') pos++;
                else break;
            }
            if (start == pos) throw new JsonException("非法值，位置 " + start);
            String n = s.substring(start, pos);
            try {
                if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.valueOf(n);
                return Long.valueOf(n);
            } catch (NumberFormatException ex) {
                throw new JsonException("数字格式非法：" + n);
            }
        }
    }
}

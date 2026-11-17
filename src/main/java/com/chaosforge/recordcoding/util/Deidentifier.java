package com.chaosforge.recordcoding.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 去标识过滤器：日志与 SQLite 只落去标识字段，保留住院号作为业务主键。 */
public final class Deidentifier {
    private static final List<String> PII_KEYS = List.of(
            "name", "patient_name", "姓名", "patientName", "id_card", "身份证", "身份证号",
            "idCard", "phone", "mobile", "联系方式", "address", "住址", "telecom");
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[0-9Xx]");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    private Deidentifier() {}

    public static boolean isPiiKey(String key) {
        if (key == null) return false;
        for (String k : PII_KEYS) if (k.equalsIgnoreCase(key)) return true;
        return false;
    }

    public static String scrub(String text) {
        if (text == null) return "";
        String s = ID_CARD.matcher(text).replaceAll("[已去标识]");
        return PHONE.matcher(s).replaceAll("[已去标识]");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> scrubMap(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (isPiiKey(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) out.put(e.getKey(), scrubMap((Map<String, Object>) m));
            else if (v instanceof List<?> l) {
                List<Object> cleaned = new ArrayList<>();
                for (Object o : l) {
                    if (o instanceof Map<?, ?> im) cleaned.add(scrubMap((Map<String, Object>) im));
                    else cleaned.add(o instanceof String s ? scrub(s) : o);
                }
                out.put(e.getKey(), cleaned);
            } else if (v instanceof String s) out.put(e.getKey(), scrub(s));
            else out.put(e.getKey(), v);
        }
        return out;
    }

    public static boolean containsPii(String text) {
        return text != null && (ID_CARD.matcher(text).find() || PHONE.matcher(text).find());
    }
}
